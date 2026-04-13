// aiops-infrastructure/src/main/java/top/codejava/aiops/infrastructure/adapter/execution/DaemonInstallerAdapter.java
package top.codejava.aiops.infrastructure.adapter.execution;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import top.codejava.aiops.application.port.execution.DaemonInstallerPort;
import top.codejava.aiops.domain.execution.ExecutionResult;
import top.codejava.aiops.domain.execution.ShellCommand;
import top.codejava.aiops.domain.execution.TargetServer;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Daemon 自动安装适配器
 * 负责检测远程 Daemon 状态，并在未安装时通过 SSH 自动部署
 */
@Slf4j
@RequiredArgsConstructor
public class DaemonInstallerAdapter implements DaemonInstallerPort {

    private final SshExecutorAdapter sshExecutor;
    private final Path localJarPath;

    private static final String INSTALL_DIR = "/opt/aiops";
    private static final String DAEMON_JAR_NAME = "aiops-daemon.jar";
    private static final String SYSTEMD_SERVICE_NAME = "aiops-daemon.service";

    public DaemonInstallerAdapter(SshExecutorAdapter sshExecutor) {
        this.sshExecutor = sshExecutor;
        // 默认使用当前进程的 jar 路径
        this.localJarPath = Paths.get(System.getProperty("java.class.path"))
                .toAbsolutePath();
    }

    @Override
    public boolean isDaemonRunning(TargetServer server) {
        // 直接探测 RPC 端口是否开放
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(server.getHost(), server.getRpcPort()), 2000);
            return socket.isConnected();
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public boolean installDaemon(TargetServer server) {
        log.info("Starting automatic AIOps Daemon installation on {}", server.getHost());

        try {
            // 1. 创建安装目录
            ExecutionResult mkdirResult = sshExecutor.execute(
                    ShellCommand.of("mkdir -p " + INSTALL_DIR), server);
            if (!mkdirResult.isSuccess()) {
                log.error("Failed to create install directory: {}", mkdirResult.getCombinedOutput());
                return false;
            }

            // 2. 上传 Jar 文件
            sshExecutor.scpUpload(server, localJarPath.toString(), INSTALL_DIR);

            // Rename to standard name
            ExecutionResult renameResult = sshExecutor.execute(
                    ShellCommand.of(String.format("cd %s && mv %s %s",
                            INSTALL_DIR, localJarPath.getFileName(), DAEMON_JAR_NAME)), server);
            if (!renameResult.isSuccess()) {
                log.warn("Rename failed (may already exist): {}", renameResult.getCombinedOutput());
            }

            // 3. 生成 systemd service 文件
            String systemdContent = generateSystemdService();
            String writeCommand = String.format("cat > /etc/systemd/system/%s << 'EOF'\n%s\nEOF",
                    SYSTEMD_SERVICE_NAME, systemdContent);
            ExecutionResult systemdResult = sshExecutor.execute(ShellCommand.of(writeCommand), server);
            if (!systemdResult.isSuccess()) {
                log.error("Failed to write systemd service file: {}", systemdResult.getCombinedOutput());
                return false;
            }

            // 4. Reload systemd and start service
            String[] reloadStartCommands = {
                    "systemctl daemon-reload",
                    "systemctl enable " + SYSTEMD_SERVICE_NAME,
                    "systemctl restart " + SYSTEMD_SERVICE_NAME
            };

            for (String cmd : reloadStartCommands) {
                ExecutionResult result = sshExecutor.execute(ShellCommand.of(cmd), server);
                if (!result.isSuccess()) {
                    log.error("Command failed: {} -> {}", cmd, result.getCombinedOutput());
                    // Non-fatal, continue
                }
            }

            // 5. Verify installation
            Thread.sleep(3000); // Wait for service to start
            if (isDaemonRunning(server)) {
                log.info("AIOps Daemon installed and started successfully on {}", server.getHost());
                return true;
            } else {
                log.error("Daemon installation completed but service is not responding");
                return false;
            }

        } catch (Exception e) {
            log.error("Automatic installation failed: {}", e.getMessage(), e);
            return false;
        }
    }

    @Override
    public String generateManualInstallScript(TargetServer server) {
        String localJar = localJarPath.toAbsolutePath().toString();

        return String.format("""
                #!/bin/bash
                # Manual installation script for AIOps Daemon

                set -e

                # Step 1: Create installation directory
                mkdir -p %s

                # Step 2: Upload the Jar file (run this from your local machine)
                scp -P %d %s %s@%s:%s/%s

                # Step 3: Install systemd service on the remote server
                # SSH to the server and run the following commands:

                cat > /etc/systemd/system/%s << 'EOF'
                %s
                EOF

                systemctl daemon-reload
                systemctl enable %s
                systemctl restart %s
                systemctl status %s

                echo "Installation complete. Check status above."
                """,
                INSTALL_DIR,
                server.getSshPort(),
                localJar,
                server.getUsername(),
                server.getHost(),
                INSTALL_DIR,
                DAEMON_JAR_NAME,
                SYSTEMD_SERVICE_NAME,
                generateSystemdService(),
                SYSTEMD_SERVICE_NAME,
                SYSTEMD_SERVICE_NAME,
                SYSTEMD_SERVICE_NAME
        );
    }

    /**
     * 生成 systemd service 配置文件内容
     */
    private String generateSystemdService() {
        return String.format("""
                [Unit]
                Description=AIOps Engine Daemon
                After=network.target

                [Service]
                Type=simple
                User=root
                WorkingDirectory=%s
                ExecStart=/usr/bin/java -jar %s/%s
                Restart=always
                RestartSec=10
                StandardOutput=journal+console
                StandardError=journal+console

                [Install]
                WantedBy=multi-user.target
                """, INSTALL_DIR, INSTALL_DIR, DAEMON_JAR_NAME);
    }
}
