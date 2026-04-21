package top.codejava.aiops.infrastructure.deploy;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import top.codejava.aiops.application.dto.RemoteDeployRequest;
import top.codejava.aiops.infrastructure.ops.SshCommandExecutorAdapter;

@Component
@Order(60)
public class ExecuteDeployNode implements DeployNode {

    private final SshCommandExecutorAdapter sshCommandExecutorAdapter;

    public ExecuteDeployNode(SshCommandExecutorAdapter sshCommandExecutorAdapter) {
        this.sshCommandExecutorAdapter = sshCommandExecutorAdapter;
    }

    @Override
    public void apply(DeployContext context) {
        context.progressMessages().add("Executing rendered deployment command on remote host.");
        SshCommandExecutorAdapter.SshExecutionResult result = sshCommandExecutorAdapter.execute(
                new RemoteDeployRequest(
                        context.request().host(),
                        context.request().port(),
                        context.request().username(),
                        context.request().password(),
                        context.renderedDeployCommand(),
                        context.request().useSudo(),
                        context.request().sudoPassword(),
                        context.request().applicationPort(),
                        context.workflowId(),
                        context.request().projectPath(),
                        context.request().runtimeEnv()
                )
        );
        context.executionSummary(new DeployContext.SshExecutionSummary(
                result.success(),
                result.stdout(),
                result.stderr()
        ));
    }
}
