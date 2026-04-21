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
 * Workflow HTTP entrypoint for the simplified three-phase Argus-Ops backend.
 *
 * <p>The controller remains transport-only: JSON binding, SSE framing, and response typing.
 * Business sequencing is owned by the application layer.
 *
 * <p>Workflow phase order is fixed:
 * PREPARE_LOCAL -> PROBE_REMOTE -> DEPLOY_EXECUTION.
 */
@RestController
@RequestMapping("/api/v1/workflow")
public class WorkflowController {

    private final WorkflowUseCase workflowUseCase;

    public WorkflowController(WorkflowUseCase workflowUseCase) {
        this.workflowUseCase = workflowUseCase;
    }

    /**
     * Phase 1: local preparation.
     *
     * <p>This phase scans the project locally, infers the stack and packaging hints,
     * and persists the preparation result for later remote probing and deployment planning.
     */
    @PostMapping("/analyze-local")
    public WorkflowModels.AnalyzeLocalResponse analyzeLocal(
            @RequestBody WorkflowModels.AnalyzeLocalRequest request
    ) {
        return workflowUseCase.analyzeLocal(request);
    }

    /**
     * Phase 2: remote probing.
     *
     * <p>This phase validates that local preparation has already completed, probes the
     * target host profile, and returns a recommended port when conflicts are detected.
     */
    @PostMapping("/probe-target")
    public WorkflowModels.ProbeTargetResponse probeTarget(
            @RequestBody WorkflowModels.ProbeTargetRequest request
    ) {
        return workflowUseCase.probeTarget(request);
    }

    /**
     * Phase 3a: deployment preview over Server-Sent Events.
     *
     * <p>The application layer assembles the immutable deployment planning context from the
     * prepared local snapshot and the probed remote profile. This controller only frames the
     * preview stream as SSE events.
     */
    @GetMapping(path = "/stream-script", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<WorkflowModels.ScriptStreamEvent>> streamScript(
            @ModelAttribute WorkflowModels.StreamScriptRequest request
    ) {
        return workflowUseCase.streamScript(request)
                .map(this::toSse);
    }

    /**
     * Phase 3b: deployment diagnosis.
     *
     * <p>After deployment preview or execution has completed, this endpoint accepts the
     * log context and returns structured bilingual diagnosis fields.
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
