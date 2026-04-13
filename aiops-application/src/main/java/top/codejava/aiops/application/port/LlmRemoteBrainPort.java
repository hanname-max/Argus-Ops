// aiops-application/src/main/java/top/codejava/aiops/application/port/LlmRemoteBrainPort.java
package top.codejava.aiops.application.port;

import top.codejava.aiops.domain.model.PlanDraft;
import top.codejava.aiops.domain.model.ProjectContext;

/**
 * 远程AI大脑端口（出站端口）
 * 由基础设施层实现，负责调用远程LLM生成部署计划
 */
public interface LlmRemoteBrainPort {

    /**
     * 根据本地项目上下文生成部署计划草稿
     *
     * @param context 本地扫描得到的项目上下文
     * @return AI生成的部署计划草稿
     */
    PlanDraft generateDeploymentPlan(ProjectContext context);

    /**
     * 对生成的计划进行安全审计（由本地AI执行）
     *
     * @param planDraft 待审计的计划草稿
     * @return 审计后的计划（包含审计结果）
     */
    PlanDraft auditPlan(PlanDraft planDraft);
}
