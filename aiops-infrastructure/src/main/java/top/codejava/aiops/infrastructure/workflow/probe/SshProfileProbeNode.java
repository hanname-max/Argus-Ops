package top.codejava.aiops.infrastructure.workflow.probe;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import top.codejava.aiops.infrastructure.ops.SshCommandExecutorAdapter;
import top.codejava.aiops.type.exception.RemoteExecutionException;

@Component
@Order(10)
public class SshProfileProbeNode implements ProbeNode {

    private static final String PROFILE_PROBE_COMMAND = """
            HOSTNAME_VALUE=$(hostname 2>/dev/null || uname -n 2>/dev/null || echo unknown)
            if [ -r /etc/os-release ]; then
              . /etc/os-release
              OS_FAMILY_VALUE=${NAME:-$(uname -s 2>/dev/null || echo Linux)}
              OS_VERSION_VALUE=${VERSION_ID:-${VERSION:-unknown}}
            else
              OS_FAMILY_VALUE=$(uname -s 2>/dev/null || echo Linux)
              OS_VERSION_VALUE=$(uname -r 2>/dev/null || echo unknown)
            fi
            ARCH_VALUE=$(uname -m 2>/dev/null || echo unknown)
            TOTAL_MEM_VALUE=$(awk '/MemTotal:/ {print int($2/1024)}' /proc/meminfo 2>/dev/null || echo 0)
            FREE_MEM_VALUE=$(awk '/MemAvailable:/ {print int($2/1024)}' /proc/meminfo 2>/dev/null || awk '/MemFree:/ {print int($2/1024)}' /proc/meminfo 2>/dev/null || echo 0)
            if command -v java >/dev/null 2>&1; then
              JAVA_VERSION_VALUE=$(java -version 2>&1 | head -n 1)
            else
              JAVA_VERSION_VALUE=
            fi
            if command -v docker >/dev/null 2>&1; then
              DOCKER_INSTALLED_VALUE=true
              DOCKER_VERSION_VALUE=$(docker --version 2>/dev/null | head -n 1)
              if docker info >/dev/null 2>&1; then
                DOCKER_DIRECT_VALUE=true
              else
                DOCKER_DIRECT_VALUE=false
              fi
            else
              DOCKER_INSTALLED_VALUE=false
              DOCKER_VERSION_VALUE=
              DOCKER_DIRECT_VALUE=false
            fi
            if [ "$(id -u)" = "0" ] || sudo -n true >/dev/null 2>&1; then
              CAN_USE_SUDO_VALUE=true
            else
              CAN_USE_SUDO_VALUE=false
            fi
            SHELL_VALUE=${SHELL##*/}
            if [ -z "$SHELL_VALUE" ]; then
              SHELL_VALUE=$(ps -p $$ -o comm= 2>/dev/null | head -n 1 | tr -d ' ')
            fi
            printf 'AIOPS_HOSTNAME=%s\\n' "$HOSTNAME_VALUE"
            printf 'AIOPS_OS_FAMILY=%s\\n' "$OS_FAMILY_VALUE"
            printf 'AIOPS_OS_VERSION=%s\\n' "$OS_VERSION_VALUE"
            printf 'AIOPS_ARCH=%s\\n' "$ARCH_VALUE"
            printf 'AIOPS_TOTAL_MEM_MB=%s\\n' "$TOTAL_MEM_VALUE"
            printf 'AIOPS_FREE_MEM_MB=%s\\n' "$FREE_MEM_VALUE"
            printf 'AIOPS_JAVA_VERSION=%s\\n' "$JAVA_VERSION_VALUE"
            printf 'AIOPS_DOCKER_INSTALLED=%s\\n' "$DOCKER_INSTALLED_VALUE"
            printf 'AIOPS_DOCKER_VERSION=%s\\n' "$DOCKER_VERSION_VALUE"
            printf 'AIOPS_DOCKER_DIRECT=%s\\n' "$DOCKER_DIRECT_VALUE"
            printf 'AIOPS_CAN_USE_SUDO=%s\\n' "$CAN_USE_SUDO_VALUE"
            printf 'AIOPS_SHELL=%s\\n' "$SHELL_VALUE"
            """;

    private final SshCommandExecutorAdapter sshCommandExecutorAdapter;

    public SshProfileProbeNode(SshCommandExecutorAdapter sshCommandExecutorAdapter) {
        this.sshCommandExecutorAdapter = sshCommandExecutorAdapter;
    }

    @Override
    public void apply(ProbeContext context) {
        SshCommandExecutorAdapter.SshExecutionResult executionResult = sshCommandExecutorAdapter.execute(
                context.request().credential(),
                PROFILE_PROBE_COMMAND,
                ignored -> {
                }
        );
        if (!executionResult.success()) {
            throw new RemoteExecutionException(
                    "SSH target profiling failed with exit status "
                            + executionResult.exitStatus()
                            + ": "
                            + executionResult.stderr(),
                    null
            );
        }

        for (String line : executionResult.stdout().split("\\R")) {
            if (!line.startsWith("AIOPS_")) {
                continue;
            }
            int separatorIndex = line.indexOf('=');
            if (separatorIndex <= 0) {
                continue;
            }
            context.profileValues().put(line.substring(0, separatorIndex), line.substring(separatorIndex + 1).trim());
        }
    }
}
