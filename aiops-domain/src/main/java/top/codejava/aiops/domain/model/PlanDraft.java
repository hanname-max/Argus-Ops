// aiops-domain/src/main/java/top/codejava/aiops/domain/model/PlanDraft.java
package top.codejava.aiops.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AI 生成的部署计划草稿领域模型
 * 包含远程AI生成的架构建议和部署脚本，以及本地AI的安全审计结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanDraft {

    /**
     * 关联的项目上下文
     */
    private ProjectContext projectContext;

    /**
     * 生成时间
     */
    private LocalDateTime generatedAt;

    /**
     * AI 分析得出的架构摘要
     */
    private String architectureSummary;

    /**
     * 推荐的部署方式
     */
    private String recommendedDeploymentType;

    /**
     * 生成的 Dockerfile 内容（如果需要）
     */
    private String dockerfileContent;

    /**
     * 生成的 docker-compose.yml 内容（如果需要）
     */
    private String dockerComposeContent;

    /**
     * 生成的 Kubernetes yaml 配置片段（如果需要）
     */
    private List<String> kubernetesManifests;

    /**
     * AI 生成的完整内容
     */
    private String generatedContent;

    /**
     * 本地AI安全审计是否通过
     */
    private boolean auditPassed;

    /**
     * 本地AI安全审计结果原文
     */
    private String auditResult;

    /**
     * 本地AI安全审计发现的问题
     */
    private List<String> auditFindings;

    /**
     * 完整的AI响应原文（用于调试）
     */
    private String rawResponse;
}
