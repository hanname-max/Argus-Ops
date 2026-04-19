package top.codejava.aiops.infrastructure.workflow;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.stereotype.Component;
import top.codejava.aiops.application.dto.WorkflowModels;
import top.codejava.aiops.application.port.WorkflowSessionPort;

@Component
public class InMemoryWorkflowSessionAdapter implements WorkflowSessionPort {

    private final ConcurrentMap<String, WorkflowModels.WorkflowSession> sessions = new ConcurrentHashMap<>();

    @Override
    public Optional<WorkflowModels.WorkflowSession> findById(String workflowId) {
        return Optional.ofNullable(sessions.get(workflowId));
    }

    @Override
    public WorkflowModels.WorkflowSession save(WorkflowModels.WorkflowSession session) {
        sessions.put(session.workflowId(), session);
        return session;
    }
}
