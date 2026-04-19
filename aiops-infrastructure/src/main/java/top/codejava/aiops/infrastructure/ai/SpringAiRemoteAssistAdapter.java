package top.codejava.aiops.infrastructure.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import top.codejava.aiops.application.dto.EnvironmentCheckResult;
import top.codejava.aiops.application.dto.LogFilterResult;
import top.codejava.aiops.application.port.RemoteAiAssistPort;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Component
public class SpringAiRemoteAssistAdapter implements RemoteAiAssistPort {

    private static final Duration AI_TIMEOUT = Duration.ofSeconds(5);

    private final ChatClient remoteAssistChatClient;
    private final String apiKey;

    public SpringAiRemoteAssistAdapter(@Qualifier("remoteAssistChatClient") ChatClient remoteAssistChatClient,
                                       @Value("${aiops.remote.api-key:}") String apiKey) {
        this.remoteAssistChatClient = remoteAssistChatClient;
        this.apiKey = apiKey;
    }

    @Override
    public EnvironmentCheckResult assistEnvironmentCheck(String projectPath, EnvironmentCheckResult localResult) {
        if (!isAiConfigured()) {
            return new EnvironmentCheckResult(
                    localResult.checks(),
                    localResult.risks(),
                    localResult.conclusion() + " Remote assist skipped because REMOTE_AI_KEY is not configured."
            );
        }

        try {
            String prompt = """
                    Given this local environment check result, provide one additional critical deployment risk.
                    Project path: %s
                    Checks: %s
                    Risks: %s
                    Conclusion: %s
                    """.formatted(projectPath, localResult.checks(), localResult.risks(), localResult.conclusion());
            String assist = runAiCall(() -> remoteAssistChatClient.prompt().user(prompt).call().content());
            if (!hasText(assist)) {
                return localResult;
            }

            List<String> risks = new ArrayList<>(localResult.risks());
            risks.add("Remote assist: " + assist.trim());
            return new EnvironmentCheckResult(localResult.checks(), risks, "Local-first + remote-assist done");
        } catch (Exception ignored) {
            return new EnvironmentCheckResult(
                    localResult.checks(),
                    localResult.risks(),
                    localResult.conclusion() + " Remote assist skipped after timeout or provider failure."
            );
        }
    }

    @Override
    public LogFilterResult assistLogFilter(String rawLog, LogFilterResult localResult) {
        if (!isAiConfigured()) {
            return new LogFilterResult(
                    localResult.keyError(),
                    localResult.filteredLog(),
                    appendLine(localResult.advice(), "Remote assist skipped because REMOTE_AI_KEY is not configured.")
            );
        }

        try {
            String prompt = """
                    Based on this local error summary, provide one prioritized root cause and one fix.
                    Raw log:
                    %s
                    Local summary:
                    %s
                    """.formatted(rawLog, localResult.filteredLog());
            String assist = runAiCall(() -> remoteAssistChatClient.prompt().user(prompt).call().content());
            if (!hasText(assist)) {
                return localResult;
            }

            return new LogFilterResult(
                    localResult.keyError(),
                    localResult.filteredLog(),
                    appendLine(localResult.advice(), "Remote assist: " + assist.trim())
            );
        } catch (Exception ignored) {
            return new LogFilterResult(
                    localResult.keyError(),
                    localResult.filteredLog(),
                    appendLine(localResult.advice(), "Remote assist skipped after timeout or provider failure.")
            );
        }
    }

    private String runAiCall(Supplier<String> call) {
        return CompletableFuture.supplyAsync(call)
                .orTimeout(AI_TIMEOUT.toSeconds(), TimeUnit.SECONDS)
                .join();
    }

    private String appendLine(String base, String extra) {
        if (!hasText(base)) {
            return extra;
        }
        return base + System.lineSeparator() + extra;
    }

    private boolean isAiConfigured() {
        return hasText(apiKey) && !Objects.equals("sk-placeholder", apiKey) && !apiKey.contains("placeholder");
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
