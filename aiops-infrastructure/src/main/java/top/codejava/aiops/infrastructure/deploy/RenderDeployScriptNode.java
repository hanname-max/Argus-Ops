package top.codejava.aiops.infrastructure.deploy;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import top.codejava.aiops.infrastructure.deploy.script.DeploymentPlan;
import top.codejava.aiops.infrastructure.deploy.script.ProjectDeploymentRuleEngine;

import java.nio.file.Path;

@Component
@Order(50)
public class RenderDeployScriptNode implements DeployNode {

    private final ProjectDeploymentRuleEngine projectDeploymentRuleEngine;

    public RenderDeployScriptNode(ProjectDeploymentRuleEngine projectDeploymentRuleEngine) {
        this.projectDeploymentRuleEngine = projectDeploymentRuleEngine;
    }

    @Override
    public void apply(DeployContext context) {
        Path projectRoot = Path.of(context.request().projectPath()).toAbsolutePath().normalize();
        DeploymentPlan plan = projectDeploymentRuleEngine.resolveForProject(projectRoot, context.request().applicationPort());
        context.renderedDeployCommand(
                projectDeploymentRuleEngine.renderExecutionScript(
                        projectRoot,
                        context.remoteWorkspacePath(),
                        context.request().applicationPort()
                )
        );
        context.progressMessages().add("Resolved deployment strategy: " + plan.strategyKey() + " - " + plan.summary());
    }
}
