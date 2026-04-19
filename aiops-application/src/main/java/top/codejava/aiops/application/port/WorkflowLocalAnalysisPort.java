package top.codejava.aiops.application.port;

import top.codejava.aiops.application.dto.WorkflowModels;

public interface WorkflowLocalAnalysisPort {

    WorkflowModels.LocalAnalysisPayload analyze(WorkflowModels.AnalyzeLocalRequest request);
}
