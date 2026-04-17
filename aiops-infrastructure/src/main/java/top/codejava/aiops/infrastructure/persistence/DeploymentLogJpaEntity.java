// aiops-infrastructure/src/main/java/top/codejava/aiops/infrastructure/persistence/DeploymentLogJpaEntity.java
package top.codejava.aiops.infrastructure.persistence;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 部署日志 JPA 实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "deployment_log")
public class DeploymentLogJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "deployment_id", length = 64, nullable = false)
    private String deploymentId;

    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    @Column(name = "level", length = 10, nullable = false)
    private String level;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "is_exception", nullable = false)
    private boolean isException;

    @Column(name = "stack_trace", columnDefinition = "TEXT")
    private String stackTrace;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }
}
