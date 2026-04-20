package top.codejava.aiops.web.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.codejava.aiops.application.dto.BuildScriptRequest;
import top.codejava.aiops.application.dto.BuildScriptResult;
import top.codejava.aiops.application.dto.EnvironmentCheckResult;
import top.codejava.aiops.application.dto.LogFilterResult;
import top.codejava.aiops.application.dto.RemoteDeployRequest;
import top.codejava.aiops.application.dto.RemoteDeployResult;
import top.codejava.aiops.application.usecase.OpsUseCase;
import top.codejava.aiops.web.dto.BuildScriptApiRequest;
import top.codejava.aiops.web.dto.DeployApiRequest;
import top.codejava.aiops.web.dto.EnvironmentCheckApiRequest;
import top.codejava.aiops.web.dto.LogFilterApiRequest;

@RestController
@RequestMapping("/api/v1/ops")
public class OpsController {

    private final OpsUseCase opsUseCase;

    public OpsController(OpsUseCase opsUseCase) {
        this.opsUseCase = opsUseCase;
    }

    @PostMapping("/build-script")
    public BuildScriptResult buildScript(@RequestBody BuildScriptApiRequest request) {
        return opsUseCase.generateBuildScript(new BuildScriptRequest(request.projectPath(), request.targetType()));
    }

    @PostMapping("/environment/check")
    public EnvironmentCheckResult checkEnvironment(@RequestBody EnvironmentCheckApiRequest request) {
        return opsUseCase.checkEnvironment(request.projectPath());
    }

    @PostMapping("/logs/filter")
    public LogFilterResult filterLogs(@RequestBody LogFilterApiRequest request) {
        return opsUseCase.filterErrorLog(request.rawLog());
    }

    @PostMapping("/deploy/exec")
    public RemoteDeployResult deploy(@RequestBody DeployApiRequest request) {
        return opsUseCase.deploy(new RemoteDeployRequest(
                request.host(),
                request.port(),
                request.username(),
                request.password(),
                request.command(),
                request.useSudo(),
                request.sudoPassword(),
                request.workflowId(),
                request.projectPath()
        ));
    }
}
