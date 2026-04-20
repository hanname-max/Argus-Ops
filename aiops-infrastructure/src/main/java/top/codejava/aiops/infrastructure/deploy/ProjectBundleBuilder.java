package top.codejava.aiops.infrastructure.deploy;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.springframework.stereotype.Component;
import top.codejava.aiops.type.exception.RemoteExecutionException;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

@Component
public class ProjectBundleBuilder {

    public Path buildBundle(String projectPath, String workflowId) {
        if (projectPath == null || projectPath.isBlank()) {
            throw new RemoteExecutionException("projectPath is required for project packaging", null);
        }

        Path sourceRoot = Path.of(projectPath).toAbsolutePath().normalize();
        if (!Files.isDirectory(sourceRoot)) {
            throw new RemoteExecutionException("projectPath must be an existing directory: " + sourceRoot, null);
        }

        try {
            Path tempDirectory = Files.createTempDirectory("argus-bundle-" + workflowId + "-");
            Path bundlePath = tempDirectory.resolve("bundle.tar.gz");
            try (OutputStream fileStream = Files.newOutputStream(bundlePath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                 GzipCompressorOutputStream gzipStream = new GzipCompressorOutputStream(fileStream);
                 TarArchiveOutputStream tarStream = new TarArchiveOutputStream(gzipStream)) {
                tarStream.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
                try (Stream<Path> paths = Files.walk(sourceRoot)) {
                    List<Path> sourceFiles = paths
                            .filter(path -> !path.equals(sourceRoot))
                            .filter(this::shouldInclude)
                            .sorted(Comparator.naturalOrder())
                            .toList();
                    for (Path path : sourceFiles) {
                        writeEntry(sourceRoot, path, tarStream);
                    }
                }
            }
            return bundlePath;
        } catch (IOException ex) {
            throw new RemoteExecutionException("Failed to build local project bundle for " + projectPath, ex);
        }
    }

    private void writeEntry(Path sourceRoot, Path path, TarArchiveOutputStream tarStream) throws IOException {
        String relativePath = sourceRoot.relativize(path).toString().replace('\\', '/');
        TarArchiveEntry entry = new TarArchiveEntry(path.toFile(), relativePath);
        tarStream.putArchiveEntry(entry);
        if (Files.isRegularFile(path)) {
            Files.copy(path, tarStream);
        }
        tarStream.closeArchiveEntry();
    }

    private boolean shouldInclude(Path path) {
        for (Path segment : path) {
            String name = segment.toString();
            if (".git".equalsIgnoreCase(name) || ".idea".equalsIgnoreCase(name) || "target".equalsIgnoreCase(name)) {
                return false;
            }
        }
        return true;
    }
}
