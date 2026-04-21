package top.codejava.aiops.infrastructure.deploy.script;

public record DeploymentPlan(
        String strategyKey,
        String summary,
        String scriptBody
) {
}
