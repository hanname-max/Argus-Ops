// aiops-infrastructure/src/main/java/top/codejava/aiops/infrastructure/config/provider/QianFanChatModelProvider.java
package top.codejava.aiops.infrastructure.config.provider;

import dev.langchain4j.model.chat.ChatLanguageModel;
import org.springframework.stereotype.Component;
import top.codejava.aiops.infrastructure.config.AiOpsProperties.ProviderConfig;

/**
 * 百度 文心一言 QianFan 模型提供商
 * 国内大模型，支持API Key + Secret Key认证，支持自定义API端点
 *
 * 需要先在pom.xml中解开 langchain4j-qianfan 依赖注释
 */
@Component
public class QianFanChatModelProvider implements ChatModelProvider {

    @Override
    public String[] names() {
        return new String[]{"qianfan", "wenxin", "baidu", "文心一言"};
    }

    @Override
    public boolean isAvailable() {
        try {
            Class.forName("dev.langchain4j.model.qianfan.QianFanChatModel");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public ChatLanguageModel create(ProviderConfig config) {
        String apiKey = config.getApiKey();
        String secretKey = config.getSecretKey();
        String modelName = config.getModelName() != null ? config.getModelName() : "ERNIE-4.0-8K";
        double temperature = config.getTemperature() != null ? config.getTemperature() : 0.7;
        int maxTokens = config.getMaxTokens() != null ? config.getMaxTokens() : 4096;

        try {
            Class<?> qianFanClass = Class.forName("dev.langchain4j.model.qianfan.QianFanChatModel");
            Object builder = qianFanClass.getMethod("builder").invoke(null);
            builder.getClass().getMethod("apiKey", String.class).invoke(builder, apiKey);
            if (secretKey != null) {
                builder.getClass().getMethod("secretKey", String.class).invoke(builder, secretKey);
            }
            builder.getClass().getMethod("modelName", String.class).invoke(builder, modelName);
            builder.getClass().getMethod("temperature", double.class).invoke(builder, temperature);
            builder.getClass().getMethod("maxTokens", int.class).invoke(builder, maxTokens);

            if (config.getBaseUrl() != null && !config.getBaseUrl().isBlank()) {
                builder.getClass().getMethod("baseUrl", String.class).invoke(builder, config.getBaseUrl());
            }

            return (ChatLanguageModel) builder.getClass().getMethod("build").invoke(builder);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create QianFanChatModel. " +
                    "Please make sure langchain4j-qianfan dependency is enabled in pom.xml", e);
        }
    }
}
