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

    /**
     * Builds the upload bundle used by the remote deployment pipeline.
     *
     * <p>Most projects are archived as-is after filtering generic build noise. A local nginx
     * distribution is treated specially: only deployable runtime inputs such as {@code conf/}
     * and {@code html/} are bundled, while logs, temp files, and local executables are skipped.
     */
    public Path buildBundle(String projectPath, String workflowId) {
        if (projectPath == null || projectPath.isBlank()) {
            throw new RemoteExecutionException("projectPath is required for project packaging", null);
        }

        Path sourceRoot = Path.of(projectPath).toAbsolutePath().normalize();
        if (!Files.isDirectory(sourceRoot)) {
            throw new RemoteExecutionException("projectPath must be an existing directory: " + sourceRoot, null);
        }

        boolean nginxDistribution = looksLikeNginxDistribution(sourceRoot);

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
                            .filter(path -> shouldInclude(sourceRoot, path, nginxDistribution))
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

    private boolean shouldInclude(Path sourceRoot, Path path, boolean nginxDistribution) {
        Path relativePath = sourceRoot.relativize(path);
        if (nginxDistribution) {
            return shouldIncludeNginxDistribution(relativePath);
        }
        return shouldIncludeGeneric(relativePath);
    }

    private boolean shouldIncludeGeneric(Path relativePath) {
        for (Path segment : relativePath) {
            String name = segment.toString();
            if (".git".equalsIgnoreCase(name) || ".idea".equalsIgnoreCase(name) || "target".equalsIgnoreCase(name)) {
                return false;
            }
        }
        return true;
    }

    private boolean shouldIncludeNginxDistribution(Path relativePath) {
        if (relativePath.getNameCount() == 0) {
            return false;
        }
        // Keep only the parts that are meaningful inside the container image.
        String firstSegment = relativePath.getName(0).toString();
        return "conf".equalsIgnoreCase(firstSegment) || "html".equalsIgnoreCase(firstSegment);
    }

    private boolean looksLikeNginxDistribution(Path sourceRoot) {
        // This pattern matches an extracted nginx runtime directory rather than a source project.
        return Files.isRegularFile(sourceRoot.resolve("conf/nginx.conf"))
                && Files.isDirectory(sourceRoot.resolve("html"))
                && Files.isRegularFile(sourceRoot.resolve("nginx.exe"));
    }
}
