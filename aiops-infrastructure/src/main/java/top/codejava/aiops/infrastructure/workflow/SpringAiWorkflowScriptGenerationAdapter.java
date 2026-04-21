package top.codejava.aiops.infrastructure.workflow;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import top.codejava.aiops.application.dto.WorkflowModels;
import top.codejava.aiops.application.port.WorkflowScriptGenerationPort;
import top.codejava.aiops.infrastructure.deploy.script.DeploymentPlanningService;

@Component
public class SpringAiWorkflowScriptGenerationAdapter implements WorkflowScriptGenerationPort {

    @SuppressWarnings("unused")
    private final ChatClient localChatClient;
    @SuppressWarnings("unused")
    private final String apiKey;
    @SuppressWarnings("unused")
    private final String model;
    private final DeploymentPlanningService deploymentPlanningService;

    public SpringAiWorkflowScriptGenerationAdapter(@Qualifier("localChatClient") ChatClient localChatClient,
                                                   @Value("${aiops.local.api-key:}") String apiKey,
                                                   @Value("${aiops.local.model:gpt-4o-mini}") String model,
                                                   DeploymentPlanningService deploymentPlanningService) {
        this.localChatClient = localChatClient;
        this.apiKey = apiKey;
        this.model = model;
        this.deploymentPlanningService = deploymentPlanningService;
    }

    @Override
    public Flux<String> streamScript(WorkflowModels.ScriptGenerationRequest request) {
        return chunkText(deploymentPlanningService.planForWorkflow(request).previewScript());
    }

    private Flux<String> chunkText(String text) {
        return Flux.fromArray(text.split("(?<=\\G.{80})"))
                .filter(chunk -> chunk != null && !chunk.isBlank());
    }
}
