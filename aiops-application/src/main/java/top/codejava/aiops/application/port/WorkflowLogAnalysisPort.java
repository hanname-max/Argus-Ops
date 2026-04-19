package top.codejava.aiops.application.port;

import top.codejava.aiops.application.dto.WorkflowModels;

public interface WorkflowLogAnalysisPort {

    WorkflowModels.LogDiagnosisPayload analyze(WorkflowModels.AnalyzeLogRequest request,
                                               WorkflowModels.WorkflowSession session);
}
