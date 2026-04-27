package top.codejava.aiops.web.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.codejava.aiops.application.dto.RemoteDeployRequest;
import top.codejava.aiops.application.dto.RemoteDeployResult;
import top.codejava.aiops.application.port.RemoteCommandPort;
import top.codejava.aiops.type.exception.ValidationException;
import top.codejava.aiops.web.dto.DeployApiRequest;

@RestController
@RequestMapping("/api/v1/ops")
public class DeployController {

    private final RemoteCommandPort remoteCommandPort;

    public DeployController(RemoteCommandPort remoteCommandPort) {
        this.remoteCommandPort = remoteCommandPort;
    }

    @PostMapping("/deploy/exec")
    public RemoteDeployResult deploy(@RequestBody DeployApiRequest request) {
        if (request == null || isBlank(request.host()) || isBlank(request.username()) || isBlank(request.command())) {
            throw new ValidationException("host/username/command are required");
        }
        return remoteCommandPort.execute(new RemoteDeployRequest(
                request.host(),
                request.port(),
                request.username(),
                request.password(),
                request.command(),
                request.useSudo(),
                request.sudoPassword(),
                request.applicationPort(),
                request.workflowId(),
                request.projectPath()
        ));
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
