// aiops-infrastructure/src/main/java/top/codejava/aiops/infrastructure/adapter/execution/SmartExecutorRoutingAdapter.java
package top.codejava.aiops.infrastructure.adapter.execution;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import top.codejava.aiops.application.port.execution.OpsExecutorPort;
import top.codejava.aiops.domain.execution.ExecutionResult;
import top.codejava.aiops.domain.execution.ShellCommand;
import top.codejava.aiops.domain.execution.TargetServer;

/**
 * 智能执行路由适配器
 * 实现策略路由模式：优先尝试 RPC Daemon 执行，失败自动降级到 SSH 直连
 *
 * 核心降级逻辑：
 * 1. 先探测 RPC 端口是否开放
 * 2. 如果开放 → 使用 RpcExecutorAdapter
 * 3. 如果探测失败 → 直接降级使用 SshExecutorAdapter
 * 4. 如果 RPC 执行过程中发生网络错误 → 也会降级
 */
@Slf4j
@RequiredArgsConstructor
public class SmartExecutorRoutingAdapter implements OpsExecutorPort {

    private final RpcExecutorAdapter rpcExecutor;
    private final SshExecutorAdapter sshExecutor;

    private static final int PROBE_TIMEOUT_MS = 2000; // 2秒探测超时

    @Override
    public ExecutionResult execute(ShellCommand cmd, TargetServer server) {
        // 第一步：探测 RPC 端口是否可达
        if (probePort(server, server.getRpcPort(), PROBE_TIMEOUT_MS)) {
            log.info("RPC daemon detected on {}:{}, using RPC execution",
                    server.getHost(), server.getRpcPort());
            try {
                return rpcExecutor.execute(cmd, server);
            } catch (Exception e) {
                log.warn("RPC execution failed, falling back to SSH: {}", e.getMessage());
                // RPC执行失败，降级到SSH
                return sshExecutor.execute(cmd, server);
            }
        } else {
            log.info("RPC daemon not detected on {}:{}, falling back to direct SSH execution",
                    server.getHost(), server.getRpcPort());
            return sshExecutor.execute(cmd, server);
        }
    }

    @Override
    public boolean probePort(TargetServer server, int port, int timeoutMs) {
        // 使用RPC执行器的探测逻辑，两者探测方式一致
        return rpcExecutor.probePort(server, port, timeoutMs);
    }
}
