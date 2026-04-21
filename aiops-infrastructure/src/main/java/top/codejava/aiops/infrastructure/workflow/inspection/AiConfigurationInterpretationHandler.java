package top.codejava.aiops.infrastructure.workflow.inspection;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
@Order(35)
public class AiConfigurationInterpretationHandler implements LocalProjectInspectionHandler {

    private static final int MAX_FILES = 5;
    private static final int MAX_CHARS_PER_FILE = 1200;

    private final ChatClient localChatClient;
    private final String apiKey;

    public AiConfigurationInterpretationHandler(@Qualifier("localChatClient") ChatClient localChatClient,
                                                @Value("${aiops.local.api-key:}") String apiKey) {
        this.localChatClient = localChatClient;
        this.apiKey = apiKey;
    }

    @Override
    public boolean supports(LocalProjectInspectionContext context) {
        return context.isBackendProject()
                && context.requiredInputs().isEmpty()
                && !context.configFiles().isEmpty()
                && isAiConfigured();
    }

    @Override
    public void handle(LocalProjectInspectionContext context) {
        String prompt = buildPrompt(context);
        if (prompt == null || prompt.isBlank()) {
            return;
        }

        try {
            String content = localChatClient.prompt()
                    .system("""
                            You are a configuration interpretation assistant.
                            Read backend config snippets and infer deploy-relevant settings.
                            Return plain text lines only.
                            Use this format exactly:
                            SUMMARY|<one sentence>
                            CANDIDATE|<MYSQL|REDIS|DISTRIBUTED|AI|OTHER>|<location>|<key>|<summary>
                            Do not use markdown fences.
                            """)
                    .user(prompt)
                    .call()
                    .content();
            parseAiResult(context, content);
        } catch (Exception ignored) {
            context.addWarning(
                    "AI_CONFIG_INTERPRETATION_SKIPPED",
                    top.codejava.aiops.application.dto.WorkflowModels.Severity.INFO,
                    "AI configuration interpretation skipped after timeout or provider failure.",
                    "Continue with deterministic config findings or fill the required secrets manually."
            );
        }
    }

    private String buildPrompt(LocalProjectInspectionContext context) {
        String snippets = context.configFiles().stream()
                .limit(MAX_FILES)
                .map(file -> "[FILE] " + file.path() + "\n" + abbreviate(file.content()))
                .collect(Collectors.joining("\n\n"));
        if (snippets.isBlank()) {
            return "";
        }

        String deterministicFindings = context.detectedCandidates().stream()
                .map(item -> item.category() + " | " + item.location() + " | " + item.key() + " | " + item.summary())
                .collect(Collectors.joining("\n"));

        return """
                Project language: %s
                Project framework: %s

                Deterministic findings:
                %s

                Config snippets:
                %s
                """.formatted(
                context.language(),
                context.framework(),
                deterministicFindings.isBlank() ? "none" : deterministicFindings,
                snippets
        );
    }

    private void parseAiResult(LocalProjectInspectionContext context, String content) {
        if (content == null || content.isBlank()) {
            return;
        }

        for (String rawLine : content.split("\\R")) {
            String line = rawLine.trim();
            if (line.isBlank()) {
                continue;
            }
            if (line.startsWith("SUMMARY|")) {
                context.aiSummary(line.substring("SUMMARY|".length()).trim());
                continue;
            }
            if (line.startsWith("CANDIDATE|")) {
                String[] parts = line.split("\\|", 5);
                if (parts.length < 5) {
                    continue;
                }
                String category = normalizeCategory(parts[1]);
                String location = parts[2].trim();
                String key = parts[3].trim();
                String summary = parts[4].trim();
                if (key.isBlank()) {
                    continue;
                }
                context.addDetectedCandidate(
                        category,
                        location.isBlank() ? "AI" : location,
                        key,
                        "AI inferred",
                        summary,
                        true,
                        true
                );
            }
        }
    }

    private String normalizeCategory(String category) {
        String normalized = category == null ? "OTHER" : category.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "MYSQL", "REDIS", "DISTRIBUTED", "AI" -> normalized;
            default -> "OTHER";
        };
    }

    private String abbreviate(String content) {
        if (content == null) {
            return "";
        }
        String normalized = content.strip();
        if (normalized.length() <= MAX_CHARS_PER_FILE) {
            return normalized;
        }
        return normalized.substring(0, MAX_CHARS_PER_FILE) + "...";
    }

    private boolean isAiConfigured() {
        return apiKey != null && !apiKey.isBlank() && !Objects.equals(apiKey, "sk-placeholder") && !apiKey.contains("placeholder");
    }
}
