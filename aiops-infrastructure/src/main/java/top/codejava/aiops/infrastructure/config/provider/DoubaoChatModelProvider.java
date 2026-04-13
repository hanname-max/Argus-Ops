// aiops-infrastructure/src/main/java/top/codejava/aiops/infrastructure/config/provider/DoubaoChatModelProvider.java
package top.codejava.aiops.infrastructure.config.provider;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.stereotype.Component;
import top.codejava.aiops.infrastructure.config.AiOpsProperties.ProviderConfig;

/**
 * 字节跳动 豆包 Doubao 模型提供商
 * 通过 OpenAI 兼容 API 访问，支持自定义端点
 *
 * 默认API地址: https://aquasearch.ai.bytedance.net/api/v1/chat/completions
 * 或者: https://doubao.com/api/v1/
 */
@Component
public class DoubaoChatModelProvider implements ChatModelProvider {

    @Override
    public String[] names() {
        return new String[]{"doubao", "bytedance", "豆包"};
    }

    @Override
    public ChatLanguageModel create(ProviderConfig config) {
        String apiKey = config.getApiKey();
        String modelName = config.getModelName() != null ? config.getModelName() : "doubao-4-32k";
        double temperature = config.getTemperature() != null ? config.getTemperature() : 0.7;
        int maxTokens = config.getMaxTokens() != null ? config.getMaxTokens() : 4096;

        var builder = OpenAiChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .temperature(temperature)
                .maxTokens(maxTokens);

        // 豆包默认端点，如果用户没配置就用默认
        if (config.getBaseUrl() != null && !config.getBaseUrl().isBlank()) {
            builder.baseUrl(config.getBaseUrl());
        }

        return builder.build();
    }
}
