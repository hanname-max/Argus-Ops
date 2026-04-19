package top.codejava.aiops.infrastructure.workflow;

import java.util.List;

import org.springframework.stereotype.Component;
import top.codejava.aiops.application.dto.WorkflowModels;
import top.codejava.aiops.application.port.WorkflowLogAnalysisPort;

@Component
public class DeterministicWorkflowLogAnalysisAdapter implements WorkflowLogAnalysisPort {

    @Override
    public WorkflowModels.LogDiagnosisPayload analyze(WorkflowModels.AnalyzeLogRequest request,
                                                      WorkflowModels.WorkflowSession session) {
        WorkflowModels.DiagnosticCategory category = detectCategory(request.exitCode());
        WorkflowModels.BilingualText summary = switch (category) {
            case OOM -> new WorkflowModels.BilingualText(
                    "检测到疑似内存不足类异常，建议优先核查 JVM 或容器内存配额。",
                    "A likely out-of-memory signature was detected. Check JVM and container memory limits first."
            );
            case DB_CONNECTION_REFUSED -> new WorkflowModels.BilingualText(
                    "检测到数据库连接拒绝类异常，建议优先检查网络、端口和凭证。",
                    "A database connection refusal signature was detected. Check network reachability, port exposure, and credentials."
            );
            default -> new WorkflowModels.BilingualText(
                    "检测到通用部署失败信号，建议结合执行脚本和目标机画像继续排查。",
                    "A generic deployment failure signal was detected. Continue troubleshooting with execution logs and target profile."
            );
        };

        WorkflowModels.DiagnosticFinding finding = new WorkflowModels.DiagnosticFinding(
                category,
                category == WorkflowModels.DiagnosticCategory.UNKNOWN ? WorkflowModels.Severity.MEDIUM : WorkflowModels.Severity.HIGH,
                List.of(
                        "logRecordId=" + request.logRecordId(),
                        "exitCode=" + request.exitCode(),
                        "project=" + safeProjectName(session)
                ),
                rootCause(category),
                fixSuggestion(category)
        );

        WorkflowModels.WorkflowWarning warning = new WorkflowModels.WorkflowWarning(
                "H2_LOG_PLACEHOLDER_ANALYSIS",
                WorkflowModels.Severity.INFO,
                "Current implementation uses deterministic structured diagnosis without loading a real H2 log body.",
                "Replace this adapter with an H2-backed log repository adapter when persistent execution logs are introduced."
        );

        return new WorkflowModels.LogDiagnosisPayload(
                "LOG-" + request.logRecordId() + "-EXIT-" + request.exitCode(),
                summary,
                List.of(finding),
                List.of(warning)
        );
    }

    private WorkflowModels.DiagnosticCategory detectCategory(int exitCode) {
        if (exitCode == 137 || exitCode == 143) {
            return WorkflowModels.DiagnosticCategory.OOM;
        }
        if (exitCode == 111) {
            return WorkflowModels.DiagnosticCategory.DB_CONNECTION_REFUSED;
        }
        return WorkflowModels.DiagnosticCategory.UNKNOWN;
    }

    private WorkflowModels.BilingualText rootCause(WorkflowModels.DiagnosticCategory category) {
        return switch (category) {
            case OOM -> new WorkflowModels.BilingualText(
                    "Error Root Cause: 进程在运行期间超出可用内存预算，导致被系统或容器运行时终止。",
                    "Error Root Cause: The process exceeded available memory budget and was terminated by the operating system or container runtime."
            );
            case DB_CONNECTION_REFUSED -> new WorkflowModels.BilingualText(
                    "Error Root Cause: 应用尝试连接数据库时遭遇连接拒绝，通常意味着目标端口未监听或网络路径未打通。",
                    "Error Root Cause: The application hit a connection-refused condition while connecting to the database, usually meaning the target port is not listening or the network path is blocked."
            );
            default -> new WorkflowModels.BilingualText(
                    "Error Root Cause: 当前日志证据不足以锁定单一根因，但已确认部署执行以非零状态失败。",
                    "Error Root Cause: The current evidence is insufficient to isolate a single root cause, but deployment execution clearly failed with a non-zero exit status."
            );
        };
    }

    private WorkflowModels.BilingualText fixSuggestion(WorkflowModels.DiagnosticCategory category) {
        return switch (category) {
            case OOM -> new WorkflowModels.BilingualText(
                    "Fix Suggestion: 提高容器内存限制、降低 JVM 堆参数，或减少部署时并发负载。",
                    "Fix Suggestion: Increase container memory limits, lower JVM heap settings, or reduce concurrent workload during deployment."
            );
            case DB_CONNECTION_REFUSED -> new WorkflowModels.BilingualText(
                    "Fix Suggestion: 核对数据库地址、监听端口、防火墙规则与凭证，并先在目标机执行连通性验证。",
                    "Fix Suggestion: Verify database address, listening port, firewall rules, and credentials, then validate connectivity directly on the target host."
            );
            default -> new WorkflowModels.BilingualText(
                    "Fix Suggestion: 回看启动脚本、环境变量、端口绑定和依赖注入配置，并补充原始错误日志后再次诊断。",
                    "Fix Suggestion: Review startup script, environment variables, port bindings, and dependency wiring, then diagnose again with the raw error log attached."
            );
        };
    }

    private String safeProjectName(WorkflowModels.WorkflowSession session) {
        return session.localContext() == null ? "unknown" : session.localContext().projectName();
    }
}
