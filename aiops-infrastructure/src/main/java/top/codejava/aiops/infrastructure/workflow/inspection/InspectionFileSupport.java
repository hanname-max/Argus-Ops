package top.codejava.aiops.infrastructure.workflow.inspection;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

final class InspectionFileSupport {

    private InspectionFileSupport() {
    }

    static String readIfExists(Path path) {
        try {
            return Files.exists(path) ? Files.readString(path) : "";
        } catch (IOException ignored) {
            return "";
        }
    }
}
