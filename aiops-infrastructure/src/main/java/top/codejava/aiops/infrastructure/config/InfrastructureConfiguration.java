// aiops-infrastructure/src/main/java/top/codejava/aiops/infrastructure/config/InfrastructureConfiguration.java
package top.codejava.aiops.infrastructure.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import top.codejava.aiops.application.port.LocalEnvironmentPort;
import top.codejava.aiops.application.port.LlmRemoteBrainPort;
import top.codejava.aiops.application.port.execution.DaemonInstallerPort;
import top.codejava.aiops.application.port.execution.OpsExecutorPort;
import top.codejava.aiops.application.usecase.GeneratePlanUseCase;
import top.codejava.aiops.infrastructure.adapter.DynamicRemoteBrainAdapter;
import top.codejava.aiops.infrastructure.adapter.LocalEnvironmentScannerAdapter;
import top.codejava.aiops.infrastructure.adapter.execution.*;

import java.nio.file.Path;
import java.nio.file.Paths;

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

    /**
     * RestTemplate for RPC calls
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    /**
     * 注册 SSH 执行适配器
     */
    @Bean
    public SshExecutorAdapter sshExecutorAdapter() {
        return new SshExecutorAdapter();
    }

    /**
     * 注册 RPC 执行适配器
     */
    @Bean
    public RpcExecutorAdapter rpcExecutorAdapter(RestTemplate restTemplate) {
        return new RpcExecutorAdapter(restTemplate);
    }

    /**
     * 注册智能执行路由适配器（策略路由+自动降级）
     */
    @Bean
    public OpsExecutorPort opsExecutorPort(
            RpcExecutorAdapter rpcExecutorAdapter,
            SshExecutorAdapter sshExecutorAdapter) {
        return new SmartExecutorRoutingAdapter(rpcExecutorAdapter, sshExecutorAdapter);
    }

    /**
     * 获取当前 Jar 路径
     */
    @Bean
    public Path localJarPath() {
        String classPath = System.getProperty("java.class.path");
        // For fat jar, the first entry is the jar file itself
        if (classPath.contains(":")) {
            classPath = classPath.split(":")[0];
        }
        return Paths.get(classPath).toAbsolutePath();
    }

    /**
     * 注册 Daemon 安装器
     */
    @Bean
    public DaemonInstallerPort daemonInstallerPort(
            SshExecutorAdapter sshExecutorAdapter,
            Path localJarPath) {
        return new DaemonInstallerAdapter(sshExecutorAdapter, localJarPath);
    }
}
