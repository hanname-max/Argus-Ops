package top.codejava.aiops.infrastructure.workflow.inspection;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
@Order(10)
public class ProjectTypeInspectionHandler implements LocalProjectInspectionHandler {

    @Override
    public boolean supports(LocalProjectInspectionContext context) {
        return true;
    }

    @Override
    public void handle(LocalProjectInspectionContext context) {
        Set<String> rootEntries = context.rootEntries();
        List<String> fileNames = context.fileNames();

        if (context.hasPom() || context.hasGradle()) {
            context.language("Java");
            context.buildTool(context.hasPom() ? "maven" : "gradle");
            context.packaging("jar");
            context.framework(detectJavaFramework(context.projectPath()));
            context.defaultPort(detectDefaultPort(context.projectPath(), 8080));
            context.jdkVersion(detectJavaRelease(context.projectPath()));
            context.addComponent("Spring Boot", context.framework().startsWith("Spring Boot") ? extractSuffix(context.framework()) : null, "web-framework");
        } else if (context.hasPackageJson()) {
            context.language("JavaScript");
            context.buildTool(detectNodeBuildTool(rootEntries));
            context.framework(detectNodeFramework(context.projectPath()));
            context.packaging(context.framework().contains("Next") || context.framework().contains("Service") ? "node-runtime" : "frontend-bundle");
            context.defaultPort(context.framework().contains("Next") ? 3000 : 8080);
            context.addComponent(context.framework(), null, "web-framework");
        } else if (context.hasPythonProject()) {
            context.language("Python");
            context.buildTool("pip");
            context.framework(detectPythonFramework(fileNames));
            context.packaging("python-app");
            context.defaultPort(8000);
            context.addComponent(context.framework(), null, "web-framework");
        } else if (context.hasGoMod()) {
            context.language("Go");
            context.buildTool("go");
            context.framework("Go HTTP Service");
            context.packaging("go-binary");
            context.defaultPort(8080);
            context.addComponent("Go", null, "runtime");
        } else if (context.hasCargo()) {
            context.language("Rust");
            context.buildTool("cargo");
            context.framework("Rust Service");
            context.packaging("native-binary");
            context.defaultPort(8080);
            context.addComponent("Rust", null, "runtime");
        } else if (context.hasIndexHtml()) {
            context.language("Static");
            context.buildTool("none");
            context.framework("Static Site");
            context.packaging("nginx-static");
            context.defaultPort(80);
            context.addComponent("Nginx", "1.27-alpine", "runtime");
            context.addWarning(
                    "STATIC_SITE_NO_BUILD",
                    top.codejava.aiops.application.dto.WorkflowModels.Severity.INFO,
                    "Static assets detected. Docker deployment will use nginx rather than a language runtime.",
                    "Verify root html and asset directories before deployment."
            );
        }

        context.addEvidence("filesystem", "projectPath", context.projectPath().toString(), false);
        context.addEvidence("inference", "default.port", String.valueOf(context.defaultPort()), true);
        if (context.jdkVersion() != null) {
            context.addEvidence("build-descriptor", "java.release", context.jdkVersion(), false);
        }
        if ("unknown".equalsIgnoreCase(context.language())) {
            context.addWarning(
                    "STACK_UNKNOWN",
                    top.codejava.aiops.application.dto.WorkflowModels.Severity.MEDIUM,
                    "Project stack could not be inferred confidently.",
                    "Review generated script before running it on a target host."
            );
        }
    }

    private String detectJavaFramework(Path projectPath) {
        String pom = InspectionFileSupport.readIfExists(projectPath.resolve("pom.xml"));
        String gradle = InspectionFileSupport.readIfExists(projectPath.resolve("build.gradle"));
        String gradleKts = InspectionFileSupport.readIfExists(projectPath.resolve("build.gradle.kts"));
        String combined = (pom + gradle + gradleKts).toLowerCase(Locale.ROOT);
        if (combined.contains("spring-boot")) {
            return "Spring Boot";
        }
        return "Java Service";
    }

    private String detectNodeFramework(Path projectPath) {
        String packageJson = InspectionFileSupport.readIfExists(projectPath.resolve("package.json")).toLowerCase(Locale.ROOT);
        if (packageJson.contains("\"next\"")) {
            return "Next.js";
        }
        if (packageJson.contains("\"express\"") || packageJson.contains("\"koa\"") || packageJson.contains("\"fastify\"") || packageJson.contains("\"@nestjs/core\"")) {
            return "Node Service";
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
        String applicationYaml = InspectionFileSupport.readIfExists(projectPath.resolve("src/main/resources/application.yml"));
        String applicationYamlAlt = InspectionFileSupport.readIfExists(projectPath.resolve("src/main/resources/application.yaml"));
        String combined = applicationYaml + "\n" + applicationYamlAlt;
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(?m)^\\s*port\\s*:\\s*(\\d+)\\s*$").matcher(combined);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : fallback;
    }

    private String detectJavaRelease(Path projectPath) {
        String pom = InspectionFileSupport.readIfExists(projectPath.resolve("pom.xml"));
        java.util.regex.Matcher releaseMatcher = java.util.regex.Pattern.compile("<java.version>([^<]+)</java.version>").matcher(pom);
        if (releaseMatcher.find()) {
            return releaseMatcher.group(1).trim();
        }
        String gradle = InspectionFileSupport.readIfExists(projectPath.resolve("build.gradle")) + InspectionFileSupport.readIfExists(projectPath.resolve("build.gradle.kts"));
        java.util.regex.Matcher toolchainMatcher = java.util.regex.Pattern.compile("languageVersion\\s*=\\s*JavaLanguageVersion\\.of\\((\\d+)\\)").matcher(gradle);
        return toolchainMatcher.find() ? toolchainMatcher.group(1).trim() : null;
    }

    private String extractSuffix(String framework) {
        int index = framework.indexOf(' ');
        return index > 0 && index < framework.length() - 1 ? framework.substring(index + 1) : null;
    }
}
