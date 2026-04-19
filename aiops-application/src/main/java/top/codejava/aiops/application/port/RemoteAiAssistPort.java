package top.codejava.aiops.application.port;

import top.codejava.aiops.application.dto.EnvironmentCheckResult;
import top.codejava.aiops.application.dto.LogFilterResult;

public interface RemoteAiAssistPort {

    EnvironmentCheckResult assistEnvironmentCheck(String projectPath, EnvironmentCheckResult localResult);

    LogFilterResult assistLogFilter(String rawLog, LogFilterResult localResult);
}
