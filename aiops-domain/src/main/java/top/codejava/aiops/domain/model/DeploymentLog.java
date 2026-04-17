// aiops-domain/src/main/java/top/codejava/aiops/domain/model/DeploymentLog.java
package top.codejava.aiops.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 部署日志实体
 * 用于持久化存储部署过程中的所有日志，供 AI 复盘分析
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeploymentLog {

    /**
     * 日志ID
     */
    private Long id;

    /**
     * 关联的部署任务ID
     */
    private String deploymentId;

    /**
     * 日志时间戳
     */
    private LocalDateTime timestamp;

    /**
     * 日志级别 (INFO/WARN/ERROR)
     */
    private String level;

    /**
     * 日志内容
     */
    private String content;

    /**
     * 是否为异常
     */
    private boolean isException;

    /**
     * 异常堆栈信息 (如果是异常)
     */
    private String stackTrace;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}
