package top.codejava.aiops.infrastructure.workflow;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;
import top.codejava.aiops.application.dto.WorkflowModels;
import top.codejava.aiops.application.port.WorkflowTargetProbePort;

@Component
public class DeterministicWorkflowTargetProbeAdapter implements WorkflowTargetProbePort {

    @Override
    public WorkflowModels.TargetProbePayload probe(WorkflowModels.ProbeTargetRequest request,
                                                   WorkflowModels.LocalProjectContext localContext) {
        int requestedPort = request.defaultApplicationPort() == null ? 8080 : request.defaultApplicationPort();
        int maxSpan = request.maxAutoIncrementProbeSpan() == null ? 10 : Math.max(0, request.maxAutoIncrementProbeSpan());
        int timeoutMillis = request.credential().connectTimeoutMillis() == null ? 1500 : Math.max(500, request.credential().connectTimeoutMillis());

        List<Integer> triedPorts = new ArrayList<>();
        Integer recommendedPort = requestedPort;
        boolean requestedPortOccupied = false;

        for (int offset = 0; offset <= maxSpan; offset++) {
            int candidate = requestedPort + offset;
            triedPorts.add(candidate);
            boolean occupied = isPortOccupied(request.credential().host(), candidate, timeoutMillis);
            if (offset == 0) {
                requestedPortOccupied = occupied;
            }
            if (!occupied) {
                recommendedPort = candidate;
                break;
            }
        }

        List<WorkflowModels.WorkflowWarning> warnings = new ArrayList<>();
        WorkflowModels.BilingualText warningMessage = null;
        if (requestedPortOccupied && recommendedPort != null && recommendedPort != requestedPort) {
            warningMessage = new WorkflowModels.BilingualText(
                    "默认端口 " + requestedPort + " 已被占用，已自动上探并推荐可用端口 " + recommendedPort + "。",
                    "Requested port " + requestedPort + " is occupied. Auto-increment probing recommended available port " + recommendedPort + "."
            );
            warnings.add(new WorkflowModels.WorkflowWarning(
                    "PORT_OCCUPIED_AUTO_INCREMENTED",
                    WorkflowModels.Severity.HIGH,
                    warningMessage.zhCn(),
                    "Deploy with the recommended port instead of the default one."
            ));
        } else if (requestedPortOccupied) {
            warningMessage = new WorkflowModels.BilingualText(
                    "默认端口被占用，且在本次探测窗口中未发现明确可用端口。",
                    "Requested port is occupied and no clearly available candidate was found in the probe window."
            );
            warnings.add(new WorkflowModels.WorkflowWarning(
                    "PORT_PROBE_EXHAUSTED",
                    WorkflowModels.Severity.CRITICAL,
                    warningMessage.zhCn(),
                    "Increase probe span or inspect the target host manually."
            ));
        } else {
            warnings.add(new WorkflowModels.WorkflowWarning(
                    "TARGET_PROFILE_INFERRED",
                    WorkflowModels.Severity.INFO,
                    "Target profile is inferred from lightweight TCP probing.",
                    "Integrate a dedicated SSH probe adapter if you need full OS and memory fidelity."
            ));
        }

        WorkflowModels.TargetSystemProfile targetProfile = new WorkflowModels.TargetSystemProfile(
                request.credential().host(),
                request.credential().host(),
                "Linux",
                "unknown",
                "x86_64",
                8192L,
                4096L,
                localContext != null && "Java".equalsIgnoreCase(localContext.primaryLanguage()) ? localContext.detectedJdkVersion() : null,
                "bash"
        );

        WorkflowModels.PortProbeDecision decision = new WorkflowModels.PortProbeDecision(
                requestedPort,
                requestedPortOccupied,
                List.copyOf(triedPorts),
                recommendedPort,
                warningMessage,
                "AUTO_INCREMENT_FIRST_AVAILABLE"
        );

        return new WorkflowModels.TargetProbePayload(targetProfile, decision, List.copyOf(warnings));
    }

    private boolean isPortOccupied(String host, int port, int timeoutMillis) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMillis);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }
}
