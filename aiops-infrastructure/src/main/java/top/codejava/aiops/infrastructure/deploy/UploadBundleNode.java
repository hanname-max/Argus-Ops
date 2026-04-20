package top.codejava.aiops.infrastructure.deploy;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import top.codejava.aiops.application.dto.WorkflowModels;
import top.codejava.aiops.infrastructure.ops.SshFileTransferAdapter;

@Component
@Order(20)
public class UploadBundleNode implements DeployNode {

    private final SshFileTransferAdapter sshFileTransferAdapter;

    public UploadBundleNode(SshFileTransferAdapter sshFileTransferAdapter) {
        this.sshFileTransferAdapter = sshFileTransferAdapter;
    }

    @Override
    public void apply(DeployContext context) {
        context.progressMessages().add("Uploading project bundle over SFTP.");
        sshFileTransferAdapter.upload(
                new WorkflowModels.TargetCredential(
                        context.request().host(),
                        context.request().port(),
                        context.request().username(),
                        WorkflowModels.CredentialType.PASSWORD,
                        context.request().password(),
                        null,
                        8_000
                ),
                context.localBundlePath(),
                context.remoteBundlePath(),
                event -> context.progressMessages().add(event.stage() + ": " + event.detail())
        );
    }
}
