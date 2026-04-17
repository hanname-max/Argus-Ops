// aiops-infrastructure/src/main/java/top/codejava/aiops/infrastructure/config/SpringAiConfig.java
package top.codejava.aiops.infrastructure.config;

import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.StreamingChatClient;
import org.springframework.ai.openai.OpenAiChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import top.codejava.aiops.infrastructure.config.AiOpsProperties;

/**
 * Spring AI 配置类
 * 配置远程和本地两个 ChatClient，支持不同模型参数
 * 适配 Spring AI 0.8.x API
 */
@Configuration
public class SpringAiConfig {

    private final AiOpsProperties properties;

    public SpringAiConfig(AiOpsProperties properties) {
        this.properties = properties;
    }

    @Bean
    @Qualifier("remoteChatClient")
    public ChatClient remoteChatClient() {
        AiOpsProperties.ProviderConfig remote = properties.getRemote();

        OpenAiApi openAiApi = new OpenAiApi(remote.getBaseUrl(), remote.getApiKey());
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .withModel(remote.getModelName())
                .withTemperature(remote.getTemperature() != null ? remote.getTemperature().floatValue() : 0.7f)
                .withMaxTokens(remote.getMaxTokens() != null ? remote.getMaxTokens() : 4096)
                .build();

        return new OpenAiChatClient(openAiApi, options);
    }

    @Bean
    @Qualifier("remoteStreamingChatClient")
    public StreamingChatClient remoteStreamingChatClient() {
        // In Spring AI 0.8.x, OpenAiChatClient implements both ChatClient and StreamingChatClient
        return (StreamingChatClient) remoteChatClient();
    }

    @Bean
    @Qualifier("localChatClient")
    public ChatClient localChatClient() {
        AiOpsProperties.ProviderConfig local = properties.getLocal();

        OpenAiApi openAiApi = new OpenAiApi(local.getBaseUrl(), local.getApiKey());
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .withModel(local.getModelName())
                .withTemperature(local.getTemperature() != null ? local.getTemperature().floatValue() : 0.3f)
                .withMaxTokens(local.getMaxTokens() != null ? local.getMaxTokens() : 2048)
                .build();

        return new OpenAiChatClient(openAiApi, options);
    }
}
