package top.codejava.aiops.application.port;

import top.codejava.aiops.application.dto.WorkflowModels;

public interface WorkflowTargetProbePort {

    WorkflowModels.TargetProbePayload probe(WorkflowModels.ProbeTargetRequest request,
                                            WorkflowModels.LocalProjectContext localContext);
}
