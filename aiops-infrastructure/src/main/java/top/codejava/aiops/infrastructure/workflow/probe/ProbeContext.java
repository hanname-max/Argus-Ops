package top.codejava.aiops.infrastructure.workflow.probe;

import top.codejava.aiops.application.dto.WorkflowModels;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ProbeContext {

    private final WorkflowModels.ProbeTargetRequest request;
    private final WorkflowModels.LocalProjectContext localContext;
    private final int requestedPort;
    private final int maxProbeSpan;
    private final int timeoutMillis;

    private final Map<String, String> profileValues = new LinkedHashMap<>();
    private final List<Integer> triedPorts = new ArrayList<>();
    private final List<WorkflowModels.WorkflowWarning> warnings = new ArrayList<>();
    private final List<WorkflowModels.RemoteServiceHint> existingDeployments = new ArrayList<>();

    private boolean requestedPortOccupied;
    private Integer recommendedPort;
    private WorkflowModels.BilingualText warningMessage;
    private WorkflowModels.TargetSystemProfile targetProfile;
    private WorkflowModels.PortProbeDecision portProbeDecision;

    public ProbeContext(WorkflowModels.ProbeTargetRequest request,
                        WorkflowModels.LocalProjectContext localContext) {
        this.request = request;
        this.localContext = localContext;
        this.requestedPort = request.defaultApplicationPort() == null ? 8080 : request.defaultApplicationPort();
        this.maxProbeSpan = request.maxAutoIncrementProbeSpan() == null ? 10 : Math.max(0, request.maxAutoIncrementProbeSpan());
        this.timeoutMillis = request.credential().connectTimeoutMillis() == null
                ? 1500
                : Math.max(500, request.credential().connectTimeoutMillis());
        this.recommendedPort = this.requestedPort;
    }

    public WorkflowModels.ProbeTargetRequest request() {
        return request;
    }

    public WorkflowModels.LocalProjectContext localContext() {
        return localContext;
    }

    public int requestedPort() {
        return requestedPort;
    }

    public int maxProbeSpan() {
        return maxProbeSpan;
    }

    public int timeoutMillis() {
        return timeoutMillis;
    }

    public Map<String, String> profileValues() {
        return profileValues;
    }

    public List<Integer> triedPorts() {
        return triedPorts;
    }

    public List<WorkflowModels.WorkflowWarning> warnings() {
        return warnings;
    }

    public List<WorkflowModels.RemoteServiceHint> existingDeployments() {
        return existingDeployments;
    }

    public boolean requestedPortOccupied() {
        return requestedPortOccupied;
    }

    public void requestedPortOccupied(boolean requestedPortOccupied) {
        this.requestedPortOccupied = requestedPortOccupied;
    }

    public Integer recommendedPort() {
        return recommendedPort;
    }

    public void recommendedPort(Integer recommendedPort) {
        this.recommendedPort = recommendedPort;
    }

    public WorkflowModels.BilingualText warningMessage() {
        return warningMessage;
    }

    public void warningMessage(WorkflowModels.BilingualText warningMessage) {
        this.warningMessage = warningMessage;
    }

    public WorkflowModels.TargetSystemProfile targetProfile() {
        return targetProfile;
    }

    public void targetProfile(WorkflowModels.TargetSystemProfile targetProfile) {
        this.targetProfile = targetProfile;
    }

    public WorkflowModels.PortProbeDecision portProbeDecision() {
        return portProbeDecision;
    }

    public void portProbeDecision(WorkflowModels.PortProbeDecision portProbeDecision) {
        this.portProbeDecision = portProbeDecision;
    }
}
