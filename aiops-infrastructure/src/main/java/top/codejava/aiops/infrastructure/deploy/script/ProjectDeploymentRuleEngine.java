package top.codejava.aiops.infrastructure.deploy.script;

import org.springframework.stereotype.Component;
import top.codejava.aiops.application.dto.WorkflowModels;

import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class ProjectDeploymentRuleEngine {

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
            return resolveForProject(projectRoot, recommendedPort);
        }
        return fallbackForWorkflow(request);
    }

    public String renderExecutionScript(Path projectRoot, String remoteWorkspacePath, Integer requestedPort) {
        DeploymentPlan plan = resolveForProject(projectRoot, requestedPort);
        return renderShell(plan.scriptBody(), remoteWorkspacePath);
    }

    public String renderPreviewScript(WorkflowModels.ScriptGenerationRequest request) {
        DeploymentPlan plan = resolveForWorkflow(request);
        return renderShell(plan.scriptBody(), null);
    }

    private DeploymentPlan resolve(DeploymentDetectionContext context) {
        ProjectMarkerSnapshot markers = context.markers();

        if (markers.hasDockerfile()) {
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
        if (markers.hasIndexHtml()) {
            return staticSitePlan(context);
        }
        return unknownPlan(context);
    }

    private DeploymentPlan customDockerfilePlan(DeploymentDetectionContext context) {
        int containerPort = context.markers().customDockerfilePortOrDefault(8080);
        String body = dockerLifecycleBlock(
                "custom-dockerfile",
                "",
                context.imageName(),
                context.hostPort(),
                containerPort,
                ""
        );
        return new DeploymentPlan("CUSTOM_DOCKERFILE", "Prefer the project's Dockerfile when present.", body);
    }

    private DeploymentPlan javaMavenPlan(DeploymentDetectionContext context) {
        String dockerfile = writeDockerfile(
                "FROM maven:3.9.9-eclipse-temurin-21 AS builder",
                "WORKDIR /workspace",
                "COPY . .",
                "RUN mvn -DskipTests clean package && \\",
                "    JAR_FILE=$(find target -maxdepth 1 -name '*.jar' | head -n 1) && \\",
                "    test -n \"$JAR_FILE\" && \\",
                "    cp \"$JAR_FILE\" /tmp/app.jar",
                "",
                "FROM eclipse-temurin:21-jre",
                "WORKDIR /app",
                "COPY --from=builder /tmp/app.jar /app/app.jar",
                "ENV APP_PORT=8080",
                "EXPOSE 8080",
                "ENTRYPOINT [\"sh\",\"-c\",\"java -Dserver.port=${APP_PORT:-8080} -jar /app/app.jar\"]"
        );
        String body = dockerLifecycleBlock(
                "java-maven",
                dockerfile,
                context.imageName(),
                context.hostPort(),
                8080,
                "-e APP_PORT=8080"
        );
        return new DeploymentPlan("JAVA_MAVEN", "Detected a Maven Java project and deploy it as a containerized app.", body);
    }

    private DeploymentPlan javaGradlePlan(DeploymentDetectionContext context) {
        String dockerfile = writeDockerfile(
                "FROM gradle:8.10.2-jdk21 AS builder",
                "WORKDIR /workspace",
                "COPY . .",
                "RUN if [ -x ./gradlew ]; then \\",
                "      ./gradlew clean bootJar -x test || ./gradlew clean jar -x test; \\",
                "    else \\",
                "      gradle clean bootJar -x test || gradle clean jar -x test; \\",
                "    fi && \\",
                "    JAR_FILE=$(find build/libs -maxdepth 1 -name '*.jar' | head -n 1) && \\",
                "    test -n \"$JAR_FILE\" && \\",
                "    cp \"$JAR_FILE\" /tmp/app.jar",
                "",
                "FROM eclipse-temurin:21-jre",
                "WORKDIR /app",
                "COPY --from=builder /tmp/app.jar /app/app.jar",
                "ENV APP_PORT=8080",
                "EXPOSE 8080",
                "ENTRYPOINT [\"sh\",\"-c\",\"java -Dserver.port=${APP_PORT:-8080} -jar /app/app.jar\"]"
        );
        String body = dockerLifecycleBlock(
                "java-gradle",
                dockerfile,
                context.imageName(),
                context.hostPort(),
                8080,
                "-e APP_PORT=8080"
        );
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
        String dockerfile = writeDockerfile(
                "FROM nginx:1.27-alpine",
                "COPY . /usr/share/nginx/html",
                "EXPOSE 80"
        );
        String body = dockerLifecycleBlock(
                "nginx-static",
                dockerfile,
                context.imageName(),
                context.hostPort(),
                80,
                ""
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
                %sdocker build -t %s:latest .
                for container_id in $(docker ps -a --format '{{.ID}} {{.Names}}' | awk '$2 ~ /^%s(-|$)/ {print $1}'); do
                  docker rm -f "$container_id" >/dev/null 2>&1 || true
                done
                docker rm -f %s >/dev/null 2>&1 || true
                docker run -d --restart unless-stopped --name %s %s-p %d:%d %s:latest
                docker ps --filter name=%s
                """.formatted(
                strategyKey,
                bootstrap,
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

    private String writeDockerfile(String... lines) {
        StringBuilder builder = new StringBuilder(": > Dockerfile");
        for (String line : lines) {
            builder.append("\n")
                    .append("printf '%s\\n' ")
                    .append(shellQuote(line))
                    .append(" >> Dockerfile");
        }
        return builder.toString();
    }

    private String shellQuoteForDockerJson(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private String shellQuote(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }
}
