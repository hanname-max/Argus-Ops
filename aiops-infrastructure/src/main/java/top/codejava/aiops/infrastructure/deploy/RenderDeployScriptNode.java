package top.codejava.aiops.infrastructure.deploy;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import top.codejava.aiops.type.exception.RemoteExecutionException;

import java.nio.file.Files;
import java.nio.file.Path;

@Component
@Order(50)
public class RenderDeployScriptNode implements DeployNode {

    @Override
    public void apply(DeployContext context) {
        Path projectRoot = Path.of(context.request().projectPath()).toAbsolutePath().normalize();
        boolean hasDockerfile = Files.isRegularFile(projectRoot.resolve("Dockerfile"));
        boolean looksStaticSite = Files.isRegularFile(projectRoot.resolve("index.html"));

        if (!hasDockerfile && !looksStaticSite) {
            throw new RemoteExecutionException(
                    "No Dockerfile found and the project does not look like a simple static site: " + projectRoot,
                    null
            );
        }

        String imageName = "argus-" + slugify(projectRoot.getFileName() == null ? "app" : projectRoot.getFileName().toString());
        String containerName = imageName;
        int hostPort = context.request().applicationPort() == null || context.request().applicationPort() <= 0
                ? 8080
                : context.request().applicationPort();

        String dockerfileBootstrap = hasDockerfile
                ? ""
                : """
                  cat > Dockerfile <<'EOF'
                  FROM nginx:1.27-alpine
                  COPY . /usr/share/nginx/html
                  EXPOSE 80
                  EOF
                  
                  """;

        String command = """
                set -euo pipefail
                cd %s
                %s
                docker build -t %s:latest .
                for container_id in $(docker ps -a --format '{{.ID}} {{.Names}}' | awk '$2 ~ /^%s(-|$)/ {print $1}'); do
                  docker rm -f "$container_id" >/dev/null 2>&1 || true
                done
                docker rm -f %s >/dev/null 2>&1 || true
                docker run -d --restart unless-stopped --name %s -p %d:80 %s:latest
                docker ps --filter name=%s
                """.formatted(
                context.remoteWorkspacePath(),
                dockerfileBootstrap,
                imageName,
                imageName,
                containerName,
                containerName,
                hostPort,
                imageName,
                containerName
        );

        context.renderedDeployCommand(command);
        context.progressMessages().add(hasDockerfile
                ? "Using Dockerfile from uploaded project."
                : "No Dockerfile found. Generated a default static-site Dockerfile.");
    }

    private String slugify(String value) {
        return value.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+", "")
                .replaceAll("-+$", "");
    }
}
