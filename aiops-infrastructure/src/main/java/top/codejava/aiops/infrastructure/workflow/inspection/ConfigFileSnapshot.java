package top.codejava.aiops.infrastructure.workflow.inspection;

import java.nio.file.Path;

record ConfigFileSnapshot(
        Path path,
        String content
) {
}
