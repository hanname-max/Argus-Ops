package top.codejava.aiops.infrastructure.config;

import java.net.URI;
import java.net.URISyntaxException;

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
        OpenAiApi api = buildOpenAiApi(baseUrl, apiKey);
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
        OpenAiApi api = buildOpenAiApi(baseUrl, apiKey);
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(OpenAiChatOptions.builder().model(model).temperature(0.1).build())
                .build();
        return ChatClient.builder(chatModel).build();
    }

    private OpenAiApi buildOpenAiApi(String configuredUrl, String apiKey) {
        OpenAiEndpoint endpoint = resolveEndpoint(configuredUrl);
        return OpenAiApi.builder()
                .baseUrl(endpoint.baseUrl())
                .completionsPath(endpoint.completionsPath())
                .embeddingsPath(endpoint.embeddingsPath())
                .apiKey(apiKey)
                .build();
    }

    private OpenAiEndpoint resolveEndpoint(String configuredUrl) {
        String normalized = configuredUrl == null || configuredUrl.isBlank()
                ? "https://api.openai.com/v1"
                : configuredUrl.trim();
        try {
            URI uri = new URI(normalized);
            String scheme = uri.getScheme();
            String authority = uri.getRawAuthority();
            String path = trimTrailingSlash(uri.getRawPath());

            if (scheme == null || authority == null) {
                return new OpenAiEndpoint(normalized, "/v1/chat/completions", "/v1/embeddings");
            }

            String origin = scheme + "://" + authority;
            if (path.endsWith("/chat/completions")) {
                return new OpenAiEndpoint(
                        origin,
                        path,
                        path.substring(0, path.length() - "/chat/completions".length()) + "/embeddings"
                );
            }
            if (path.matches(".*/v\\d+$") || path.matches(".*/api/v\\d+$")) {
                return new OpenAiEndpoint(origin, path + "/chat/completions", path + "/embeddings");
            }
            if (path.isEmpty()) {
                return new OpenAiEndpoint(origin, "/v1/chat/completions", "/v1/embeddings");
            }
            return new OpenAiEndpoint(origin, path + "/chat/completions", path + "/embeddings");
        } catch (URISyntaxException ignored) {
            return new OpenAiEndpoint(normalized, "/v1/chat/completions", "/v1/embeddings");
        }
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isEmpty() || "/".equals(value)) {
            return "";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private record OpenAiEndpoint(String baseUrl, String completionsPath, String embeddingsPath) {
    }
}
