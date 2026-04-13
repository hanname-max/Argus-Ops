// aiops-infrastructure/src/main/java/top/codejava/aiops/infrastructure/config/provider/LmStudioChatModelProvider.java
package top.codejava.aiops.infrastructure.config.provider;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.localai.LocalAiChatModel;
import org.springframework.stereotype.Component;
import top.codejava.aiops.infrastructure.config.AiOpsProperties.ProviderConfig;

/**
 * LMStudio / LocalAI 本地模型提供商
 * 本地部署，兼容OpenAI API格式
 */
@Component
public class LmStudioChatModelProvider implements ChatModelProvider {

    @Override
    public String[] names() {
        return new String[]{"lmstudio", "localai", "lm-studio"};
    }

    @Override
    public ChatLanguageModel create(ProviderConfig config) {
        String baseUrl = config.getBaseUrl() != null ? config.getBaseUrl() : "http://localhost:1234/v1";
        String modelName = config.getModelName() != null ? config.getModelName() : "default";
        double temperature = config.getTemperature() != null ? config.getTemperature() : 0.7;
        int maxTokens = config.getMaxTokens() != null ? config.getMaxTokens() : 4096;

        LocalAiChatModel.Builder builder = LocalAiChatModel.builder()
                .baseUrl(baseUrl)
                .modelName(modelName)
                .temperature(temperature)
                .maxTokens(maxTokens);

        if (config.getApiKey() != null && !config.getApiKey().isBlank()) {
            builder.apiKey(config.getApiKey());
        }

        return builder.build();
    }
}
