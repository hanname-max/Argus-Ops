package top.codejava.aiops.infrastructure.ops;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import org.springframework.stereotype.Component;
import top.codejava.aiops.application.dto.RemoteDeployRequest;
import top.codejava.aiops.application.dto.WorkflowModels;
import top.codejava.aiops.type.exception.RemoteExecutionException;
import top.codejava.aiops.type.exception.SshConnectionException;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/**
 * Shared SSH command executor for Argus-Ops infrastructure adapters.
 *
 * <p>When privilege escalation is required, prefer configuring sudoers to avoid interactive prompts:
 * <pre>{@code
 * # Run visudo on the remote host
 * Defaults:argus !requiretty
 * argus ALL=(ALL) NOPASSWD: /usr/bin/docker, /usr/bin/systemctl, /usr/sbin/nginx
 *
 * # Or, if your risk model allows it in an isolated test host:
 * argus ALL=(ALL) NOPASSWD: ALL
 * }</pre>
 *
 * <p>If password-less sudo is not available, this adapter supports {@code sudo -S -p ''} and injects
 * the sudo password into the remote process stdin immediately after the exec channel is connected.
 */
@Component
public class SshCommandExecutorAdapter {

    private static final int DEFAULT_CONNECT_TIMEOUT_MILLIS = 8_000;
    private static final int DEFAULT_COMMAND_TIMEOUT_MILLIS = 300_000;
    private static final ThreadFactory LOG_PUMP_THREAD_FACTORY = runnable -> {
        Thread thread = new Thread(runnable, "aiops-ssh-log-pump");
        thread.setDaemon(true);
        return thread;
    };

    private final SshSessionFactory sshSessionFactory;

    public SshCommandExecutorAdapter(SshSessionFactory sshSessionFactory) {
        this.sshSessionFactory = sshSessionFactory;
    }

    public SshExecutionResult execute(RemoteDeployRequest request) {
        return execute(
                new SshExecutionRequest(
                        request.host(),
                        normalizePort(request.port()),
                        request.username(),
                        request.password(),
                        null,
                        request.command(),
                        DEFAULT_CONNECT_TIMEOUT_MILLIS,
                        DEFAULT_COMMAND_TIMEOUT_MILLIS,
                        request.useSudo(),
                        request.sudoPassword(),
                        false
                ),
                logFrame -> {
                }
        );
    }

    public SshExecutionResult execute(WorkflowModels.TargetCredential credential,
                                      String command,
                                      Consumer<LogFrame> logSink) {
        return execute(
                new SshExecutionRequest(
                        credential.host(),
                        normalizePort(credential.sshPort()),
                        credential.username(),
                        credential.password(),
                        credential.privateKeyPem(),
                        command,
                        credential.connectTimeoutMillis() == null ? DEFAULT_CONNECT_TIMEOUT_MILLIS : credential.connectTimeoutMillis(),
                        20_000,
                        false,
                        null,
                        false
                ),
                logSink
        );
    }

    public SshExecutionResult execute(SshExecutionRequest request, Consumer<LogFrame> logSink) {
        Objects.requireNonNull(request, "request");
        Consumer<LogFrame> safeLogSink = logSink == null ? ignored -> {
        } : logSink;

        Session session = null;
        ChannelExec channel = null;
        OutputStream stdin = null;
        ExecutorService pumpExecutor = Executors.newFixedThreadPool(2, LOG_PUMP_THREAD_FACTORY);

        StringBuilder stdoutBuffer = new StringBuilder();
        StringBuilder stderrBuffer = new StringBuilder();

        try {
            session = openSession(request);
            channel = (ChannelExec) session.openChannel("exec");
            channel.setPty(request.allocatePty());
            channel.setCommand(buildShellCommand(request.command(), request.useSudo()));

            InputStream stdout = channel.getInputStream();
            InputStream stderr = channel.getErrStream();
            stdin = channel.getOutputStream();

            Future<?> stdoutPump = startPump(stdout, LogStream.STDOUT, stdoutBuffer, safeLogSink, pumpExecutor);
            Future<?> stderrPump = startPump(stderr, LogStream.STDERR, stderrBuffer, safeLogSink, pumpExecutor);

            channel.connect(request.connectTimeoutMillis());
            writeSudoPasswordIfNeeded(request, stdin);

            int exitStatus = waitForExit(channel, request.commandTimeoutMillis());
            awaitPump(stdoutPump, request.commandTimeoutMillis());
            awaitPump(stderrPump, request.commandTimeoutMillis());

            return new SshExecutionResult(exitStatus == 0, exitStatus, stdoutBuffer.toString(), stderrBuffer.toString());
        } catch (SshConnectionException ex) {
            throw ex;
        } catch (JSchException ex) {
            throw new SshConnectionException("Failed to open SSH exec channel: " + ex.getMessage(), ex);
        } catch (IOException | InterruptedException | ExecutionException | TimeoutException ex) {
            if (ex instanceof InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                throw new RemoteExecutionException("Remote command execution was interrupted", interruptedException);
            }
            throw new RemoteExecutionException("Failed to execute remote command over SSH", ex);
        } finally {
            closeQuietly(stdin);
            if (channel != null) {
                channel.disconnect();
            }
            if (session != null) {
                session.disconnect();
            }
            shutdownPumpExecutor(pumpExecutor);
        }
    }

    private Session openSession(SshExecutionRequest request) {
        return sshSessionFactory.openSession(new SshSessionFactory.SshAccessRequest(
                request.host(),
                request.port(),
                request.username(),
                request.password(),
                request.privateKeyPem(),
                request.connectTimeoutMillis()
        ));
    }

    private String buildShellCommand(String rawCommand, boolean useSudo) {
        String shellWrapped = "bash -lc " + shellQuote(rawCommand);
        if (!useSudo) {
            return shellWrapped;
        }
        return "sudo -S -p '' " + shellWrapped;
    }

    private void writeSudoPasswordIfNeeded(SshExecutionRequest request, OutputStream stdin) throws IOException {
        if (!request.useSudo()) {
            closeQuietly(stdin);
            return;
        }

        String sudoPassword = resolveSudoPassword(request);
        stdin.write((sudoPassword + "\n").getBytes(StandardCharsets.UTF_8));
        stdin.flush();
        closeQuietly(stdin);
    }

    private String resolveSudoPassword(SshExecutionRequest request) {
        if (request.sudoPassword() != null && !request.sudoPassword().isBlank()) {
            return request.sudoPassword();
        }
        if (request.password() != null && !request.password().isBlank()) {
            return request.password();
        }
        throw new RemoteExecutionException("sudo -S requested but no sudo password was provided", null);
    }

    private Future<?> startPump(InputStream stream,
                                LogStream streamType,
                                StringBuilder aggregate,
                                Consumer<LogFrame> logSink,
                                ExecutorService executorService) {
        return executorService.submit(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    aggregate.append(line).append(System.lineSeparator());
                    logSink.accept(new LogFrame(streamType, line));
                }
            } catch (IOException ex) {
                throw new IllegalStateException("Failed to consume remote " + streamType + " stream", ex);
            }
        });
    }

    private int waitForExit(ChannelExec channel, int timeoutMillis) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofMillis(timeoutMillis).toNanos();
        while (!channel.isClosed()) {
            if (System.nanoTime() > deadline) {
                channel.disconnect();
                throw new RemoteExecutionException(
                        "Remote command timed out after " + timeoutMillis + " ms",
                        new TimeoutException("SSH exec timeout")
                );
            }
            Thread.sleep(100);
        }
        return channel.getExitStatus();
    }

    private void awaitPump(Future<?> future, int timeoutMillis) throws InterruptedException, ExecutionException, TimeoutException {
        future.get(Math.max(timeoutMillis, 1_000), TimeUnit.MILLISECONDS);
    }

    private void shutdownPumpExecutor(ExecutorService executorService) {
        executorService.shutdownNow();
        try {
            executorService.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private String shellQuote(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

    private int normalizePort(Integer port) {
        return sshSessionFactory.normalizePort(port);
    }

    private void closeQuietly(Closeable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (IOException ignored) {
        }
    }

    public record SshExecutionRequest(
            String host,
            int port,
            String username,
            String password,
            String privateKeyPem,
            String command,
            int connectTimeoutMillis,
            int commandTimeoutMillis,
            boolean useSudo,
            String sudoPassword,
            boolean allocatePty
    ) {
    }

    public record SshExecutionResult(
            boolean success,
            int exitStatus,
            String stdout,
            String stderr
    ) {
    }

    public record LogFrame(LogStream stream, String line) {
    }

    public enum LogStream {
        STDOUT,
        STDERR
    }
}
