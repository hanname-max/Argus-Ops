package top.codejava.aiops.infrastructure.workflow.probe;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import top.codejava.aiops.application.dto.WorkflowModels;

import java.util.List;

@Component
@Order(40)
public class ProbeDecisionNode implements ProbeNode {

    @Override
    public void apply(ProbeContext context) {
        boolean hasPortConflictContainer = context.existingDeployments().stream()
                .anyMatch(top.codejava.aiops.application.dto.WorkflowModels.RemoteServiceHint::conflictsWithRequestedPort);

        if (context.requestedPortOccupied()
                && context.recommendedPort() != null
                && !context.recommendedPort().equals(context.requestedPort())) {
            WorkflowModels.BilingualText warningMessage = new WorkflowModels.BilingualText(
                    "Requested port " + context.requestedPort() + " is occupied. Use recommended port " + context.recommendedPort() + ".",
                    "Requested port " + context.requestedPort() + " is occupied. Use recommended port " + context.recommendedPort() + "."
            );
            context.warningMessage(warningMessage);
            context.warnings().add(new WorkflowModels.WorkflowWarning(
                    "PORT_OCCUPIED_AUTO_INCREMENTED",
                    WorkflowModels.Severity.HIGH,
                    warningMessage.zhCn(),
                    "Deploy with the recommended port instead of the default one."
            ));
        } else if (context.requestedPortOccupied()) {
            WorkflowModels.BilingualText warningMessage = new WorkflowModels.BilingualText(
                    "Requested port is occupied and no clearly available candidate was found in the probe window.",
                    "Requested port is occupied and no clearly available candidate was found in the probe window."
            );
            context.warningMessage(warningMessage);
            context.warnings().add(new WorkflowModels.WorkflowWarning(
                    "PORT_PROBE_EXHAUSTED",
                    WorkflowModels.Severity.CRITICAL,
                    warningMessage.zhCn(),
                    "Increase probe span or inspect the target host manually."
            ));
        } else {
            context.warnings().add(new WorkflowModels.WorkflowWarning(
                    "SSH_TARGET_PROFILE_COLLECTED",
                    WorkflowModels.Severity.INFO,
                    "Target profile collected through live SSH probing.",
                    "Continue with the returned host profile and recommended port."
            ));
        }

        if (hasPortConflictContainer) {
            context.warnings().add(new WorkflowModels.WorkflowWarning(
                    "DOCKER_CONTAINER_USING_TARGET_PORT",
                    WorkflowModels.Severity.HIGH,
                    "An existing Docker container is already using the requested deployment port.",
                    "Review the existing deployment before reusing this port."
            ));
        } else if (!context.existingDeployments().isEmpty()) {
            context.warnings().add(new WorkflowModels.WorkflowWarning(
                    "DOCKER_CONTAINERS_DETECTED",
                    WorkflowModels.Severity.INFO,
                    "Existing Docker containers were detected on the target host.",
                    "Check whether the current deployment will replace or coexist with those services."
            ));
        }

        boolean unresolvedDependencies = false;
        for (WorkflowModels.DependencyProbeResult dependencyProbeResult : context.dependencyProbeResults()) {
            if (!dependencyProbeResult.requiresDecision()) {
                continue;
            }
            WorkflowModels.DependencyDecision decision = context.dependencyDecisionMap().get(dependencyProbeResult.kind());
            if (decision == null || decision.mode() == null) {
                unresolvedDependencies = true;
                context.warnings().add(new WorkflowModels.WorkflowWarning(
                        "DEPENDENCY_CONFIRM_REQUIRED",
                        WorkflowModels.Severity.HIGH,
                        dependencyProbeResult.displayName() + " is not ready on the target host. Confirm whether to deploy it automatically.",
                        "Choose whether to auto-deploy, prepare manually, or continue without provisioning this dependency."
                ));
                continue;
            }
            context.warnings().add(new WorkflowModels.WorkflowWarning(
                    "DEPENDENCY_DECISION_CAPTURED",
                    WorkflowModels.Severity.INFO,
                    dependencyProbeResult.displayName() + " decision recorded as " + decision.mode() + ".",
                    "Continue with script preview after confirming all dependency decisions."
            ));
        }

        if (unresolvedDependencies) {
            context.warnings().add(new WorkflowModels.WorkflowWarning(
                    "DEPENDENCY_PROBE_INCOMPLETE",
                    WorkflowModels.Severity.HIGH,
                    "One or more required dependencies are missing or unreachable and still need operator confirmation.",
                    "Review the dependency section in target probe and choose how each missing dependency should be handled."
            ));
        }

        context.portProbeDecision(new WorkflowModels.PortProbeDecision(
                context.requestedPort(),
                context.requestedPortOccupied(),
                List.copyOf(context.triedPorts()),
                context.recommendedPort(),
                context.warningMessage(),
                "AUTO_INCREMENT_FIRST_AVAILABLE"
        ));
    }
}
