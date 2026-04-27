package top.codejava.aiops.infrastructure.workflow.probe;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import top.codejava.aiops.application.dto.WorkflowModels;
import top.codejava.aiops.infrastructure.ops.SshCommandExecutorAdapter;

import java.util.Locale;
import java.util.Map;

@Component
@Order(25)
public class DependencyProbeNode implements ProbeNode {

    private final SshCommandExecutorAdapter sshCommandExecutorAdapter;

    public DependencyProbeNode(SshCommandExecutorAdapter sshCommandExecutorAdapter) {
        this.sshCommandExecutorAdapter = sshCommandExecutorAdapter;
    }

    @Override
    public void apply(ProbeContext context) {
        for (WorkflowModels.DependencyRequirement requirement : context.dependencyRequirements()) {
            if (requirement == null || requirement.kind() == null) {
                continue;
            }
            context.dependencyProbeResults().add(probeRequirement(context, requirement));
        }
    }

    private WorkflowModels.DependencyProbeResult probeRequirement(ProbeContext context,
                                                                 WorkflowModels.DependencyRequirement requirement) {
        if (requirement.host() == null || requirement.host().isBlank() || isPlaceholder(requirement.host())) {
            return result(requirement, WorkflowModels.DependencyProbeStatus.UNKNOWN, null, "config",
                    "Dependency host is not confirmed from local config.", true);
        }

        if (isLocalHost(requirement.host())) {
            return probeLocalDependency(context, requirement);
        }
        return probeExternalDependency(context, requirement);
    }

    private WorkflowModels.DependencyProbeResult probeLocalDependency(ProbeContext context,
                                                                     WorkflowModels.DependencyRequirement requirement) {
        String command = switch (requirement.kind()) {
            case MYSQL -> localMysqlCommand(context, requirement);
            case REDIS -> localRedisCommand(requirement.port() == null ? 6379 : requirement.port());
        };
        SshCommandExecutorAdapter.SshExecutionResult result = sshCommandExecutorAdapter.execute(
                context.request().credential(),
                command,
                ignored -> {
                }
        );
        if (!result.success()) {
            return result(requirement, WorkflowModels.DependencyProbeStatus.UNKNOWN, null, "ssh",
                    "Failed to probe dependency over SSH: " + result.stderr(), true);
        }
        Map<String, String> values = parseKeyValues(result.stdout());
        WorkflowModels.DependencyProbeStatus status = parseStatus(values.get("AIOPS_DEP_STATUS"));
        return result(
                requirement,
                status,
                values.get("AIOPS_DEP_VERSION"),
                values.getOrDefault("AIOPS_DEP_SOURCE", "host"),
                values.getOrDefault("AIOPS_DEP_MESSAGE", "Dependency probe completed."),
                requirement.required() && status != WorkflowModels.DependencyProbeStatus.READY
        );
    }

    private WorkflowModels.DependencyProbeResult probeExternalDependency(ProbeContext context,
                                                                        WorkflowModels.DependencyRequirement requirement) {
        int port = requirement.port() == null ? defaultPort(requirement.kind()) : requirement.port();
        String command = """
                TARGET_HOST=%s
                TARGET_PORT=%d
                if timeout 3 bash -lc "</dev/tcp/${TARGET_HOST}/${TARGET_PORT}" >/dev/null 2>&1; then
                  printf 'AIOPS_DEP_STATUS=READY\\n'
                  printf 'AIOPS_DEP_SOURCE=tcp\\n'
                  printf 'AIOPS_DEP_MESSAGE=External dependency is reachable from the target host.\\n'
                else
                  printf 'AIOPS_DEP_STATUS=UNREACHABLE\\n'
                  printf 'AIOPS_DEP_SOURCE=tcp\\n'
                  printf 'AIOPS_DEP_MESSAGE=External dependency is not reachable from the target host.\\n'
                fi
                """.formatted(shellSafe(requirement.host()), port);
        SshCommandExecutorAdapter.SshExecutionResult result = sshCommandExecutorAdapter.execute(
                context.request().credential(),
                command,
                ignored -> {
                }
        );
        if (!result.success()) {
            return result(requirement, WorkflowModels.DependencyProbeStatus.UNKNOWN, null, "ssh",
                    "Failed to probe external dependency reachability: " + result.stderr(), true);
        }
        Map<String, String> values = parseKeyValues(result.stdout());
        WorkflowModels.DependencyProbeStatus status = parseStatus(values.get("AIOPS_DEP_STATUS"));
        return result(
                requirement,
                status,
                values.get("AIOPS_DEP_VERSION"),
                values.getOrDefault("AIOPS_DEP_SOURCE", "tcp"),
                values.getOrDefault("AIOPS_DEP_MESSAGE", "Dependency reachability probe completed."),
                requirement.required() && status != WorkflowModels.DependencyProbeStatus.READY
        );
    }

    private WorkflowModels.DependencyProbeResult result(WorkflowModels.DependencyRequirement requirement,
                                                        WorkflowModels.DependencyProbeStatus status,
                                                        String version,
                                                        String source,
                                                        String message,
                                                        boolean requiresDecision) {
        return new WorkflowModels.DependencyProbeResult(
                requirement.kind(),
                requirement.displayName(),
                requirement.required(),
                requirement.host(),
                requirement.port(),
                status,
                version,
                source,
                message,
                requiresDecision
        );
    }

    private String localMysqlCommand(ProbeContext context, WorkflowModels.DependencyRequirement requirement) {
        int port = requirement.port() == null ? 3306 : requirement.port();
        String username = runtimeConfigValue(context,
                "sky.datasource.username",
                "spring.datasource.username",
                "spring.datasource.druid.username",
                "spring.datasource.hikari.username");
        String password = runtimeConfigValue(context,
                "sky.datasource.password",
                "spring.datasource.password",
                "spring.datasource.druid.password",
                "spring.datasource.hikari.password");
        String databaseName = requirement.databaseName();

        StringBuilder command = new StringBuilder();
        command.append("PORT=").append(port).append("\n");
        command.append("MYSQL_USER=").append(shellQuote(username == null || username.isBlank() ? "root" : username)).append("\n");
        command.append("MYSQL_PASSWORD=").append(shellQuote(password == null ? "" : password)).append("\n");
        command.append("DATABASE_NAME=").append(shellQuote(databaseName == null ? "" : databaseName)).append("\n");
        command.append("""
                if command -v docker >/dev/null 2>&1; then
                  RUNNING=$(docker ps --format '{{.Image}}|{{.Names}}|{{.Ports}}' | grep -Ei 'mysql|mariadb' | head -n 1 || true)
                  STOPPED=$(docker ps -a --format '{{.Image}}|{{.Names}}|{{.Status}}' | grep -Ei 'mysql|mariadb' | head -n 1 || true)
                else
                  RUNNING=
                  STOPPED=
                fi
                if [ -n "$RUNNING" ]; then
                  MYSQL_CONTAINER=$(printf '%s' "$RUNNING" | cut -d'|' -f2)
                else
                  MYSQL_CONTAINER=
                fi
                if [ -n "$RUNNING" ]; then
                  STATUS_VALUE=READY
                  SOURCE_VALUE=docker
                  VERSION_VALUE=$(printf '%s' "$RUNNING" | cut -d'|' -f1)
                  MESSAGE_VALUE='MySQL-compatible container is running on the target host.'
                elif ss -lnt 2>/dev/null | grep -q ":$PORT "; then
                  STATUS_VALUE=READY
                  SOURCE_VALUE=port
                  VERSION_VALUE=
                  MESSAGE_VALUE='Target host is listening on the expected MySQL port.'
                elif systemctl is-active mysqld >/dev/null 2>&1 || systemctl is-active mysql >/dev/null 2>&1 || systemctl is-active mariadb >/dev/null 2>&1; then
                  STATUS_VALUE=READY
                  SOURCE_VALUE=systemd
                  VERSION_VALUE=
                  MESSAGE_VALUE='MySQL or MariaDB service is active on the target host.'
                elif [ -n "$STOPPED" ] || command -v mysqld >/dev/null 2>&1 || command -v mysql >/dev/null 2>&1; then
                  STATUS_VALUE=FOUND_INACTIVE
                  SOURCE_VALUE=host
                  VERSION_VALUE=
                  MESSAGE_VALUE='MySQL-compatible runtime exists but does not appear ready.'
                else
                  STATUS_VALUE=NOT_FOUND
                  SOURCE_VALUE=host
                  VERSION_VALUE=
                  MESSAGE_VALUE='MySQL-compatible runtime was not detected on the target host.'
                fi
                if [ "$STATUS_VALUE" = "READY" ] && [ -n "$DATABASE_NAME" ]; then
                  DB_EXISTS=
                  DB_VERIFIED=0
                  if [ -n "$MYSQL_CONTAINER" ]; then
                    DB_VERIFIED=1
                    DB_EXISTS=$(docker exec "$MYSQL_CONTAINER" mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" -Nse "SELECT SCHEMA_NAME FROM INFORMATION_SCHEMA.SCHEMATA WHERE SCHEMA_NAME='$DATABASE_NAME'" 2>/dev/null || true)
                  elif command -v mysql >/dev/null 2>&1; then
                    DB_VERIFIED=1
                    DB_EXISTS=$(mysql -h 127.0.0.1 -P "$PORT" -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" -Nse "SELECT SCHEMA_NAME FROM INFORMATION_SCHEMA.SCHEMATA WHERE SCHEMA_NAME='$DATABASE_NAME'" 2>/dev/null || true)
                  fi
                  if [ "$DB_VERIFIED" = "1" ] && [ -z "$DB_EXISTS" ]; then
                    STATUS_VALUE=FOUND_INACTIVE
                    MESSAGE_VALUE='MySQL service is reachable, but the configured database or credentials could not be verified.'
                  elif [ "$DB_VERIFIED" = "1" ]; then
                    MESSAGE_VALUE='MySQL service and configured database are reachable on the target host.'
                  fi
                fi
                printf 'AIOPS_DEP_STATUS=%s\\n' "$STATUS_VALUE"
                printf 'AIOPS_DEP_SOURCE=%s\\n' "$SOURCE_VALUE"
                printf 'AIOPS_DEP_VERSION=%s\\n' "$VERSION_VALUE"
                printf 'AIOPS_DEP_MESSAGE=%s\\n' "$MESSAGE_VALUE"
                """);
        return command.toString();
    }

    private String localRedisCommand(int port) {
        return """
                PORT=%d
                if command -v docker >/dev/null 2>&1; then
                  RUNNING=$(docker ps --format '{{.Image}}|{{.Names}}|{{.Ports}}' | grep -Ei 'redis' | head -n 1 || true)
                  STOPPED=$(docker ps -a --format '{{.Image}}|{{.Names}}|{{.Status}}' | grep -Ei 'redis' | head -n 1 || true)
                else
                  RUNNING=
                  STOPPED=
                fi
                if [ -n "$RUNNING" ]; then
                  printf 'AIOPS_DEP_STATUS=READY\\n'
                  printf 'AIOPS_DEP_SOURCE=docker\\n'
                  printf 'AIOPS_DEP_VERSION=%%s\\n' "$(printf '%%s' "$RUNNING" | cut -d'|' -f1)"
                  printf 'AIOPS_DEP_MESSAGE=Redis container is running on the target host.\\n'
                elif ss -lnt 2>/dev/null | grep -q ":%s "; then
                  printf 'AIOPS_DEP_STATUS=READY\\n'
                  printf 'AIOPS_DEP_SOURCE=port\\n'
                  printf 'AIOPS_DEP_MESSAGE=Target host is listening on the expected Redis port.\\n'
                elif systemctl is-active redis >/dev/null 2>&1 || systemctl is-active redis-server >/dev/null 2>&1; then
                  printf 'AIOPS_DEP_STATUS=READY\\n'
                  printf 'AIOPS_DEP_SOURCE=systemd\\n'
                  printf 'AIOPS_DEP_MESSAGE=Redis service is active on the target host.\\n'
                elif [ -n "$STOPPED" ] || command -v redis-server >/dev/null 2>&1 || command -v redis-cli >/dev/null 2>&1; then
                  printf 'AIOPS_DEP_STATUS=FOUND_INACTIVE\\n'
                  printf 'AIOPS_DEP_SOURCE=host\\n'
                  printf 'AIOPS_DEP_MESSAGE=Redis runtime exists but does not appear ready.\\n'
                else
                  printf 'AIOPS_DEP_STATUS=NOT_FOUND\\n'
                  printf 'AIOPS_DEP_SOURCE=host\\n'
                  printf 'AIOPS_DEP_MESSAGE=Redis runtime was not detected on the target host.\\n'
                fi
                """.formatted(port, port);
    }

    private Map<String, String> parseKeyValues(String stdout) {
        java.util.LinkedHashMap<String, String> values = new java.util.LinkedHashMap<>();
        if (stdout == null || stdout.isBlank()) {
            return values;
        }
        for (String line : stdout.split("\\R")) {
            int index = line.indexOf('=');
            if (index <= 0) {
                continue;
            }
            values.put(line.substring(0, index).trim(), line.substring(index + 1).trim());
        }
        return values;
    }

    private WorkflowModels.DependencyProbeStatus parseStatus(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return WorkflowModels.DependencyProbeStatus.UNKNOWN;
        }
        try {
            return WorkflowModels.DependencyProbeStatus.valueOf(rawValue.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return WorkflowModels.DependencyProbeStatus.UNKNOWN;
        }
    }

    private boolean isLocalHost(String host) {
        String normalized = host.trim().toLowerCase(Locale.ROOT);
        return "localhost".equals(normalized) || "127.0.0.1".equals(normalized) || "::1".equals(normalized);
    }

    private boolean isPlaceholder(String value) {
        return value.contains("${") || value.startsWith("{");
    }

    private int defaultPort(WorkflowModels.DependencyKind kind) {
        return kind == WorkflowModels.DependencyKind.REDIS ? 6379 : 3306;
    }

    private String runtimeConfigValue(ProbeContext context, String... keys) {
        if (context.localContext() == null || context.localContext().runtimeConfigItems() == null) {
            return null;
        }
        for (String key : keys) {
            for (WorkflowModels.RuntimeConfigItem item : context.localContext().runtimeConfigItems()) {
                if (item != null && key.equals(item.key()) && item.valuePreview() != null && !item.valuePreview().isBlank()) {
                    return item.valuePreview();
                }
            }
        }
        return null;
    }

    private String shellSafe(String value) {
        return value.replace("'", "");
    }

    private String shellQuote(String value) {
        if (value == null) {
            return "''";
        }
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }
}
