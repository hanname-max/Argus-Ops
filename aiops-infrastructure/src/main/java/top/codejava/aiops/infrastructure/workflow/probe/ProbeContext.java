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
    private final List<WorkflowModels.DependencyRequirement> dependencyRequirements;
    private final Map<WorkflowModels.DependencyKind, WorkflowModels.DependencyDecision> dependencyDecisionMap = new LinkedHashMap<>();
    private final Map<WorkflowModels.DependencyKind, WorkflowModels.DependencyOverride> dependencyOverrideMap = new LinkedHashMap<>();

    private final Map<String, String> profileValues = new LinkedHashMap<>();
    private final List<Integer> triedPorts = new ArrayList<>();
    private final List<WorkflowModels.WorkflowWarning> warnings = new ArrayList<>();
    private final List<WorkflowModels.RemoteServiceHint> existingDeployments = new ArrayList<>();
    private final List<WorkflowModels.DependencyProbeResult> dependencyProbeResults = new ArrayList<>();

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

        if (request.dependencyOverrides() != null) {
            request.dependencyOverrides().stream()
                    .filter(override -> override != null && override.kind() != null)
                    .forEach(override -> dependencyOverrideMap.put(override.kind(), override));
        }

        this.dependencyRequirements = buildDependencyRequirements(localContext, dependencyOverrideMap);

        if (request.dependencyDecisions() != null) {
            request.dependencyDecisions().stream()
                    .filter(decision -> decision != null && decision.kind() != null && decision.mode() != null)
                    .forEach(decision -> dependencyDecisionMap.put(decision.kind(), decision));
        }
    }

    private List<WorkflowModels.DependencyRequirement> buildDependencyRequirements(
            WorkflowModels.LocalProjectContext localContext,
            Map<WorkflowModels.DependencyKind, WorkflowModels.DependencyOverride> overrides) {
        List<WorkflowModels.DependencyRequirement> original = localContext == null || localContext.dependencyRequirements() == null
                ? List.of()
                : localContext.dependencyRequirements();

        if (overrides.isEmpty()) {
            return List.copyOf(original);
        }

        List<WorkflowModels.DependencyRequirement> result = new ArrayList<>();
        for (WorkflowModels.DependencyRequirement req : original) {
            if (req == null || req.kind() == null) {
                continue;
            }
            WorkflowModels.DependencyOverride override = overrides.get(req.kind());
            if (override != null) {
                result.add(new WorkflowModels.DependencyRequirement(
                        req.kind(),
                        req.displayName(),
                        req.required(),
                        override.host() != null && !override.host().isBlank() ? override.host() : req.host(),
                        override.port() != null ? override.port() : req.port(),
                        override.databaseName() != null && !override.databaseName().isBlank() ? override.databaseName() : req.databaseName(),
                        req.sourceKey(),
                        req.sourceModule(),
                        req.sourceFile(),
                        req.sourceProfile(),
                        req.operatorHint()
                ));
            } else {
                result.add(req);
            }
        }
        return List.copyOf(result);
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

    public List<WorkflowModels.DependencyRequirement> dependencyRequirements() {
        return dependencyRequirements;
    }

    public Map<WorkflowModels.DependencyKind, WorkflowModels.DependencyDecision> dependencyDecisionMap() {
        return dependencyDecisionMap;
    }

    public WorkflowModels.DependencyOverride dependencyOverride(WorkflowModels.DependencyKind kind) {
        return dependencyOverrideMap.get(kind);
    }

    public List<WorkflowModels.DependencyProbeResult> dependencyProbeResults() {
        return dependencyProbeResults;
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
