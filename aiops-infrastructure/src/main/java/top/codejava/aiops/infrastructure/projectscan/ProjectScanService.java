package top.codejava.aiops.infrastructure.projectscan;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class ProjectScanService {

    private final ExecutorService scanExecutor = Executors.newFixedThreadPool(
            Math.max(4, Math.min(8, Runtime.getRuntime().availableProcessors()))
    );

    public ProjectScanSnapshot scan(Path projectRoot) {
        Path normalizedRoot = projectRoot.toAbsolutePath().normalize();

        CompletableFuture<Set<String>> rootEntriesFuture = supply(() -> listRootEntries(normalizedRoot));
        CompletableFuture<List<String>> fileNamesFuture = supply(() -> listFileNames(normalizedRoot, 4));
        CompletableFuture<String> dockerfileFuture = read(normalizedRoot.resolve("Dockerfile"));
        CompletableFuture<String> packageJsonFuture = read(normalizedRoot.resolve("package.json"));
        CompletableFuture<String> pomFuture = read(normalizedRoot.resolve("pom.xml"));
        CompletableFuture<String> gradleFuture = read(normalizedRoot.resolve("build.gradle"));
        CompletableFuture<String> gradleKtsFuture = read(normalizedRoot.resolve("build.gradle.kts"));
        CompletableFuture<String> appPyFuture = read(normalizedRoot.resolve("app.py"));
        CompletableFuture<String> mainPyFuture = read(normalizedRoot.resolve("main.py"));
        CompletableFuture<String> applicationYamlFuture = read(normalizedRoot.resolve("src/main/resources/application.yml"));
        CompletableFuture<String> applicationYamlAltFuture = read(normalizedRoot.resolve("src/main/resources/application.yaml"));

        CompletableFuture.allOf(
                rootEntriesFuture,
                fileNamesFuture,
                dockerfileFuture,
                packageJsonFuture,
                pomFuture,
                gradleFuture,
                gradleKtsFuture,
                appPyFuture,
                mainPyFuture,
                applicationYamlFuture,
                applicationYamlAltFuture
        ).join();

        return new ProjectScanSnapshot(
                normalizedRoot,
                rootEntriesFuture.join(),
                fileNamesFuture.join(),
                dockerfileFuture.join(),
                packageJsonFuture.join(),
                pomFuture.join(),
                gradleFuture.join(),
                gradleKtsFuture.join(),
                appPyFuture.join(),
                mainPyFuture.join(),
                applicationYamlFuture.join() + "\n" + applicationYamlAltFuture.join()
        );
    }

    @PreDestroy
    void shutdown() {
        scanExecutor.shutdownNow();
    }

    private CompletableFuture<String> read(Path file) {
        return supply(() -> {
            try {
                return Files.isRegularFile(file) ? Files.readString(file) : "";
            } catch (IOException ignored) {
                return "";
            }
        });
    }

    private <T> CompletableFuture<T> supply(java.util.concurrent.Callable<T> task) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return task.call();
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        }, scanExecutor);
    }

    private Set<String> listRootEntries(Path projectRoot) {
        try (Stream<Path> stream = Files.list(projectRoot)) {
            return stream
                    .map(path -> path.getFileName().toString().toLowerCase(Locale.ROOT))
                    .collect(Collectors.toSet());
        } catch (IOException ignored) {
            return Set.of();
        }
    }

    private List<String> listFileNames(Path projectRoot, int depth) {
        try (Stream<Path> stream = Files.walk(projectRoot, depth)) {
            return stream
                    .filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString().toLowerCase(Locale.ROOT))
                    .toList();
        } catch (IOException ignored) {
            return List.of();
        }
    }
}
