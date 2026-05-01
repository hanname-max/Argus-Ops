package top.codejava.aiops.infrastructure.deploy.script;

import org.springframework.stereotype.Component;
import top.codejava.aiops.application.dto.WorkflowModels;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class ProjectDeploymentRuleEngine {

    private static final String GENERATED_DOCKERFILE = "Dockerfile.aiops";

    /**
     * Resolves one deterministic deployment strategy from project markers plus workflow context.
     *
     * <p>The rule order matters: explicit Dockerfiles win first, then language/runtime heuristics,
     * then static-site nginx handling as the final structured fallback.
     */
    public DeploymentPlan resolveForProject(Path projectRoot, Integer requestedPort) {
        DeploymentDetectionContext context = new DeploymentDetectionContext(
                projectRoot.toAbsolutePath().normalize(),
                normalizePort(requestedPort)
        );
        return resolve(context);
    }

    public DeploymentPlan resolveForWorkflow(WorkflowModels.ScriptGenerationRequest request) {
        Path projectRoot = resolveProjectRoot(request);
        if (projectRoot != null && Files.isDirectory(projectRoot)) {
            Integer recommendedPort = request != null && request.metadata() != null
                    ? request.metadata().recommendedPort()
                    : null;
            return resolve(new DeploymentDetectionContext(
                    projectRoot,
                    normalizePort(recommendedPort),
                    request.localContext(),
                    request.dependencyProbeResults(),
                    request.dependencyDecisions(),
                    request.dependencyOverrides()
            ));
        }
        return fallbackForWorkflow(request);
    }

    public String renderExecutionScript(Path projectRoot, String remoteWorkspacePath, Integer requestedPort) {
        DeploymentPlan plan = resolveForProject(projectRoot, requestedPort);
        return renderShell(plan.scriptBody(), remoteWorkspacePath);
    }

    public String renderExecutionScript(WorkflowModels.ScriptGenerationRequest request, String remoteWorkspacePath) {
        DeploymentPlan plan = resolveForWorkflow(request);
        return renderShell(plan.scriptBody(), remoteWorkspacePath);
    }

    public String renderPreviewScript(WorkflowModels.ScriptGenerationRequest request) {
        DeploymentPlan plan = resolveForWorkflow(request);
        return renderShell(plan.scriptBody(), null);
    }

    private DeploymentPlan resolve(DeploymentDetectionContext context) {
        ProjectMarkerSnapshot markers = context.markers();

        if (markers.hasDockerfile() && !shouldPreferManagedDockerPlan(context)) {
            return customDockerfilePlan(context);
        }
        if (markers.hasPom()) {
            return javaMavenPlan(context);
        }
        if (markers.hasGradle()) {
            return javaGradlePlan(context);
        }
        if (markers.hasPackageJson()) {
            if (markers.nodeLooksNext()) {
                return nextNodePlan(context);
            }
            if (markers.nodeLooksFrontend()) {
                return nodeFrontendPlan(context);
            }
            if (markers.nodeLooksRuntime()) {
                return nodeRuntimePlan(context);
            }
        }
        if (markers.hasManagePy()) {
            return pythonDjangoPlan(context);
        }
        if (markers.hasPyproject() || markers.hasRequirementsTxt() || markers.hasAppPy() || markers.hasMainPy()) {
            return pythonAppPlan(context);
        }
        if (markers.hasIndexHtml() || markers.hasNestedStaticRoot() || markers.hasNginxConf()) {
            return staticSitePlan(context);
        }
        return unknownPlan(context);
    }

    private boolean shouldPreferManagedDockerPlan(DeploymentDetectionContext context) {
        if (context.localContext() == null || context.localContext().packaging() == null) {
            return false;
        }
        String packaging = context.localContext().packaging().trim().toLowerCase(Locale.ROOT);
        boolean isJarOrNginx = "jar".equals(packaging) || "nginx-static".equals(packaging);
        if (!isJarOrNginx) {
            return false;
        }
        String confirmedConfigChoice = context.localContext().confirmedConfigChoice();
        if (confirmedConfigChoice != null && "USE_LOCAL_DOCKERFILE".equalsIgnoreCase(confirmedConfigChoice)) {
            return false;
        }
        return true;
    }

    private DeploymentPlan customDockerfilePlan(DeploymentDetectionContext context) {
        int containerPort = context.markers().customDockerfilePortOrDefault(8080);
        String body = dockerLifecycleBlock(
                "custom-dockerfile",
                "cp Dockerfile " + GENERATED_DOCKERFILE,
                context.imageName(),
                context.hostPort(),
                containerPort,
                ""
        );
        return new DeploymentPlan("CUSTOM_DOCKERFILE", "Prefer the project's Dockerfile when present.", body);
    }

    private DeploymentPlan javaMavenPlan(DeploymentDetectionContext context) {
        int javaVersion = resolvedJavaImageVersion(context);
        String dockerfile = writeDockerfile(
                "FROM maven:3.9.9-eclipse-temurin-" + javaVersion + " AS builder",
                "WORKDIR /workspace",
                "COPY . .",
                "RUN mvn -DskipTests clean package && \\",
                "    JAR_FILE=$(find . -type f -path '*/target/*.jar' ! -name '*-sources.jar' ! -name '*-javadoc.jar' ! -name '*.original' -printf '%s|%p\\n' | sort -t'|' -nr | head -n 1 | cut -d'|' -f2-) && \\",
                "    test -n \"$JAR_FILE\" && \\",
                "    cp \"$JAR_FILE\" /tmp/app.jar",
                "",
                "FROM eclipse-temurin:" + javaVersion + "-jre",
                "WORKDIR /app",
                "COPY --from=builder /tmp/app.jar /app/app.jar",
                "ENV APP_PORT=8080",
                "ENV JAVA_OPTS=",
                "EXPOSE 8080",
                "ENTRYPOINT [\"sh\",\"-c\",\"java $JAVA_OPTS -Dserver.port=${APP_PORT:-8080} ${SPRING_PROFILES_ACTIVE:+-Dspring.profiles.active=${SPRING_PROFILES_ACTIVE}} -jar /app/app.jar\"]"
        );
        String body = javaLifecycleBlock(
                "java-maven",
                dockerfile,
                context.imageName(),
                context.hostPort(),
                8080,
                combineEnvironmentArgs("-e APP_PORT=8080", javaRuntimeEnvironmentArgs(context))
        );
        body = prependScriptBlock(buildDependencySetupBlock(context), body);
        return new DeploymentPlan("JAVA_MAVEN", "Detected a Maven Java project and deploy it as a containerized app.", body);
    }

    private DeploymentPlan javaGradlePlan(DeploymentDetectionContext context) {
        int javaVersion = resolvedJavaImageVersion(context);
        String dockerfile = writeDockerfile(
                "FROM gradle:8.10.2-jdk" + javaVersion + " AS builder",
                "WORKDIR /workspace",
                "COPY . .",
                "RUN if [ -x ./gradlew ]; then \\",
                      "      ./gradlew clean bootJar -x test || ./gradlew clean jar -x test; \\",
                    "    else \\",
                      "      gradle clean bootJar -x test || gradle clean jar -x test; \\",
                    "    fi && \\",
                    "    JAR_FILE=$(find . -type f -path '*/build/libs/*.jar' ! -name '*-sources.jar' ! -name '*-javadoc.jar' ! -name '*.original' -printf '%s|%p\\n' | sort -t'|' -nr | head -n 1 | cut -d'|' -f2-) && \\",
                    "    test -n \"$JAR_FILE\" && \\",
                    "    cp \"$JAR_FILE\" /tmp/app.jar",
                "",
                "FROM eclipse-temurin:" + javaVersion + "-jre",
                "WORKDIR /app",
                "COPY --from=builder /tmp/app.jar /app/app.jar",
                "ENV APP_PORT=8080",
                "ENV JAVA_OPTS=",
                "EXPOSE 8080",
                "ENTRYPOINT [\"sh\",\"-c\",\"java $JAVA_OPTS -Dserver.port=${APP_PORT:-8080} ${SPRING_PROFILES_ACTIVE:+-Dspring.profiles.active=${SPRING_PROFILES_ACTIVE}} -jar /app/app.jar\"]"
        );
        String body = javaLifecycleBlock(
                "java-gradle",
                dockerfile,
                context.imageName(),
                context.hostPort(),
                8080,
                combineEnvironmentArgs("-e APP_PORT=8080", javaRuntimeEnvironmentArgs(context))
        );
        body = prependScriptBlock(buildDependencySetupBlock(context), body);
        return new DeploymentPlan("JAVA_GRADLE", "Detected a Gradle Java project and deploy it as a containerized app.", body);
    }

    private DeploymentPlan nextNodePlan(DeploymentDetectionContext context) {
        String installCommand = context.markers().packageInstallCommand();
        String dockerfile = writeDockerfile(
                "FROM node:20-alpine AS builder",
                "WORKDIR /app",
                "COPY . .",
                "RUN " + installCommand + " && npm run build",
                "",
                "FROM node:20-alpine",
                "WORKDIR /app",
                "COPY --from=builder /app /app",
                "ENV PORT=3000",
                "EXPOSE 3000",
                "CMD [\"sh\",\"-c\",\"npm run start\"]"
        );
        String body = dockerLifecycleBlock(
                "node-next",
                dockerfile,
                context.imageName(),
                context.hostPort(),
                3000,
                "-e PORT=3000"
        );
        return new DeploymentPlan("NODE_NEXT", "Detected a Next.js project and deploy it with a Node runtime image.", body);
    }

    private DeploymentPlan nodeFrontendPlan(DeploymentDetectionContext context) {
        String installCommand = context.markers().packageInstallCommand();
        String dockerfile = writeDockerfile(
                "FROM node:20-alpine AS builder",
                "WORKDIR /app",
                "COPY . .",
                "RUN " + installCommand + " && npm run build && \\",
                "    if [ -d dist ]; then cp -r dist /tmp/web; \\",
                "    elif [ -d build ]; then cp -r build /tmp/web; \\",
                "    else echo \"No dist/ or build/ directory generated.\" >&2; exit 1; fi",
                "",
                "FROM nginx:1.27-alpine",
                "COPY --from=builder /tmp/web /usr/share/nginx/html",
                "EXPOSE 80"
        );
        String body = dockerLifecycleBlock(
                "node-frontend-static",
                dockerfile,
                context.imageName(),
                context.hostPort(),
                80,
                ""
        );
        return new DeploymentPlan("NODE_FRONTEND_STATIC", "Detected a frontend Node project and serve the build output with Nginx.", body);
    }

    private DeploymentPlan nodeRuntimePlan(DeploymentDetectionContext context) {
        String installCommand = context.markers().packageInstallCommand();
        String startCommand = context.markers().nodeStartCommand();
        String dockerfile = writeDockerfile(
                "FROM node:20-alpine",
                "WORKDIR /app",
                "COPY . .",
                "RUN " + installCommand,
                "ENV PORT=8080",
                "EXPOSE 8080",
                "CMD [\"sh\",\"-c\"," + shellQuoteForDockerJson(startCommand) + "]"
        );
        String body = dockerLifecycleBlock(
                "node-runtime",
                dockerfile,
                context.imageName(),
                context.hostPort(),
                8080,
                "-e PORT=8080"
        );
        return new DeploymentPlan("NODE_RUNTIME", "Detected a Node runtime project and start it directly inside the container.", body);
    }

    private DeploymentPlan pythonDjangoPlan(DeploymentDetectionContext context) {
        String dockerfile = writeDockerfile(
                "FROM python:3.11-slim",
                "WORKDIR /app",
                "COPY . .",
                "RUN pip install --no-cache-dir --upgrade pip && \\",
                "    if [ -f requirements.txt ]; then pip install --no-cache-dir -r requirements.txt; \\",
                "    elif [ -f pyproject.toml ]; then pip install --no-cache-dir .; \\",
                "    fi",
                "ENV APP_PORT=8080",
                "EXPOSE 8080",
                "CMD [\"sh\",\"-c\",\"python manage.py migrate && python manage.py runserver 0.0.0.0:${APP_PORT:-8080}\"]"
        );
        String body = dockerLifecycleBlock(
                "python-django",
                dockerfile,
                context.imageName(),
                context.hostPort(),
                8080,
                "-e APP_PORT=8080"
        );
        return new DeploymentPlan("PYTHON_DJANGO", "Detected a Django project and install dependencies before startup.", body);
    }

    private DeploymentPlan pythonAppPlan(DeploymentDetectionContext context) {
        String startupCommand = context.markers().pythonStartupCommand();
        String dockerfile = writeDockerfile(
                "FROM python:3.11-slim",
                "WORKDIR /app",
                "COPY . .",
                "RUN pip install --no-cache-dir --upgrade pip && \\",
                "    if [ -f requirements.txt ]; then pip install --no-cache-dir -r requirements.txt; \\",
                "    elif [ -f pyproject.toml ]; then pip install --no-cache-dir .; \\",
                "    fi",
                "ENV APP_PORT=8080",
                "EXPOSE 8080",
                "CMD [\"sh\",\"-c\"," + shellQuoteForDockerJson(startupCommand) + "]"
        );
        String body = dockerLifecycleBlock(
                "python-app",
                dockerfile,
                context.imageName(),
                context.hostPort(),
                8080,
                "-e APP_PORT=8080"
        );
        return new DeploymentPlan("PYTHON_APP", "Detected a Python project and run the inferred entrypoint inside the container.", body);
    }

    private DeploymentPlan staticSitePlan(DeploymentDetectionContext context) {
        ProjectMarkerSnapshot markers = context.markers();
        String staticRoot = markers.detectStaticSourceRoot();
        String nginxConfigSource = markers.detectNginxConfigSource();
        String confirmedConfigChoice = resolvedConfigChoice(context);
        boolean useCustomNginxConfig = nginxConfigSource != null
                && !"GENERATED_STATIC".equalsIgnoreCase(confirmedConfigChoice)
                && !"GENERATED_SPA".equalsIgnoreCase(confirmedConfigChoice);
        boolean useSpaConfig = "GENERATED_SPA".equalsIgnoreCase(confirmedConfigChoice)
                || (!useCustomNginxConfig && markers.hasNestedStaticRoot());
        String dockerfile = useCustomNginxConfig
                ? writeDockerfile(buildCustomNginxDockerfileLines(context, staticRoot, nginxConfigSource))
                : writeDockerfile(
                        "FROM nginx:1.27-alpine",
                        "ENV BACKEND_PORT=8080",
                        "COPY " + normalizeStaticCopyRoot(staticRoot) + " /usr/share/nginx/html",
                        "RUN mkdir -p /etc/nginx/templates && cat > /etc/nginx/templates/default.conf.template <<'EOF'",
                        "server {",
                        "    listen 80;",
                        "    server_name _;",
                        "    root /usr/share/nginx/html;",
                        "    index index.html index.htm;",
                        useSpaConfig ? "    location / { try_files $uri $uri/ /index.html; }" : "    location / { try_files $uri $uri/ =404; }",
                        markers.hasNginxConf()
                                ? "    location /api/ { proxy_pass http://host.docker.internal:${BACKEND_PORT}/admin/; proxy_http_version 1.1; proxy_set_header Host $host; proxy_set_header X-Real-IP $remote_addr; }"
                                : "    location /api/ { proxy_pass http://host.docker.internal:${BACKEND_PORT}/; proxy_http_version 1.1; proxy_set_header Host $host; proxy_set_header X-Real-IP $remote_addr; }",
                        "    error_page 500 502 503 504 /50x.html;",
                        "}",
                        "EOF",
                        "EXPOSE 80",
                        "CMD [\"sh\",\"-c\",\"envsubst '$BACKEND_PORT' < /etc/nginx/templates/default.conf.template > /etc/nginx/conf.d/default.conf && exec nginx -g 'daemon off;'\"]"
                );
        String body = nginxLifecycleBlock(
                "nginx-static",
                dockerfile,
                context.imageName(),
                context.hostPort(),
                80
        );
        return new DeploymentPlan("NGINX_STATIC", "Detected a pure static site and serve it with Nginx.", body);
    }

    private DeploymentPlan unknownPlan(DeploymentDetectionContext context) {
        String body = """
                echo "Unable to infer a supported deployment strategy from the current project markers."
                echo "Expected one of: Dockerfile / pom.xml / build.gradle(.kts) / package.json / requirements.txt / pyproject.toml / index.html"
                exit 1
                """;
        return new DeploymentPlan("UNKNOWN", "No supported deployment strategy could be inferred.", body);
    }

    private String dockerLifecycleBlock(String strategyKey,
                                        String dockerfileBootstrap,
                                        String imageName,
                                        int hostPort,
                                        int containerPort,
                                        String environmentArgs) {
        String containerName = imageName;
        String runArgs = environmentArgs == null || environmentArgs.isBlank()
                ? ""
                : environmentArgs.trim() + " ";
        String bootstrap = dockerfileBootstrap == null || dockerfileBootstrap.isBlank()
                ? ""
                : dockerfileBootstrap.trim() + "\n";

        return """
                echo "[AIOPS] strategy=%s"
                %sdocker build -f %s -t %s:latest .
                for container_id in $(docker ps -a --format '{{.ID}} {{.Names}}' | awk '$2 ~ /^%s(-|$)/ {print $1}'); do
                  docker rm -f "$container_id" >/dev/null 2>&1 || true
                done
                docker rm -f %s >/dev/null 2>&1 || true
                docker run -d --restart unless-stopped --name %s %s-p %d:%d %s:latest
                docker ps --filter name=%s
                """.formatted(
                strategyKey,
                bootstrap,
                GENERATED_DOCKERFILE,
                imageName,
                imageName,
                containerName,
                containerName,
                runArgs,
                hostPort,
                containerPort,
                imageName,
                containerName
        ).trim();
    }

    private String javaLifecycleBlock(String strategyKey,
                                      String dockerfileBootstrap,
                                      String imageName,
                                      int hostPort,
                                      int containerPort,
                                      String environmentArgs) {
        String containerName = imageName;
        String runArgs = environmentArgs == null || environmentArgs.isBlank()
                ? ""
                : environmentArgs.trim() + " ";
        String bootstrap = dockerfileBootstrap == null || dockerfileBootstrap.isBlank()
                ? ""
                : dockerfileBootstrap.trim() + "\n";

        return """
                echo "[AIOPS] strategy=%s"
                %sdocker build -f %s -t %s:latest .
                for container_id in $(docker ps -a --format '{{.ID}} {{.Names}}' | awk '$2 ~ /^%s(-|$)/ {print $1}'); do
                  docker rm -f "$container_id" >/dev/null 2>&1 || true
                done
                docker rm -f %s >/dev/null 2>&1 || true
                docker run -d --restart unless-stopped --add-host=host.docker.internal:host-gateway --name %s %s-p %d:%d %s:latest
                sleep 4
                docker ps --filter name=%s
                if ! docker ps --filter name=%s --format '{{.Names}}' | grep -q '^%s$'; then
                  docker logs --tail 120 %s || true
                  exit 1
                fi
                if command -v ss >/dev/null 2>&1; then
                  ss -lnt | grep ':%d' || true
                fi
                docker logs --tail 40 %s || true
                """.formatted(
                strategyKey,
                bootstrap,
                GENERATED_DOCKERFILE,
                imageName,
                imageName,
                containerName,
                containerName,
                runArgs,
                hostPort,
                containerPort,
                imageName,
                containerName,
                containerName,
                containerName,
                containerName,
                hostPort,
                containerName
        ).trim();
    }

    private String nginxLifecycleBlock(String strategyKey,
                                       String dockerfileBootstrap,
                                       String imageName,
                                       int hostPort,
                                       int containerPort) {
        String containerName = imageName;
        String bootstrap = dockerfileBootstrap == null || dockerfileBootstrap.isBlank()
                ? ""
                : dockerfileBootstrap.trim() + "\n";

        return """
                echo "[AIOPS] strategy=%s"
                %sdocker build -f %s -t %s:latest .
                for container_id in $(docker ps -a --format '{{.ID}} {{.Names}}' | awk '$2 ~ /^%s(-|$)/ {print $1}'); do
                  docker rm -f "$container_id" >/dev/null 2>&1 || true
                done
                docker rm -f %s >/dev/null 2>&1 || true
                if [ -z "${BACKEND_PORT:-}" ]; then
                  BACKEND_PORT="$(
                    docker ps --format '{{.Names}} {{.Ports}}' \
                      | awk -v current='%s' '$1 != current { print }' \
                      | grep -oE ':[0-9]+->' \
                      | sed -E 's/^:([0-9]+)->$/\\1/' \
                      | awk -v target='%d' '$1 >= 8090 && $1 < target { if ($1 > best) best = $1 } END { if (best > 0) print best }' \
                      || true
                  )"
                fi
                BACKEND_PORT="${BACKEND_PORT:-8080}"
                echo "[AIOPS] resolved backend port=${BACKEND_PORT}"
                docker run -d --restart unless-stopped --add-host=host.docker.internal:host-gateway --name %s -e BACKEND_PORT=${BACKEND_PORT} -p %d:%d %s:latest
                sleep 3
                docker ps --filter name=%s
                if command -v ss >/dev/null 2>&1; then
                  ss -lnt | grep ':%d' || true
                fi
                docker logs --tail 30 %s || true
                """.formatted(
                strategyKey,
                bootstrap,
                GENERATED_DOCKERFILE,
                imageName,
                imageName,
                containerName,
                containerName,
                hostPort,
                containerName,
                hostPort,
                containerPort,
                imageName,
                containerName,
                hostPort,
                containerName
        ).trim();
    }

    private String javaRuntimeEnvironmentArgs(DeploymentDetectionContext context) {
        if (context.localContext() == null || context.localContext().runtimeConfigItems() == null) {
            return "";
        }

        Map<String, String> environment = new LinkedHashMap<>();
        context.localContext().runtimeConfigItems().stream()
                .filter(item -> "JAVA".equalsIgnoreCase(item.family()))
                .filter(item -> "SPRING_ENV".equalsIgnoreCase(item.applyStrategy()))
                .filter(item -> item.key() != null && !item.key().isBlank())
                .filter(item -> item.valuePreview() != null && !item.valuePreview().isBlank())
                .filter(item -> !"server.port".equalsIgnoreCase(item.key()))
                .forEach(item -> environment.put(toSpringEnvironmentName(item.key()), item.valuePreview()));

        applyDependencyEnvironmentOverrides(context, environment);

        if (environment.isEmpty()) {
            return "";
        }

        List<String> args = new ArrayList<>();
        environment.forEach((key, value) -> args.add("-e " + shellQuote(key + "=" + value)));
        return String.join(" ", args);
    }

    private String combineEnvironmentArgs(String... args) {
        List<String> values = new ArrayList<>();
        for (String value : args) {
            if (value != null && !value.isBlank()) {
                values.add(value.trim());
            }
        }
        return String.join(" ", values);
    }

    private int resolvedJavaImageVersion(DeploymentDetectionContext context) {
        if (context.localContext() == null || context.localContext().detectedJdkVersion() == null) {
            return 17;
        }
        String raw = context.localContext().detectedJdkVersion().trim();
        String digits = raw.replaceAll("[^0-9.]", "");
        if (digits.isBlank()) {
            return 17;
        }
        String majorToken = digits;
        int dotIndex = digits.indexOf('.');
        if (dotIndex >= 0) {
            majorToken = digits.substring(0, dotIndex);
        }
        try {
            int major = Integer.parseInt(majorToken);
            if (major >= 21) {
                return 21;
            }
        } catch (NumberFormatException ignored) {
            return 17;
        }
        return 17;
    }

    private void applyDependencyEnvironmentOverrides(DeploymentDetectionContext context, Map<String, String> environment) {
        List<WorkflowModels.DependencyRequirement> effectiveRequirements = context.effectiveDependencyRequirements();
        if (effectiveRequirements == null || effectiveRequirements.isEmpty()) {
            return;
        }
        for (WorkflowModels.DependencyRequirement requirement : effectiveRequirements) {
            if (requirement == null || requirement.kind() == null) {
                continue;
            }

            WorkflowModels.DependencyDecision decision = dependencyDecisionFor(context, requirement.kind());
            WorkflowModels.DependencyOverride override = context.dependencyOverride(requirement.kind());

            boolean autoProvision = decision != null
                    && decision.mode() == WorkflowModels.DependencyDecisionMode.DEPLOY_AUTOMATICALLY;
            boolean reuseExisting = decision != null
                    && decision.mode() == WorkflowModels.DependencyDecisionMode.REUSE_EXISTING;

            boolean effectiveHostIsLocal = isLocalDependencyHost(requirement.host());
            boolean overrideHostIsLocal = override != null && override.host() != null
                    && isLocalDependencyHost(override.host());

            boolean routeThroughHostGateway = effectiveHostIsLocal
                    || autoProvision
                    || (reuseExisting && overrideHostIsLocal);

            if (!routeThroughHostGateway) {
                continue;
            }

            switch (requirement.kind()) {
                case MYSQL -> applyMysqlEnvironmentOverrides(context, environment, requirement, autoProvision, routeThroughHostGateway);
                case REDIS -> applyRedisEnvironmentOverrides(context, environment, requirement, autoProvision, routeThroughHostGateway);
            }
        }
    }

    private void applyMysqlEnvironmentOverrides(DeploymentDetectionContext context,
                                                Map<String, String> environment,
                                                WorkflowModels.DependencyRequirement requirement,
                                                boolean autoProvision,
                                                boolean routeThroughHostGateway) {
        int port = requirement.port() == null ? 3306 : requirement.port();
        String host = routeThroughHostGateway
                ? "host.docker.internal"
                : (requirement.host() != null ? requirement.host() : "localhost");
        String databaseName = resolvedMysqlDatabaseName(requirement, autoProvision);
        String username = resolvedMysqlUsername(context, autoProvision);
        String password = resolvedMysqlPassword(context, autoProvision);
        String jdbcUrl = resolvedMysqlJdbcUrl(context, host, port, databaseName);

        putEnvironment(environment, host, "sky.datasource.host", "spring.datasource.host");
        putEnvironment(environment, String.valueOf(port), "sky.datasource.port", "spring.datasource.port");
        putEnvironment(environment, databaseName, "sky.datasource.database", "spring.datasource.name", "spring.datasource.database");
        putEnvironment(environment, username,
                "sky.datasource.username",
                "spring.datasource.username",
                "spring.datasource.druid.username",
                "spring.datasource.hikari.username");
        putEnvironment(environment, password,
                "sky.datasource.password",
                "spring.datasource.password",
                "spring.datasource.druid.password",
                "spring.datasource.hikari.password");
        putEnvironment(environment, jdbcUrl,
                "spring.datasource.url",
                "spring.datasource.druid.url",
                "spring.datasource.hikari.jdbc-url",
                "spring.datasource.jdbc-url");
    }

    private void applyRedisEnvironmentOverrides(DeploymentDetectionContext context,
                                                Map<String, String> environment,
                                                WorkflowModels.DependencyRequirement requirement,
                                                boolean autoProvision,
                                                boolean routeThroughHostGateway) {
        int port = requirement.port() == null ? 6379 : requirement.port();
        String host = routeThroughHostGateway
                ? "host.docker.internal"
                : (requirement.host() != null ? requirement.host() : "localhost");
        String password = resolvedRedisPassword(context, autoProvision);
        String redisUrl = resolvedRedisUrl(context, host, port, password);

        putEnvironment(environment, host, "sky.redis.host", "spring.redis.host", "spring.data.redis.host");
        putEnvironment(environment, String.valueOf(port), "sky.redis.port", "spring.redis.port", "spring.data.redis.port");
        putEnvironment(environment, password, "sky.redis.password", "spring.redis.password", "spring.data.redis.password");
        putEnvironment(environment, redisUrl, "sky.redis.url", "spring.redis.url", "spring.data.redis.url");
    }

    private void putEnvironment(Map<String, String> environment, String value, String... keys) {
        if (value == null || keys == null) {
            return;
        }
        for (String key : keys) {
            if (key != null && !key.isBlank()) {
                environment.put(toSpringEnvironmentName(key), value);
            }
        }
    }

    private String resolvedMysqlUsername(DeploymentDetectionContext context, boolean autoProvision) {
        WorkflowModels.DependencyOverride override = context.dependencyOverride(WorkflowModels.DependencyKind.MYSQL);
        if (override != null && override.username() != null && !override.username().isBlank()) {
            return override.username();
        }
        String current = runtimeConfigValue(context,
                "sky.datasource.username",
                "spring.datasource.username",
                "spring.datasource.druid.username",
                "spring.datasource.hikari.username");
        if (current != null && !current.isBlank()) {
            return current;
        }
        return autoProvision ? "root" : current;
    }

    private String resolvedMysqlPassword(DeploymentDetectionContext context, boolean autoProvision) {
        WorkflowModels.DependencyOverride override = context.dependencyOverride(WorkflowModels.DependencyKind.MYSQL);
        if (override != null && override.password() != null) {
            return override.password();
        }
        String current = runtimeConfigValue(context,
                "sky.datasource.password",
                "spring.datasource.password",
                "spring.datasource.druid.password",
                "spring.datasource.hikari.password");
        if (current != null && !current.isBlank()) {
            return current;
        }
        return autoProvision ? "123456" : current;
    }

    private String resolvedMysqlDatabaseName(WorkflowModels.DependencyRequirement requirement, boolean autoProvision) {
        if (requirement != null && requirement.databaseName() != null && !requirement.databaseName().isBlank()) {
            return requirement.databaseName();
        }
        return autoProvision ? "app" : null;
    }

    private String resolvedMysqlJdbcUrl(DeploymentDetectionContext context,
                                        String host,
                                        int port,
                                        String databaseName) {
        String current = runtimeConfigValue(context,
                "spring.datasource.url",
                "spring.datasource.druid.url",
                "spring.datasource.hikari.jdbc-url",
                "spring.datasource.jdbc-url");
        if (current != null && !current.isBlank()) {
            return rewriteMysqlJdbcUrl(current, host, port, databaseName);
        }
        if (databaseName == null || databaseName.isBlank()) {
            return null;
        }
        return "jdbc:mysql://" + host + ":" + port + "/" + databaseName;
    }

    private String rewriteMysqlJdbcUrl(String jdbcUrl, String host, int port, String databaseName) {
        JdbcEndpoint endpoint = parseJdbcEndpoint(jdbcUrl);
        String resolvedDatabase = (databaseName == null || databaseName.isBlank())
                ? endpoint.databaseName()
                : databaseName;
        String querySuffix = "";
        int queryIndex = jdbcUrl.indexOf('?');
        if (queryIndex >= 0) {
            querySuffix = jdbcUrl.substring(queryIndex);
        }
        if (resolvedDatabase == null || resolvedDatabase.isBlank()) {
            return "jdbc:mysql://" + host + ":" + port + querySuffix;
        }
        return "jdbc:mysql://" + host + ":" + port + "/" + resolvedDatabase + querySuffix;
    }

    private String resolvedRedisPassword(DeploymentDetectionContext context, boolean autoProvision) {
        WorkflowModels.DependencyOverride override = context.dependencyOverride(WorkflowModels.DependencyKind.REDIS);
        if (override != null && override.password() != null) {
            return override.password();
        }
        String current = runtimeConfigValue(context,
                "sky.redis.password",
                "spring.redis.password",
                "spring.data.redis.password");
        if (current != null && !current.isBlank()) {
            return current;
        }
        return autoProvision ? "" : current;
    }

    private String resolvedRedisUrl(DeploymentDetectionContext context,
                                    String host,
                                    int port,
                                    String password) {
        String current = runtimeConfigValue(context,
                "sky.redis.url",
                "spring.redis.url",
                "spring.data.redis.url");
        if (current != null && !current.isBlank()) {
            return rewriteRedisUrl(current, host, port, password);
        }
        StringBuilder builder = new StringBuilder("redis://");
        if (password != null && !password.isBlank()) {
            builder.append(':').append(password).append('@');
        }
        builder.append(host).append(':').append(port);
        return builder.toString();
    }

    private String rewriteRedisUrl(String redisUrl, String host, int port, String password) {
        try {
            String normalized = redisUrl.contains("://") ? redisUrl : "redis://" + redisUrl;
            URI uri = URI.create(normalized);
            String scheme = uri.getScheme() == null || uri.getScheme().isBlank() ? "redis" : uri.getScheme();
            String path = uri.getRawPath() == null ? "" : uri.getRawPath();
            String query = uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery();
            StringBuilder builder = new StringBuilder();
            builder.append(scheme).append("://");
            if (password != null && !password.isBlank()) {
                builder.append(':').append(password).append('@');
            }
            builder.append(host).append(':').append(port).append(path).append(query);
            return builder.toString();
        } catch (IllegalArgumentException ignored) {
            StringBuilder builder = new StringBuilder("redis://");
            if (password != null && !password.isBlank()) {
                builder.append(':').append(password).append('@');
            }
            builder.append(host).append(':').append(port);
            return builder.toString();
        }
    }

    private boolean isLocalDependencyHost(String host) {
        if (host == null || host.isBlank()) {
            return false;
        }
        String normalized = host.trim().toLowerCase(Locale.ROOT);
        return "localhost".equals(normalized) || "127.0.0.1".equals(normalized) || "::1".equals(normalized);
    }

    private String toSpringEnvironmentName(String key) {
        return key.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_");
    }

    private String resolvedConfigChoice(DeploymentDetectionContext context) {
        if (context.localContext() != null && context.localContext().confirmedConfigChoice() != null
                && !context.localContext().confirmedConfigChoice().isBlank()) {
            return context.localContext().confirmedConfigChoice();
        }
        if (context.localContext() != null && context.localContext().deploymentHints() != null) {
            return context.localContext().deploymentHints().recommendedConfigChoice();
        }
        return null;
    }

    private String buildDependencySetupBlock(DeploymentDetectionContext context) {
        List<WorkflowModels.DependencyRequirement> effectiveRequirements = context.effectiveDependencyRequirements();
        if (effectiveRequirements == null || effectiveRequirements.isEmpty()) {
            return "";
        }

        List<String> lines = new ArrayList<>();
        for (WorkflowModels.DependencyRequirement requirement : effectiveRequirements) {
            WorkflowModels.DependencyDecision decision = dependencyDecisionFor(context, requirement.kind());
            WorkflowModels.DependencyProbeResult probeResult = dependencyProbeResultFor(context, requirement.kind());
            if (decision == null || decision.mode() == null) {
                continue;
            }
            switch (decision.mode()) {
                case DEPLOY_AUTOMATICALLY -> lines.add(buildAutoDependencyProvision(context, requirement, probeResult));
                case MANUAL_PREPARE -> lines.add("echo '[AIOPS] dependency " + requirement.kind() + " should be prepared manually before application verification.'");
                case CONTINUE_ANYWAY -> lines.add("echo '[AIOPS] continue deployment without provisioning " + requirement.kind() + ".'");
                case REUSE_EXISTING -> lines.add("echo '[AIOPS] reusing existing " + requirement.kind() + " dependency on target host.'");
            }
        }
        return String.join("\n", lines);
    }

    private String buildAutoDependencyProvision(DeploymentDetectionContext context,
                                                WorkflowModels.DependencyRequirement requirement,
                                                WorkflowModels.DependencyProbeResult probeResult) {
        return switch (requirement.kind()) {
            case MYSQL -> mysqlProvisionBlock(context, requirement, probeResult);
            case REDIS -> redisProvisionBlock(context, requirement, probeResult);
        };
    }

    private String mysqlProvisionBlock(DeploymentDetectionContext context,
                                       WorkflowModels.DependencyRequirement requirement,
                                       WorkflowModels.DependencyProbeResult probeResult) {
        String username = resolvedMysqlUsername(context, true);
        String password = resolvedMysqlPassword(context, true);
        String databaseName = resolvedMysqlDatabaseName(requirement, true);
        int hostPort = requirement.port() == null ? 3306 : requirement.port();
        StringBuilder builder = new StringBuilder();
        builder.append("MYSQL_ROOT_PASSWORD=").append(shellQuote(password)).append("\n");
        builder.append("MYSQL_APP_USER=").append(shellQuote(username)).append("\n");
        builder.append("MYSQL_APP_PASSWORD=").append(shellQuote(password)).append("\n");
        builder.append("MYSQL_APP_DATABASE=").append(shellQuote(databaseName)).append("\n");
        builder.append("echo '[AIOPS] ensure mysql dependency'\n");
        builder.append("docker rm -f argus-dependency-mysql >/dev/null 2>&1 || true\n");
        builder.append("docker volume rm -f argus-dependency-mysql-data >/dev/null 2>&1 || true\n");
        builder.append("docker volume create argus-dependency-mysql-data >/dev/null 2>&1 || true\n");
        builder.append("docker run -d --restart unless-stopped --name argus-dependency-mysql ");
        builder.append("-p ").append(hostPort).append(":3306 ");
        builder.append("-e MYSQL_ROOT_PASSWORD=\"$MYSQL_ROOT_PASSWORD\" ");
        builder.append("-e MYSQL_DATABASE=\"$MYSQL_APP_DATABASE\" ");
        if (!"root".equalsIgnoreCase(username)) {
            builder.append("-e MYSQL_USER=\"$MYSQL_APP_USER\" ");
            builder.append("-e MYSQL_PASSWORD=\"$MYSQL_APP_PASSWORD\" ");
        }
        builder.append("-v argus-dependency-mysql-data:/var/lib/mysql ");
        builder.append("mysql:8.0\n");
        builder.append("MYSQL_READY=0\n");
        builder.append("for attempt in $(seq 1 24); do\n");
        builder.append("  if docker exec argus-dependency-mysql mysqladmin ping -h 127.0.0.1 -uroot -p\"$MYSQL_ROOT_PASSWORD\" --silent >/dev/null 2>&1; then\n");
        builder.append("    MYSQL_READY=1\n");
        builder.append("    break\n");
        builder.append("  fi\n");
        builder.append("  sleep 5\n");
        builder.append("done\n");
        builder.append("if [ \"$MYSQL_READY\" != \"1\" ]; then\n");
        builder.append("  docker logs --tail 80 argus-dependency-mysql || true\n");
        builder.append("  echo '[AIOPS] mysql dependency did not become ready in time.' >&2\n");
        builder.append("  exit 1\n");
        builder.append("fi\n");
        builder.append("docker logs --tail 20 argus-dependency-mysql || true");
        return builder.toString();
    }

    private String redisProvisionBlock(DeploymentDetectionContext context,
                                       WorkflowModels.DependencyRequirement requirement,
                                       WorkflowModels.DependencyProbeResult probeResult) {
        String password = resolvedRedisPassword(context, true);
        if (password == null) {
            password = "";
        }
        int hostPort = requirement.port() == null ? 6379 : requirement.port();
        StringBuilder builder = new StringBuilder();
        builder.append("REDIS_PASSWORD=").append(shellQuote(password)).append("\n");
        builder.append("echo '[AIOPS] ensure redis dependency'\n");
        builder.append("docker rm -f argus-dependency-redis >/dev/null 2>&1 || true\n");
        builder.append("docker run -d --restart unless-stopped --name argus-dependency-redis ");
        builder.append("-p ").append(hostPort).append(":6379 ");
        if (password != null && !password.isBlank()) {
            builder.append("redis:7-alpine redis-server --appendonly yes --requirepass \"$REDIS_PASSWORD\"");
        } else {
            builder.append("redis:7-alpine redis-server --appendonly yes");
        }
        builder.append("\n");
        builder.append("REDIS_READY=0\n");
        builder.append("for attempt in $(seq 1 20); do\n");
        builder.append("  if [ -n \"$REDIS_PASSWORD\" ]; then\n");
        builder.append("    if docker exec argus-dependency-redis redis-cli -a \"$REDIS_PASSWORD\" ping 2>/dev/null | grep -q '^PONG$'; then\n");
        builder.append("      REDIS_READY=1\n");
        builder.append("      break\n");
        builder.append("    fi\n");
        builder.append("  elif docker exec argus-dependency-redis redis-cli ping 2>/dev/null | grep -q '^PONG$'; then\n");
        builder.append("    REDIS_READY=1\n");
        builder.append("    break\n");
        builder.append("  fi\n");
        builder.append("  sleep 3\n");
        builder.append("done\n");
        builder.append("if [ \"$REDIS_READY\" != \"1\" ]; then\n");
        builder.append("  docker logs --tail 80 argus-dependency-redis || true\n");
        builder.append("  echo '[AIOPS] redis dependency did not become ready in time.' >&2\n");
        builder.append("  exit 1\n");
        builder.append("fi\n");
        builder.append("docker logs --tail 20 argus-dependency-redis || true");
        return builder.toString();
    }

    private WorkflowModels.DependencyDecision dependencyDecisionFor(DeploymentDetectionContext context,
                                                                   WorkflowModels.DependencyKind kind) {
        return context.dependencyDecisions().stream()
                .filter(decision -> decision != null && decision.kind() == kind)
                .findFirst()
                .orElse(null);
    }

    private WorkflowModels.DependencyProbeResult dependencyProbeResultFor(DeploymentDetectionContext context,
                                                                         WorkflowModels.DependencyKind kind) {
        return context.dependencyProbeResults().stream()
                .filter(result -> result != null && result.kind() == kind)
                .findFirst()
                .orElse(null);
    }

    private String runtimeConfigValue(DeploymentDetectionContext context, String... keys) {
        if (context.localContext() == null || context.localContext().runtimeConfigItems() == null) {
            return null;
        }
        for (String key : keys) {
            for (WorkflowModels.RuntimeConfigItem item : context.localContext().runtimeConfigItems()) {
                if (item != null && key.equals(item.key()) && item.valuePreview() != null && !item.valuePreview().isBlank()) {
                    return item.valuePreview();
                }
            }
        }
        return null;
    }

    private String prependScriptBlock(String prelude, String body) {
        if (prelude == null || prelude.isBlank()) {
            return body;
        }
        if (body == null || body.isBlank()) {
            return prelude;
        }
        return prelude.trim() + "\n" + body.trim();
    }

    private List<String> buildCustomNginxDockerfileLines(DeploymentDetectionContext context,
                                                         String staticRoot,
                                                         String nginxConfigSource) {
        String renderedConfig = renderResolvedNginxConfig(context, staticRoot, nginxConfigSource);
        List<String> lines = new ArrayList<>();
        lines.add("FROM nginx:1.27-alpine");
        lines.add("ENV BACKEND_PORT=8080");
        lines.add("COPY " + normalizeStaticCopyRoot(staticRoot) + " /usr/share/nginx/html");
        lines.add("RUN mkdir -p /etc/nginx/templates && cat > /etc/nginx/templates/nginx.conf.template <<'EOF'");
        lines.addAll(renderedConfig.lines().toList());
        lines.add("EOF");
        lines.add("EXPOSE 80");
        lines.add("CMD [\"sh\",\"-c\",\"envsubst '$BACKEND_PORT' < /etc/nginx/templates/nginx.conf.template > /etc/nginx/nginx.conf && exec nginx -g 'daemon off;'\"]");
        return lines;
    }

    private String renderResolvedNginxConfig(DeploymentDetectionContext context,
                                             String staticRoot,
                                             String nginxConfigSource) {
        Path configPath = context.projectRoot().resolve(nginxConfigSource).normalize();
        List<String> lines;
        try {
            lines = new ArrayList<>(Files.readAllLines(configPath));
        } catch (IOException ignored) {
            return """
                    worker_processes  1;
                    events { worker_connections 1024; }
                    http {
                        include       mime.types;
                        default_type  application/octet-stream;
                        sendfile        on;
                        keepalive_timeout  65;
                        server {
                            listen 80;
                            server_name _;
                            root /usr/share/nginx/html;
                            index index.html index.htm;
                            location / {
                                try_files $uri $uri/ /index.html;
                            }
                        }
                    }
                    """.trim();
        }

        if (context.localContext() != null && context.localContext().runtimeConfigItems() != null) {
            context.localContext().runtimeConfigItems().stream()
                    .filter(item -> "NGINX".equalsIgnoreCase(item.family()))
                    .filter(item -> item.sourceFile() != null && item.sourceFile().replace('\\', '/').equals(nginxConfigSource.replace('\\', '/')))
                    .filter(item -> item.sourceLine() != null && item.sourceLine() > 0 && item.sourceLine() <= lines.size())
                    .filter(item -> item.valuePreview() != null && !item.valuePreview().isBlank())
                    .sorted((left, right) -> Integer.compare(left.sourceLine(), right.sourceLine()))
                    .forEach(item -> {
                        int lineIndex = item.sourceLine() - 1;
                        lines.set(lineIndex, rewriteNginxDirectiveLine(lines.get(lineIndex), item.valuePreview()));
                    });
        }

        rewriteLocalNginxBackends(lines);
        rewriteNginxListen(lines);
        rewriteNginxRoots(lines, staticRoot);
        return String.join("\n", lines);
    }

    private String rewriteNginxDirectiveLine(String originalLine, String newValue) {
        return originalLine.replaceFirst("^(\\s*[a-zA-Z_]+\\s+).+?(\\s*;.*)$", "$1" + java.util.regex.Matcher.quoteReplacement(newValue) + "$2");
    }

    private void rewriteLocalNginxBackends(List<String> lines) {
        String backendReplacement = java.util.regex.Matcher.quoteReplacement("host.docker.internal:${BACKEND_PORT}");
        for (int index = 0; index < lines.size(); index++) {
            String rewritten = lines.get(index);
            // Custom nginx configs often point to localhost because they were authored on a VM or
            // bare host. Inside Docker those backends must route through host.docker.internal.
            rewritten = rewritten.replaceAll(
                    "(?i)(proxy_pass\\s+https?://)(localhost|127\\.0\\.0\\.1|0\\.0\\.0\\.0|\\[::1]|::1)(?::\\d+)?",
                    "$1" + backendReplacement
            );
            rewritten = rewritten.replaceAll(
                    "(?i)(server\\s+)(localhost|127\\.0\\.0\\.1|0\\.0\\.0\\.0|\\[::1]|::1)(?::\\d+)?",
                    "$1" + backendReplacement
            );
            lines.set(index, rewritten);
        }
    }

    private void rewriteNginxRoots(List<String> lines, String staticRoot) {
        if (staticRoot == null || staticRoot.isBlank()) {
            return;
        }
        String normalizedStaticRoot = staticRoot.replace('\\', '/').replaceAll("/+$", "");
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            java.util.regex.Matcher matcher = java.util.regex.Pattern
                    .compile("^(\\s*root\\s+)([^;]+)(\\s*;.*)$", java.util.regex.Pattern.CASE_INSENSITIVE)
                    .matcher(line);
            if (!matcher.matches()) {
                continue;
            }
            String configuredRoot = matcher.group(2).trim().replace('\\', '/');
            // The copied static assets always end up in /usr/share/nginx/html inside the image.
            if ("html".equalsIgnoreCase(configuredRoot)
                    || normalizedStaticRoot.equalsIgnoreCase(configuredRoot)
                    || configuredRoot.endsWith("/" + normalizedStaticRoot)) {
                lines.set(index, matcher.group(1)
                        + java.util.regex.Matcher.quoteReplacement("/usr/share/nginx/html")
                        + matcher.group(3));
            }
        }
    }

    private void rewriteNginxListen(List<String> lines) {
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            java.util.regex.Matcher matcher = java.util.regex.Pattern
                    .compile("^(\\s*listen\\s+)([^;]+)(\\s*;.*)$", java.util.regex.Pattern.CASE_INSENSITIVE)
                    .matcher(line);
            if (!matcher.matches()) {
                continue;
            }
            String value = matcher.group(2).trim();
            if (value.isBlank()) {
                continue;
            }
            String[] segments = value.split("\\s+", 2);
            String rewrittenValue = segments.length > 1 ? "80 " + segments[1] : "80";
            lines.set(index, matcher.group(1)
                    + java.util.regex.Matcher.quoteReplacement(rewrittenValue)
                    + matcher.group(3));
        }
    }

    private JdbcEndpoint parseJdbcEndpoint(String jdbcUrl) {
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            return new JdbcEndpoint(null, null, null);
        }
        String trimmed = jdbcUrl.trim();
        int prefixIndex = trimmed.indexOf("://");
        if (prefixIndex < 0) {
            return new JdbcEndpoint(null, null, null);
        }
        String target = trimmed.substring(prefixIndex + 3);
        int slashIndex = target.indexOf('/');
        if (slashIndex < 0) {
            return new JdbcEndpoint(null, null, null);
        }
        String hostPort = target.substring(0, slashIndex);
        String databasePart = target.substring(slashIndex + 1);
        int queryIndex = databasePart.indexOf('?');
        String databaseName = queryIndex >= 0 ? databasePart.substring(0, queryIndex) : databasePart;
        int colonIndex = hostPort.lastIndexOf(':');
        if (colonIndex < 0) {
            return new JdbcEndpoint(hostPort, 3306, databaseName);
        }
        Integer port;
        try {
            port = Integer.parseInt(hostPort.substring(colonIndex + 1));
        } catch (NumberFormatException ignored) {
            port = 3306;
        }
        return new JdbcEndpoint(hostPort.substring(0, colonIndex), port, databaseName);
    }

    private String renderShell(String scriptBody, String workingDirectory) {
        String cdBlock = workingDirectory == null || workingDirectory.isBlank()
                ? ""
                : "cd " + shellQuote(workingDirectory) + "\n";
        return """
                #!/usr/bin/env bash
                set -euo pipefail
                %s%s
                """.formatted(cdBlock, scriptBody.trim()).trim();
    }

    private Path resolveProjectRoot(WorkflowModels.ScriptGenerationRequest request) {
        if (request == null || request.localContext() == null || request.localContext().configEvidences() == null) {
            return null;
        }
        return request.localContext().configEvidences().stream()
                .filter(evidence -> "projectPath".equalsIgnoreCase(evidence.key()))
                .map(WorkflowModels.ConfigEvidence::valuePreview)
                .filter(value -> value != null && !value.isBlank())
                .map(Path::of)
                .map(Path::toAbsolutePath)
                .map(Path::normalize)
                .findFirst()
                .orElse(null);
    }

    private DeploymentPlan fallbackForWorkflow(WorkflowModels.ScriptGenerationRequest request) {
        String port = request != null && request.metadata() != null && request.metadata().recommendedPort() != null
                ? String.valueOf(request.metadata().recommendedPort())
                : "8080";
        String body = """
                echo "[AIOPS] strategy=workflow-fallback"
                echo "Unable to resolve the local project path from workflow context."
                echo "Recommended port: %s"
                exit 1
                """.formatted(port);
        return new DeploymentPlan("WORKFLOW_FALLBACK", "Could not restore the project path from workflow context.", body);
    }

    private int normalizePort(Integer port) {
        return port == null || port <= 0 ? 8080 : port;
    }

    private String writeDockerfile(List<String> lines) {
        return writeDockerfile(lines.toArray(String[]::new));
    }

    private String writeDockerfile(String... lines) {
        StringBuilder builder = new StringBuilder(": > " + GENERATED_DOCKERFILE);
        for (String line : lines) {
            builder.append("\n")
                    .append("printf '%s\\n' ")
                    .append(shellQuote(line))
                    .append(" >> ")
                    .append(GENERATED_DOCKERFILE);
        }
        return builder.toString();
    }

    private String shellQuoteForDockerJson(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private String shellQuote(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

    private String normalizeStaticCopyRoot(String path) {
        if (path == null || path.isBlank() || ".".equals(path)) {
            return ".";
        }
        return path.endsWith("/") ? path : path + "/";
    }

    private record JdbcEndpoint(
            String host,
            Integer port,
            String databaseName
    ) {
    }
}
