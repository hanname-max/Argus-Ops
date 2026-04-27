package top.codejava.aiops.infrastructure.workflow;

import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import top.codejava.aiops.application.dto.WorkflowModels;
import top.codejava.aiops.application.port.WorkflowScriptGenerationPort;
import top.codejava.aiops.infrastructure.deploy.script.ProjectDeploymentRuleEngine;

@Component
public class DeterministicWorkflowScriptGenerationAdapter implements WorkflowScriptGenerationPort {

    private final ProjectDeploymentRuleEngine projectDeploymentRuleEngine;

    public DeterministicWorkflowScriptGenerationAdapter(ProjectDeploymentRuleEngine projectDeploymentRuleEngine) {
        this.projectDeploymentRuleEngine = projectDeploymentRuleEngine;
    }

    @Override
    public Flux<String> streamScript(WorkflowModels.ScriptGenerationRequest request) {
        return chunkText(projectDeploymentRuleEngine.renderPreviewScript(request));
    }

    private Flux<String> chunkText(String text) {
        return Flux.fromArray(text.split("(?<=\\G.{80})"))
                .filter(chunk -> chunk != null && !chunk.isBlank());
    }
}
