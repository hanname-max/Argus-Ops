// aiops-application/src/main/java/top/codejava/aiops/application/port/execution/OpsExecutorPort.java
package top.codejava.aiops.application.port.execution;

import top.codejava.aiops.domain.execution.ExecutionResult;
import top.codejava.aiops.domain.execution.ShellCommand;
import top.codejava.aiops.domain.execution.TargetServer;

/**
 * 运维命令执行端口（出站端口）
 * 定义在目标服务器上执行Shell命令的抽象
 * 本地主导架构：远程仅负责执行，所有AI分析在本地完成
 */
public interface OpsExecutorPort {

    /**
     * 在目标服务器上执行Shell命令，同步返回完整结果
     *
     * @param cmd    要执行的命令
     * @param server 目标服务器
     * @return 执行结果
     */
    ExecutionResult execute(ShellCommand cmd, TargetServer server);

    /**
     * 在目标服务器上执行Shell命令，实时流式回传日志
     * 本地主导架构：所有AI分析由本地通过回调处理，远程只执行
     *
     * @param cmd      要执行的命令
     * @param server   目标服务器
     * @param callback 流式日志回调（本地处理）
     */
    void executeAndStream(ShellCommand cmd, TargetServer server, LogStreamCallback callback);

    /**
     * 探测目标服务器指定端口是否可连通
     *
     * @param server 目标服务器
     * @param port   要探测的端口
     * @param timeoutMs 超时时间
     * @return 是否可连通
     */
    boolean probePort(TargetServer server, int port, int timeoutMs);
}
