package top.codejava.aiops.application.dto;

import java.time.Instant;
import java.util.List;

public final class WorkflowModels {

    private WorkflowModels() {
    }

    public record AnalyzeLocalRequest(
            String workflowId,
            Long expectedStateVersion,
            String projectPath,
            String operator,
            boolean includeDependencyGraph,
            boolean simulateCompile
    ) {
    }

    public record AnalyzeLocalResponse(
            WorkflowStateSnapshot state,
            LocalProjectContext context,
            List<WorkflowWarning> warnings
    ) {
    }

    public record ProbeTargetRequest(
            String workflowId,
            Long expectedStateVersion,
            TargetCredential credential,
            Integer defaultApplicationPort,
            Integer maxAutoIncrementProbeSpan
    ) {
    }

    public record ProbeTargetResponse(
            WorkflowStateSnapshot state,
            TargetSystemProfile targetProfile,
            PortProbeDecision portProbe,
            List<WorkflowWarning> warnings
    ) {
    }

    public record StreamScriptRequest(
            String workflowId,
            Long expectedStateVersion,
            String operator,
            boolean regenerate
    ) {
    }

    public record AnalyzeLogRequest(
            String workflowId,
            Long expectedStateVersion,
            Long logRecordId,
            Integer exitCode,
            Integer tailLineCount
    ) {
    }

    public record AnalyzeLogResponse(
            WorkflowStateSnapshot state,
            Long logRecordId,
            String exceptionFingerprint,
            BilingualText summary,
            List<DiagnosticFinding> findings,
            List<WorkflowWarning> warnings
    ) {
    }

    public record WorkflowStateSnapshot(
            String workflowId,
            Long stateVersion,
            WorkflowStage currentStage,
            WorkflowStageStatus stageStatus,
            WorkflowStage nextStage,
            List<WorkflowStage> completedStages,
            List<WorkflowStage> remainingStages,
            String transitionComment,
            Instant updatedAt
    ) {
    }

    public record WorkflowSession(
            String workflowId,
            long stateVersion,
            WorkflowStage currentStage,
            WorkflowStageStatus stageStatus,
            List<WorkflowStage> completedStages,
            String transitionComment,
            Instant updatedAt,
            LocalProjectContext localContext,
            TargetSystemProfile targetProfile,
            PortProbeDecision portProbeDecision,
            ScriptGenerationMetadata scriptMetadata
    ) {
    }

    public record LocalProjectContext(
            String projectName,
            String primaryLanguage,
            String primaryFramework,
            String buildTool,
            String packaging,
            String detectedJdkVersion,
            Integer defaultApplicationPort,
            List<StackComponent> stackComponents,
            List<ConfigEvidence> configEvidences
    ) {
    }

    public record StackComponent(
            String name,
            String version,
            String role
    ) {
    }

    public record ConfigEvidence(
            String source,
            String key,
            String valuePreview,
            boolean inferred
    ) {
    }

    public record TargetCredential(
            String host,
            Integer sshPort,
            String username,
            CredentialType credentialType,
            String password,
            String privateKeyPem,
            Integer connectTimeoutMillis
    ) {
    }

    public record TargetSystemProfile(
            String host,
            String hostname,
            String osFamily,
            String osVersion,
            String architecture,
            Long totalMemoryMb,
            Long freeMemoryMb,
            String detectedJavaVersion,
            String detectedShell,
            boolean dockerInstalled,
            String dockerVersion,
            boolean canUseDockerDirectly,
            boolean canUseSudo
    ) {
    }

    public record PortProbeDecision(
            Integer requestedPort,
            boolean requestedPortOccupied,
            List<Integer> triedPorts,
            Integer recommendedAvailablePort,
            BilingualText warningMessage,
            String resolutionStrategy
    ) {
    }

    public record ScriptGenerationMetadata(
            String shell,
            String targetOs,
            Integer recommendedPort,
            boolean defensiveMode,
            String modelProvider,
            String modelName
    ) {
    }

    public record ScriptGenerationRequest(
            String workflowId,
            LocalProjectContext localContext,
            TargetSystemProfile targetProfile,
            PortProbeDecision portProbeDecision,
            ScriptGenerationMetadata metadata
    ) {
    }

    public record ScriptStreamEvent(
            String workflowId,
            WorkflowStage stage,
            ScriptStreamEventType eventType,
            long sequence,
            String chunk,
            ScriptGenerationMetadata metadata,
            WorkflowStateSnapshot state,
            String message,
            Instant emittedAt
    ) {
    }

    public record DiagnosticFinding(
            DiagnosticCategory category,
            Severity severity,
            List<String> evidence,
            BilingualText errorRootCause,
            BilingualText fixSuggestion
    ) {
    }

    public record BilingualText(
            String zhCn,
            String enUs
    ) {
    }

    public record WorkflowWarning(
            String code,
            Severity severity,
            String message,
            String operatorAction
    ) {
    }

    public record LocalAnalysisPayload(
            LocalProjectContext context,
            List<WorkflowWarning> warnings
    ) {
    }

    public record TargetProbePayload(
            TargetSystemProfile targetProfile,
            PortProbeDecision portProbeDecision,
            List<WorkflowWarning> warnings
    ) {
    }

    public record LogDiagnosisPayload(
            String exceptionFingerprint,
            BilingualText summary,
            List<DiagnosticFinding> findings,
            List<WorkflowWarning> warnings
    ) {
    }

    public enum WorkflowStage {
        ANALYZE_LOCAL,
        PROBE_TARGET,
        STREAM_SCRIPT,
        ANALYZE_LOG
    }

    public enum WorkflowStageStatus {
        PENDING,
        RUNNING,
        COMPLETED,
        FAILED,
        BLOCKED
    }

    public enum ScriptStreamEventType {
        START("start"),
        TOKEN("token"),
        WARNING("warning"),
        COMPLETE("complete"),
        ERROR("error"),
        HEARTBEAT("heartbeat");

        private final String wireName;

        ScriptStreamEventType(String wireName) {
            this.wireName = wireName;
        }

        public String wireName() {
            return wireName;
        }
    }

    public enum CredentialType {
        PASSWORD,
        PRIVATE_KEY
    }

    public enum DiagnosticCategory {
        OOM,
        DB_CONNECTION_REFUSED,
        DEPENDENCY_CONFLICT,
        PORT_CONFLICT,
        PERMISSION_DENIED,
        CONFIGURATION_MISMATCH,
        UNKNOWN
    }

    public enum Severity {
        INFO,
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }
}
