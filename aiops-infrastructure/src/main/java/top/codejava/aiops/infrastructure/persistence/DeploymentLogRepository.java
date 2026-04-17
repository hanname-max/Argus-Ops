// aiops-infrastructure/src/main/java/top/codejava/aiops/infrastructure/persistence/DeploymentLogRepository.java
package top.codejava.aiops.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 部署日志 Spring Data JPA 仓库
 */
public interface DeploymentLogRepository extends JpaRepository<DeploymentLogJpaEntity, Long> {

    /**
     * 根据部署任务ID查询所有日志
     */
    List<DeploymentLogJpaEntity> findByDeploymentIdOrderByTimestampAsc(String deploymentId);

    /**
     * 根据部署任务ID查询所有异常日志
     */
    @Query("SELECT d FROM DeploymentLogJpaEntity d WHERE d.deploymentId = :deploymentId AND d.isException = true")
    List<DeploymentLogJpaEntity> findExceptionsByDeploymentId(@Param("deploymentId") String deploymentId);

    /**
     * 删除部署任务的所有日志
     */
    void deleteByDeploymentId(String deploymentId);
}
