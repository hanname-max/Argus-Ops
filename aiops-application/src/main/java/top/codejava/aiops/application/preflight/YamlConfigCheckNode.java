// aiops-application/src/main/java/top/codejava/aiops/application/preflight/YamlConfigCheckNode.java
package top.codejava.aiops.application.preflight;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import top.codejava.aiops.type.preflight.PreflightContext;
import top.codejava.aiops.type.preflight.PreflightNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * YAML 配置检查节点
 * 检查 application.yml 核心配置是否完整
 */
@Component
public class YamlConfigCheckNode implements PreflightNode {

    private final ResourceLoader resourceLoader;

    @Value("${aiops.remote.api-key:}")
    private String remoteApiKey;

    @Value("${aiops.remote.base-url:}")
    private String remoteBaseUrl;

    @Value("${aiops.remote.model-name:}")
    private String remoteModelName;

    @Value("${aiops.local.api-key:}")
    private String localApiKey;

    @Value("${aiops.local.base-url:}")
    private String localBaseUrl;

    @Value("${aiops.local.model-name:}")
    private String localModelName;

    public YamlConfigCheckNode(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @Override
    public void check(PreflightContext context) {
        // 检查远程 AI 配置
        if (remoteApiKey == null || remoteApiKey.isBlank()) {
            context.addFinding("远程 AI 配置不完整: aiops.remote.api-key 未配置");
        }
        if (remoteBaseUrl == null || remoteBaseUrl.isBlank()) {
            context.addFinding("远程 AI 配置不完整: aiops.remote.base-url 未配置");
        }
        if (remoteModelName == null || remoteModelName.isBlank()) {
            context.addFinding("远程 AI 配置不完整: aiops.remote.model-name 未配置");
        }

        // 检查本地 AI 配置
        if (localApiKey == null || localApiKey.isBlank()) {
            context.addFinding("本地 AI 配置不完整: aiops.local.api-key 未配置");
        }
        if (localBaseUrl == null || localBaseUrl.isBlank()) {
            context.addFinding("本地 AI 配置不完整: aiops.local.base-url 未配置");
        }
        if (localModelName == null || localModelName.isBlank()) {
            context.addFinding("本地 AI 配置不完整: aiops.local.model-name 未配置");
        }

        // 检查配置文件是否可读取
        try {
            var resource = resourceLoader.getResource("classpath:application.yml");
            if (!resource.exists()) {
                context.addFinding("application.yml 配置文件未找到");
            } else {
                // 读取内容检查是否包含占位符
                String content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                if (content.contains("you-api-key") || content.contains("you-base-url")) {
                    context.addFinding("配置文件中仍包含占位符示例值，请替换为真实配置");
                }
            }
        } catch (IOException e) {
            context.addFinding("无法读取 application.yml: " + e.getMessage());
        }
    }

    @Override
    public int order() {
        return 10; // 最先执行配置检查
    }

    @Override
    public String name() {
        return "YamlConfigCheck";
    }
}
