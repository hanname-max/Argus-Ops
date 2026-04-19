package top.codejava.aiops.infrastructure.workflow;

import java.time.Duration;
import java.util.Locale;
import java.util.Objects;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import top.codejava.aiops.application.dto.WorkflowModels;
import top.codejava.aiops.application.port.WorkflowScriptGenerationPort;

@Component
public class SpringAiWorkflowScriptGenerationAdapter implements WorkflowScriptGenerationPort {

    private final ChatClient localChatClient;
    private final String apiKey;
    private final String model;

    public SpringAiWorkflowScriptGenerationAdapter(@Qualifier("localChatClient") ChatClient localChatClient,
                                                   @Value("${aiops.local.api-key:}") String apiKey,
                                                   @Value("${aiops.local.model:gpt-4o-mini}") String model) {
        this.localChatClient = localChatClient;
        this.apiKey = apiKey;
        this.model = model;
    }

    @Override
    public Flux<String> streamScript(WorkflowModels.ScriptGenerationRequest request) {
        String fallbackScript = buildFallbackScript(request);
        if (!isAiConfigured()) {
            return chunkText(fallbackScript);
        }

        String systemPrompt = """
                You are an elite deployment engineer.
                Generate a defensive startup script only.
                Prefer explicit checks, idempotent commands, and clear failure messages.
                Output shell script chunks only, no markdown fences.
                """;
        String userPrompt = """
                Workflow ID: %s
                Project name: %s
                Primary language: %s
                Framework: %s
                Packaging: %s
                Target OS: %s
                Recommended port: %s
                Shell: %s
                Generate a defensive deployment startup script that uses the recommended port.
                """.formatted(
                request.workflowId(),
                request.localContext().projectName(),
                request.localContext().primaryLanguage(),
                request.localContext().primaryFramework(),
                request.localContext().packaging(),
                request.metadata().targetOs(),
                request.metadata().recommendedPort(),
                request.metadata().shell()
        );

        return localChatClient.prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .stream()
                .content()
                .timeout(Duration.ofSeconds(10))
                .filter(chunk -> chunk != null && !chunk.isBlank())
                .onErrorResume(ex -> chunkText(fallbackScript));
    }

    private String buildFallbackScript(WorkflowModels.ScriptGenerationRequest request) {
        String projectName = request.localContext().projectName().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+|-+$)", "");
        String port = String.valueOf(request.metadata().recommendedPort());
        String packaging = request.localContext().packaging() == null ? "" : request.localContext().packaging().toLowerCase(Locale.ROOT);

        if (packaging.contains("jar")) {
            return """
                    #!/usr/bin/env bash
                    set -euo pipefail
                    APP_PORT=%s
                    JAR_FILE=$(find target -maxdepth 1 -name '*.jar' | head -n 1)
                    test -n "$JAR_FILE" || { echo "No runnable jar found under target/"; exit 1; }
                    java -Dserver.port=$APP_PORT -jar "$JAR_FILE"
                    """.formatted(port).trim();
        }
        if (packaging.contains("nginx-static") || "Static".equalsIgnoreCase(request.localContext().primaryLanguage())) {
            return """
                    #!/usr/bin/env bash
                    set -euo pipefail
                    docker build -t %s:latest .
                    docker run --rm -p %s:80 %s:latest
                    """.formatted(projectName, port, projectName).trim();
        }
        if ("JavaScript".equalsIgnoreCase(request.localContext().primaryLanguage())) {
            return """
                    #!/usr/bin/env bash
                    set -euo pipefail
                    export PORT=%s
                    npm install
                    npm start
                    """.formatted(port).trim();
        }
        return """
                #!/usr/bin/env bash
                set -euo pipefail
                echo "Review project-specific startup logic before execution."
                echo "Recommended port: %s"
                """.formatted(port).trim();
    }

    private Flux<String> chunkText(String text) {
        return Flux.fromArray(text.split("(?<=\\G.{80})"))
                .filter(chunk -> chunk != null && !chunk.isBlank());
    }

    private boolean isAiConfigured() {
        return apiKey != null && !apiKey.isBlank() && !Objects.equals(apiKey, "sk-placeholder") && !apiKey.contains("placeholder");
    }
}
