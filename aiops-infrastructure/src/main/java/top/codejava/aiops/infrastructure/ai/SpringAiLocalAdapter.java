package top.codejava.aiops.infrastructure.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import top.codejava.aiops.application.dto.BuildScriptRequest;
import top.codejava.aiops.application.dto.BuildScriptResult;
import top.codejava.aiops.application.dto.EnvironmentCheckResult;
import top.codejava.aiops.application.dto.LogFilterResult;
import top.codejava.aiops.application.port.LocalAiPort;
import top.codejava.aiops.type.exception.ValidationException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class SpringAiLocalAdapter implements LocalAiPort {

    private static final Duration AI_TIMEOUT = Duration.ofSeconds(5);

    private final ChatClient localChatClient;
    private final String apiKey;

    public SpringAiLocalAdapter(@Qualifier("localChatClient") ChatClient localChatClient,
                                @Value("${aiops.local.api-key:}") String apiKey) {
        this.localChatClient = localChatClient;
        this.apiKey = apiKey;
    }

    @Override
    public BuildScriptResult generateBuildScript(BuildScriptRequest request) {
        ProjectSnapshot snapshot = inspectProject(request.projectPath());
        String fallbackScript = buildScriptFromSnapshot(snapshot, request.targetType());
        if (!isAiConfigured()) {
            return new BuildScriptResult(
                    fallbackScript,
                    "Local analyzer generated build script; AI enhancement skipped because LOCAL_AI_KEY is not configured.",
                    false
            );
        }

        try {
            String prompt = """
                    Generate a concise deployment build script for this project.
                    Project path: %s
                    Target type: %s
                    Detected build tool: %s
                    Detected runtime: %s
                    Has Dockerfile: %s
                    Output shell script only.
                    """.formatted(
                    snapshot.projectPath(),
                    normalizeTargetType(request.targetType()),
                    snapshot.buildTool(),
                    snapshot.runtime(),
                    snapshot.hasDockerfile()
            );
            String script = runAiCall(() -> localChatClient.prompt().user(prompt).call().content());
            if (hasText(script)) {
                return new BuildScriptResult(script.trim(), "Local AI generated build script from detected project structure.", false);
            }
        } catch (Exception ignored) {
        }

        return new BuildScriptResult(
                fallbackScript,
                "Local analyzer generated build script after AI enhancement timed out or failed.",
                false
        );
    }

    @Override
    public EnvironmentCheckResult checkEnvironment(String projectPath) {
        ProjectSnapshot snapshot = inspectProject(projectPath);
        List<String> checks = new ArrayList<>();
        List<String> risks = new ArrayList<>();

        checks.add("Project path exists: " + snapshot.projectPath());
        checks.add("Detected build tool: " + snapshot.buildTool());
        checks.add("Detected runtime: " + snapshot.runtime());
        checks.add("Java runtime: " + Runtime.version());
        checks.add("Dockerfile present: " + snapshot.hasDockerfile());
        checks.add("Application config present: " + snapshot.hasApplicationConfig());

        if (!snapshot.hasBuildDescriptor()) {
            risks.add("No supported build descriptor found. Expected pom.xml, build.gradle, build.gradle.kts, or package.json.");
        }
        if (!snapshot.hasDockerfile()) {
            risks.add("Dockerfile is missing. Build-script generation will create one, but deployment is not repository-ready yet.");
        }
        if (!snapshot.hasApplicationConfig()) {
            risks.add("application.yml or application.yaml not found under src/main/resources.");
        }
        if ("unknown".equals(snapshot.runtime())) {
            risks.add("Runtime stack could not be inferred from common markers. Review the generated script before deployment.");
        }

        if (isAiConfigured()) {
            try {
                String aiRisk = runAiCall(() -> localChatClient.prompt().user("""
                        Review this local environment scan and return one extra deployment risk in one sentence.
                        Build tool: %s
                        Runtime: %s
                        Dockerfile: %s
                        Application config: %s
                        """.formatted(
                        snapshot.buildTool(),
                        snapshot.runtime(),
                        snapshot.hasDockerfile(),
                        snapshot.hasApplicationConfig()
                )).call().content());
                if (hasText(aiRisk)) {
                    risks.add("AI supplement: " + aiRisk.trim());
                }
            } catch (Exception ignored) {
                checks.add("AI supplement skipped after timeout or provider failure.");
            }
        } else {
            checks.add("AI supplement skipped because LOCAL_AI_KEY is not configured.");
        }

        String conclusion = risks.isEmpty()
                ? "Local environment check passed without blocking risks."
                : "Local environment check completed with %d risk(s).".formatted(risks.size());
        return new EnvironmentCheckResult(checks, risks, conclusion);
    }

    @Override
    public LogFilterResult filterAndAnalyzeLog(String rawLog) {
        List<String> interestingLines = Arrays.stream(rawLog.split("\\R"))
                .map(String::trim)
                .filter(this::hasText)
                .filter(this::looksImportant)
                .limit(8)
                .toList();

        String keyError = interestingLines.isEmpty() ? firstMeaningfulLine(rawLog) : interestingLines.getFirst();
        String filteredLog = interestingLines.isEmpty()
                ? truncate(rawLog, 1200)
                : String.join(System.lineSeparator(), interestingLines);
        String advice = buildLocalAdvice(filteredLog);

        if (isAiConfigured()) {
            try {
                String aiAdvice = runAiCall(() -> localChatClient.prompt().user(
                        "Filter key errors from this deployment log and provide one prioritized fix:\n" + rawLog
                ).call().content());
                if (hasText(aiAdvice)) {
                    advice = advice + System.lineSeparator() + "AI supplement: " + aiAdvice.trim();
                }
            } catch (Exception ignored) {
                advice = advice + System.lineSeparator() + "AI supplement skipped after timeout or provider failure.";
            }
        } else {
            advice = advice + System.lineSeparator() + "AI supplement skipped because LOCAL_AI_KEY is not configured.";
        }

        return new LogFilterResult(keyError, filteredLog, advice);
    }

    private ProjectSnapshot inspectProject(String projectPath) {
        if (!hasText(projectPath)) {
            throw new ValidationException("projectPath is required");
        }

        Path path = Path.of(projectPath).toAbsolutePath().normalize();
        if (!Files.exists(path)) {
            throw new ValidationException("projectPath does not exist: " + path);
        }
        if (!Files.isDirectory(path)) {
            throw new ValidationException("projectPath must be a directory: " + path);
        }

        boolean hasPom = Files.exists(path.resolve("pom.xml"));
        boolean hasGradle = Files.exists(path.resolve("build.gradle")) || Files.exists(path.resolve("build.gradle.kts"));
        boolean hasPackageJson = Files.exists(path.resolve("package.json"));
        boolean hasDockerfile = Files.exists(path.resolve("Dockerfile"));
        boolean hasApplicationConfig = Files.exists(path.resolve("src/main/resources/application.yml"))
                || Files.exists(path.resolve("src/main/resources/application.yaml"));

        String buildTool = hasPom ? "maven" : hasGradle ? "gradle" : hasPackageJson ? "npm" : "unknown";
        String runtime = detectRuntime(path, hasPom, hasGradle, hasPackageJson);

        return new ProjectSnapshot(path, buildTool, runtime, hasDockerfile, hasApplicationConfig,
                hasPom || hasGradle || hasPackageJson);
    }

    private String detectRuntime(Path path, boolean hasPom, boolean hasGradle, boolean hasPackageJson) {
        if (hasPom || hasGradle) {
            return "java";
        }
        if (hasPackageJson) {
            return "node";
        }
        if (Files.exists(path.resolve("requirements.txt")) || Files.exists(path.resolve("pyproject.toml"))) {
            return "python";
        }

        try (Stream<Path> stream = Files.walk(path, 2)) {
            List<String> names = stream
                    .filter(Files::isRegularFile)
                    .map(item -> item.getFileName().toString().toLowerCase(Locale.ROOT))
                    .collect(Collectors.toList());
            if (names.stream().anyMatch(name -> name.endsWith(".java"))) {
                return "java";
            }
            if (names.stream().anyMatch(name -> name.endsWith(".js") || name.endsWith(".ts"))) {
                return "node";
            }
            if (names.stream().anyMatch(name -> name.endsWith(".py"))) {
                return "python";
            }
        } catch (IOException ignored) {
        }

        return "unknown";
    }

    private String buildScriptFromSnapshot(ProjectSnapshot snapshot, String targetType) {
        String normalizedTarget = normalizeTargetType(targetType);
        String imageName = slugify(snapshot.projectPath().getFileName() == null
                ? snapshot.projectPath().toString()
                : snapshot.projectPath().getFileName().toString());
        String quotedPath = snapshot.projectPath().toString().replace("\\", "/");

        if ("java".equals(snapshot.runtime()) && "maven".equals(snapshot.buildTool())) {
            if (normalizedTarget.contains("docker")) {
                return """
                        #!/usr/bin/env bash
                        set -e
                        cd "%s"
                        mvn clean package -DskipTests
                        if [ ! -f Dockerfile ]; then
                          cat > Dockerfile <<'EOF'
                        FROM eclipse-temurin:21-jre
                        WORKDIR /app
                        COPY target/*.jar app.jar
                        EXPOSE 8080
                        ENTRYPOINT ["java","-jar","/app/app.jar"]
                        EOF
                        fi
                        docker build -t %s:latest .
                        docker run --rm -p 8080:8080 %s:latest
                        """.formatted(quotedPath, imageName, imageName).trim();
            }
            return """
                    #!/usr/bin/env bash
                    set -e
                    cd "%s"
                    mvn clean package -DskipTests
                    java -jar target/*.jar
                    """.formatted(quotedPath).trim();
        }

        if ("java".equals(snapshot.runtime()) && "gradle".equals(snapshot.buildTool())) {
            return """
                    #!/usr/bin/env bash
                    set -e
                    cd "%s"
                    ./gradlew clean build -x test
                    docker build -t %s:latest .
                    docker run --rm -p 8080:8080 %s:latest
                    """.formatted(quotedPath, imageName, imageName).trim();
        }

        if ("node".equals(snapshot.runtime())) {
            return """
                    #!/usr/bin/env bash
                    set -e
                    cd "%s"
                    npm install
                    npm run build
                    if [ ! -f Dockerfile ]; then
                      cat > Dockerfile <<'EOF'
                    FROM node:20-alpine
                    WORKDIR /app
                    COPY package*.json ./
                    RUN npm install
                    COPY . .
                    EXPOSE 3000
                    CMD ["npm","start"]
                    EOF
                    fi
                    docker build -t %s:latest .
                    docker run --rm -p 3000:3000 %s:latest
                    """.formatted(quotedPath, imageName, imageName).trim();
        }

        return """
                #!/usr/bin/env bash
                set -e
                cd "%s"
                echo "Unknown stack. Review project layout before deployment."
                ls -la
                """.formatted(quotedPath).trim();
    }

    private String buildLocalAdvice(String filteredLog) {
        String normalized = filteredLog.toLowerCase(Locale.ROOT);
        if (normalized.contains("connection refused") || normalized.contains("connect timed out")) {
            return "Network connectivity failed. Check target host, exposed port, firewall, and whether the remote service is running.";
        }
        if (normalized.contains("permission denied") || normalized.contains("access denied")) {
            return "Permission issue detected. Verify deployment user, SSH key or password, file permissions, and target directory ownership.";
        }
        if (normalized.contains("outofmemory") || normalized.contains("java heap space")) {
            return "Process ran out of memory. Increase JVM heap or reduce the workload before redeploying.";
        }
        if (normalized.contains("no such file") || normalized.contains("not found")) {
            return "Referenced file or command is missing. Verify build artifacts, deployment paths, and required runtime commands.";
        }
        if (normalized.contains("syntax error")) {
            return "Script syntax error detected. Recheck shell quoting, heredoc boundaries, and line endings.";
        }
        return "Review the first failing line, confirm the generated build artifact exists, and reproduce the failing command locally before retrying remote deployment.";
    }

    private boolean looksImportant(String line) {
        String normalized = line.toLowerCase(Locale.ROOT);
        return normalized.contains("error")
                || normalized.contains("exception")
                || normalized.contains("caused by")
                || normalized.contains("failed")
                || normalized.contains("denied")
                || normalized.contains("timeout");
    }

    private String firstMeaningfulLine(String rawLog) {
        return Arrays.stream(rawLog.split("\\R"))
                .map(String::trim)
                .filter(this::hasText)
                .findFirst()
                .orElse("No meaningful log line found.");
    }

    private String truncate(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...";
    }

    private String normalizeTargetType(String targetType) {
        return hasText(targetType) ? targetType.trim().toLowerCase(Locale.ROOT) : "docker";
    }

    private String slugify(String value) {
        String normalized = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
        return normalized.replaceAll("(^-+|-+$)", "");
    }

    private String runAiCall(Supplier<String> call) {
        return CompletableFuture.supplyAsync(call)
                .orTimeout(AI_TIMEOUT.toSeconds(), TimeUnit.SECONDS)
                .join();
    }

    private boolean isAiConfigured() {
        return hasText(apiKey) && !Objects.equals("sk-placeholder", apiKey) && !apiKey.contains("placeholder");
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record ProjectSnapshot(Path projectPath,
                                   String buildTool,
                                   String runtime,
                                   boolean hasDockerfile,
                                   boolean hasApplicationConfig,
                                   boolean hasBuildDescriptor) {
    }
}
