package top.codejava.aiops.application.dto;

import java.util.Map;

public record RemoteDeployRequest(
        String host,
        int port,
        String username,
        String password,
        String command,
        boolean useSudo,
        String sudoPassword,
        Integer applicationPort,
        String workflowId,
        String projectPath,
        Map<String, String> runtimeEnv
) {
}
