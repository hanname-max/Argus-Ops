package top.codejava.aiops.infrastructure.deploy.script;

import java.nio.file.Path;
import java.util.Locale;

import top.codejava.aiops.application.dto.WorkflowModels;

final class DeploymentDetectionContext {

    private final Path projectRoot;
    private final int hostPort;
    private final ProjectMarkerSnapshot markers;
    private final WorkflowModels.LocalProjectContext localContext;
    private final java.util.List<WorkflowModels.DependencyProbeResult> dependencyProbeResults;
    private final java.util.List<WorkflowModels.DependencyDecision> dependencyDecisions;

    DeploymentDetectionContext(Path projectRoot, int hostPort) {
        this(projectRoot, hostPort, null, java.util.List.of(), java.util.List.of());
    }

    DeploymentDetectionContext(Path projectRoot,
                               int hostPort,
                               WorkflowModels.LocalProjectContext localContext) {
        this(projectRoot, hostPort, localContext, java.util.List.of(), java.util.List.of());
    }

    DeploymentDetectionContext(Path projectRoot,
                               int hostPort,
                               WorkflowModels.LocalProjectContext localContext,
                               java.util.List<WorkflowModels.DependencyProbeResult> dependencyProbeResults,
                               java.util.List<WorkflowModels.DependencyDecision> dependencyDecisions) {
        this.projectRoot = projectRoot;
        this.hostPort = hostPort;
        this.markers = ProjectMarkerSnapshot.from(projectRoot);
        this.localContext = localContext;
        this.dependencyProbeResults = dependencyProbeResults == null ? java.util.List.of() : java.util.List.copyOf(dependencyProbeResults);
        this.dependencyDecisions = dependencyDecisions == null ? java.util.List.of() : java.util.List.copyOf(dependencyDecisions);
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

    java.util.List<WorkflowModels.DependencyProbeResult> dependencyProbeResults() {
        return dependencyProbeResults;
    }

    java.util.List<WorkflowModels.DependencyDecision> dependencyDecisions() {
        return dependencyDecisions;
    }

    String imageName() {
        String projectName = projectRoot.getFileName() == null ? "app" : projectRoot.getFileName().toString();
        return "argus-" + projectName.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+", "")
                .replaceAll("-+$", "");
    }
}
