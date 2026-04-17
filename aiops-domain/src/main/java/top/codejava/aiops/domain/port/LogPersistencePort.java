// aiops-domain/src/main/java/top/codejava/aiops/domain/port/LogPersistencePort.java
package top.codejava.aiops.domain.port;

import top.codejava.aiops.domain.model.DeploymentLog;

import java.util.List;
import java.util.Optional;

/**
 * 日志持久化端口
 * 遵循六边形架构，领域层定义接口，基础设施层实现
 */
public interface LogPersistencePort {

    /**
     * 保存一条日志
     */
    DeploymentLog save(DeploymentLog log);

    /**
     * 根据部署任务ID查询所有日志
     */
    List<DeploymentLog> findByDeploymentId(String deploymentId);

    /**
     * 根据ID查询日志
     */
    Optional<DeploymentLog> findById(Long id);

    /**
     * 查询所有异常日志
     */
    List<DeploymentLog> findExceptionsByDeploymentId(String deploymentId);

    /**
     * 删除部署任务的所有日志
     */
    void deleteByDeploymentId(String deploymentId);
}
