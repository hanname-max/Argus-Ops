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
