// aiops-infrastructure/src/main/java/top/codejava/aiops/infrastructure/adapter/execution/SshExecutorAdapter.java
package top.codejava.aiops.infrastructure.adapter.execution;

import com.jcraft.jsch.*;
import lombok.extern.slf4j.Slf4j;
import top.codejava.aiops.application.port.execution.OpsExecutorPort;
import top.codejava.aiops.domain.exception.OpsExecutionException;
import top.codejava.aiops.domain.execution.ExecutionResult;
import top.codejava.aiops.domain.execution.ShellCommand;
import top.codejava.aiops.domain.execution.TargetServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * SSH 命令执行适配器
 * 基于 JSch 实现，通过 SSH 协议直接在远程执行命令
 */
@Slf4j
public class SshExecutorAdapter implements OpsExecutorPort {

    @Override
    public ExecutionResult execute(ShellCommand cmd, TargetServer server) {
        long startTime = System.currentTimeMillis();
        JSch jsch = new JSch();
        Session session = null;

        try {
            // 创建 SSH session
            session = createSession(jsch, server);
            session.connect( (int) Math.min(cmd.getTimeoutMs(), 30000) );
            log.debug("SSH connected to {}:{}", server.getHost(), server.getSshPort());

            // 打开执行通道
            ChannelExec channel = (ChannelExec) session.openChannel("exec");

            // 设置工作目录
            String fullCmd = buildFullCommand(cmd);
            channel.setCommand(fullCmd);
            log.debug("Executing command over SSH: {}", fullCmd);

            // 捕获输出
            ByteArrayOutputStream stdoutBuffer = new ByteArrayOutputStream();
            ByteArrayOutputStream stderrBuffer = new ByteArrayOutputStream();

            channel.setOutputStream(stdoutBuffer);
            channel.setErrStream(stderrBuffer);

            // 执行命令
            channel.connect();

            // 等待完成，带超时检查
            long deadline = System.currentTimeMillis() + cmd.getTimeoutMs();
            while (!channel.isClosed()) {
                if (System.currentTimeMillis() > deadline) {
                    log.warn("Command execution timed out after {}ms", cmd.getTimeoutMs());
                    channel.disconnect();
                    session.disconnect();
                    throw new OpsExecutionException("Command execution timed out: " + fullCmd);
                }
                Thread.sleep(100);
            }

            int exitCode = channel.getExitStatus();
            String stdout = stdoutBuffer.toString(StandardCharsets.UTF_8);
            String stderr = stderrBuffer.toString(StandardCharsets.UTF_8);
            long duration = System.currentTimeMillis() - startTime;

            log.debug("Command completed with exit code {} in {}ms", exitCode, duration);

            channel.disconnect();
            session.disconnect();

            return ExecutionResult.builder()
                    .exitCode(exitCode)
                    .stdout(stdout)
                    .stderr(stderr)
                    .durationMs(duration)
                    .build();

        } catch (JSchException | InterruptedException e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("SSH execution failed: {}", e.getMessage());
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
            throw new OpsExecutionException("SSH execution failed: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean probePort(TargetServer server, int port, int timeoutMs) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(server.getHost(), port), timeoutMs);
            return true;
        } catch (IOException e) {
            log.debug("Port {} on {} is not reachable: {}", port, server.getHost(), e.getMessage());
            return false;
        }
    }

    /**
     * 创建并配置 SSH Session
     */
    private Session createSession(JSch jsch, TargetServer server) throws JSchException {
        if (server.getPrivateKeyPath() != null && !server.getPrivateKeyPath().isEmpty()) {
            try {
                byte[] privateKeyBytes = Files.readAllBytes(Paths.get(server.getPrivateKeyPath()));
                if (server.getPassphrase() != null) {
                    jsch.addIdentity("automatic-key", privateKeyBytes, null,
                            server.getPassphrase().getBytes(StandardCharsets.UTF_8));
                } else {
                    jsch.addIdentity("automatic-key", privateKeyBytes, null, null);
                }
            } catch (IOException e) {
                throw new OpsExecutionException("Failed to read private key: " + server.getPrivateKeyPath(), e);
            }
        }

        Session session = jsch.getSession(server.getUsername(), server.getHost(), server.getSshPort());

        if (server.getPassword() != null && !server.getPassword().isEmpty()) {
            session.setPassword(server.getPassword());
        }

        // Disable strict host key checking for automatic deployment
        java.util.Properties config = new java.util.Properties();
        config.put("StrictHostKeyChecking", "no");
        session.setConfig(config);

        return session;
    }

    /**
     * 构建完整命令字符串
     */
    private String buildFullCommand(ShellCommand cmd) {
        StringBuilder sb = new StringBuilder();
        if (cmd.getWorkingDirectory() != null && !cmd.getWorkingDirectory().isEmpty()) {
            sb.append("cd ").append(cmd.getWorkingDirectory()).append(" && ");
        }
        sb.append(cmd.getFullCommand());
        return sb.toString();
    }

    /**
     * 通过 SCP 传输文件到远程
     */
    public void scpUpload(TargetServer server, String localPath, String remoteDir) {
        JSch jsch = new JSch();
        Session session = null;
        try {
            session = createSession(jsch, server);
            session.connect(30000);

            ChannelSftp channel = (ChannelSftp) session.openChannel("sftp");
            channel.connect();

            try {
                channel.cd(remoteDir);
            } catch (SftpException e) {
                // Directory doesn't exist, create it recursively
                for (String dir : remoteDir.split("/")) {
                    if (!dir.isEmpty()) {
                        try {
                            channel.cd(dir);
                        } catch (SftpException ex) {
                            try {
                                channel.mkdir(dir);
                                channel.cd(dir);
                            } catch (SftpException ex2) {
                                throw new OpsExecutionException("Failed to create directory " + dir, ex2);
                            }
                        }
                    }
                }
            }

            try {
                channel.put(localPath, channel.pwd());
                log.info("File uploaded successfully to {}:{}", server.getHost(), remoteDir);
            } catch (SftpException e) {
                throw new OpsExecutionException("Failed to upload file: " + e.getMessage(), e);
            }

            channel.disconnect();
            session.disconnect();
        } catch (JSchException e) {
            throw new OpsExecutionException("SCP upload failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void executeAndStream(top.codejava.aiops.domain.execution.ShellCommand cmd, top.codejava.aiops.domain.execution.TargetServer server, top.codejava.aiops.application.port.execution.LogStreamCallback callback) {
        JSch jsch = new JSch();
        com.jcraft.jsch.Session session = null;

        try {
            // 创建 SSH session
            session = createSession(jsch, server);
            session.connect( (int) Math.min(cmd.getTimeoutMs(), 30000) );
            log.debug("SSH connected to {}:{}", server.getHost(), server.getSshPort());

            // 打开执行通道
            com.jcraft.jsch.ChannelExec channel = (com.jcraft.jsch.ChannelExec) session.openChannel("exec");

            // 设置工作目录
            String fullCmd = buildFullCommand(cmd);
            channel.setCommand(fullCmd);
            log.debug("Streaming command over SSH: {}", fullCmd);

            // 实时回调每一行输出
            java.io.BufferedReader stdoutReader = new java.io.BufferedReader(
                new java.io.InputStreamReader(channel.getInputStream(), java.nio.charset.StandardCharsets.UTF_8));
            java.io.BufferedReader stderrReader = new java.io.BufferedReader(
                new java.io.InputStreamReader(channel.getErrStream(), java.nio.charset.StandardCharsets.UTF_8));

            channel.connect();

            // 并发读取 stdout 和 stderr，实时回调
            Thread stdoutThread = new Thread(() -> {
                String line;
                try {
                    while ((line = stdoutReader.readLine()) != null) {
                        callback.onNext(line);
                    }
                } catch (java.io.IOException e) {
                    log.debug("Stdout reading interrupted", e);
                }
            });

            Thread stderrThread = new Thread(() -> {
                String line;
                try {
                    while ((line = stderrReader.readLine()) != null) {
                        callback.onError(line);
                    }
                } catch (java.io.IOException e) {
                    log.debug("Stderr reading interrupted", e);
                }
            });

            stdoutThread.start();
            stderrThread.start();

            try {
                stdoutThread.join(cmd.getTimeoutMs());
                stderrThread.join(cmd.getTimeoutMs());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            int exitCode = channel.getExitStatus();
            callback.onComplete(exitCode);

            channel.disconnect();
            session.disconnect();

        } catch (Exception e) {
            log.error("SSH streaming failed: {}", e.getMessage());
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
            throw new OpsExecutionException("SSH streaming failed: " + e.getMessage(), e);
        }
    }
}
