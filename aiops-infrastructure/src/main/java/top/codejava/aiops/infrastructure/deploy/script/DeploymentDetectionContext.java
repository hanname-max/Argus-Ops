package top.codejava.aiops.infrastructure.deploy.script;

import top.codejava.aiops.infrastructure.projectscan.ProjectScanSnapshot;

import java.nio.file.Path;
import java.util.Locale;

final class DeploymentDetectionContext {

    private final Path projectRoot;
    private final int hostPort;
    private final ProjectScanSnapshot scanSnapshot;

    DeploymentDetectionContext(Path projectRoot, int hostPort, ProjectScanSnapshot scanSnapshot) {
        this.projectRoot = projectRoot;
        this.hostPort = hostPort;
        this.scanSnapshot = scanSnapshot;
    }

    Path projectRoot() {
        return projectRoot;
    }

    int hostPort() {
        return hostPort;
    }

    ProjectScanSnapshot scanSnapshot() {
        return scanSnapshot;
    }

    String imageName() {
        String projectName = projectRoot.getFileName() == null ? "app" : projectRoot.getFileName().toString();
        return "argus-" + projectName.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+", "")
                .replaceAll("-+$", "");
    }
}
