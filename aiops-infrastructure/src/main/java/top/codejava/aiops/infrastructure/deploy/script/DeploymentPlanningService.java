package top.codejava.aiops.infrastructure.deploy.script;

import org.springframework.stereotype.Component;
import top.codejava.aiops.application.dto.WorkflowModels;
import top.codejava.aiops.infrastructure.projectscan.ProjectScanService;
import top.codejava.aiops.infrastructure.projectscan.ProjectScanSnapshot;

import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class DeploymentPlanningService {

    private final ProjectScanService projectScanService;
    private final ProjectDeploymentRuleEngine projectDeploymentRuleEngine;
    private final DeploymentPlanRenderer deploymentPlanRenderer;

    public DeploymentPlanningService(ProjectScanService projectScanService,
                                     ProjectDeploymentRuleEngine projectDeploymentRuleEngine,
                                     DeploymentPlanRenderer deploymentPlanRenderer) {
        this.projectScanService = projectScanService;
        this.projectDeploymentRuleEngine = projectDeploymentRuleEngine;
        this.deploymentPlanRenderer = deploymentPlanRenderer;
    }

    public ResolvedDeploymentPlan planForProject(Path projectRoot, Integer requestedPort, String remoteWorkspacePath) {
        ProjectScanSnapshot scanSnapshot = projectScanService.scan(projectRoot);
        DeploymentDetectionContext context = new DeploymentDetectionContext(
                projectRoot.toAbsolutePath().normalize(),
                normalizePort(requestedPort),
                scanSnapshot
        );
        DeploymentPlan deploymentPlan = projectDeploymentRuleEngine.resolve(context);
        return new ResolvedDeploymentPlan(
                scanSnapshot,
                deploymentPlan,
                deploymentPlanRenderer.renderPreview(deploymentPlan),
                deploymentPlanRenderer.renderExecution(deploymentPlan, remoteWorkspacePath)
        );
    }

    public ResolvedDeploymentPlan planForWorkflow(WorkflowModels.ScriptGenerationRequest request) {
        Path projectRoot = resolveProjectRoot(request);
        if (projectRoot != null && Files.isDirectory(projectRoot)) {
            Integer recommendedPort = request != null && request.metadata() != null
                    ? request.metadata().recommendedPort()
                    : null;
            return planForProject(projectRoot, recommendedPort, null);
        }
        DeploymentPlan fallback = fallbackForWorkflow(request);
        return new ResolvedDeploymentPlan(
                null,
                fallback,
                deploymentPlanRenderer.renderPreview(fallback),
                deploymentPlanRenderer.renderExecution(fallback, null)
        );
    }

    private Path resolveProjectRoot(WorkflowModels.ScriptGenerationRequest request) {
        if (request == null || request.localContext() == null || request.localContext().configEvidences() == null) {
            return null;
        }
        return request.localContext().configEvidences().stream()
                .filter(evidence -> "projectPath".equalsIgnoreCase(evidence.key()))
                .map(WorkflowModels.ConfigEvidence::valuePreview)
                .filter(value -> value != null && !value.isBlank())
                .map(Path::of)
                .map(Path::toAbsolutePath)
                .map(Path::normalize)
                .findFirst()
                .orElse(null);
    }

    private DeploymentPlan fallbackForWorkflow(WorkflowModels.ScriptGenerationRequest request) {
        String port = request != null && request.metadata() != null && request.metadata().recommendedPort() != null
                ? String.valueOf(request.metadata().recommendedPort())
                : "8080";
        String body = """
                echo "[AIOPS] strategy=workflow-fallback"
                echo "Unable to resolve the local project path from workflow context."
                echo "Recommended port: %s"
                exit 1
                """.formatted(port);
        return new DeploymentPlan("WORKFLOW_FALLBACK", "Could not restore the project path from workflow context.", body);
    }

    private int normalizePort(Integer port) {
        return port == null || port <= 0 ? 8080 : port;
    }
}
