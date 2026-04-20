package top.codejava.aiops.infrastructure.deploy;

import top.codejava.aiops.application.dto.RemoteDeployRequest;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DeployContext {

    private final RemoteDeployRequest request;
    private final String workflowId;
    private final String remoteRootPath;
    private final String remoteBundlePath;
    private final String remoteWorkspacePath;
    private final List<String> progressMessages = new ArrayList<>();

    private Path localBundlePath;
    private String renderedDeployCommand;
    private boolean dockerInstalled;
    private SshExecutionSummary executionSummary;

    public DeployContext(RemoteDeployRequest request) {
        this.request = request;
        this.workflowId = (request.workflowId() == null || request.workflowId().isBlank())
                ? UUID.randomUUID().toString()
                : request.workflowId().trim();
        this.remoteRootPath = "/opt/argus/workflows/" + workflowId;
        this.remoteBundlePath = remoteRootPath + "/bundle.tar.gz";
        this.remoteWorkspacePath = remoteRootPath + "/workspace";
    }

    public RemoteDeployRequest request() {
        return request;
    }

    public String workflowId() {
        return workflowId;
    }

    public String remoteRootPath() {
        return remoteRootPath;
    }

    public String remoteBundlePath() {
        return remoteBundlePath;
    }

    public String remoteWorkspacePath() {
        return remoteWorkspacePath;
    }

    public List<String> progressMessages() {
        return progressMessages;
    }

    public Path localBundlePath() {
        return localBundlePath;
    }

    public void localBundlePath(Path localBundlePath) {
        this.localBundlePath = localBundlePath;
    }

    public String renderedDeployCommand() {
        return renderedDeployCommand;
    }

    public void renderedDeployCommand(String renderedDeployCommand) {
        this.renderedDeployCommand = renderedDeployCommand;
    }

    public boolean dockerInstalled() {
        return dockerInstalled;
    }

    public void dockerInstalled(boolean dockerInstalled) {
        this.dockerInstalled = dockerInstalled;
    }

    public SshExecutionSummary executionSummary() {
        return executionSummary;
    }

    public void executionSummary(SshExecutionSummary executionSummary) {
        this.executionSummary = executionSummary;
    }

    public record SshExecutionSummary(boolean success, String stdout, String stderr) {
    }
}
