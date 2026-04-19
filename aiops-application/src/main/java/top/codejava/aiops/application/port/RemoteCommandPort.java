package top.codejava.aiops.application.port;

import top.codejava.aiops.application.dto.RemoteDeployRequest;
import top.codejava.aiops.application.dto.RemoteDeployResult;

public interface RemoteCommandPort {

    RemoteDeployResult execute(RemoteDeployRequest request);
}
