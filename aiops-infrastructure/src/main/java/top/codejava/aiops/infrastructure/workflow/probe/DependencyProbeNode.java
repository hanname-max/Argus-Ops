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
                    "Dependency host is not confirmed. Please provide a valid host address.", true);
        }

        if (isLocalHost(requirement.host())) {
            return probeLocalDependency(context, requirement);
        }
        return probeExternalDependency(context, requirement);
    }

    private WorkflowModels.DependencyProbeResult probeLocalDependency(ProbeContext context,
                                                                     WorkflowModels.DependencyRequirement requirement) {
        WorkflowModels.DependencyOverride override = context.dependencyOverride(requirement.kind());
        String command = switch (requirement.kind()) {
            case MYSQL -> localMysqlCommand(context, requirement, override);
            case REDIS -> localRedisCommand(requirement, override);
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
        WorkflowModels.DependencyOverride override = context.dependencyOverride(requirement.kind());
        String command = switch (requirement.kind()) {
            case MYSQL -> externalMysqlCommand(requirement, port, override);
            case REDIS -> externalRedisCommand(requirement, port, override);
        };
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

    private String localMysqlCommand(ProbeContext context,
                                      WorkflowModels.DependencyRequirement requirement,
                                      WorkflowModels.DependencyOverride override) {
        int port = requirement.port() == null ? 3306 : requirement.port();

        String username = null;
        String password = null;
        String databaseName = requirement.databaseName();

        if (override != null) {
            if (override.username() != null && !override.username().isBlank()) {
                username = override.username();
            }
            if (override.password() != null) {
                password = override.password();
            }
            if (override.databaseName() != null && !override.databaseName().isBlank()) {
                databaseName = override.databaseName();
            }
        }

        if (username == null || username.isBlank()) {
            username = runtimeConfigValue(context,
                    "sky.datasource.username",
                    "spring.datasource.username",
                    "spring.datasource.druid.username",
                    "spring.datasource.hikari.username");
            if (username == null || username.isBlank()) {
                username = "root";
            }
        }

        if (password == null) {
            password = runtimeConfigValue(context,
                    "sky.datasource.password",
                    "spring.datasource.password",
                    "spring.datasource.druid.password",
                    "spring.datasource.hikari.password");
            if (password == null) {
                password = "";
            }
        }

        StringBuilder command = new StringBuilder();
        command.append("TARGET_PORT=").append(port).append("\n");
        command.append("MYSQL_USER=").append(shellQuote(username)).append("\n");
        command.append("MYSQL_PASSWORD=").append(shellQuote(password)).append("\n");
        command.append("DATABASE_NAME=").append(shellQuote(databaseName == null ? "" : databaseName)).append("\n");
        command.append("""
                CONTAINER_ID=
                CONTAINER_PORT_MATCH=0
                CONTAINER_VERSION=
                PORT_LISTENING=0
                SERVICE_ACTIVE=0
                RUNTIME_EXISTS=0

                if command -v docker >/dev/null 2>&1; then
                  while IFS= read -r line; do
                    [ -z "$line" ] && continue
                    IMAGE=$(printf '%s' "$line" | cut -d'|' -f1)
                    NAME=$(printf '%s' "$line" | cut -d'|' -f2)
                    PORTS=$(printf '%s' "$line" | cut -d'|' -f3)

                    if echo "$PORTS" | grep -qE "(^|[^0-9])${TARGET_PORT}->"; then
                      CONTAINER_ID="$NAME"
                      CONTAINER_PORT_MATCH=1
                      CONTAINER_VERSION="$IMAGE"
                      break
                    fi
                  done <<EOF
                $(docker ps --format '{{.Image}}|{{.Names}}|{{.Ports}}' | grep -Ei 'mysql|mariadb' || true)
                EOF
                fi

                if ss -lntH "( sport = :$TARGET_PORT )" 2>/dev/null | grep -q .; then
                  PORT_LISTENING=1
                fi

                if systemctl is-active mysqld >/dev/null 2>&1 || systemctl is-active mysql >/dev/null 2>&1 || systemctl is-active mariadb >/dev/null 2>&1; then
                  SERVICE_ACTIVE=1
                fi

                if command -v mysqld >/dev/null 2>&1 || command -v mysql >/dev/null 2>&1; then
                  RUNTIME_EXISTS=1
                fi

                DB_VERIFIED=0
                DB_EXISTS=0

                if [ "$CONTAINER_PORT_MATCH" = "1" ]; then
                  if docker exec "$CONTAINER_ID" mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" -e "SELECT 1" >/dev/null 2>&1; then
                    DB_VERIFIED=1
                    if [ -n "$DATABASE_NAME" ]; then
                      DB_EXISTS=$(docker exec "$CONTAINER_ID" mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" -Nse "SELECT SCHEMA_NAME FROM INFORMATION_SCHEMA.SCHEMATA WHERE SCHEMA_NAME='$DATABASE_NAME'" 2>/dev/null || true)
                    fi
                  fi
                elif [ "$PORT_LISTENING" = "1" ] && command -v mysql >/dev/null 2>&1; then
                  if mysql -h 127.0.0.1 -P "$TARGET_PORT" -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" -e "SELECT 1" >/dev/null 2>&1; then
                    DB_VERIFIED=1
                    if [ -n "$DATABASE_NAME" ]; then
                      DB_EXISTS=$(mysql -h 127.0.0.1 -P "$TARGET_PORT" -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" -Nse "SELECT SCHEMA_NAME FROM INFORMATION_SCHEMA.SCHEMATA WHERE SCHEMA_NAME='$DATABASE_NAME'" 2>/dev/null || true)
                    fi
                  fi
                fi

                if [ "$DB_VERIFIED" = "1" ] && { [ -z "$DATABASE_NAME" ] || [ -n "$DB_EXISTS" ]; }; then
                  STATUS_VALUE=READY
                  if [ "$CONTAINER_PORT_MATCH" = "1" ]; then
                    SOURCE_VALUE=docker
                    VERSION_VALUE="$CONTAINER_VERSION"
                  else
                    SOURCE_VALUE=mysql
                    VERSION_VALUE=
                  fi
                  if [ -n "$DATABASE_NAME" ] && [ -n "$DB_EXISTS" ]; then
                    MESSAGE_VALUE='MySQL service is reachable with valid credentials and database exists on the target host.'
                  else
                    MESSAGE_VALUE='MySQL service is reachable with valid credentials on the target host.'
                  fi
                elif [ "$CONTAINER_PORT_MATCH" = "1" ]; then
                  STATUS_VALUE=FOUND_INACTIVE
                  SOURCE_VALUE=docker
                  VERSION_VALUE="$CONTAINER_VERSION"
                  MESSAGE_VALUE='MySQL container is running on the expected port but credentials or database could not be verified.'
                elif [ "$PORT_LISTENING" = "1" ]; then
                  STATUS_VALUE=UNKNOWN
                  SOURCE_VALUE=port
                  VERSION_VALUE=
                  MESSAGE_VALUE='Port is listening but MySQL protocol verification failed (credentials may be incorrect or mysql client not available).'
                elif [ "$SERVICE_ACTIVE" = "1" ]; then
                  STATUS_VALUE=FOUND_INACTIVE
                  SOURCE_VALUE=systemd
                  VERSION_VALUE=
                  MESSAGE_VALUE='MySQL service is active but not listening on the expected port or verification failed.'
                elif [ "$RUNTIME_EXISTS" = "1" ]; then
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

                printf 'AIOPS_DEP_STATUS=%s\\n' "$STATUS_VALUE"
                printf 'AIOPS_DEP_SOURCE=%s\\n' "$SOURCE_VALUE"
                printf 'AIOPS_DEP_VERSION=%s\\n' "$VERSION_VALUE"
                printf 'AIOPS_DEP_MESSAGE=%s\\n' "$MESSAGE_VALUE"
                """);
        return command.toString();
    }

    private String localRedisCommand(WorkflowModels.DependencyRequirement requirement,
                                      WorkflowModels.DependencyOverride override) {
        int port = requirement.port() == null ? 6379 : requirement.port();
        String password = null;

        if (override != null && override.password() != null) {
            password = override.password();
        }

        if (password == null) {
            password = "";
        }

        return """
                TARGET_PORT=%d
                REDIS_PASSWORD=%s

                CONTAINER_ID=
                CONTAINER_PORT_MATCH=0
                CONTAINER_VERSION=
                PORT_LISTENING=0
                SERVICE_ACTIVE=0
                RUNTIME_EXISTS=0

                if command -v docker >/dev/null 2>&1; then
                  while IFS= read -r line; do
                    [ -z "$line" ] && continue
                    IMAGE=$(printf '%s' "$line" | cut -d'|' -f1)
                    NAME=$(printf '%s' "$line" | cut -d'|' -f2)
                    PORTS=$(printf '%s' "$line" | cut -d'|' -f3)

                    if echo "$PORTS" | grep -qE "(^|[^0-9])${TARGET_PORT}->"; then
                      CONTAINER_ID="$NAME"
                      CONTAINER_PORT_MATCH=1
                      CONTAINER_VERSION="$IMAGE"
                      break
                    fi
                  done <<EOF
                $(docker ps --format '{{.Image}}|{{.Names}}|{{.Ports}}' | grep -Ei 'redis' || true)
                EOF
                fi

                if ss -lntH "( sport = :$TARGET_PORT )" 2>/dev/null | grep -q .; then
                  PORT_LISTENING=1
                fi

                if systemctl is-active redis >/dev/null 2>&1 || systemctl is-active redis-server >/dev/null 2>&1; then
                  SERVICE_ACTIVE=1
                fi

                if command -v redis-server >/dev/null 2>&1 || command -v redis-cli >/dev/null 2>&1; then
                  RUNTIME_EXISTS=1
                fi

                PING_SUCCESS=0

                if [ "$CONTAINER_PORT_MATCH" = "1" ]; then
                  if [ -n "$REDIS_PASSWORD" ]; then
                    if docker exec "$CONTAINER_ID" redis-cli -a "$REDIS_PASSWORD" ping 2>/dev/null | grep -q '^PONG$'; then
                      PING_SUCCESS=1
                    fi
                  else
                    if docker exec "$CONTAINER_ID" redis-cli ping 2>/dev/null | grep -q '^PONG$'; then
                      PING_SUCCESS=1
                    fi
                  fi
                elif [ "$PORT_LISTENING" = "1" ] && command -v redis-cli >/dev/null 2>&1; then
                  if [ -n "$REDIS_PASSWORD" ]; then
                    if redis-cli -h 127.0.0.1 -p "$TARGET_PORT" -a "$REDIS_PASSWORD" ping 2>/dev/null | grep -q '^PONG$'; then
                      PING_SUCCESS=1
                    fi
                  else
                    if redis-cli -h 127.0.0.1 -p "$TARGET_PORT" ping 2>/dev/null | grep -q '^PONG$'; then
                      PING_SUCCESS=1
                    fi
                  fi
                fi

                if [ "$PING_SUCCESS" = "1" ]; then
                  STATUS_VALUE=READY
                  if [ "$CONTAINER_PORT_MATCH" = "1" ]; then
                    SOURCE_VALUE=docker
                    VERSION_VALUE="$CONTAINER_VERSION"
                  else
                    SOURCE_VALUE=redis
                    VERSION_VALUE=
                  fi
                  MESSAGE_VALUE='Redis is reachable with valid credentials on the target host (PING returned PONG).'
                elif [ "$CONTAINER_PORT_MATCH" = "1" ]; then
                  STATUS_VALUE=FOUND_INACTIVE
                  SOURCE_VALUE=docker
                  VERSION_VALUE="$CONTAINER_VERSION"
                  MESSAGE_VALUE='Redis container is running on the expected port but PING verification failed (password may be incorrect).'
                elif [ "$PORT_LISTENING" = "1" ]; then
                  STATUS_VALUE=UNKNOWN
                  SOURCE_VALUE=port
                  VERSION_VALUE=
                  MESSAGE_VALUE='Port is listening but Redis protocol verification failed (password may be incorrect or redis-cli not available).'
                elif [ "$SERVICE_ACTIVE" = "1" ]; then
                  STATUS_VALUE=FOUND_INACTIVE
                  SOURCE_VALUE=systemd
                  VERSION_VALUE=
                  MESSAGE_VALUE='Redis service is active but not listening on the expected port or verification failed.'
                elif [ "$RUNTIME_EXISTS" = "1" ]; then
                  STATUS_VALUE=FOUND_INACTIVE
                  SOURCE_VALUE=host
                  VERSION_VALUE=
                  MESSAGE_VALUE='Redis runtime exists but does not appear ready.'
                else
                  STATUS_VALUE=NOT_FOUND
                  SOURCE_VALUE=host
                  VERSION_VALUE=
                  MESSAGE_VALUE='Redis runtime was not detected on the target host.'
                fi

                printf 'AIOPS_DEP_STATUS=%s\\n' "$STATUS_VALUE"
                printf 'AIOPS_DEP_SOURCE=%s\\n' "$SOURCE_VALUE"
                printf 'AIOPS_DEP_VERSION=%s\\n' "$VERSION_VALUE"
                printf 'AIOPS_DEP_MESSAGE=%s\\n' "$MESSAGE_VALUE"
                """.formatted(port, shellQuote(password));
    }

    private String externalMysqlCommand(WorkflowModels.DependencyRequirement requirement,
                                         int port,
                                         WorkflowModels.DependencyOverride override) {
        String host = requirement.host();
        String username = "root";
        String password = "";
        String databaseName = requirement.databaseName();

        if (override != null) {
            if (override.username() != null && !override.username().isBlank()) {
                username = override.username();
            }
            if (override.password() != null) {
                password = override.password();
            }
            if (override.databaseName() != null && !override.databaseName().isBlank()) {
                databaseName = override.databaseName();
            }
        }

        return """
                TARGET_HOST=%s
                TARGET_PORT=%d
                MYSQL_USER=%s
                MYSQL_PASSWORD=%s
                DATABASE_NAME=%s

                TCP_REACHABLE=0
                DB_VERIFIED=0
                DB_EXISTS=0

                if timeout 3 bash -lc "</dev/tcp/${TARGET_HOST}/${TARGET_PORT}" >/dev/null 2>&1; then
                  TCP_REACHABLE=1
                fi

                if [ "$TCP_REACHABLE" = "1" ] && command -v mysql >/dev/null 2>&1; then
                  if mysql -h "$TARGET_HOST" -P "$TARGET_PORT" -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" -e "SELECT 1" >/dev/null 2>&1; then
                    DB_VERIFIED=1
                    if [ -n "$DATABASE_NAME" ]; then
                      DB_EXISTS=$(mysql -h "$TARGET_HOST" -P "$TARGET_PORT" -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" -Nse "SELECT SCHEMA_NAME FROM INFORMATION_SCHEMA.SCHEMATA WHERE SCHEMA_NAME='$DATABASE_NAME'" 2>/dev/null || true)
                    fi
                  fi
                fi

                if [ "$DB_VERIFIED" = "1" ] && { [ -z "$DATABASE_NAME" ] || [ -n "$DB_EXISTS" ]; }; then
                  STATUS_VALUE=READY
                  SOURCE_VALUE=mysql
                  if [ -n "$DATABASE_NAME" ] && [ -n "$DB_EXISTS" ]; then
                    MESSAGE_VALUE='External MySQL is reachable with valid credentials and database exists.'
                  else
                    MESSAGE_VALUE='External MySQL is reachable with valid credentials.'
                  fi
                elif [ "$TCP_REACHABLE" = "1" ]; then
                  STATUS_VALUE=UNKNOWN
                  SOURCE_VALUE=tcp
                  MESSAGE_VALUE='External host port is reachable but MySQL protocol verification failed (credentials may be incorrect or mysql client not available).'
                else
                  STATUS_VALUE=UNREACHABLE
                  SOURCE_VALUE=tcp
                  MESSAGE_VALUE='External dependency is not reachable from the target host.'
                fi

                printf 'AIOPS_DEP_STATUS=%s\\n' "$STATUS_VALUE"
                printf 'AIOPS_DEP_SOURCE=%s\\n' "$SOURCE_VALUE"
                printf 'AIOPS_DEP_VERSION=%s\\n' ''
                printf 'AIOPS_DEP_MESSAGE=%s\\n' "$MESSAGE_VALUE"
                """.formatted(
                        shellSafe(host),
                        port,
                        shellQuote(username),
                        shellQuote(password),
                        shellQuote(databaseName == null ? "" : databaseName)
                );
    }

    private String externalRedisCommand(WorkflowModels.DependencyRequirement requirement,
                                         int port,
                                         WorkflowModels.DependencyOverride override) {
        String host = requirement.host();
        String password = "";

        if (override != null && override.password() != null) {
            password = override.password();
        }

        return """
                TARGET_HOST=%s
                TARGET_PORT=%d
                REDIS_PASSWORD=%s

                TCP_REACHABLE=0
                PING_SUCCESS=0

                if timeout 3 bash -lc "</dev/tcp/${TARGET_HOST}/${TARGET_PORT}" >/dev/null 2>&1; then
                  TCP_REACHABLE=1
                fi

                if [ "$TCP_REACHABLE" = "1" ] && command -v redis-cli >/dev/null 2>&1; then
                  if [ -n "$REDIS_PASSWORD" ]; then
                    if redis-cli -h "$TARGET_HOST" -p "$TARGET_PORT" -a "$REDIS_PASSWORD" ping 2>/dev/null | grep -q '^PONG$'; then
                      PING_SUCCESS=1
                    fi
                  else
                    if redis-cli -h "$TARGET_HOST" -p "$TARGET_PORT" ping 2>/dev/null | grep -q '^PONG$'; then
                      PING_SUCCESS=1
                    fi
                  fi
                fi

                if [ "$PING_SUCCESS" = "1" ]; then
                  STATUS_VALUE=READY
                  SOURCE_VALUE=redis
                  MESSAGE_VALUE='External Redis is reachable with valid credentials (PING returned PONG).'
                elif [ "$TCP_REACHABLE" = "1" ]; then
                  STATUS_VALUE=UNKNOWN
                  SOURCE_VALUE=tcp
                  MESSAGE_VALUE='External host port is reachable but Redis protocol verification failed (password may be incorrect or redis-cli not available).'
                else
                  STATUS_VALUE=UNREACHABLE
                  SOURCE_VALUE=tcp
                  MESSAGE_VALUE='External dependency is not reachable from the target host.'
                fi

                printf 'AIOPS_DEP_STATUS=%s\\n' "$STATUS_VALUE"
                printf 'AIOPS_DEP_SOURCE=%s\\n' "$SOURCE_VALUE"
                printf 'AIOPS_DEP_VERSION=%s\\n' ''
                printf 'AIOPS_DEP_MESSAGE=%s\\n' "$MESSAGE_VALUE"
                """.formatted(
                        shellSafe(host),
                        port,
                        shellQuote(password)
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
