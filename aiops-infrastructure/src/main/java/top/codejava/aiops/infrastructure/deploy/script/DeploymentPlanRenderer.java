package top.codejava.aiops.infrastructure.deploy.script;

import org.springframework.stereotype.Component;

@Component
public class DeploymentPlanRenderer {

    public String renderPreview(DeploymentPlan plan) {
        return renderShell(plan.scriptBody(), null);
    }

    public String renderExecution(DeploymentPlan plan, String remoteWorkspacePath) {
        return renderShell(plan.scriptBody(), remoteWorkspacePath);
    }

    private String renderShell(String scriptBody, String workingDirectory) {
        String cdBlock = workingDirectory == null || workingDirectory.isBlank()
                ? ""
                : "cd " + shellQuote(workingDirectory) + "\n";
        return """
                #!/usr/bin/env bash
                set -euo pipefail
                %s%s
                """.formatted(cdBlock, scriptBody.trim()).trim();
    }

    private String shellQuote(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }
}
