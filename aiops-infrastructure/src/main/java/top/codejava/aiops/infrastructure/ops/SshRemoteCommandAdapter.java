package top.codejava.aiops.infrastructure.ops;

import org.springframework.stereotype.Component;
import top.codejava.aiops.application.dto.RemoteDeployRequest;
import top.codejava.aiops.application.dto.RemoteDeployResult;
import top.codejava.aiops.application.port.RemoteCommandPort;
import top.codejava.aiops.infrastructure.deploy.DeployContext;
import top.codejava.aiops.infrastructure.deploy.DeployExecutionRouter;

@Component
public class SshRemoteCommandAdapter implements RemoteCommandPort {

    private final SshCommandExecutorAdapter sshCommandExecutorAdapter;
    private final DeployExecutionRouter deployExecutionRouter;

    public SshRemoteCommandAdapter(SshCommandExecutorAdapter sshCommandExecutorAdapter,
                                   DeployExecutionRouter deployExecutionRouter) {
        this.sshCommandExecutorAdapter = sshCommandExecutorAdapter;
        this.deployExecutionRouter = deployExecutionRouter;
    }

    @Override
    public RemoteDeployResult execute(RemoteDeployRequest request) {
        if (request.projectPath() != null && !request.projectPath().isBlank()) {
            DeployContext deployContext = deployExecutionRouter.execute(new DeployContext(request));
            DeployContext.SshExecutionSummary executionSummary = deployContext.executionSummary();
            String prefixedOutput = String.join(System.lineSeparator(), deployContext.progressMessages());
            String stdout = executionSummary == null ? prefixedOutput : join(prefixedOutput, executionSummary.stdout());
            String stderr = executionSummary == null ? "" : executionSummary.stderr();
            boolean success = executionSummary != null && executionSummary.success();
            return new RemoteDeployResult(success, stdout, stderr);
        }

        SshCommandExecutorAdapter.SshExecutionResult executionResult = sshCommandExecutorAdapter.execute(request);
        return new RemoteDeployResult(
                executionResult.success(),
                executionResult.stdout(),
                executionResult.stderr()
        );
    }

    private String join(String prefix, String body) {
        if (prefix == null || prefix.isBlank()) {
            return body == null ? "" : body;
        }
        if (body == null || body.isBlank()) {
            return prefix;
        }
        return prefix + System.lineSeparator() + body;
    }
}
