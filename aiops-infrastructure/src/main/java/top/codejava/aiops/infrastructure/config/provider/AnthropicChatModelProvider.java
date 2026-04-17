// aiops-infrastructure/src/main/java/top/codejava/aiops/infrastructure/config/provider/AnthropicChatModelProvider.java
package top.codejava.aiops.infrastructure.config.provider;

import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.springframework.stereotype.Component;
import top.codejava.aiops.infrastructure.config.AiOpsProperties.ProviderConfig;

import java.time.Duration;

/**
 * Anthropic Claude 聊天模型提供商
 * 兼容火山引擎 ARK (ByteDance ARK)
 */
@Component
public class AnthropicChatModelProvider implements ChatModelProvider {

    @Override
    public String[] names() {
        return new String[]{"claude", "anthropic"};
    }

    @Override
    public ChatLanguageModel create(ProviderConfig config) {
        String apiKey = config.getApiKey();
        String modelName = config.getModelName() != null ? config.getModelName() : "claude-3-5-sonnet-20241022";
        double temperature = config.getTemperature() != null ? config.getTemperature() : 0.7;
        int maxTokens = config.getMaxTokens() != null ? config.getMaxTokens() : 4096;
        String baseUrl = config.getBaseUrl();

        var builder = AnthropicChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .timeout(Duration.ofMinutes(5));

        if (baseUrl != null && !baseUrl.isBlank()) {
            // 火山引擎 ARK 对代码模型已经包含完整版本路径，需要确保正确拼接
            // 如果 baseUrl 已经是类似 ".../v3"，langchain4j 会自动追加 "/messages"
            builder.baseUrl(baseUrl);
        }

        return builder.build();
    }
}
