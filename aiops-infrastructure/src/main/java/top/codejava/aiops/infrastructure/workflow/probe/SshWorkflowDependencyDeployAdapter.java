package top.codejava.aiops.infrastructure.workflow.probe;

import org.springframework.stereotype.Component;
import top.codejava.aiops.application.dto.WorkflowModels;
import top.codejava.aiops.application.port.WorkflowDependencyDeployPort;
import top.codejava.aiops.infrastructure.ops.SshCommandExecutorAdapter;

import java.util.function.Consumer;

@Component
public class SshWorkflowDependencyDeployAdapter implements WorkflowDependencyDeployPort {

    private static final int DEPLOY_COMMAND_TIMEOUT_MILLIS = 180_000;
    private static final int DEFAULT_CONNECT_TIMEOUT_MILLIS = 8_000;

    private final SshCommandExecutorAdapter sshCommandExecutorAdapter;

    public SshWorkflowDependencyDeployAdapter(SshCommandExecutorAdapter sshCommandExecutorAdapter) {
        this.sshCommandExecutorAdapter = sshCommandExecutorAdapter;
    }

    @Override
    public DependencyDeployPayload deploy(WorkflowModels.DeployDependencyRequest request) {
        String script = generateDeployScript(request);
        WorkflowModels.TargetCredential credential = request.credential();
        if (credential == null) {
            return new DependencyDeployPayload(false, "No credential provided", "", "");
        }

        Consumer<SshCommandExecutorAdapter.LogFrame> logSink = ignored -> {};

        SshCommandExecutorAdapter.SshExecutionRequest executionRequest = new SshCommandExecutorAdapter.SshExecutionRequest(
                credential.host(),
                normalizePort(credential.sshPort()),
                credential.username(),
                credential.password(),
                credential.privateKeyPem(),
                script,
                credential.connectTimeoutMillis() == null ? DEFAULT_CONNECT_TIMEOUT_MILLIS : credential.connectTimeoutMillis(),
                DEPLOY_COMMAND_TIMEOUT_MILLIS,
                false,
                null,
                false
        );

        SshCommandExecutorAdapter.SshExecutionResult result = sshCommandExecutorAdapter.execute(
                executionRequest,
                logSink
        );

        return new DependencyDeployPayload(
                result.success(),
                result.success() ? "Deployment completed successfully" : "Deployment failed with exit code " + result.exitStatus(),
                result.stdout(),
                result.stderr()
        );
    }

    private int normalizePort(Integer port) {
        return port == null || port <= 0 ? 22 : port;
    }

    private String generateDeployScript(WorkflowModels.DeployDependencyRequest request) {
        return switch (request.kind()) {
            case MYSQL -> generateMysqlScript(request);
            case REDIS -> generateRedisScript(request);
        };
    }

    private String generateMysqlScript(WorkflowModels.DeployDependencyRequest request) {
        String username = request.username() == null || request.username().isBlank() ? "root" : request.username();
        String password = request.password() == null || request.password().isBlank() ? "123456" : request.password();
        String databaseName = request.databaseName() == null || request.databaseName().isBlank() ? "app" : request.databaseName();
        int hostPort = request.port() == null ? 3306 : request.port();

        StringBuilder builder = new StringBuilder();
        builder.append("set -euo pipefail\n");
        builder.append("MYSQL_ROOT_PASSWORD=").append(shellQuote(password)).append("\n");
        builder.append("MYSQL_APP_USER=").append(shellQuote(username)).append("\n");
        builder.append("MYSQL_APP_PASSWORD=").append(shellQuote(password)).append("\n");
        builder.append("MYSQL_APP_DATABASE=").append(shellQuote(databaseName)).append("\n");
        builder.append("echo '[AIOPS] ensure mysql dependency'\n");
        builder.append("docker rm -f argus-dependency-mysql >/dev/null 2>&1 || true\n");
        builder.append("docker volume rm -f argus-dependency-mysql-data >/dev/null 2>&1 || true\n");
        builder.append("docker volume create argus-dependency-mysql-data >/dev/null 2>&1 || true\n");
        builder.append("docker run -d --restart unless-stopped --name argus-dependency-mysql ");
        builder.append("-p ").append(hostPort).append(":3306 ");
        builder.append("-e MYSQL_ROOT_PASSWORD=\"$MYSQL_ROOT_PASSWORD\" ");
        builder.append("-e MYSQL_DATABASE=\"$MYSQL_APP_DATABASE\" ");
        if (!"root".equalsIgnoreCase(username)) {
            builder.append("-e MYSQL_USER=\"$MYSQL_APP_USER\" ");
            builder.append("-e MYSQL_PASSWORD=\"$MYSQL_APP_PASSWORD\" ");
        }
        builder.append("-v argus-dependency-mysql-data:/var/lib/mysql ");
        builder.append("mysql:8.0\n");
        builder.append("MYSQL_READY=0\n");
        builder.append("for attempt in $(seq 1 24); do\n");
        builder.append("  if docker exec argus-dependency-mysql mysqladmin ping -h 127.0.0.1 -uroot -p\"$MYSQL_ROOT_PASSWORD\" --silent >/dev/null 2>&1; then\n");
        builder.append("    MYSQL_READY=1\n");
        builder.append("    break\n");
        builder.append("  fi\n");
        builder.append("  sleep 5\n");
        builder.append("done\n");
        builder.append("if [ \"$MYSQL_READY\" != \"1\" ]; then\n");
        builder.append("  docker logs --tail 80 argus-dependency-mysql || true\n");
        builder.append("  echo '[AIOPS] mysql dependency did not become ready in time.' >&2\n");
        builder.append("  exit 1\n");
        builder.append("fi\n");
        builder.append("docker logs --tail 20 argus-dependency-mysql || true");
        return builder.toString();
    }

    private String generateRedisScript(WorkflowModels.DeployDependencyRequest request) {
        String password = request.password() == null ? "" : request.password();
        int hostPort = request.port() == null ? 6379 : request.port();

        StringBuilder builder = new StringBuilder();
        builder.append("set -euo pipefail\n");
        builder.append("REDIS_PASSWORD=").append(shellQuote(password)).append("\n");
        builder.append("echo '[AIOPS] ensure redis dependency'\n");
        builder.append("docker rm -f argus-dependency-redis >/dev/null 2>&1 || true\n");
        builder.append("docker run -d --restart unless-stopped --name argus-dependency-redis ");
        builder.append("-p ").append(hostPort).append(":6379 ");
        if (password != null && !password.isBlank()) {
            builder.append("redis:7-alpine redis-server --appendonly yes --requirepass \"$REDIS_PASSWORD\"");
        } else {
            builder.append("redis:7-alpine redis-server --appendonly yes");
        }
        builder.append("\n");
        builder.append("REDIS_READY=0\n");
        builder.append("for attempt in $(seq 1 20); do\n");
        builder.append("  if [ -n \"$REDIS_PASSWORD\" ]; then\n");
        builder.append("    if docker exec argus-dependency-redis redis-cli -a \"$REDIS_PASSWORD\" ping 2>/dev/null | grep -q '^PONG$'; then\n");
        builder.append("      REDIS_READY=1\n");
        builder.append("      break\n");
        builder.append("    fi\n");
        builder.append("  elif docker exec argus-dependency-redis redis-cli ping 2>/dev/null | grep -q '^PONG$'; then\n");
        builder.append("    REDIS_READY=1\n");
        builder.append("    break\n");
        builder.append("  fi\n");
        builder.append("  sleep 3\n");
        builder.append("done\n");
        builder.append("if [ \"$REDIS_READY\" != \"1\" ]; then\n");
        builder.append("  docker logs --tail 80 argus-dependency-redis || true\n");
        builder.append("  echo '[AIOPS] redis dependency did not become ready in time.' >&2\n");
        builder.append("  exit 1\n");
        builder.append("fi\n");
        builder.append("docker logs --tail 20 argus-dependency-redis || true");
        return builder.toString();
    }

    private String shellQuote(String value) {
        if (value == null) {
            return "''";
        }
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }
}
