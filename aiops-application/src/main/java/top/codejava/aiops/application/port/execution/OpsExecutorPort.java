// aiops-application/src/main/java/top/codejava/aiops/application/port/execution/OpsExecutorPort.java
package top.codejava.aiops.application.port.execution;

import top.codejava.aiops.domain.execution.ExecutionResult;
import top.codejava.aiops.domain.execution.ShellCommand;
import top.codejava.aiops.domain.execution.TargetServer;

/**
 * 运维命令执行端口（出站端口）
 * 定义在目标服务器上执行Shell命令的抽象
 */
public interface OpsExecutorPort {

    /**
     * 在目标服务器上执行Shell命令
     *
     * @param cmd    要执行的命令
     * @param server 目标服务器
     * @return 执行结果
     */
    ExecutionResult execute(ShellCommand cmd, TargetServer server);

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
