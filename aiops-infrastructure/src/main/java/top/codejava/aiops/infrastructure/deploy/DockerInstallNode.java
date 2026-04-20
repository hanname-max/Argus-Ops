package top.codejava.aiops.infrastructure.deploy;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import top.codejava.aiops.application.dto.RemoteDeployRequest;
import top.codejava.aiops.infrastructure.ops.SshCommandExecutorAdapter;
import top.codejava.aiops.type.exception.RemoteExecutionException;

@Component
@Order(40)
public class DockerInstallNode implements DeployNode {

    private static final String CHECK_DOCKER_COMMAND = "command -v docker >/dev/null 2>&1";
    private static final String INSTALL_DOCKER_COMMAND = """
            set -euo pipefail
            if command -v docker >/dev/null 2>&1; then
              exit 0
            fi
            if command -v dnf >/dev/null 2>&1; then
              (dnf install -y docker || dnf install -y moby-engine)
            elif command -v yum >/dev/null 2>&1; then
              (yum install -y docker || yum install -y docker-ce || yum install -y moby-engine)
            elif command -v apt-get >/dev/null 2>&1; then
              apt-get update -y
              apt-get install -y docker.io
            else
              echo "Unsupported package manager for automatic Docker installation." >&2
              exit 1
            fi
            systemctl enable --now docker
            docker --version
            """;

    private final SshCommandExecutorAdapter sshCommandExecutorAdapter;

    public DockerInstallNode(SshCommandExecutorAdapter sshCommandExecutorAdapter) {
        this.sshCommandExecutorAdapter = sshCommandExecutorAdapter;
    }

    @Override
    public void apply(DeployContext context) {
        SshCommandExecutorAdapter.SshExecutionResult checkResult = sshCommandExecutorAdapter.execute(rawRequest(context, CHECK_DOCKER_COMMAND));
        if (checkResult.success()) {
            context.dockerInstalled(true);
            context.progressMessages().add("Docker already installed on target host.");
            return;
        }

        context.progressMessages().add("Docker not found on target host. Installing automatically.");
        SshCommandExecutorAdapter.SshExecutionResult installResult = sshCommandExecutorAdapter.execute(rawRequest(context, INSTALL_DOCKER_COMMAND));
        if (!installResult.success()) {
            throw new RemoteExecutionException("Failed to install Docker automatically: " + installResult.stderr(), null);
        }

        context.dockerInstalled(true);
        context.progressMessages().add("Docker installation completed.");
    }

    private RemoteDeployRequest rawRequest(DeployContext context, String command) {
        return new RemoteDeployRequest(
                context.request().host(),
                context.request().port(),
                context.request().username(),
                context.request().password(),
                command,
                context.request().useSudo(),
                context.request().sudoPassword(),
                context.request().applicationPort(),
                context.workflowId(),
                context.request().projectPath()
        );
    }
}
