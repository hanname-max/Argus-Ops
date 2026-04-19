package top.codejava.aiops.application.usecase;

import top.codejava.aiops.application.dto.BuildScriptRequest;
import top.codejava.aiops.application.dto.BuildScriptResult;
import top.codejava.aiops.application.dto.EnvironmentCheckResult;
import top.codejava.aiops.application.dto.LogFilterResult;
import top.codejava.aiops.application.dto.RemoteDeployRequest;
import top.codejava.aiops.application.dto.RemoteDeployResult;
import top.codejava.aiops.application.port.LocalAiPort;
import top.codejava.aiops.application.port.RemoteAiAssistPort;
import top.codejava.aiops.application.port.RemoteCommandPort;
import top.codejava.aiops.type.exception.ValidationException;

public class OpsUseCase {

    private final LocalAiPort localAiPort;
    private final RemoteAiAssistPort remoteAiAssistPort;
    private final RemoteCommandPort remoteCommandPort;

    public OpsUseCase(LocalAiPort localAiPort,
                      RemoteAiAssistPort remoteAiAssistPort,
                      RemoteCommandPort remoteCommandPort) {
        this.localAiPort = localAiPort;
        this.remoteAiAssistPort = remoteAiAssistPort;
        this.remoteCommandPort = remoteCommandPort;
    }

    public BuildScriptResult generateBuildScript(BuildScriptRequest request) {
        if (request == null || isBlank(request.projectPath())) {
            throw new ValidationException("projectPath is required");
        }
        return localAiPort.generateBuildScript(request);
    }

    public EnvironmentCheckResult checkEnvironment(String projectPath) {
        if (isBlank(projectPath)) {
            throw new ValidationException("projectPath is required");
        }
        EnvironmentCheckResult localResult = localAiPort.checkEnvironment(projectPath);
        return remoteAiAssistPort.assistEnvironmentCheck(projectPath, localResult);
    }

    public LogFilterResult filterErrorLog(String rawLog) {
        if (isBlank(rawLog)) {
            throw new ValidationException("rawLog is required");
        }
        LogFilterResult localResult = localAiPort.filterAndAnalyzeLog(rawLog);
        return remoteAiAssistPort.assistLogFilter(rawLog, localResult);
    }

    public RemoteDeployResult deploy(RemoteDeployRequest request) {
        if (request == null || isBlank(request.host()) || isBlank(request.username()) || isBlank(request.command())) {
            throw new ValidationException("host/username/command are required");
        }
        return remoteCommandPort.execute(request);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
