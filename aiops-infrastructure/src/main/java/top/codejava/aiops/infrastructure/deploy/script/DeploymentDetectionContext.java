package top.codejava.aiops.infrastructure.deploy.script;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import top.codejava.aiops.application.dto.WorkflowModels;

final class DeploymentDetectionContext {

    private final Path projectRoot;
    private final int hostPort;
    private final ProjectMarkerSnapshot markers;
    private final WorkflowModels.LocalProjectContext localContext;
    private final List<WorkflowModels.DependencyProbeResult> dependencyProbeResults;
    private final List<WorkflowModels.DependencyDecision> dependencyDecisions;
    private final List<WorkflowModels.DependencyOverride> dependencyOverrides;
    private final List<WorkflowModels.DependencyRequirement> effectiveDependencyRequirements;

    DeploymentDetectionContext(Path projectRoot, int hostPort) {
        this(projectRoot, hostPort, null, List.of(), List.of(), List.of());
    }

    DeploymentDetectionContext(Path projectRoot,
                               int hostPort,
                               WorkflowModels.LocalProjectContext localContext) {
        this(projectRoot, hostPort, localContext, List.of(), List.of(), List.of());
    }

    DeploymentDetectionContext(Path projectRoot,
                               int hostPort,
                               WorkflowModels.LocalProjectContext localContext,
                               List<WorkflowModels.DependencyProbeResult> dependencyProbeResults,
                               List<WorkflowModels.DependencyDecision> dependencyDecisions,
                               List<WorkflowModels.DependencyOverride> dependencyOverrides) {
        this.projectRoot = projectRoot;
        this.hostPort = hostPort;
        this.markers = ProjectMarkerSnapshot.from(projectRoot);
        this.localContext = localContext;
        this.dependencyProbeResults = dependencyProbeResults == null ? List.of() : List.copyOf(dependencyProbeResults);
        this.dependencyDecisions = dependencyDecisions == null ? List.of() : List.copyOf(dependencyDecisions);
        this.dependencyOverrides = dependencyOverrides == null ? List.of() : List.copyOf(dependencyOverrides);
        this.effectiveDependencyRequirements = computeEffectiveRequirements(
                localContext != null ? localContext.dependencyRequirements() : null,
                this.dependencyOverrides
        );
    }

    private static List<WorkflowModels.DependencyRequirement> computeEffectiveRequirements(
            List<WorkflowModels.DependencyRequirement> originalRequirements,
            List<WorkflowModels.DependencyOverride> overrides) {
        if (originalRequirements == null || originalRequirements.isEmpty()) {
            return List.of();
        }
        if (overrides == null || overrides.isEmpty()) {
            return List.copyOf(originalRequirements);
        }

        Map<WorkflowModels.DependencyKind, WorkflowModels.DependencyOverride> overrideMap = new LinkedHashMap<>();
        for (WorkflowModels.DependencyOverride override : overrides) {
            if (override != null && override.kind() != null) {
                overrideMap.put(override.kind(), override);
            }
        }

        if (overrideMap.isEmpty()) {
            return List.copyOf(originalRequirements);
        }

        List<WorkflowModels.DependencyRequirement> result = new ArrayList<>();
        for (WorkflowModels.DependencyRequirement req : originalRequirements) {
            if (req == null || req.kind() == null) {
                continue;
            }
            WorkflowModels.DependencyOverride override = overrideMap.get(req.kind());
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

    Path projectRoot() {
        return projectRoot;
    }

    int hostPort() {
        return hostPort;
    }

    ProjectMarkerSnapshot markers() {
        return markers;
    }

    WorkflowModels.LocalProjectContext localContext() {
        return localContext;
    }

    List<WorkflowModels.DependencyProbeResult> dependencyProbeResults() {
        return dependencyProbeResults;
    }

    List<WorkflowModels.DependencyDecision> dependencyDecisions() {
        return dependencyDecisions;
    }

    List<WorkflowModels.DependencyOverride> dependencyOverrides() {
        return dependencyOverrides;
    }

    List<WorkflowModels.DependencyRequirement> effectiveDependencyRequirements() {
        return effectiveDependencyRequirements;
    }

    WorkflowModels.DependencyOverride dependencyOverride(WorkflowModels.DependencyKind kind) {
        if (dependencyOverrides == null || kind == null) {
            return null;
        }
        for (WorkflowModels.DependencyOverride override : dependencyOverrides) {
            if (override != null && override.kind() == kind) {
                return override;
            }
        }
        return null;
    }

    String imageName() {
        String projectName = projectRoot.getFileName() == null ? "app" : projectRoot.getFileName().toString();
        return "argus-" + projectName.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+", "")
                .replaceAll("-+$", "");
    }
}
