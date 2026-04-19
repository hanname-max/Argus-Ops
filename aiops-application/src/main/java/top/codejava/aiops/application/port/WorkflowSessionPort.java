package top.codejava.aiops.application.port;

import java.util.Optional;

import top.codejava.aiops.application.dto.WorkflowModels;

public interface WorkflowSessionPort {

    Optional<WorkflowModels.WorkflowSession> findById(String workflowId);

    WorkflowModels.WorkflowSession save(WorkflowModels.WorkflowSession session);
}
