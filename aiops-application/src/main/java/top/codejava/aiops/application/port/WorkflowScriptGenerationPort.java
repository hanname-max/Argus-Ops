package top.codejava.aiops.application.port;

import reactor.core.publisher.Flux;
import top.codejava.aiops.application.dto.WorkflowModels;

public interface WorkflowScriptGenerationPort {

    Flux<String> streamScript(WorkflowModels.ScriptGenerationRequest request);
}
