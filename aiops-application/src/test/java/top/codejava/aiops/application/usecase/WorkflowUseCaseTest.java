package top.codejava.aiops.application.usecase;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import top.codejava.aiops.application.dto.WorkflowModels;
import top.codejava.aiops.application.port.WorkflowLocalAnalysisPort;
import top.codejava.aiops.application.port.WorkflowLogAnalysisPort;
import top.codejava.aiops.application.port.WorkflowScriptGenerationPort;
import top.codejava.aiops.application.port.WorkflowSessionPort;
import top.codejava.aiops.application.port.WorkflowTargetProbePort;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkflowUseCaseTest {

    @Test
    void shouldProgressThroughThreePhases() {
        InMemorySessionPort sessionPort = new InMemorySessionPort();
        WorkflowUseCase useCase = new WorkflowUseCase(
                sessionPort,
                localAnalysisPort(),
                targetProbePort(),
                scriptGenerationPort(),
                logAnalysisPort()
        );

        WorkflowModels.AnalyzeLocalResponse analyze = useCase.analyzeLocal(new WorkflowModels.AnalyzeLocalRequest(
                null,
                null,
                "D:/workspace/demo",
                "test",
                true,
                true
        ));

        assertEquals(WorkflowModels.WorkflowStage.PREPARE_LOCAL, analyze.state().currentStage());
        assertEquals(WorkflowModels.WorkflowStage.PROBE_REMOTE, analyze.state().nextStage());

        WorkflowModels.ProbeTargetResponse probe = useCase.probeTarget(new WorkflowModels.ProbeTargetRequest(
                analyze.state().workflowId(),
                analyze.state().stateVersion(),
                new WorkflowModels.TargetCredential("127.0.0.1", 22, "root", WorkflowModels.CredentialType.PASSWORD, "pw", "", 5000),
                8087,
                3
        ));

        assertEquals(WorkflowModels.WorkflowStage.PROBE_REMOTE, probe.state().currentStage());
        assertEquals(WorkflowModels.WorkflowStage.DEPLOY_EXECUTION, probe.state().nextStage());

        List<WorkflowModels.ScriptStreamEvent> events = useCase.streamScript(new WorkflowModels.StreamScriptRequest(
                probe.state().workflowId(),
                probe.state().stateVersion(),
                "test",
                true
        )).collectList().block();

        assertFalse(events == null || events.isEmpty());
        assertEquals(WorkflowModels.WorkflowStage.DEPLOY_EXECUTION, events.getFirst().stage());
        assertEquals(WorkflowModels.ScriptStreamEventType.START, events.getFirst().eventType());
        assertEquals(WorkflowModels.ScriptStreamEventType.COMPLETE, events.getLast().eventType());

        WorkflowModels.AnalyzeLogResponse diagnosis = useCase.analyzeLog(new WorkflowModels.AnalyzeLogRequest(
                probe.state().workflowId(),
                events.getLast().state().stateVersion(),
                1L,
                1,
                20
        ));

        assertEquals(WorkflowModels.WorkflowStage.DEPLOY_EXECUTION, diagnosis.state().currentStage());
        assertNull(diagnosis.state().nextStage());
        assertTrue(diagnosis.state().remainingStages().isEmpty());
    }

    private WorkflowLocalAnalysisPort localAnalysisPort() {
        return request -> new WorkflowModels.LocalAnalysisPayload(
                new WorkflowModels.LocalProjectContext(
                        "demo",
                        "Static",
                        "Static Site",
                        "none",
                        "nginx-static",
                        null,
                        8087,
                        List.of(),
                        List.of(new WorkflowModels.ConfigEvidence("filesystem", "projectPath", request.projectPath(), false))
                ),
                List.of()
        );
    }

    private WorkflowTargetProbePort targetProbePort() {
        return (request, localContext) -> new WorkflowModels.TargetProbePayload(
                new WorkflowModels.TargetSystemProfile(
                        request.credential().host(),
                        "host",
                        "linux",
                        "1",
                        "x86_64",
                        1024L,
                        512L,
                        "21",
                        "bash",
                        true,
                        "Docker 26",
                        true,
                        true
                ),
                new WorkflowModels.PortProbeDecision(
                        request.defaultApplicationPort(),
                        false,
                        List.of(request.defaultApplicationPort()),
                        request.defaultApplicationPort(),
                        null,
                        "FIXED"
                ),
                List.of()
        );
    }

    private WorkflowScriptGenerationPort scriptGenerationPort() {
        return request -> Flux.just("echo preview", "echo done");
    }

    private WorkflowLogAnalysisPort logAnalysisPort() {
        return (request, session) -> new WorkflowModels.LogDiagnosisPayload(
                "fingerprint",
                new WorkflowModels.BilingualText("ok", "ok"),
                List.of(),
                List.of()
        );
    }

    private static final class InMemorySessionPort implements WorkflowSessionPort {

        private final Map<String, WorkflowModels.WorkflowSession> store = new ConcurrentHashMap<>();

        @Override
        public Optional<WorkflowModels.WorkflowSession> findById(String workflowId) {
          return Optional.ofNullable(store.get(workflowId));
        }

        @Override
        public WorkflowModels.WorkflowSession save(WorkflowModels.WorkflowSession session) {
            WorkflowModels.WorkflowSession normalized = new WorkflowModels.WorkflowSession(
                    session.workflowId(),
                    session.stateVersion(),
                    session.currentStage(),
                    session.stageStatus(),
                    session.completedStages(),
                    session.transitionComment(),
                    session.updatedAt() == null ? Instant.now() : session.updatedAt(),
                    session.localContext(),
                    session.targetProfile(),
                    session.portProbeDecision(),
                    session.scriptMetadata()
            );
            store.put(session.workflowId(), normalized);
            return normalized;
        }
    }
}
