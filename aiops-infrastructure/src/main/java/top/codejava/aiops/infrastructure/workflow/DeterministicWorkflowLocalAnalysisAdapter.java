package top.codejava.aiops.infrastructure.workflow;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;
import top.codejava.aiops.application.dto.WorkflowModels;
import top.codejava.aiops.application.port.WorkflowLocalAnalysisPort;
import top.codejava.aiops.type.exception.ValidationException;

@Component
public class DeterministicWorkflowLocalAnalysisAdapter implements WorkflowLocalAnalysisPort {

    @Override
    public WorkflowModels.LocalAnalysisPayload analyze(WorkflowModels.AnalyzeLocalRequest request) {
        Path projectPath = Path.of(request.projectPath()).toAbsolutePath().normalize();
        if (!Files.exists(projectPath) || !Files.isDirectory(projectPath)) {
            throw new ValidationException("projectPath must be an existing directory: " + projectPath);
        }

        Set<String> rootEntries = listRootEntries(projectPath);
        List<String> fileNames = listFileNames(projectPath, 4);
        String projectName = projectPath.getFileName() == null ? projectPath.toString() : projectPath.getFileName().toString();

        boolean hasPom = rootEntries.contains("pom.xml");
        boolean hasGradle = rootEntries.contains("build.gradle") || rootEntries.contains("build.gradle.kts");
        boolean hasPackageJson = rootEntries.contains("package.json");
        boolean hasPyproject = rootEntries.contains("pyproject.toml") || rootEntries.contains("requirements.txt");
        boolean hasGoMod = rootEntries.contains("go.mod");
        boolean hasCargo = rootEntries.contains("cargo.toml");
        boolean hasIndexHtml = rootEntries.contains("index.html");

        String language = "unknown";
        String framework = "generic";
        String buildTool = "unknown";
        String packaging = "generic";
        Integer defaultPort = 8080;
        String jdkVersion = null;

        List<WorkflowModels.StackComponent> components = new ArrayList<>();
        List<WorkflowModels.ConfigEvidence> evidences = new ArrayList<>();
        List<WorkflowModels.WorkflowWarning> warnings = new ArrayList<>();

        if (hasPom || hasGradle) {
            language = "Java";
            buildTool = hasPom ? "maven" : "gradle";
            packaging = "jar";
            framework = detectJavaFramework(projectPath);
            defaultPort = detectDefaultPort(projectPath, 8080);
            jdkVersion = detectJavaRelease(projectPath);
            components.add(new WorkflowModels.StackComponent("Spring Boot", framework.startsWith("Spring Boot") ? extractSuffix(framework) : null, "web-framework"));
        } else if (hasPackageJson) {
            language = "JavaScript";
            buildTool = detectNodeBuildTool(rootEntries);
            framework = detectNodeFramework(projectPath);
            packaging = framework.contains("Next") ? "node-runtime" : "frontend-bundle";
            defaultPort = framework.contains("Next") ? 3000 : 8080;
            components.add(new WorkflowModels.StackComponent(framework, null, "web-framework"));
        } else if (hasPyproject) {
            language = "Python";
            buildTool = "pip";
            framework = detectPythonFramework(fileNames);
            packaging = "python-app";
            defaultPort = 8000;
            components.add(new WorkflowModels.StackComponent(framework, null, "web-framework"));
        } else if (hasGoMod) {
            language = "Go";
            buildTool = "go";
            framework = "Go HTTP Service";
            packaging = "go-binary";
            defaultPort = 8080;
            components.add(new WorkflowModels.StackComponent("Go", null, "runtime"));
        } else if (hasCargo) {
            language = "Rust";
            buildTool = "cargo";
            framework = "Rust Service";
            packaging = "native-binary";
            defaultPort = 8080;
            components.add(new WorkflowModels.StackComponent("Rust", null, "runtime"));
        } else if (hasIndexHtml) {
            language = "Static";
            buildTool = "none";
            framework = "Static Site";
            packaging = "nginx-static";
            defaultPort = 80;
            components.add(new WorkflowModels.StackComponent("Nginx", "1.27-alpine", "runtime"));
            warnings.add(new WorkflowModels.WorkflowWarning(
                    "STATIC_SITE_NO_BUILD",
                    WorkflowModels.Severity.INFO,
                    "Static assets detected. Docker deployment will use nginx rather than a language runtime.",
                    "Verify root html and asset directories before deployment."
            ));
        }

        evidences.add(new WorkflowModels.ConfigEvidence("filesystem", "projectPath", projectPath.toString(), false));
        evidences.add(new WorkflowModels.ConfigEvidence("inference", "default.port", String.valueOf(defaultPort), true));
        if (jdkVersion != null) {
            evidences.add(new WorkflowModels.ConfigEvidence("build-descriptor", "java.release", jdkVersion, false));
        }
        if ("unknown".equalsIgnoreCase(language)) {
            warnings.add(new WorkflowModels.WorkflowWarning(
                    "STACK_UNKNOWN",
                    WorkflowModels.Severity.MEDIUM,
                    "Project stack could not be inferred confidently.",
                    "Review generated script before running it on a target host."
            ));
        }

        WorkflowModels.LocalProjectContext context = new WorkflowModels.LocalProjectContext(
                projectName,
                language,
                framework,
                buildTool,
                packaging,
                jdkVersion,
                defaultPort,
                List.copyOf(components),
                List.copyOf(evidences)
        );
        return new WorkflowModels.LocalAnalysisPayload(context, List.copyOf(warnings));
    }

    private String detectJavaFramework(Path projectPath) {
        String pom = readIfExists(projectPath.resolve("pom.xml"));
        String gradle = readIfExists(projectPath.resolve("build.gradle"));
        String gradleKts = readIfExists(projectPath.resolve("build.gradle.kts"));
        String combined = (pom + gradle + gradleKts).toLowerCase(Locale.ROOT);
        if (combined.contains("spring-boot")) {
            return "Spring Boot";
        }
        return "Java Service";
    }

    private String detectNodeFramework(Path projectPath) {
        String packageJson = readIfExists(projectPath.resolve("package.json")).toLowerCase(Locale.ROOT);
        if (packageJson.contains("\"next\"")) {
            return "Next.js";
        }
        if (packageJson.contains("\"react\"")) {
            return "React";
        }
        if (packageJson.contains("\"vue\"")) {
            return "Vue";
        }
        if (packageJson.contains("\"@angular/core\"")) {
            return "Angular";
        }
        return "Node Application";
    }

    private String detectPythonFramework(List<String> fileNames) {
        if (fileNames.stream().anyMatch(name -> name.equalsIgnoreCase("manage.py"))) {
            return "Django";
        }
        if (fileNames.stream().anyMatch(name -> name.equalsIgnoreCase("app.py") || name.equalsIgnoreCase("main.py"))) {
            return "Python Service";
        }
        return "Python Application";
    }

    private String detectNodeBuildTool(Set<String> rootEntries) {
        if (rootEntries.contains("pnpm-lock.yaml")) {
            return "pnpm";
        }
        if (rootEntries.contains("yarn.lock")) {
            return "yarn";
        }
        return "npm";
    }

    private Integer detectDefaultPort(Path projectPath, int fallback) {
        String applicationYaml = readIfExists(projectPath.resolve("src/main/resources/application.yml"));
        String applicationYamlAlt = readIfExists(projectPath.resolve("src/main/resources/application.yaml"));
        String combined = applicationYaml + "\n" + applicationYamlAlt;
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(?m)^\\s*port\\s*:\\s*(\\d+)\\s*$").matcher(combined);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : fallback;
    }

    private String detectJavaRelease(Path projectPath) {
        String pom = readIfExists(projectPath.resolve("pom.xml"));
        java.util.regex.Matcher releaseMatcher = java.util.regex.Pattern.compile("<java.version>([^<]+)</java.version>").matcher(pom);
        if (releaseMatcher.find()) {
            return releaseMatcher.group(1).trim();
        }
        String gradle = readIfExists(projectPath.resolve("build.gradle")) + readIfExists(projectPath.resolve("build.gradle.kts"));
        java.util.regex.Matcher toolchainMatcher = java.util.regex.Pattern.compile("languageVersion\\s*=\\s*JavaLanguageVersion\\.of\\((\\d+)\\)").matcher(gradle);
        return toolchainMatcher.find() ? toolchainMatcher.group(1).trim() : null;
    }

    private String extractSuffix(String framework) {
        int index = framework.indexOf(' ');
        return index > 0 && index < framework.length() - 1 ? framework.substring(index + 1) : null;
    }

    private Set<String> listRootEntries(Path projectPath) {
        try (Stream<Path> stream = Files.list(projectPath)) {
            return stream.map(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        } catch (IOException ignored) {
            return Set.of();
        }
    }

    private List<String> listFileNames(Path projectPath, int depth) {
        try (Stream<Path> stream = Files.walk(projectPath, depth)) {
            return stream.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .toList();
        } catch (IOException ignored) {
            return List.of();
        }
    }

    private String readIfExists(Path path) {
        try {
            return Files.exists(path) ? Files.readString(path) : "";
        } catch (IOException ignored) {
            return "";
        }
    }
}
