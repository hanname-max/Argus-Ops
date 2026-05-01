package top.codejava.aiops.infrastructure.deploy;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import top.codejava.aiops.application.dto.WorkflowModels;
import top.codejava.aiops.application.port.WorkflowSessionPort;
import top.codejava.aiops.infrastructure.deploy.script.DeploymentPlan;
import top.codejava.aiops.infrastructure.deploy.script.ProjectDeploymentRuleEngine;

import java.nio.file.Path;

@Component
@Order(50)
public class RenderDeployScriptNode implements DeployNode {

    private final ProjectDeploymentRuleEngine projectDeploymentRuleEngine;
    private final WorkflowSessionPort workflowSessionPort;

    public RenderDeployScriptNode(ProjectDeploymentRuleEngine projectDeploymentRuleEngine,
                                  WorkflowSessionPort workflowSessionPort) {
        this.projectDeploymentRuleEngine = projectDeploymentRuleEngine;
        this.workflowSessionPort = workflowSessionPort;
    }

    @Override
    public void apply(DeployContext context) {
        Path projectRoot = Path.of(context.request().projectPath()).toAbsolutePath().normalize();
        WorkflowModels.WorkflowSession session = context.request().workflowId() == null || context.request().workflowId().isBlank()
                ? null
                : workflowSessionPort.findById(context.request().workflowId().trim()).orElse(null);

        if (session != null && session.localContext() != null) {
            WorkflowModels.ScriptGenerationMetadata metadata = session.scriptMetadata() != null
                    ? session.scriptMetadata()
                    : new WorkflowModels.ScriptGenerationMetadata(
                    "bash",
                    "linux",
                    context.request().applicationPort(),
                    true,
                    "Local Rule Engine",
                    "deterministic"
            );
            WorkflowModels.ScriptGenerationRequest generationRequest = new WorkflowModels.ScriptGenerationRequest(
                    session.workflowId(),
                    session.localContext(),
                    session.targetProfile(),
                    session.portProbeDecision(),
                    session.dependencyProbeResults(),
                    session.dependencyDecisions(),
                    session.dependencyOverrides(),
                    metadata
            );
            DeploymentPlan plan = projectDeploymentRuleEngine.resolveForWorkflow(generationRequest);
            context.renderedDeployCommand(projectDeploymentRuleEngine.renderExecutionScript(
                    generationRequest,
                    context.remoteWorkspacePath()
            ));
            context.progressMessages().add("Resolved deployment strategy: " + plan.strategyKey() + " - " + plan.summary());
            return;
        }

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
