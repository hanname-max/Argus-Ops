package top.codejava.aiops.infrastructure.deploy.script;

import top.codejava.aiops.infrastructure.projectscan.ProjectScanSnapshot;

public record ResolvedDeploymentPlan(
        ProjectScanSnapshot scanSnapshot,
        DeploymentPlan deploymentPlan,
        String previewScript,
        String executionScript
) {
}
