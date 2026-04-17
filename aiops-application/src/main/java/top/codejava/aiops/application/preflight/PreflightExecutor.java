// aiops-application/src/main/java/top/codejava/aiops/application/preflight/PreflightExecutor.java
package top.codejava.aiops.application.preflight;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import top.codejava.aiops.type.preflight.PreflightContext;
import top.codejava.aiops.type.preflight.PreflightNode;

import java.util.Comparator;
import java.util.List;

/**
 * 预检责任链执行器
 * 按优先级排序并执行所有预检节点
 */
@Slf4j
@Component
public class PreflightExecutor {

    private final List<PreflightNode> preflightNodes;

    public PreflightExecutor(List<PreflightNode> preflightNodes) {
        this.preflightNodes = preflightNodes;
        // 按优先级排序
        this.preflightNodes.sort(Comparator.comparingInt(PreflightNode::order));
    }

    /**
     * 执行所有预检检查
     * @param projectPath 目标项目路径
     * @return 预检上下文（包含检查结果）
     */
    public PreflightContext execute(String projectPath) {
        PreflightContext context = PreflightContext.builder()
                .projectPath(projectPath)
                .build();

        log.info("Starting preflight checks for project: {}", projectPath);

        for (PreflightNode node : preflightNodes) {
            try {
                log.debug("Running preflight node: {}", node.name());
                node.check(context);
                if (!context.isPassed()) {
                    log.warn("Preflight node {} found issues: {}", node.name(), context.getFindings());
                }
            } catch (Exception e) {
                log.error("Preflight node {} failed with exception: {}", node.name(), e.getMessage(), e);
                context.addFinding("节点 " + node.name() + " 执行异常: " + e.getMessage());
            }
        }

        if (context.isPassed()) {
            log.info("All preflight checks passed");
        } else {
            log.warn("Preflight checks failed with {} findings", context.getFindings().size());
        }

        return context;
    }
}
