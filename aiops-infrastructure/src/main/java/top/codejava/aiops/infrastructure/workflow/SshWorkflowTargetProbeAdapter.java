package top.codejava.aiops.infrastructure.workflow;

import org.springframework.stereotype.Component;
import top.codejava.aiops.application.dto.WorkflowModels;
import top.codejava.aiops.application.port.WorkflowTargetProbePort;
import top.codejava.aiops.infrastructure.workflow.probe.ProbeContext;
import top.codejava.aiops.infrastructure.workflow.probe.ProbeExecutionRouter;

import java.util.List;

@Component
public class SshWorkflowTargetProbeAdapter implements WorkflowTargetProbePort {

    private final ProbeExecutionRouter probeExecutionRouter;

    public SshWorkflowTargetProbeAdapter(ProbeExecutionRouter probeExecutionRouter) {
        this.probeExecutionRouter = probeExecutionRouter;
    }

    @Override
    public WorkflowModels.TargetProbePayload probe(WorkflowModels.ProbeTargetRequest request,
                                                   WorkflowModels.LocalProjectContext localContext) {
        ProbeContext probeContext = probeExecutionRouter.execute(new ProbeContext(request, localContext));
        return new WorkflowModels.TargetProbePayload(
                probeContext.targetProfile(),
                probeContext.portProbeDecision(),
                List.copyOf(probeContext.warnings())
        );
    }
}
