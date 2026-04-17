// aiops-infrastructure/src/main/java/top/codejava/aiops/infrastructure/adapter/H2LogRepositoryAdapter.java
package top.codejava.aiops.infrastructure.adapter;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import top.codejava.aiops.domain.model.DeploymentLog;
import top.codejava.aiops.domain.port.LogPersistencePort;
import top.codejava.aiops.infrastructure.persistence.DeploymentLogJpaEntity;
import top.codejava.aiops.infrastructure.persistence.DeploymentLogRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 基于 H2 + Spring Data JPA 的日志持久化适配器
 * 实现领域层的 LogPersistencePort 接口
 */
@Component
public class H2LogRepositoryAdapter implements LogPersistencePort {

    private final DeploymentLogRepository repository;

    public H2LogRepositoryAdapter(DeploymentLogRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public DeploymentLog save(DeploymentLog log) {
        DeploymentLogJpaEntity entity = toEntity(log);
        DeploymentLogJpaEntity saved = repository.save(entity);
        return toDomain(saved);
    }

    @Override
    public List<DeploymentLog> findByDeploymentId(String deploymentId) {
        return repository.findByDeploymentIdOrderByTimestampAsc(deploymentId)
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public Optional<DeploymentLog> findById(Long id) {
        return repository.findById(id)
                .map(this::toDomain);
    }

    @Override
    public List<DeploymentLog> findExceptionsByDeploymentId(String deploymentId) {
        return repository.findExceptionsByDeploymentId(deploymentId)
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public void deleteByDeploymentId(String deploymentId) {
        repository.deleteByDeploymentId(deploymentId);
    }

    private DeploymentLogJpaEntity toEntity(DeploymentLog domain) {
        return DeploymentLogJpaEntity.builder()
                .id(domain.getId())
                .deploymentId(domain.getDeploymentId())
                .timestamp(domain.getTimestamp() != null ? domain.getTimestamp() : LocalDateTime.now())
                .level(domain.getLevel())
                .content(domain.getContent())
                .isException(domain.isException())
                .stackTrace(domain.getStackTrace())
                .createdAt(domain.getCreatedAt() != null ? domain.getCreatedAt() : LocalDateTime.now())
                .build();
    }

    private DeploymentLog toDomain(DeploymentLogJpaEntity entity) {
        return DeploymentLog.builder()
                .id(entity.getId())
                .deploymentId(entity.getDeploymentId())
                .timestamp(entity.getTimestamp())
                .level(entity.getLevel())
                .content(entity.getContent())
                .isException(entity.isException())
                .stackTrace(entity.getStackTrace())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
