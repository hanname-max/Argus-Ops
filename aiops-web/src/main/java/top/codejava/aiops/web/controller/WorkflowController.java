package top.codejava.aiops.web.controller;

import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import top.codejava.aiops.application.dto.WorkflowModels;
import top.codejava.aiops.application.usecase.WorkflowUseCase;

/**
 * Workflow HTTP entrypoint for the deterministic Argus-Ops 2.0 backend.
 *
 * <p>This controller intentionally delegates all state-machine decisions to the application
 * layer. The controller owns transport concerns only: JSON binding, SSE framing, and
 * response typing.
 *
 * <p>Workflow state order is fixed:
 * ANALYZE_LOCAL -> PROBE_TARGET -> STREAM_SCRIPT -> ANALYZE_LOG.
 */
@RestController
@RequestMapping("/api/v1/workflow")
public class WorkflowController {

    private final WorkflowUseCase workflowUseCase;

    public WorkflowController(WorkflowUseCase workflowUseCase) {
        this.workflowUseCase = workflowUseCase;
    }

    /**
     * Step 1: local deep analysis.
     *
     * <p>Expected behavior:
     * 1. scan the local project in read-only mode;
     * 2. infer language, framework, packaging, JDK, and default application port;
     * 3. persist reusable workflow context for the next stages;
     * 4. move the state machine to ANALYZE_LOCAL/COMPLETED.
     *
     * <p>Anti-footgun design:
     * the application layer must not launch the web process or connect to a real database.
     */
    @PostMapping("/analyze-local")
    public WorkflowModels.AnalyzeLocalResponse analyzeLocal(
            @RequestBody WorkflowModels.AnalyzeLocalRequest request
    ) {
        return workflowUseCase.analyzeLocal(request);
    }

    /**
     * Step 2: target probing with smart port selection.
     *
     * <p>Expected behavior:
     * 1. validate that local analysis has already completed;
     * 2. probe target host profile and initial port availability;
     * 3. auto-increment port candidates when the default port is occupied;
     * 4. return warnings plus the recommended available port.
     *
     * <p>Anti-footgun design:
     * target credentials are accepted for probing only and must never be returned in any response.
     */
    @PostMapping("/probe-target")
    public WorkflowModels.ProbeTargetResponse probeTarget(
            @RequestBody WorkflowModels.ProbeTargetRequest request
    ) {
        return workflowUseCase.probeTarget(request);
    }

    /**
     * Step 3: AI-targeted script generation over Server-Sent Events.
     *
     * <p>The application layer is responsible for assembling the immutable prompt context from:
     * local analysis snapshot, target profile, and confirmed recommended port. This controller
     * only frames structured workflow events as SSE.
     *
     * <p>Spring AI streaming is bridged through {@code ChatClient.prompt().stream().content()} in
     * the infrastructure adapter, then surfaced here as {@code start/token/complete/error} events.
     */
    @GetMapping(path = "/stream-script", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<WorkflowModels.ScriptStreamEvent>> streamScript(
            @ModelAttribute WorkflowModels.StreamScriptRequest request
    ) {
        return workflowUseCase.streamScript(request)
                .map(this::toSse);
    }

    /**
     * Step 4: bilingual structured log diagnosis.
     *
     * <p>Expected behavior:
     * 1. validate that script generation has already completed;
     * 2. accept only a persisted log identifier plus a non-zero exit code;
     * 3. classify the dominant error signature;
     * 4. return bilingual root cause and fix suggestion fields.
     */
    @PostMapping("/analyze-log")
    public WorkflowModels.AnalyzeLogResponse analyzeLog(
            @RequestBody WorkflowModels.AnalyzeLogRequest request
    ) {
        return workflowUseCase.analyzeLog(request);
    }

    private ServerSentEvent<WorkflowModels.ScriptStreamEvent> toSse(WorkflowModels.ScriptStreamEvent event) {
        return ServerSentEvent.<WorkflowModels.ScriptStreamEvent>builder()
                .id(event.workflowId() + ":" + event.sequence())
                .event(event.eventType().wireName())
                .data(event)
                .build();
    }
}
