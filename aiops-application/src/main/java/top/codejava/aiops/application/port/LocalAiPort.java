package top.codejava.aiops.application.port;

import top.codejava.aiops.application.dto.BuildScriptRequest;
import top.codejava.aiops.application.dto.BuildScriptResult;
import top.codejava.aiops.application.dto.EnvironmentCheckResult;
import top.codejava.aiops.application.dto.LogFilterResult;

public interface LocalAiPort {

    BuildScriptResult generateBuildScript(BuildScriptRequest request);

    EnvironmentCheckResult checkEnvironment(String projectPath);

    LogFilterResult filterAndAnalyzeLog(String rawLog);
}
