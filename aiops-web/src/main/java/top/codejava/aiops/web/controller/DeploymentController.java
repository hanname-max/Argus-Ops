// aiops-web/src/main/java/top/codejava/aiops/web/controller/DeploymentController.java
package top.codejava.aiops.web.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import top.codejava.aiops.application.preflight.PreflightExecutor;
import top.codejava.aiops.application.port.LocalEnvironmentPort;
import top.codejava.aiops.application.port.LlmRemoteBrainPort;
import top.codejava.aiops.domain.model.ProjectContext;
import top.codejava.aiops.type.preflight.PreflightContext;
import top.codejava.aiops.domain.port.LogPersistencePort;

import java.nio.file.Paths;
import java.util.UUID;

/**
 * 部署分析 Controller
 * 提供 REST API 和 SSE 流式推送
 */
@RestController
@RequestMapping("/api/deploy")
public class DeploymentController {

    private final LocalEnvironmentPort localEnvironmentPort;
    private final PreflightExecutor preflightExecutor;
    private final LlmRemoteBrainPort llmRemoteBrain;
    private final LogPersistencePort logPersistence;

    public DeploymentController(
            LocalEnvironmentPort localEnvironmentPort,
            PreflightExecutor preflightExecutor,
            LlmRemoteBrainPort llmRemoteBrain,
            LogPersistencePort logPersistence) {
        this.localEnvironmentPort = localEnvironmentPort;
        this.preflightExecutor = preflightExecutor;
        this.llmRemoteBrain = llmRemoteBrain;
        this.logPersistence = logPersistence;
    }

    /**
     * 1. 触发本地扫描和责任链预检
     * POST /api/deploy/analyze
     */
    @PostMapping("/analyze")
    public ResponseEntity<AnalysisResponse> analyze(@RequestBody AnalysisRequest request) {
        // 执行预检
        PreflightContext preflight = preflightExecutor.execute(request.projectPath());

        // 本地项目扫描获取上下文
        ProjectContext context = localEnvironmentPort.scanProject(Paths.get(request.projectPath()));

        return ResponseEntity.ok(AnalysisResponse.builder()
                .preflightPassed(preflight.isPassed())
                .findings(preflight.getFindings())
                .projectContext(context)
                .build());
    }

    /**
     * 2. SSE 流式接口，打字机式推送 AI 生成的部署脚本
     * GET /api/deploy/stream-script
     */
    @GetMapping(value = "/stream-script", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> streamScript(@RequestParam String projectPath) {
        String deploymentId = UUID.randomUUID().toString();
        ProjectContext context = localEnvironmentPort.scanProject(Paths.get(projectPath));

        return llmRemoteBrain.generateDeploymentPlanStream(context)
                .map(token -> ServerSentEvent.<String>builder()
                        .id(deploymentId)
                        .event("token")
                        .data(token)
                        .build());
    }

    /**
     * 3. 分析持久化日志，返回修复建议（流式）
     * POST /api/logs/analyze
     */
    @PostMapping(value = "/logs/{logId}/analyze", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> analyzeLog(@PathVariable Long logId) {
        var logOpt = logPersistence.findById(logId);
        if (logOpt.isEmpty()) {
            return Flux.just(ServerSentEvent.<String>builder()
                    .event("error")
                    .data("Log not found: " + logId)
                    .build());
        }

        String logContent = logOpt.get().getContent();
        if (logOpt.get().getStackTrace() != null) {
            logContent += "\n\nStack trace:\n" + logOpt.get().getStackTrace();
        }

        return llmRemoteBrain.analyzeErrorLogStream(logContent)
                .map(token -> ServerSentEvent.<String>builder()
                        .id(String.valueOf(logId))
                        .event("token")
                        .data(token)
                        .build());
    }

    /**
     * 获取某部署任务的完整历史日志
     */
    @GetMapping("/logs/{deploymentId}")
    public ResponseEntity<?> getDeploymentLogs(@PathVariable String deploymentId) {
        var logs = logPersistence.findByDeploymentId(deploymentId);
        return ResponseEntity.ok(logs);
    }

    // === Request/Response DTOs ===

    public record AnalysisRequest(String projectPath) {}

    @lombok.Builder
    public record AnalysisResponse(
            boolean preflightPassed,
            java.util.List<String> findings,
            ProjectContext projectContext
    ) {}
}
