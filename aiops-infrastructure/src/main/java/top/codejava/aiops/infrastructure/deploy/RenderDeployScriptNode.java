package top.codejava.aiops.infrastructure.deploy;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import top.codejava.aiops.infrastructure.deploy.script.DeploymentPlanningService;
import top.codejava.aiops.infrastructure.deploy.script.ResolvedDeploymentPlan;

import java.nio.file.Path;

@Component
@Order(50)
public class RenderDeployScriptNode implements DeployNode {

    private final DeploymentPlanningService deploymentPlanningService;

    public RenderDeployScriptNode(DeploymentPlanningService deploymentPlanningService) {
        this.deploymentPlanningService = deploymentPlanningService;
    }

    @Override
    public void apply(DeployContext context) {
        Path projectRoot = Path.of(context.request().projectPath()).toAbsolutePath().normalize();
        ResolvedDeploymentPlan resolvedDeploymentPlan = deploymentPlanningService.planForProject(
                projectRoot,
                context.request().applicationPort(),
                context.remoteWorkspacePath()
        );
        context.renderedDeployCommand(resolvedDeploymentPlan.executionScript());
        context.progressMessages().add(
                "Resolved deployment strategy: "
                        + resolvedDeploymentPlan.deploymentPlan().strategyKey()
                        + " - "
                        + resolvedDeploymentPlan.deploymentPlan().summary()
        );
    }
}
