package top.codejava.aiops.infrastructure.workflow.probe;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import top.codejava.aiops.infrastructure.ops.SshCommandExecutorAdapter;

@Component
@Order(35)
public class DockerProcessProbeNode implements ProbeNode {

    private static final String DOCKER_PS_COMMAND = """
            if ! command -v docker >/dev/null 2>&1; then
              exit 0
            fi
            docker ps --format '{{.ID}}|{{.Image}}|{{.Names}}|{{.Ports}}'
            """;

    private final SshCommandExecutorAdapter sshCommandExecutorAdapter;

    public DockerProcessProbeNode(SshCommandExecutorAdapter sshCommandExecutorAdapter) {
        this.sshCommandExecutorAdapter = sshCommandExecutorAdapter;
    }

    @Override
    public void apply(ProbeContext context) {
        SshCommandExecutorAdapter.SshExecutionResult result = sshCommandExecutorAdapter.execute(
                context.request().credential(),
                DOCKER_PS_COMMAND,
                ignored -> {
                }
        );
        if (!result.success() || result.stdout().isBlank()) {
            return;
        }

        for (String line : result.stdout().split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isBlank()) {
                continue;
            }
            String[] parts = trimmed.split("\\|", 4);
            if (parts.length < 4) {
                continue;
            }
            String ports = parts[3].trim();
            boolean conflicts = ports.contains(":" + context.requestedPort() + "->")
                    || (context.recommendedPort() != null && ports.contains(":" + context.recommendedPort() + "->"));
            String suspectedType = inferType(parts[1], parts[2], ports);
            context.existingDeployments().add(new top.codejava.aiops.application.dto.WorkflowModels.RemoteServiceHint(
                    "docker",
                    parts[0].trim(),
                    parts[1].trim(),
                    parts[2].trim(),
                    ports,
                    suspectedType,
                    conflicts,
                    conflicts
                            ? "The requested deployment port appears to be exposed by an existing docker container."
                            : "Existing docker container detected on the target host."
            ));
        }
    }

    private String inferType(String image, String name, String ports) {
        String combined = (image + " " + name + " " + ports).toLowerCase();
        if (combined.contains("nginx")) {
            return "NGINX";
        }
        if (combined.contains("java") || combined.contains("jar") || combined.contains("spring")) {
            return "JAVA";
        }
        if (combined.contains("node") || combined.contains("next")) {
            return "NODE";
        }
        return "UNKNOWN";
    }
}
