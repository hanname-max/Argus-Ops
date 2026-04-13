// aiops-application/src/main/java/top/codejava/aiops/application/port/execution/DaemonInstallerPort.java
package top.codejava.aiops.application.port.execution;

import top.codejava.aiops.domain.execution.TargetServer;

/**
 * Daemon 安装器端口（出站端口）
 * 负责在目标服务器上自动安装引导 AIOps Daemon
 */
public interface DaemonInstallerPort {

    /**
     * 检查目标服务器上的 Daemon 是否已安装并运行
     *
     * @param server 目标服务器
     * @return Daemon 是否正在运行
     */
    boolean isDaemonRunning(TargetServer server);

    /**
     * 自动安装 Daemon 到目标服务器
     * 通过 SSH 将本地 Jar 传输到远程并配置 systemd 服务
     *
     * @param server 目标服务器
     * @return 安装是否成功
     */
    boolean installDaemon(TargetServer server);

    /**
     * 生成手动安装脚本
     * 当自动安装失败时，供用户手动复制执行
     *
     * @param server 目标服务器
     * @return Shell 脚本内容
     */
    String generateManualInstallScript(TargetServer server);
}
