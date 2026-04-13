// aiops-application/src/main/java/top/codejava/aiops/application/usecase/GeneratePlanUseCase.java
package top.codejava.aiops.application.usecase;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import top.codejava.aiops.application.port.LocalEnvironmentPort;
import top.codejava.aiops.application.port.LlmRemoteBrainPort;
import top.codejava.aiops.domain.model.PlanDraft;
import top.codejava.aiops.domain.model.ProjectContext;

import java.nio.file.Path;

/**
 * 生成部署计划用例（入站用例）
 * 协调本地环境扫描 -> 远程AI生成 -> 本地AI审计 的完整流程
 */
@Slf4j
@RequiredArgsConstructor
public class GeneratePlanUseCase {

    private final LocalEnvironmentPort localEnvironmentPort;
    private final LlmRemoteBrainPort llmRemoteBrainPort;

    /**
     * 执行完整的计划生成流程
     *
     * @param projectPath 项目根目录路径
     * @return 经过审计的最终部署计划
     */
    public PlanDraft execute(Path projectPath) {
        log.info("Starting deployment plan generation for: {}", projectPath);

        // 步骤1：扫描本地项目上下文
        ProjectContext context = localEnvironmentPort.scanProject(projectPath);
        log.info("Local project scan completed: language={}, hasDockerfile={}",
                context.getPrimaryLanguage(), context.isHasDockerfile());

        // 步骤2：远程AI生成初始计划
        PlanDraft draft = llmRemoteBrainPort.generateDeploymentPlan(context);
        log.info("Remote AI plan generation completed, audit starting...");

        // 步骤3：本地AI安全审计
        PlanDraft auditedDraft = llmRemoteBrainPort.auditPlan(draft);

        if (auditedDraft.isAuditPassed()) {
            log.info("Local security audit PASSED");
        } else {
            log.warn("Local security audit FAILED, findings: {}", auditedDraft.getAuditFindings());
        }

        return auditedDraft;
    }

    /**
     * 对当前工作目录生成计划
     *
     * @return 经过审计的最终部署计划
     */
    public PlanDraft executeForCurrentDirectory() {
        return execute(Path.of("").toAbsolutePath());
    }
}
