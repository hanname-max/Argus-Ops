// aiops-infrastructure/src/main/java/top/codejava/aiops/infrastructure/config/AiModelFactory.java
package top.codejava.aiops.infrastructure.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.localai.LocalAiChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * AI 模型工厂
 * 根据配置动态创建对应的 ChatLanguageModel 实例，支持热拔插
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class AiModelFactory {

    private final AiOpsProperties properties;

    /**
     * 创建远程AI模型 bean
     * 根据配置的 activeProvider 动态选择实现
     */
    @Bean
    @ConditionalOnMissingBean(name = "remoteChatModel")
    public ChatLanguageModel remoteChatModel() {
        log.info("Creating remote ChatLanguageModel with provider: {}", properties.getRemote().getActiveProvider());
        return createChatModel(properties.getRemote());
    }

    /**
     * 创建本地AI模型 bean
     * 根据配置的 activeProvider 动态选择实现
     */
    @Bean
    @ConditionalOnMissingBean(name = "localChatModel")
    public ChatLanguageModel localChatModel() {
        log.info("Creating local ChatLanguageModel with provider: {}", properties.getLocal().getActiveProvider());
        return createChatModel(properties.getLocal());
    }

    /**
     * 根据配置创建具体的 ChatLanguageModel 实例
     */
    private ChatLanguageModel createChatModel(AiOpsProperties.ProviderConfig config) {
        String provider = config.getActiveProvider().toLowerCase();
        String baseUrl = config.getBaseUrl();
        String apiKey = config.getApiKey();
        String modelName = config.getModelName();
        double temperature = config.getTemperature() != null ? config.getTemperature() : 0.7;
        int maxTokens = config.getMaxTokens() != null ? config.getMaxTokens() : 4096;

        return switch (provider) {
            case "claude", "anthropic" -> AnthropicChatModel.builder()
                    .apiKey(apiKey)
                    .modelName(modelName != null ? modelName : "claude-3-5-sonnet-20241022")
                    .temperature(temperature)
                    .maxTokens(maxTokens)
                    .baseUrl(baseUrl)
                    .timeout(Duration.ofMinutes(5))
                    .build();

            case "openai", "gpt" -> OpenAiChatModel.builder()
                    .apiKey(apiKey)
                    .modelName(modelName != null ? modelName : "gpt-4o")
                    .temperature(temperature)
                    .maxTokens(maxTokens)
                    .baseUrl(baseUrl)
                    .timeout(Duration.ofMinutes(5))
                    .build();

            case "ollama" -> OllamaChatModel.builder()
                    .baseUrl(baseUrl != null ? baseUrl : "http://localhost:11434")
                    .modelName(modelName != null ? modelName : "llama3")
                    .temperature(temperature)
                    .numPredict(maxTokens)
                    .build();

            case "lmstudio", "localai", "lm-studio" -> LocalAiChatModel.builder()
                    .baseUrl(baseUrl != null ? baseUrl : "http://localhost:1234/v1")
                    .modelName(modelName != null ? modelName : "default")
                    .temperature(temperature)
                    .maxTokens(maxTokens)
                    .apiKey(apiKey != null ? apiKey : "lm-studio")
                    .build();

            default -> throw new IllegalArgumentException(
                    "Unsupported AI provider: " + provider + ". Supported: claude, openai, ollama, lmstudio");
        };
    }
}
