// aiops-infrastructure/src/main/java/top/codejava/aiops/infrastructure/config/provider/DashScopeChatModelProvider.java
package top.codejava.aiops.infrastructure.config.provider;

import dev.langchain4j.model.chat.ChatLanguageModel;
import org.springframework.stereotype.Component;
import top.codejava.aiops.infrastructure.config.AiOpsProperties.ProviderConfig;

/**
 * 阿里云 DashScope 通义千问 模型提供商
 * 国内主流大模型，支持自定义API端点
 *
 * 需要先在pom.xml中解开 langchain4j-dashscope 依赖注释
 */
@Component
public class DashScopeChatModelProvider implements ChatModelProvider {

    @Override
    public String[] names() {
        return new String[]{"dashscope", "tongyi", "qwen", "通义千问"};
    }

    @Override
    public boolean isAvailable() {
        try {
            Class.forName("dev.langchain4j.model.dashscope.DashScopeChatModel");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public ChatLanguageModel create(ProviderConfig config) {
        String apiKey = config.getApiKey();
        String modelName = config.getModelName() != null ? config.getModelName() : "qwen-max";
        double temperature = config.getTemperature() != null ? config.getTemperature() : 0.7;
        int maxTokens = config.getMaxTokens() != null ? config.getMaxTokens() : 4096;

        try {
            Class<?> dashScopeClass = Class.forName("dev.langchain4j.model.dashscope.DashScopeChatModel");
            Object builder = dashScopeClass.getMethod("builder").invoke(null);
            builder.getClass().getMethod("apiKey", String.class).invoke(builder, apiKey);
            builder.getClass().getMethod("modelName", String.class).invoke(builder, modelName);
            builder.getClass().getMethod("temperature", double.class).invoke(builder, temperature);
            builder.getClass().getMethod("maxTokens", int.class).invoke(builder, maxTokens);

            if (config.getBaseUrl() != null && !config.getBaseUrl().isBlank()) {
                builder.getClass().getMethod("baseUrl", String.class).invoke(builder, config.getBaseUrl());
            }

            return (ChatLanguageModel) builder.getClass().getMethod("build").invoke(builder);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create DashScopeChatModel. " +
                    "Please make sure langchain4j-dashscope dependency is enabled in pom.xml", e);
        }
    }
}
