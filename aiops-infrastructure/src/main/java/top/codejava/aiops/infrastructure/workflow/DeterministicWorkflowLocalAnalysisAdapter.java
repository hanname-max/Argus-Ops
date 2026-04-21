package top.codejava.aiops.infrastructure.workflow;

import org.springframework.stereotype.Component;
import top.codejava.aiops.application.dto.WorkflowModels;
import top.codejava.aiops.application.port.WorkflowLocalAnalysisPort;
import top.codejava.aiops.infrastructure.projectscan.ProjectScanService;
import top.codejava.aiops.infrastructure.projectscan.ProjectScanSnapshot;
import top.codejava.aiops.type.exception.ValidationException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Component
public class DeterministicWorkflowLocalAnalysisAdapter implements WorkflowLocalAnalysisPort {

    private final ProjectScanService projectScanService;

    public DeterministicWorkflowLocalAnalysisAdapter(ProjectScanService projectScanService) {
        this.projectScanService = projectScanService;
    }

    @Override
    public WorkflowModels.LocalAnalysisPayload analyze(WorkflowModels.AnalyzeLocalRequest request) {
        Path projectPath = Path.of(request.projectPath()).toAbsolutePath().normalize();
        if (!Files.exists(projectPath) || !Files.isDirectory(projectPath)) {
            throw new ValidationException("projectPath must be an existing directory: " + projectPath);
        }

        ProjectScanSnapshot scanSnapshot = projectScanService.scan(projectPath);
        String projectName = projectPath.getFileName() == null ? projectPath.toString() : projectPath.getFileName().toString();

        String language = "unknown";
        String framework = "generic";
        String buildTool = "unknown";
        String packaging = "generic";
        Integer defaultPort = 8080;
        String jdkVersion = null;

        List<WorkflowModels.StackComponent> components = new ArrayList<>();
        List<WorkflowModels.ConfigEvidence> evidences = new ArrayList<>();
        List<WorkflowModels.WorkflowWarning> warnings = new ArrayList<>();

        if (scanSnapshot.hasPom() || scanSnapshot.hasGradle()) {
            language = "Java";
            buildTool = scanSnapshot.hasPom() ? "maven" : "gradle";
            packaging = "jar";
            framework = scanSnapshot.detectJavaFramework();
            defaultPort = scanSnapshot.detectDefaultPort(8080);
            jdkVersion = scanSnapshot.detectJavaRelease();
            components.add(new WorkflowModels.StackComponent("Spring Boot", framework.startsWith("Spring Boot") ? extractSuffix(framework) : null, "web-framework"));
        } else if (scanSnapshot.hasPackageJson()) {
            language = "JavaScript";
            buildTool = scanSnapshot.detectNodeBuildTool();
            framework = scanSnapshot.detectNodeFramework();
            packaging = framework.contains("Next") || framework.contains("Service") ? "node-runtime" : "frontend-bundle";
            defaultPort = framework.contains("Next") ? 3000 : 8080;
            components.add(new WorkflowModels.StackComponent(framework, null, "web-framework"));
        } else if (scanSnapshot.hasPythonProject()) {
            language = "Python";
            buildTool = "pip";
            framework = scanSnapshot.detectPythonFramework();
            packaging = "python-app";
            defaultPort = 8000;
            components.add(new WorkflowModels.StackComponent(framework, null, "web-framework"));
        } else if (scanSnapshot.hasGoMod()) {
            language = "Go";
            buildTool = "go";
            framework = "Go HTTP Service";
            packaging = "go-binary";
            defaultPort = 8080;
            components.add(new WorkflowModels.StackComponent("Go", null, "runtime"));
        } else if (scanSnapshot.hasCargoToml()) {
            language = "Rust";
            buildTool = "cargo";
            framework = "Rust Service";
            packaging = "native-binary";
            defaultPort = 8080;
            components.add(new WorkflowModels.StackComponent("Rust", null, "runtime"));
        } else if (scanSnapshot.hasIndexHtml()) {
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

    private String extractSuffix(String framework) {
        int index = framework.indexOf(' ');
        return index > 0 && index < framework.length() - 1 ? framework.substring(index + 1) : null;
    }
}
