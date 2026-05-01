package top.codejava.aiops.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import top.codejava.aiops.application.port.WorkflowDependencyDeployPort;
import top.codejava.aiops.application.port.WorkflowLocalAnalysisPort;
import top.codejava.aiops.application.port.WorkflowLogAnalysisPort;
import top.codejava.aiops.application.port.WorkflowScriptGenerationPort;
import top.codejava.aiops.application.port.WorkflowSessionPort;
import top.codejava.aiops.application.port.WorkflowTargetProbePort;
import top.codejava.aiops.application.usecase.WorkflowUseCase;

@Configuration
public class UseCaseConfiguration {

    @Bean
    public WorkflowUseCase workflowUseCase(WorkflowSessionPort workflowSessionPort,
                                           WorkflowLocalAnalysisPort workflowLocalAnalysisPort,
                                           WorkflowTargetProbePort workflowTargetProbePort,
                                           WorkflowScriptGenerationPort workflowScriptGenerationPort,
                                           WorkflowLogAnalysisPort workflowLogAnalysisPort,
                                           WorkflowDependencyDeployPort workflowDependencyDeployPort) {
        return new WorkflowUseCase(
                workflowSessionPort,
                workflowLocalAnalysisPort,
                workflowTargetProbePort,
                workflowScriptGenerationPort,
                workflowLogAnalysisPort,
                workflowDependencyDeployPort
        );
    }
}
