package top.codejava.aiops.infrastructure.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SpringAiClientsConfig {

    @Bean
    public ChatClient localChatClient(
            @Value("${aiops.local.base-url:https://api.openai.com/v1}") String baseUrl,
            @Value("${aiops.local.api-key:sk-placeholder}") String apiKey,
            @Value("${aiops.local.model:gpt-4o-mini}") String model
    ) {
        OpenAiApi api = OpenAiApi.builder().baseUrl(baseUrl).apiKey(apiKey).build();
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(OpenAiChatOptions.builder().model(model).temperature(0.2).build())
                .build();
        return ChatClient.builder(chatModel).build();
    }

    @Bean
    public ChatClient remoteAssistChatClient(
            @Value("${aiops.remote.base-url:https://api.openai.com/v1}") String baseUrl,
            @Value("${aiops.remote.api-key:sk-placeholder}") String apiKey,
            @Value("${aiops.remote.model:gpt-4o-mini}") String model
    ) {
        OpenAiApi api = OpenAiApi.builder().baseUrl(baseUrl).apiKey(apiKey).build();
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(OpenAiChatOptions.builder().model(model).temperature(0.1).build())
                .build();
        return ChatClient.builder(chatModel).build();
    }
}
