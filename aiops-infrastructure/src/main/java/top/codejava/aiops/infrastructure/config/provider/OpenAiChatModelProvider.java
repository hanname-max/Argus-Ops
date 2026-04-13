// aiops-infrastructure/src/main/java/top/codejava/aiops/infrastructure/config/provider/OpenAiChatModelProvider.java
package top.codejava.aiops.infrastructure.config.provider;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.stereotype.Component;
import top.codejava.aiops.infrastructure.config.AiOpsProperties.ProviderConfig;

import java.time.Duration;

/**
 * OpenAI GPT 聊天模型提供商
 */
@Component
public class OpenAiChatModelProvider implements ChatModelProvider {

    @Override
    public String[] names() {
        return new String[]{"openai", "gpt"};
    }

    @Override
    public ChatLanguageModel create(ProviderConfig config) {
        String apiKey = config.getApiKey();
        String modelName = config.getModelName() != null ? config.getModelName() : "gpt-4o";
        double temperature = config.getTemperature() != null ? config.getTemperature() : 0.7;
        int maxTokens = config.getMaxTokens() != null ? config.getMaxTokens() : 4096;
        String baseUrl = config.getBaseUrl();

        OpenAiChatModel.Builder builder = OpenAiChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .timeout(Duration.ofMinutes(5));

        if (baseUrl != null && !baseUrl.isBlank()) {
            builder.baseUrl(baseUrl);
        }

        return builder.build();
    }
}
