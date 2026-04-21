package top.codejava.aiops.web.dto;

import java.util.Map;

public record DeployApiRequest(
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
