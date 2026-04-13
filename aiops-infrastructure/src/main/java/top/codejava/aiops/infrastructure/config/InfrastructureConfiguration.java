// aiops-infrastructure/src/main/java/top/codejava/aiops/infrastructure/config/InfrastructureConfiguration.java
package top.codejava.aiops.infrastructure.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import top.codejava.aiops.application.port.LocalEnvironmentPort;
import top.codejava.aiops.application.port.LlmRemoteBrainPort;
import top.codejava.aiops.application.usecase.GeneratePlanUseCase;
import top.codejava.aiops.infrastructure.adapter.DynamicRemoteBrainAdapter;
import top.codejava.aiops.infrastructure.adapter.LocalEnvironmentScannerAdapter;

/**
 * 基础设施层配置类
 * 注册所有端口实现和用例到Spring容器
 */
@Configuration
@EnableConfigurationProperties(AiOpsProperties.class)
public class InfrastructureConfiguration {

    /**
     * 注册本地环境扫描适配器
     */
    @Bean
    public LocalEnvironmentPort localEnvironmentPort() {
        return new LocalEnvironmentScannerAdapter();
    }

    /**
     * 注册远程AI大脑适配器
     * 依赖动态注入的 ChatLanguageModel，不绑定具体提供商
     */
    @Bean
    public LlmRemoteBrainPort llmRemoteBrainPort(
            ChatLanguageModel remoteChatModel,
            ChatLanguageModel localChatModel) {
        return new DynamicRemoteBrainAdapter(remoteChatModel, localChatModel);
    }

    /**
     * 注册生成计划用例
     */
    @Bean
    public GeneratePlanUseCase generatePlanUseCase(
            LocalEnvironmentPort localEnvironmentPort,
            LlmRemoteBrainPort llmRemoteBrainPort) {
        return new GeneratePlanUseCase(localEnvironmentPort, llmRemoteBrainPort);
    }
}
