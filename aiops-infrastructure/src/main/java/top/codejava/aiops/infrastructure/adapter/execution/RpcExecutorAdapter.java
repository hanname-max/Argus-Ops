// aiops-infrastructure/src/main/java/top/codejava/aiops/infrastructure/adapter/execution/RpcExecutorAdapter.java
package top.codejava.aiops.infrastructure.adapter.execution;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import top.codejava.aiops.application.port.execution.OpsExecutorPort;
import top.codejava.aiops.domain.exception.OpsExecutionException;
import top.codejava.aiops.domain.execution.ExecutionResult;
import top.codejava.aiops.domain.execution.ShellCommand;
import top.codejava.aiops.domain.execution.TargetServer;

import java.net.SocketTimeoutException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.io.IOException;

/**
 * RPC 执行适配器
 * 通过 HTTP RPC 调用远程 Daemon 执行命令
 * 比直接 SSH 更高效，支持更多特性
 */
@Slf4j
@RequiredArgsConstructor
public class RpcExecutorAdapter implements OpsExecutorPort {

    private final RestTemplate restTemplate;

    public RpcExecutorAdapter() {
        this.restTemplate = new RestTemplate();
    }

    @Override
    public ExecutionResult execute(ShellCommand cmd, TargetServer server) {
        long startTime = System.currentTimeMillis();
        String url = server.getRpcBaseUrl() + "/api/v1/execute";

        RpcExecuteRequest request = new RpcExecuteRequest(
                cmd.getArguments(),
                cmd.getWorkingDirectory(),
                cmd.getTimeoutMs()
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<RpcExecuteRequest> entity = new HttpEntity<>(request, headers);

        try {
            RpcExecuteResponse response = restTemplate.postForObject(
                    url, entity, RpcExecuteResponse.class);

            if (response == null) {
                throw new OpsExecutionException("Empty response from RPC daemon");
            }

            long duration = System.currentTimeMillis() - startTime;
            log.debug("RPC execution completed with exit code {} in {}ms",
                    response.exitCode(), duration);

            return ExecutionResult.builder()
                    .exitCode(response.exitCode())
                    .stdout(response.stdout())
                    .stderr(response.stderr())
                    .durationMs(duration)
                    .build();

        } catch (ResourceAccessException e) {
            if (e.getRootCause() instanceof SocketTimeoutException) {
                throw new OpsExecutionException("RPC daemon timeout at " + url, e);
            }
            throw new OpsExecutionException("RPC connection failed: " + url, e);
        } catch (Exception e) {
            throw new OpsExecutionException("RPC execution failed: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean probePort(TargetServer server, int port, int timeoutMs) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(server.getHost(), port), timeoutMs);
            return true;
        } catch (IOException e) {
            log.debug("RPC port {} on {} is not reachable: {}", port, server.getHost(), e.getMessage());
            return false;
        }
    }

    /**
     * RPC 请求记录
     */
    public record RpcExecuteRequest(java.util.List<String> command, String workingDirectory, long timeout) {}

    /**
     * RPC 响应记录
     */
    public record RpcExecuteResponse(int exitCode, String stdout, String stderr) {}
}
