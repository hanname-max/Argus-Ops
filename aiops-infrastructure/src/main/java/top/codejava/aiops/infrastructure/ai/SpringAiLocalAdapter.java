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
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class SpringAiLocalAdapter implements LocalAiPort {

    private static final Duration AI_TIMEOUT = Duration.ofSeconds(5);
    private static final Set<String> STATIC_DIR_NAMES = Set.of(
            "assets", "css", "fonts", "images", "img", "js", "public", "static"
    );

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
        String script = buildScriptFromSnapshot(snapshot, request.targetType());
        return new BuildScriptResult(script, buildScriptSummary(snapshot), false);
    }

    @Override
    public EnvironmentCheckResult checkEnvironment(String projectPath) {
        ProjectSnapshot snapshot = inspectProject(projectPath);
        List<String> checks = new ArrayList<>();
        List<String> risks = new ArrayList<>();

        checks.add("Project path exists: " + snapshot.projectPath());
        checks.add("Detected language: " + snapshot.language());
        checks.add("Detected project kind: " + snapshot.projectKind());
        checks.add("Detected build tool: " + snapshot.buildTool());
        checks.add("Suggested container port: " + snapshot.containerPort());
        checks.add("Dockerfile present: " + snapshot.hasDockerfile());

        if (snapshot.hasApplicationConfig()) {
            checks.add("Application config present: true");
        } else if (expectsApplicationConfig(snapshot)) {
            checks.add("Application config present: false");
        }

        if (!snapshot.hasBuildDescriptor() && !"static-site".equals(snapshot.projectKind())) {
            risks.add("No standard build descriptor found. The generated Docker script is heuristic and should be reviewed before deployment.");
        }
        if (!snapshot.hasDockerfile()) {
            risks.add("Dockerfile is missing. The generated build script will create one for the detected stack.");
        }
        if (expectsApplicationConfig(snapshot) && !snapshot.hasApplicationConfig()) {
            risks.add("application.yml or application.yaml not found under src/main/resources.");
        }
        if ("unknown".equals(snapshot.language())) {
            risks.add("Project language could not be inferred confidently. Review the generated Docker script before using it.");
        }

        if (isAiConfigured()) {
            try {
                String aiRisk = runAiCall(() -> localChatClient.prompt().user("""
                        Review this local environment scan and return one extra deployment risk in one sentence.
                        Language: %s
                        Project kind: %s
                        Build tool: %s
                        Dockerfile: %s
                        """.formatted(
                        snapshot.language(),
                        snapshot.projectKind(),
                        snapshot.buildTool(),
                        snapshot.hasDockerfile()
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

        Set<String> rootEntries = listRootEntries(path);
        List<String> fileNames = listFileNames(path, 3);
        List<String> staticEntries = detectStaticEntries(path);

        boolean hasPom = rootEntries.contains("pom.xml");
        boolean hasGradle = rootEntries.contains("build.gradle") || rootEntries.contains("build.gradle.kts");
        boolean hasPackageJson = rootEntries.contains("package.json");
        boolean hasRequirements = rootEntries.contains("requirements.txt");
        boolean hasPyproject = rootEntries.contains("pyproject.toml");
        boolean hasPipfile = rootEntries.contains("pipfile");
        boolean hasGoMod = rootEntries.contains("go.mod");
        boolean hasCargoToml = rootEntries.contains("cargo.toml");
        boolean hasComposerJson = rootEntries.contains("composer.json");
        boolean hasGemfile = rootEntries.contains("gemfile");
        boolean hasDockerfile = rootEntries.contains("dockerfile");
        boolean hasApplicationConfig = Files.exists(path.resolve("src/main/resources/application.yml"))
                || Files.exists(path.resolve("src/main/resources/application.yaml"));
        boolean hasSolution = rootEntries.stream().anyMatch(name -> name.endsWith(".sln"));
        boolean hasCsproj = fileNames.stream().anyMatch(name -> name.endsWith(".csproj"));
        boolean hasIndexHtml = rootEntries.contains("index.html");
        boolean hasHtmlFiles = fileNames.stream().anyMatch(name -> name.endsWith(".html") || name.endsWith(".htm"));
        boolean hasCssFiles = fileNames.stream().anyMatch(name -> name.endsWith(".css"));
        boolean hasJavaFiles = fileNames.stream().anyMatch(name -> name.endsWith(".java"));
        boolean hasJsFiles = fileNames.stream().anyMatch(name -> name.endsWith(".js") || name.endsWith(".mjs") || name.endsWith(".cjs"));
        boolean hasTsFiles = fileNames.stream().anyMatch(name -> name.endsWith(".ts") || name.endsWith(".tsx"));
        boolean hasPythonFiles = fileNames.stream().anyMatch(name -> name.endsWith(".py"));
        boolean hasGoFiles = fileNames.stream().anyMatch(name -> name.endsWith(".go"));
        boolean hasRustFiles = fileNames.stream().anyMatch(name -> name.endsWith(".rs"));
        boolean hasPhpFiles = fileNames.stream().anyMatch(name -> name.endsWith(".php"));
        boolean hasRubyFiles = fileNames.stream().anyMatch(name -> name.endsWith(".rb"));
        boolean looksLikeStaticSite = !hasPackageJson && (hasIndexHtml || hasHtmlFiles)
                && (!staticEntries.isEmpty() || hasCssFiles || hasJsFiles || hasTsFiles);

        if (hasPom) {
            return new ProjectSnapshot(path, "java", "maven", "java-maven", hasDockerfile, hasApplicationConfig, true, 8080, 8080, List.of());
        }
        if (hasGradle) {
            return new ProjectSnapshot(path, "java", "gradle", "java-gradle", hasDockerfile, hasApplicationConfig, true, 8080, 8080, List.of());
        }
        if (hasPackageJson) {
            return inspectNodeProject(path, rootEntries, hasDockerfile);
        }
        if (hasGoMod) {
            return new ProjectSnapshot(path, "go", "go", "go-module", hasDockerfile, false, true, 8080, 8080, List.of());
        }
        if (hasRequirements || hasPyproject || hasPipfile || hasPythonFiles) {
            return new ProjectSnapshot(path, "python", "pip", "python-app", hasDockerfile, false,
                    hasRequirements || hasPyproject || hasPipfile, 8000, 8000, List.of());
        }
        if (hasCsproj || hasSolution) {
            return new ProjectSnapshot(path, "dotnet", "dotnet", "dotnet-app", hasDockerfile, false, true, 8080, 8080, List.of());
        }
        if (hasComposerJson || hasPhpFiles) {
            return new ProjectSnapshot(path, "php", "composer", "php-app", hasDockerfile, false,
                    hasComposerJson, 8080, 80, List.of());
        }
        if (hasGemfile || hasRubyFiles) {
            return new ProjectSnapshot(path, "ruby", "bundler", "ruby-app", hasDockerfile, false,
                    hasGemfile, 3000, 3000, List.of());
        }
        if (hasCargoToml) {
            return new ProjectSnapshot(path, "rust", "cargo", "rust-app", hasDockerfile, false, true, 8080, 8080, List.of());
        }
        if (looksLikeStaticSite) {
            return new ProjectSnapshot(path, "static", "none", "static-site", hasDockerfile, false, true, 3000, 80, staticEntries);
        }
        if (hasJavaFiles) {
            return new ProjectSnapshot(path, "java", "none", "java-source", hasDockerfile, hasApplicationConfig, false, 8080, 8080, List.of());
        }
        if (hasGoFiles) {
            return new ProjectSnapshot(path, "go", "none", "go-source", hasDockerfile, false, false, 8080, 8080, List.of());
        }
        if (hasRustFiles) {
            return new ProjectSnapshot(path, "rust", "none", "rust-source", hasDockerfile, false, false, 8080, 8080, List.of());
        }
        if (hasPhpFiles) {
            return new ProjectSnapshot(path, "php", "none", "php-source", hasDockerfile, false, false, 8080, 80, List.of());
        }
        if (hasRubyFiles) {
            return new ProjectSnapshot(path, "ruby", "none", "ruby-source", hasDockerfile, false, false, 3000, 3000, List.of());
        }
        if (hasPythonFiles) {
            return new ProjectSnapshot(path, "python", "none", "python-source", hasDockerfile, false, false, 8000, 8000, List.of());
        }
        if (hasJsFiles || hasTsFiles) {
            return new ProjectSnapshot(path, "javascript", "none", "js-source", hasDockerfile, false, false, 3000, 3000, List.of());
        }

        return new ProjectSnapshot(path, "unknown", "unknown", "unknown", hasDockerfile, false, false, 8080, 8080, List.of());
    }

    private ProjectSnapshot inspectNodeProject(Path path, Set<String> rootEntries, boolean hasDockerfile) {
        String packageJson = readText(path.resolve("package.json"));
        String normalizedPackageJson = packageJson.toLowerCase(Locale.ROOT);
        String packageManager = detectNodePackageManager(rootEntries);
        boolean hasBuildScript = normalizedPackageJson.contains("\"build\"");
        boolean isNext = normalizedPackageJson.contains("\"next\"");
        boolean isFrontend = normalizedPackageJson.contains("\"vite\"")
                || normalizedPackageJson.contains("\"react-scripts\"")
                || normalizedPackageJson.contains("\"@angular/core\"")
                || normalizedPackageJson.contains("\"vue\"")
                || normalizedPackageJson.contains("\"nuxt\"")
                || normalizedPackageJson.contains("\"svelte\"");

        if (isNext) {
            return new ProjectSnapshot(path, "node", packageManager, "node-next", hasDockerfile, false, true, 3000, 3000, List.of());
        }
        if (isFrontend && hasBuildScript) {
            return new ProjectSnapshot(path, "node", packageManager, "node-frontend", hasDockerfile, false, true, 3000, 80, List.of());
        }
        return new ProjectSnapshot(path, "node", packageManager, "node-service", hasDockerfile, false, true, 3000, 3000, List.of());
    }

    private String buildScriptFromSnapshot(ProjectSnapshot snapshot, String targetType) {
        String normalizedTarget = normalizeTargetType(targetType);
        if (!normalizedTarget.contains("docker")) {
            return nonDockerScript(snapshot);
        }

        return dockerScript(snapshot, switch (snapshot.projectKind()) {
            case "java-maven" -> dockerfileForJavaMaven();
            case "java-gradle" -> dockerfileForJavaGradle();
            case "node-next" -> dockerfileForNodeNext(snapshot);
            case "node-frontend" -> dockerfileForNodeFrontend(snapshot);
            case "node-service" -> dockerfileForNodeService(snapshot);
            case "python-app", "python-source" -> dockerfileForPython();
            case "go-module", "go-source" -> dockerfileForGo();
            case "dotnet-app" -> dockerfileForDotnet();
            case "php-app", "php-source" -> dockerfileForPhp();
            case "ruby-app", "ruby-source" -> dockerfileForRuby();
            case "rust-app" -> dockerfileForRust();
            case "rust-source" -> dockerfileForRustSource();
            case "static-site" -> dockerfileForStaticSite(snapshot);
            case "java-source" -> dockerfileForJavaSource();
            case "js-source" -> dockerfileForJavascriptSource();
            default -> dockerfileForUnknown();
        });
    }

    private String nonDockerScript(ProjectSnapshot snapshot) {
        String quotedPath = snapshot.projectPath().toString().replace("\\", "/");
        return switch (snapshot.projectKind()) {
            case "java-maven" -> """
                    #!/usr/bin/env bash
                    set -e
                    cd "%s"
                    mvn clean package -DskipTests
                    java -jar target/*.jar
                    """.formatted(quotedPath).trim();
            case "java-gradle" -> """
                    #!/usr/bin/env bash
                    set -e
                    cd "%s"
                    ./gradlew clean build -x test
                    java -jar build/libs/*.jar
                    """.formatted(quotedPath).trim();
            case "node-service", "node-next" -> """
                    #!/usr/bin/env bash
                    set -e
                    cd "%s"
                    %s
                    %s
                    """.formatted(quotedPath, nodeInstallCommand(snapshot), nodeStartCommand(snapshot)).trim();
            case "node-frontend" -> """
                    #!/usr/bin/env bash
                    set -e
                    cd "%s"
                    %s
                    %s run build
                    """.formatted(quotedPath, nodeInstallCommand(snapshot), nodeRunner(snapshot)).trim();
            case "static-site" -> """
                    #!/usr/bin/env bash
                    set -e
                    cd "%s"
                    python -m http.server 3000
                    """.formatted(quotedPath).trim();
            case "python-app", "python-source" -> """
                    #!/usr/bin/env bash
                    set -e
                    cd "%s"
                    python main.py
                    """.formatted(quotedPath).trim();
            default -> """
                    #!/usr/bin/env bash
                    set -e
                    cd "%s"
                    echo "Detected %s. Review the project entrypoint and build steps before running."
                    ls -la
                    """.formatted(quotedPath, snapshot.projectKind()).trim();
        };
    }

    private String dockerScript(ProjectSnapshot snapshot, String dockerfileContent) {
        String imageName = slugify(snapshot.projectPath().getFileName() == null
                ? snapshot.projectPath().toString()
                : snapshot.projectPath().getFileName().toString());
        String quotedPath = snapshot.projectPath().toString().replace("\\", "/");

        return """
                #!/usr/bin/env bash
                set -e
                # Detected language: %s
                # Detected project kind: %s
                cd "%s"
                if [ ! -f Dockerfile ]; then
                  cat > Dockerfile <<'EOF'
                %s
                EOF
                fi
                docker build -t %s:latest .
                docker run --rm -p %d:%d %s:latest
                """.formatted(
                snapshot.language(),
                snapshot.projectKind(),
                quotedPath,
                dockerfileContent.strip(),
                imageName,
                snapshot.hostPort(),
                snapshot.containerPort(),
                imageName
        ).trim();
    }

    private String dockerfileForJavaMaven() {
        return """
                FROM maven:3.9.9-eclipse-temurin-21 AS build
                WORKDIR /workspace
                COPY . .
                RUN mvn -DskipTests package

                FROM eclipse-temurin:21-jre
                WORKDIR /app
                COPY --from=build /workspace/target/*.jar /app/app.jar
                EXPOSE 8080
                ENTRYPOINT ["java","-jar","/app/app.jar"]
                """;
    }

    private String dockerfileForJavaGradle() {
        return """
                FROM gradle:8.10.2-jdk21 AS build
                WORKDIR /workspace
                COPY . .
                RUN gradle clean build -x test --no-daemon

                FROM eclipse-temurin:21-jre
                WORKDIR /app
                COPY --from=build /workspace/build/libs/*.jar /app/app.jar
                EXPOSE 8080
                ENTRYPOINT ["java","-jar","/app/app.jar"]
                """;
    }

    private String dockerfileForJavaSource() {
        return """
                FROM eclipse-temurin:21-jdk
                WORKDIR /workspace
                COPY . .
                RUN find . -name "*.java" > sources.list && javac @sources.list
                EXPOSE 8080
                CMD ["sh","-c","echo Compiled plain Java sources. Add an explicit entrypoint class before production deployment."]
                """;
    }

    private String dockerfileForNodeNext(ProjectSnapshot snapshot) {
        return """
                FROM node:20-alpine AS build
                WORKDIR /app
                COPY . .
                RUN %s
                RUN %s run build

                FROM node:20-alpine
                WORKDIR /app
                ENV NODE_ENV=production
                COPY --from=build /app ./
                RUN %s
                EXPOSE 3000
                CMD ["sh","-c","%s"]
                """.formatted(
                nodeInstallCommand(snapshot),
                nodeRunner(snapshot),
                nodeProdInstallCommand(snapshot),
                nodeStartCommand(snapshot)
        );
    }

    private String dockerfileForNodeFrontend(ProjectSnapshot snapshot) {
        return """
                FROM node:20-alpine AS build
                WORKDIR /app
                COPY . .
                RUN %s
                RUN %s run build

                FROM nginx:1.27-alpine
                COPY --from=build /app /tmp/app
                RUN rm -rf /usr/share/nginx/html/* && \
                    if [ -d /tmp/app/dist ]; then cp -r /tmp/app/dist/. /usr/share/nginx/html/; \
                    elif [ -d /tmp/app/build ]; then cp -r /tmp/app/build/. /usr/share/nginx/html/; \
                    elif [ -d /tmp/app/out ]; then cp -r /tmp/app/out/. /usr/share/nginx/html/; \
                    else echo "No frontend build output found (dist/build/out)." && exit 1; fi && \
                    rm -rf /tmp/app
                EXPOSE 80
                CMD ["nginx","-g","daemon off;"]
                """.formatted(nodeInstallCommand(snapshot), nodeRunner(snapshot));
    }

    private String dockerfileForNodeService(ProjectSnapshot snapshot) {
        String buildStep = "node-service".equals(snapshot.projectKind()) ? """
                RUN if grep -q '"build"' package.json; then %s run build; fi
                """.formatted(nodeRunner(snapshot)) : "";

        return """
                FROM node:20-alpine
                WORKDIR /app
                COPY . .
                RUN %s
                %sEXPOSE 3000
                CMD ["sh","-c","%s"]
                """.formatted(nodeInstallCommand(snapshot), buildStep, nodeStartCommand(snapshot));
    }

    private String dockerfileForPython() {
        return """
                FROM python:3.12-slim
                WORKDIR /app
                COPY . .
                RUN if [ -f requirements.txt ]; then pip install --no-cache-dir -r requirements.txt; \
                    elif [ -f pyproject.toml ]; then pip install --no-cache-dir .; \
                    elif [ -f Pipfile ]; then pip install --no-cache-dir pipenv && pipenv install --system; \
                    fi
                EXPOSE 8000
                CMD ["sh","-c","if [ -f manage.py ]; then python manage.py runserver 0.0.0.0:8000; \
                    elif [ -f app.py ]; then python app.py; \
                    elif [ -f main.py ]; then python main.py; \
                    else python -m http.server 8000; fi"]
                """;
    }

    private String dockerfileForGo() {
        return """
                FROM golang:1.22-alpine AS build
                WORKDIR /src
                COPY . .
                RUN if [ -f go.mod ]; then go mod download; fi
                RUN CGO_ENABLED=0 GOOS=linux go build -o /app/service .

                FROM alpine:3.20
                WORKDIR /app
                COPY --from=build /app/service /app/service
                EXPOSE 8080
                ENTRYPOINT ["/app/service"]
                """;
    }

    private String dockerfileForDotnet() {
        return """
                FROM mcr.microsoft.com/dotnet/sdk:8.0 AS build
                WORKDIR /src
                COPY . .
                RUN project=$(find . -maxdepth 3 -name "*.csproj" | head -n 1) && \
                    test -n "$project" && \
                    dotnet publish "$project" -c Release -o /app/publish

                FROM mcr.microsoft.com/dotnet/aspnet:8.0
                WORKDIR /app
                COPY --from=build /app/publish .
                EXPOSE 8080
                CMD ["sh","-c","dll=$(find . -maxdepth 1 -name '*.dll' | head -n 1); exec dotnet \"$dll\""]
                """;
    }

    private String dockerfileForPhp() {
        return """
                FROM php:8.3-apache
                WORKDIR /var/www/html
                COPY . .
                EXPOSE 80
                CMD ["apache2-foreground"]
                """;
    }

    private String dockerfileForRuby() {
        return """
                FROM ruby:3.3-alpine
                WORKDIR /app
                COPY Gemfile* ./
                RUN if [ -f Gemfile ]; then bundle install; fi
                COPY . .
                EXPOSE 3000
                CMD ["sh","-c","if [ -f bin/rails ]; then bundle exec rails server -b 0.0.0.0 -p 3000; \
                    else bundle exec rackup --host 0.0.0.0 -p 3000; fi"]
                """;
    }

    private String dockerfileForRust() {
        return """
                FROM rust:1.77 AS build
                WORKDIR /app
                COPY . .
                RUN cargo build --release

                FROM debian:bookworm-slim
                WORKDIR /app
                COPY --from=build /app/target/release /tmp/release
                RUN bin=$(find /tmp/release -maxdepth 1 -type f -perm /111 | head -n 1) && \
                    test -n "$bin" && \
                    cp "$bin" /app/service && \
                    rm -rf /tmp/release
                EXPOSE 8080
                ENTRYPOINT ["/app/service"]
                """;
    }

    private String dockerfileForRustSource() {
        return """
                FROM rust:1.77
                WORKDIR /app
                COPY . .
                EXPOSE 8080
                CMD ["sh","-c","echo Rust sources detected without Cargo.toml. Add a Cargo project before containerized deployment."]
                """;
    }

    private String dockerfileForStaticSite(ProjectSnapshot snapshot) {
        String copyLines = snapshot.staticEntries().isEmpty()
                ? "COPY . ."
                : snapshot.staticEntries().stream()
                .map(entry -> "COPY " + entry + " " + entry)
                .collect(Collectors.joining(System.lineSeparator()));

        return """
                FROM nginx:1.27-alpine
                WORKDIR /usr/share/nginx/html
                RUN rm -rf ./*
                %s
                EXPOSE 80
                CMD ["nginx","-g","daemon off;"]
                """.formatted(copyLines);
    }

    private String dockerfileForJavascriptSource() {
        return """
                FROM node:20-alpine
                WORKDIR /app
                COPY . .
                EXPOSE 3000
                CMD ["sh","-c","echo Plain JavaScript/TypeScript sources detected without package.json. Add an explicit runtime entrypoint before deployment."]
                """;
    }

    private String dockerfileForUnknown() {
        return """
                FROM alpine:3.20
                WORKDIR /app
                COPY . .
                EXPOSE 8080
                CMD ["sh","-c","echo Unknown project layout. Review this repository and create a tailored Dockerfile."]
                """;
    }

    private String buildScriptSummary(ProjectSnapshot snapshot) {
        return switch (snapshot.projectKind()) {
            case "static-site" ->
                    "Detected a static site from HTML/CSS/JS assets and generated an nginx-based Docker deployment script.";
            case "node-frontend" ->
                    "Detected a frontend Node package with a build step and generated a build-to-nginx Docker deployment script.";
            case "node-next" ->
                    "Detected a Next.js application and generated a Node-based Docker deployment script.";
            case "node-service" ->
                    "Detected a Node package application and generated a service-style Docker deployment script.";
            default ->
                    "Detected %s using %s and generated a Docker deployment script for the inferred stack."
                            .formatted(snapshot.projectKind(), snapshot.buildTool());
        };
    }

    private String detectNodePackageManager(Set<String> rootEntries) {
        if (rootEntries.contains("pnpm-lock.yaml")) {
            return "pnpm";
        }
        if (rootEntries.contains("yarn.lock")) {
            return "yarn";
        }
        return "npm";
    }

    private String nodeInstallCommand(ProjectSnapshot snapshot) {
        return switch (snapshot.buildTool()) {
            case "pnpm" -> "corepack enable && pnpm install --frozen-lockfile";
            case "yarn" -> "corepack enable && yarn install --frozen-lockfile";
            default -> "npm install";
        };
    }

    private String nodeProdInstallCommand(ProjectSnapshot snapshot) {
        return switch (snapshot.buildTool()) {
            case "pnpm" -> "corepack enable && pnpm install --prod --frozen-lockfile";
            case "yarn" -> "corepack enable && yarn install --production=true --frozen-lockfile";
            default -> "npm install --omit=dev";
        };
    }

    private String nodeRunner(ProjectSnapshot snapshot) {
        return switch (snapshot.buildTool()) {
            case "pnpm" -> "pnpm";
            case "yarn" -> "yarn";
            default -> "npm";
        };
    }

    private String nodeStartCommand(ProjectSnapshot snapshot) {
        return switch (snapshot.buildTool()) {
            case "pnpm" -> "corepack enable && pnpm start";
            case "yarn" -> "corepack enable && yarn start";
            default -> "npm start";
        };
    }

    private boolean expectsApplicationConfig(ProjectSnapshot snapshot) {
        return snapshot.projectKind().startsWith("java-") || "java-source".equals(snapshot.projectKind());
    }

    private List<String> detectStaticEntries(Path projectPath) {
        try (Stream<Path> stream = Files.list(projectPath)) {
            return stream
                    .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)))
                    .map(path -> path.getFileName().toString())
                    .filter(name -> {
                        String normalized = name.toLowerCase(Locale.ROOT);
                        return normalized.endsWith(".html")
                                || normalized.endsWith(".htm")
                                || normalized.endsWith(".ico")
                                || normalized.endsWith(".txt")
                                || normalized.equals("favicon.ico")
                                || STATIC_DIR_NAMES.contains(normalized);
                    })
                    .toList();
        } catch (IOException ignored) {
            return List.of();
        }
    }

    private Set<String> listRootEntries(Path projectPath) {
        try (Stream<Path> stream = Files.list(projectPath)) {
            return stream
                    .map(path -> path.getFileName().toString().toLowerCase(Locale.ROOT))
                    .collect(Collectors.toSet());
        } catch (IOException ignored) {
            return Set.of();
        }
    }

    private List<String> listFileNames(Path projectPath, int depth) {
        try (Stream<Path> stream = Files.walk(projectPath, depth)) {
            return stream
                    .filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString().toLowerCase(Locale.ROOT))
                    .toList();
        } catch (IOException ignored) {
            return List.of();
        }
    }

    private String readText(Path file) {
        try {
            return Files.exists(file) ? Files.readString(file) : "";
        } catch (IOException ignored) {
            return "";
        }
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
                                   String language,
                                   String buildTool,
                                   String projectKind,
                                   boolean hasDockerfile,
                                   boolean hasApplicationConfig,
                                   boolean hasBuildDescriptor,
                                   int hostPort,
                                   int containerPort,
                                   List<String> staticEntries) {
    }
}
