// aiops-infrastructure/src/main/java/top/codejava/aiops/infrastructure/config/provider/OllamaChatModelProvider.java
package top.codejava.aiops.infrastructure.config.provider;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import org.springframework.stereotype.Component;
import top.codejava.aiops.infrastructure.config.AiOpsProperties.ProviderConfig;

/**
 * Ollama 本地模型提供商
 * 完全本地部署，不需要API Key
 */
@Component
public class OllamaChatModelProvider implements ChatModelProvider {

    @Override
    public String[] names() {
        return new String[]{"ollama"};
    }

    @Override
    public ChatLanguageModel create(ProviderConfig config) {
        String baseUrl = config.getBaseUrl() != null ? config.getBaseUrl() : "http://localhost:11434";
        String modelName = config.getModelName() != null ? config.getModelName() : "llama3";
        double temperature = config.getTemperature() != null ? config.getTemperature() : 0.7;
        int numPredict = config.getMaxTokens() != null ? config.getMaxTokens() : 4096;

        return OllamaChatModel.builder()
                .baseUrl(baseUrl)
                .modelName(modelName)
                .temperature(temperature)
                .numPredict(numPredict)
                .build();
    }
}
