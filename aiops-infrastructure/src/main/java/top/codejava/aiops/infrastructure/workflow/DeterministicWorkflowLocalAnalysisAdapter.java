package top.codejava.aiops.infrastructure.workflow;

import org.springframework.stereotype.Component;
import top.codejava.aiops.application.dto.WorkflowModels;
import top.codejava.aiops.application.port.WorkflowLocalAnalysisPort;
import top.codejava.aiops.infrastructure.workflow.inspection.LocalProjectInspectionContext;
import top.codejava.aiops.infrastructure.workflow.inspection.LocalProjectInspectionHandler;
import top.codejava.aiops.type.chain.ChainHandler;
import top.codejava.aiops.type.chain.ChainExecutor;
import top.codejava.aiops.type.exception.ValidationException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Component
public class DeterministicWorkflowLocalAnalysisAdapter implements WorkflowLocalAnalysisPort {

    private final ChainExecutor<LocalProjectInspectionContext> chainExecutor;

    public DeterministicWorkflowLocalAnalysisAdapter(List<LocalProjectInspectionHandler> inspectionHandlers) {
        this.chainExecutor = new ChainExecutor<>(
                inspectionHandlers.stream()
                        .map(handler -> (ChainHandler<LocalProjectInspectionContext>) handler)
                        .toList()
        );
    }

    @Override
    public WorkflowModels.LocalAnalysisPayload analyze(WorkflowModels.AnalyzeLocalRequest request) {
        Path projectPath = Path.of(request.projectPath()).toAbsolutePath().normalize();
        if (!Files.exists(projectPath) || !Files.isDirectory(projectPath)) {
            throw new ValidationException("projectPath must be an existing directory: " + projectPath);
        }

        LocalProjectInspectionContext inspectionContext = new LocalProjectInspectionContext(projectPath);
        chainExecutor.execute(inspectionContext);

        return new WorkflowModels.LocalAnalysisPayload(
                inspectionContext.toLocalProjectContext(),
                List.copyOf(inspectionContext.warnings())
        );
    }
}
