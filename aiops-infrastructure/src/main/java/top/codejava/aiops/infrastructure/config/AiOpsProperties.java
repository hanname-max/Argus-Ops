// aiops-infrastructure/src/main/java/top/codejava/aiops/infrastructure/config/AiOpsProperties.java
package top.codejava.aiops.infrastructure.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AIOps 配置属性类
 * 支持远程和本地双AI模型的配置绑定
 */
@Data
@ConfigurationProperties(prefix = "aiops")
public class AiOpsProperties {

    /**
     * 远程AI配置（负责架构规划和脚本生成）
     */
    private ProviderConfig remote;

    /**
     * 本地AI配置（负责上下文扫描和安全审计）
     */
    private ProviderConfig local;

    /**
     * LLM提供商配置
     */
    @Data
    public static class ProviderConfig {
        /**
         * 激活的提供商: claude, openai, ollama, lmstudio 等
         */
        @NotBlank(message = "activeProvider cannot be blank")
        private String activeProvider;

        /**
         * API 基础 URL（对于自托管模型如 Ollama/LMStudio 需要）
         */
        private String baseUrl;

        /**
         * API Key（对于云端服务需要）
         */
        private String apiKey;

        /**
         * Secret Key（部分提供商如文心一言需要）
         */
        private String secretKey;

        /**
         * 模型名称
         */
        private String modelName;

        /**
         * 温度参数 (0.0 - 1.0)
         */
        private Double temperature = 0.7;

        /**
         * 最大 token 数
         */
        private Integer maxTokens = 4096;
    }
}
