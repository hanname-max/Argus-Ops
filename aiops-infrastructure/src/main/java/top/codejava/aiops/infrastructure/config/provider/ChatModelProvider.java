// aiops-infrastructure/src/main/java/top/codejava/aiops/infrastructure/config/provider/ChatModelProvider.java
package top.codejava.aiops.infrastructure.config.provider;

import dev.langchain4j.model.chat.ChatLanguageModel;
import top.codejava.aiops.infrastructure.config.AiOpsProperties.ProviderConfig;

/**
 * 聊天模型提供商策略接口
 * 每个AI提供商实现此接口，通过Spring自动发现注册
 */
public interface ChatModelProvider {

    /**
     * 获取提供商名称（用于匹配配置中的activeProvider）
     * 支持多个别名，第一个是主名称
     * @return 提供商名称数组
     */
    String[] names();

    /**
     * 判断该提供商是否可用（检查依赖是否存在）
     * @return true if available
     */
    default boolean isAvailable() {
        return true;
    }

    /**
     * 根据配置创建ChatLanguageModel实例
     * @param config 提供商配置
     * @return ChatLanguageModel实例
     */
    ChatLanguageModel create(ProviderConfig config);
}
