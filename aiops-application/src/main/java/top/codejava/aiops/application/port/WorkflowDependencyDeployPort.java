package top.codejava.aiops.application.port;

import top.codejava.aiops.application.dto.WorkflowModels;

public interface WorkflowDependencyDeployPort {

    DependencyDeployPayload deploy(WorkflowModels.DeployDependencyRequest request);

    record DependencyDeployPayload(
            boolean success,
            String message,
            String stdout,
            String stderr
    ) {
    }
}
