package top.codejava.aiops.infrastructure.deploy.script;

import java.nio.file.Path;
import java.util.Locale;

final class DeploymentDetectionContext {

    private final Path projectRoot;
    private final int hostPort;
    private final ProjectMarkerSnapshot markers;

    DeploymentDetectionContext(Path projectRoot, int hostPort) {
        this.projectRoot = projectRoot;
        this.hostPort = hostPort;
        this.markers = ProjectMarkerSnapshot.from(projectRoot);
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

    String imageName() {
        String projectName = projectRoot.getFileName() == null ? "app" : projectRoot.getFileName().toString();
        return "argus-" + projectName.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+", "")
                .replaceAll("-+$", "");
    }
}
