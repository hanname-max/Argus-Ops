package top.codejava.aiops.infrastructure.deploy;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import top.codejava.aiops.application.dto.RemoteDeployRequest;
import top.codejava.aiops.infrastructure.ops.SshCommandExecutorAdapter;
import top.codejava.aiops.type.exception.RemoteExecutionException;

@Component
@Order(30)
public class PrepareWorkspaceNode implements DeployNode {

    private final SshCommandExecutorAdapter sshCommandExecutorAdapter;

    public PrepareWorkspaceNode(SshCommandExecutorAdapter sshCommandExecutorAdapter) {
        this.sshCommandExecutorAdapter = sshCommandExecutorAdapter;
    }

    @Override
    public void apply(DeployContext context) {
        context.progressMessages().add("Preparing remote workspace.");
        String command = """
                set -euo pipefail
                mkdir -p %s
                rm -rf %s
                mkdir -p %s
                tar -xzf %s -C %s
                """.formatted(
                context.remoteRootPath(),
                context.remoteWorkspacePath(),
                context.remoteWorkspacePath(),
                context.remoteBundlePath(),
                context.remoteWorkspacePath()
        );

        SshCommandExecutorAdapter.SshExecutionResult result = sshCommandExecutorAdapter.execute(
                new RemoteDeployRequest(
                        context.request().host(),
                        context.request().port(),
                        context.request().username(),
                        context.request().password(),
                        command,
                        context.request().useSudo(),
                        context.request().sudoPassword(),
                        context.workflowId(),
                        context.request().projectPath()
                )
        );

        if (!result.success()) {
            throw new RemoteExecutionException("Failed to prepare remote workspace: " + result.stderr(), null);
        }
    }
}
