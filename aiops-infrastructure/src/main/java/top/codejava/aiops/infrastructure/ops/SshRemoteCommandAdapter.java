package top.codejava.aiops.infrastructure.ops;

import org.springframework.stereotype.Component;
import top.codejava.aiops.application.dto.RemoteDeployRequest;
import top.codejava.aiops.application.dto.RemoteDeployResult;
import top.codejava.aiops.application.port.RemoteCommandPort;

@Component
public class SshRemoteCommandAdapter implements RemoteCommandPort {

    private final SshCommandExecutorAdapter sshCommandExecutorAdapter;

    public SshRemoteCommandAdapter(SshCommandExecutorAdapter sshCommandExecutorAdapter) {
        this.sshCommandExecutorAdapter = sshCommandExecutorAdapter;
    }

    @Override
    public RemoteDeployResult execute(RemoteDeployRequest request) {
        SshCommandExecutorAdapter.SshExecutionResult executionResult = sshCommandExecutorAdapter.execute(request);
        return new RemoteDeployResult(
                executionResult.success(),
                executionResult.stdout(),
                executionResult.stderr()
        );
    }
}
