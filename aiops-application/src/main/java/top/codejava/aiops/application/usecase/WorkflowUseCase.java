package top.codejava.aiops.application.usecase;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import reactor.core.publisher.Flux;
import top.codejava.aiops.application.dto.WorkflowModels;
import top.codejava.aiops.application.port.WorkflowLocalAnalysisPort;
import top.codejava.aiops.application.port.WorkflowLogAnalysisPort;
import top.codejava.aiops.application.port.WorkflowScriptGenerationPort;
import top.codejava.aiops.application.port.WorkflowSessionPort;
import top.codejava.aiops.application.port.WorkflowTargetProbePort;
import top.codejava.aiops.type.exception.ValidationException;

public class WorkflowUseCase {

    private static final List<WorkflowModels.WorkflowStage> ORDERED_STAGES = List.of(
            WorkflowModels.WorkflowStage.PREPARE_LOCAL,
            WorkflowModels.WorkflowStage.PROBE_REMOTE,
            WorkflowModels.WorkflowStage.DEPLOY_EXECUTION
    );

    private final WorkflowSessionPort workflowSessionPort;
    private final WorkflowLocalAnalysisPort workflowLocalAnalysisPort;
    private final WorkflowTargetProbePort workflowTargetProbePort;
    private final WorkflowScriptGenerationPort workflowScriptGenerationPort;
    private final WorkflowLogAnalysisPort workflowLogAnalysisPort;

    public WorkflowUseCase(WorkflowSessionPort workflowSessionPort,
                           WorkflowLocalAnalysisPort workflowLocalAnalysisPort,
                           WorkflowTargetProbePort workflowTargetProbePort,
                           WorkflowScriptGenerationPort workflowScriptGenerationPort,
                           WorkflowLogAnalysisPort workflowLogAnalysisPort) {
        this.workflowSessionPort = workflowSessionPort;
        this.workflowLocalAnalysisPort = workflowLocalAnalysisPort;
        this.workflowTargetProbePort = workflowTargetProbePort;
        this.workflowScriptGenerationPort = workflowScriptGenerationPort;
        this.workflowLogAnalysisPort = workflowLogAnalysisPort;
    }

    public WorkflowModels.AnalyzeLocalResponse analyzeLocal(WorkflowModels.AnalyzeLocalRequest request) {
        if (request == null || isBlank(request.projectPath())) {
            throw new ValidationException("projectPath is required");
        }

        WorkflowModels.WorkflowSession existingSession = findSession(request.workflowId()).orElse(null);
        assertVersion(existingSession, request.expectedStateVersion());

        String workflowId = isBlank(request.workflowId()) ? UUID.randomUUID().toString() : request.workflowId().trim();
        WorkflowModels.AnalyzeLocalRequest normalizedRequest = new WorkflowModels.AnalyzeLocalRequest(
                workflowId,
                request.expectedStateVersion(),
                request.projectPath(),
                request.operator(),
                request.includeDependencyGraph(),
                request.simulateCompile()
        );

        WorkflowModels.LocalAnalysisPayload payload = workflowLocalAnalysisPort.analyze(normalizedRequest);
        WorkflowModels.WorkflowSession saved = workflowSessionPort.save(new WorkflowModels.WorkflowSession(
                workflowId,
                nextVersion(existingSession),
                WorkflowModels.WorkflowStage.PREPARE_LOCAL,
                WorkflowModels.WorkflowStageStatus.COMPLETED,
                List.of(WorkflowModels.WorkflowStage.PREPARE_LOCAL),
                "Local preparation completed. Remote probing is now allowed.",
                Instant.now(),
                payload.context(),
                null,
                null,
                null
        ));

        return new WorkflowModels.AnalyzeLocalResponse(snapshot(saved), payload.context(), payload.warnings());
    }

    public WorkflowModels.ProbeTargetResponse probeTarget(WorkflowModels.ProbeTargetRequest request) {
        if (request == null || isBlank(request.workflowId())) {
            throw new ValidationException("workflowId is required");
        }
        if (request.credential() == null || isBlank(request.credential().host()) || isBlank(request.credential().username())) {
            throw new ValidationException("credential.host and credential.username are required");
        }

        WorkflowModels.WorkflowSession session = requiredSession(request.workflowId());
        assertVersion(session, request.expectedStateVersion());
        requireCompleted(session, WorkflowModels.WorkflowStage.PREPARE_LOCAL);

        Integer defaultPort = request.defaultApplicationPort() != null
                ? request.defaultApplicationPort()
                : session.localContext() != null ? session.localContext().defaultApplicationPort() : 8080;
        WorkflowModels.ProbeTargetRequest normalizedRequest = new WorkflowModels.ProbeTargetRequest(
                request.workflowId(),
                request.expectedStateVersion(),
                request.credential(),
                defaultPort,
                request.maxAutoIncrementProbeSpan()
        );

        WorkflowModels.TargetProbePayload payload = workflowTargetProbePort.probe(normalizedRequest, session.localContext());
        WorkflowModels.WorkflowSession saved = workflowSessionPort.save(new WorkflowModels.WorkflowSession(
                session.workflowId(),
                nextVersion(session),
                WorkflowModels.WorkflowStage.PROBE_REMOTE,
                WorkflowModels.WorkflowStageStatus.COMPLETED,
                appendCompletedStage(session.completedStages(), WorkflowModels.WorkflowStage.PROBE_REMOTE),
                "Remote probing completed. Deployment execution is now allowed.",
                Instant.now(),
                session.localContext(),
                payload.targetProfile(),
                payload.portProbeDecision(),
                null
        ));

        return new WorkflowModels.ProbeTargetResponse(
                snapshot(saved),
                payload.targetProfile(),
                payload.portProbeDecision(),
                payload.warnings()
        );
    }

    public Flux<WorkflowModels.ScriptStreamEvent> streamScript(WorkflowModels.StreamScriptRequest request) {
        if (request == null || isBlank(request.workflowId())) {
            throw new ValidationException("workflowId is required");
        }

        WorkflowModels.WorkflowSession session = requiredSession(request.workflowId());
        assertVersion(session, request.expectedStateVersion());
        requireCompleted(session, WorkflowModels.WorkflowStage.PROBE_REMOTE);

        WorkflowModels.ScriptGenerationMetadata metadata = buildMetadata(session);
        WorkflowModels.WorkflowSession runningSession = new WorkflowModels.WorkflowSession(
                session.workflowId(),
                session.stateVersion(),
                WorkflowModels.WorkflowStage.DEPLOY_EXECUTION,
                WorkflowModels.WorkflowStageStatus.RUNNING,
                session.completedStages(),
                "Deployment preview is streaming.",
                Instant.now(),
                session.localContext(),
                session.targetProfile(),
                session.portProbeDecision(),
                metadata
        );
        WorkflowModels.WorkflowStateSnapshot runningSnapshot = snapshot(runningSession);
        AtomicLong sequence = new AtomicLong(1);

        WorkflowModels.ScriptGenerationRequest generationRequest = new WorkflowModels.ScriptGenerationRequest(
                session.workflowId(),
                session.localContext(),
                session.targetProfile(),
                session.portProbeDecision(),
                metadata
        );

        WorkflowModels.ScriptStreamEvent startEvent = new WorkflowModels.ScriptStreamEvent(
                session.workflowId(),
                WorkflowModels.WorkflowStage.DEPLOY_EXECUTION,
                WorkflowModels.ScriptStreamEventType.START,
                0,
                null,
                metadata,
                runningSnapshot,
                "Script streaming started.",
                Instant.now()
        );

        Flux<WorkflowModels.ScriptStreamEvent> tokenEvents = workflowScriptGenerationPort.streamScript(generationRequest)
                .map(chunk -> new WorkflowModels.ScriptStreamEvent(
                        session.workflowId(),
                        WorkflowModels.WorkflowStage.DEPLOY_EXECUTION,
                        WorkflowModels.ScriptStreamEventType.TOKEN,
                        sequence.getAndIncrement(),
                        chunk,
                        metadata,
                        runningSnapshot,
                        null,
                        Instant.now()
                ));

        Flux<WorkflowModels.ScriptStreamEvent> completionEvent = Flux.defer(() -> {
            WorkflowModels.WorkflowSession completed = workflowSessionPort.save(new WorkflowModels.WorkflowSession(
                    session.workflowId(),
                    nextVersion(session),
                    WorkflowModels.WorkflowStage.DEPLOY_EXECUTION,
                    WorkflowModels.WorkflowStageStatus.COMPLETED,
                    appendCompletedStage(session.completedStages(), WorkflowModels.WorkflowStage.DEPLOY_EXECUTION),
                    "Deployment preview completed.",
                    Instant.now(),
                    session.localContext(),
                    session.targetProfile(),
                    session.portProbeDecision(),
                    metadata
            ));
            return Flux.just(new WorkflowModels.ScriptStreamEvent(
                    session.workflowId(),
                    WorkflowModels.WorkflowStage.DEPLOY_EXECUTION,
                    WorkflowModels.ScriptStreamEventType.COMPLETE,
                    sequence.getAndIncrement(),
                    null,
                    metadata,
                    snapshot(completed),
                    "Script streaming completed.",
                    Instant.now()
            ));
        });

        return Flux.concat(Flux.just(startEvent), tokenEvents, completionEvent)
                .onErrorResume(ex -> {
                    WorkflowModels.WorkflowSession failed = workflowSessionPort.save(new WorkflowModels.WorkflowSession(
                            session.workflowId(),
                            nextVersion(session),
                            WorkflowModels.WorkflowStage.DEPLOY_EXECUTION,
                            WorkflowModels.WorkflowStageStatus.FAILED,
                            session.completedStages(),
                            "Deployment preview failed.",
                            Instant.now(),
                            session.localContext(),
                            session.targetProfile(),
                            session.portProbeDecision(),
                            metadata
                    ));
                    return Flux.just(new WorkflowModels.ScriptStreamEvent(
                            session.workflowId(),
                            WorkflowModels.WorkflowStage.DEPLOY_EXECUTION,
                            WorkflowModels.ScriptStreamEventType.ERROR,
                            sequence.getAndIncrement(),
                            null,
                            metadata,
                            snapshot(failed),
                            ex.getMessage(),
                            Instant.now()
                    ));
                });
    }

    public WorkflowModels.AnalyzeLogResponse analyzeLog(WorkflowModels.AnalyzeLogRequest request) {
        if (request == null || isBlank(request.workflowId())) {
            throw new ValidationException("workflowId is required");
        }
        if (request.logRecordId() == null) {
            throw new ValidationException("logRecordId is required");
        }
        if (request.exitCode() == null || request.exitCode() == 0) {
            throw new ValidationException("exitCode must be non-zero");
        }

        WorkflowModels.WorkflowSession session = requiredSession(request.workflowId());
        assertVersion(session, request.expectedStateVersion());
        requireCompleted(session, WorkflowModels.WorkflowStage.DEPLOY_EXECUTION);

        WorkflowModels.LogDiagnosisPayload payload = workflowLogAnalysisPort.analyze(request, session);
        WorkflowModels.WorkflowSession saved = workflowSessionPort.save(new WorkflowModels.WorkflowSession(
                session.workflowId(),
                nextVersion(session),
                WorkflowModels.WorkflowStage.DEPLOY_EXECUTION,
                WorkflowModels.WorkflowStageStatus.COMPLETED,
                appendCompletedStage(session.completedStages(), WorkflowModels.WorkflowStage.DEPLOY_EXECUTION),
                "Deployment diagnosis completed.",
                Instant.now(),
                session.localContext(),
                session.targetProfile(),
                session.portProbeDecision(),
                session.scriptMetadata()
        ));

        return new WorkflowModels.AnalyzeLogResponse(
                snapshot(saved),
                request.logRecordId(),
                payload.exceptionFingerprint(),
                payload.summary(),
                payload.findings(),
                payload.warnings()
        );
    }

    private WorkflowModels.ScriptGenerationMetadata buildMetadata(WorkflowModels.WorkflowSession session) {
        Integer recommendedPort = session.portProbeDecision() != null && session.portProbeDecision().recommendedAvailablePort() != null
                ? session.portProbeDecision().recommendedAvailablePort()
                : session.localContext() != null ? session.localContext().defaultApplicationPort() : 8080;
        String shell = session.targetProfile() != null && !isBlank(session.targetProfile().detectedShell())
                ? session.targetProfile().detectedShell()
                : "bash";
        String targetOs = session.targetProfile() != null && !isBlank(session.targetProfile().osFamily())
                ? session.targetProfile().osFamily()
                : "linux";
        return new WorkflowModels.ScriptGenerationMetadata(
                shell,
                targetOs,
                recommendedPort,
                true,
                "Spring AI",
                "localChatClient"
        );
    }

    private WorkflowModels.WorkflowSession requiredSession(String workflowId) {
        return findSession(workflowId)
                .orElseThrow(() -> new ValidationException("workflowId not found: " + workflowId));
    }

    private java.util.Optional<WorkflowModels.WorkflowSession> findSession(String workflowId) {
        if (isBlank(workflowId)) {
            return java.util.Optional.empty();
        }
        return workflowSessionPort.findById(workflowId.trim());
    }

    private void requireCompleted(WorkflowModels.WorkflowSession session, WorkflowModels.WorkflowStage requiredStage) {
        if (session.completedStages() == null || !session.completedStages().contains(requiredStage)) {
            throw new ValidationException("Stage " + requiredStage + " must be completed before proceeding.");
        }
    }

    private void assertVersion(WorkflowModels.WorkflowSession session, Long expectedStateVersion) {
        if (session != null && expectedStateVersion != null && session.stateVersion() != expectedStateVersion) {
            throw new ValidationException("Workflow state version mismatch. Refresh and retry.");
        }
    }

    private long nextVersion(WorkflowModels.WorkflowSession session) {
        return session == null ? 1L : session.stateVersion() + 1;
    }

    private List<WorkflowModels.WorkflowStage> appendCompletedStage(List<WorkflowModels.WorkflowStage> completedStages,
                                                                    WorkflowModels.WorkflowStage stage) {
        List<WorkflowModels.WorkflowStage> stages = new ArrayList<>(completedStages == null ? List.of() : completedStages);
        if (!stages.contains(stage)) {
            stages.add(stage);
        }
        return List.copyOf(stages);
    }

    private WorkflowModels.WorkflowStateSnapshot snapshot(WorkflowModels.WorkflowSession session) {
        List<WorkflowModels.WorkflowStage> completed = session.completedStages() == null ? List.of() : List.copyOf(session.completedStages());
        List<WorkflowModels.WorkflowStage> remaining = ORDERED_STAGES.stream()
                .filter(stage -> !completed.contains(stage))
                .toList();
        WorkflowModels.WorkflowStage nextStage = remaining.isEmpty()
                ? null
                : session.stageStatus() == WorkflowModels.WorkflowStageStatus.COMPLETED
                ? remaining.getFirst()
                : session.currentStage();

        return new WorkflowModels.WorkflowStateSnapshot(
                session.workflowId(),
                session.stateVersion(),
                session.currentStage(),
                session.stageStatus(),
                nextStage,
                completed,
                remaining,
                session.transitionComment(),
                session.updatedAt()
        );
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
