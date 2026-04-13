// aiops-infrastructure/src/main/java/top/codejava/aiops/infrastructure/config/AiModelFactory.java
package top.codejava.aiops.infrastructure.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import top.codejava.aiops.infrastructure.config.provider.ChatModelProvider;

import java.util.List;

/**
 * AI 模型工厂
 * 根据配置动态创建对应的 ChatLanguageModel 实例，支持热拔插
 * 使用策略模式，通过Spring自动发现所有ChatModelProvider实现
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class AiModelFactory {

    private final AiOpsProperties properties;
    private final List<ChatModelProvider> providers;

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
     * 根据配置查找对应的ChatModelProvider并创建模型
     */
    private ChatLanguageModel createChatModel(AiOpsProperties.ProviderConfig config) {
        String providerName = config.getActiveProvider().toLowerCase();

        for (ChatModelProvider provider : providers) {
            for (String name : provider.names()) {
                if (name.equalsIgnoreCase(providerName)) {
                    if (!provider.isAvailable()) {
                        throw new IllegalArgumentException(
                                "AI provider '" + providerName + "' is not available. " +
                                "Please add the corresponding LangChain4j dependency to pom.xml");
                    }
                    log.debug("Using ChatModelProvider: {}", provider.getClass().getSimpleName());
                    return provider.create(config);
                }
            }
        }

        // 收集所有支持的提供商名称
        StringBuilder supported = new StringBuilder();
        for (ChatModelProvider p : providers) {
            if (supported.length() > 0) {
                supported.append(", ");
            }
            supported.append(p.names()[0]);
        }

        throw new IllegalArgumentException(
                "Unsupported AI provider: '" + providerName + "'. " +
                "Supported providers: " + supported);
    }
}
