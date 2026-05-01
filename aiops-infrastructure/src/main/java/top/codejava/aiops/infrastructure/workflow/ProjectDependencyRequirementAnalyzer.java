package top.codejava.aiops.infrastructure.workflow;

import org.springframework.stereotype.Component;
import top.codejava.aiops.application.dto.WorkflowModels;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
class ProjectDependencyRequirementAnalyzer {

    List<WorkflowModels.DependencyRequirement> analyze(List<WorkflowModels.RuntimeConfigItem> runtimeConfigItems) {
        if (runtimeConfigItems == null || runtimeConfigItems.isEmpty()) {
            return List.of();
        }

        Map<String, WorkflowModels.RuntimeConfigItem> javaItems = new LinkedHashMap<>();
        runtimeConfigItems.stream()
                .filter(item -> item != null && "JAVA".equalsIgnoreCase(item.family()))
                .forEach(item -> javaItems.put(item.key(), item));

        List<WorkflowModels.DependencyRequirement> requirements = new ArrayList<>();

        WorkflowModels.DependencyRequirement mysql = mysqlRequirement(javaItems);
        if (mysql != null) {
            requirements.add(mysql);
        }

        WorkflowModels.DependencyRequirement redis = redisRequirement(javaItems);
        if (redis != null) {
            requirements.add(redis);
        }

        return List.copyOf(requirements);
    }

    private WorkflowModels.DependencyRequirement mysqlRequirement(Map<String, WorkflowModels.RuntimeConfigItem> javaItems) {
        WorkflowModels.RuntimeConfigItem hostItem = first(javaItems,
                "sky.datasource.host",
                "spring.datasource.host");
        WorkflowModels.RuntimeConfigItem portItem = first(javaItems,
                "sky.datasource.port",
                "spring.datasource.port");
        WorkflowModels.RuntimeConfigItem dbItem = first(javaItems,
                "sky.datasource.database",
                "spring.datasource.name",
                "spring.datasource.database");
        WorkflowModels.RuntimeConfigItem driverItem = first(javaItems,
                "sky.datasource.driver-class-name",
                "spring.datasource.driver-class-name",
                "spring.datasource.druid.driver-class-name",
                "spring.datasource.hikari.driver-class-name");
        WorkflowModels.RuntimeConfigItem urlItem = first(javaItems,
                "spring.datasource.url",
                "spring.datasource.druid.url",
                "spring.datasource.hikari.jdbc-url",
                "spring.datasource.jdbc-url");

        if (hostItem == null && portItem == null && dbItem == null && driverItem == null && urlItem == null) {
            return null;
        }

        String host = valueOf(hostItem);
        Integer port = parseInteger(valueOf(portItem), 3306);
        String databaseName = valueOf(dbItem);

        if ((host == null || host.isBlank()) && urlItem != null) {
            JdbcEndpoint endpoint = parseJdbcEndpoint(urlItem.valuePreview());
            host = endpoint.host();
            if (endpoint.port() != null) {
                port = endpoint.port();
            }
            if (databaseName == null || databaseName.isBlank()) {
                databaseName = endpoint.databaseName();
            }
        }

        if (!looksMysql(driverItem, urlItem, hostItem, dbItem, portItem)) {
            return null;
        }

        WorkflowModels.RuntimeConfigItem source = firstNonNull(hostItem, urlItem, driverItem, dbItem, portItem);
        return new WorkflowModels.DependencyRequirement(
                WorkflowModels.DependencyKind.MYSQL,
                "MySQL",
                true,
                host == null || host.isBlank() ? null : host,
                port == null ? 3306 : port,
                databaseName,
                source == null ? "spring.datasource" : source.key(),
                source == null ? "." : source.sourceModule(),
                source == null ? "" : source.sourceFile(),
                source == null ? null : source.sourceProfile(),
                "Probe whether MySQL is reachable on the target host. If not, ask whether to provision a local MySQL instance just for startup."
        );
    }

    private WorkflowModels.DependencyRequirement redisRequirement(Map<String, WorkflowModels.RuntimeConfigItem> javaItems) {
        WorkflowModels.RuntimeConfigItem hostItem = first(javaItems,
                "sky.redis.host",
                "spring.redis.host",
                "spring.data.redis.host");
        WorkflowModels.RuntimeConfigItem portItem = first(javaItems,
                "sky.redis.port",
                "spring.redis.port",
                "spring.data.redis.port");
        WorkflowModels.RuntimeConfigItem urlItem = first(javaItems,
                "sky.redis.url",
                "spring.redis.url",
                "spring.data.redis.url");
        WorkflowModels.RuntimeConfigItem passwordItem = first(javaItems,
                "sky.redis.password",
                "spring.redis.password",
                "spring.data.redis.password");

        if (hostItem == null && portItem == null && urlItem == null && passwordItem == null) {
            return null;
        }

        String host = valueOf(hostItem);
        Integer port = parseInteger(valueOf(portItem), 6379);
        if ((host == null || host.isBlank() || portItem == null) && urlItem != null) {
            RedisEndpoint endpoint = parseRedisEndpoint(urlItem.valuePreview());
            if (host == null || host.isBlank()) {
                host = endpoint.host();
            }
            if (endpoint.port() != null) {
                port = endpoint.port();
            }
        }

        if (!looksRedis(urlItem, hostItem, portItem, passwordItem)) {
            return null;
        }

        WorkflowModels.RuntimeConfigItem source = firstNonNull(hostItem, urlItem, portItem, passwordItem);
        return new WorkflowModels.DependencyRequirement(
                WorkflowModels.DependencyKind.REDIS,
                "Redis",
                true,
                host == null || host.isBlank() ? null : host,
                port == null ? 6379 : port,
                null,
                source == null ? "spring.redis" : source.key(),
                source == null ? "." : source.sourceModule(),
                source == null ? "" : source.sourceFile(),
                source == null ? null : source.sourceProfile(),
                "Probe whether Redis is reachable on the target host. If not, ask whether to provision a local Redis instance just for startup."
        );
    }

    private boolean looksMysql(WorkflowModels.RuntimeConfigItem driverItem,
                               WorkflowModels.RuntimeConfigItem urlItem,
                               WorkflowModels.RuntimeConfigItem hostItem,
                               WorkflowModels.RuntimeConfigItem dbItem,
                               WorkflowModels.RuntimeConfigItem portItem) {
        return containsMysql(valueOf(driverItem))
                || containsMysql(valueOf(urlItem))
                || hostItem != null
                || dbItem != null
                || portItem != null;
    }

    private boolean looksRedis(WorkflowModels.RuntimeConfigItem urlItem,
                               WorkflowModels.RuntimeConfigItem hostItem,
                               WorkflowModels.RuntimeConfigItem portItem,
                               WorkflowModels.RuntimeConfigItem passwordItem) {
        return containsRedis(valueOf(urlItem))
                || hostItem != null
                || portItem != null
                || passwordItem != null;
    }

    private boolean containsMysql(String value) {
        return value != null && value.toLowerCase(Locale.ROOT).contains("mysql");
    }

    private boolean containsRedis(String value) {
        return value != null && value.toLowerCase(Locale.ROOT).contains("redis");
    }

    private WorkflowModels.RuntimeConfigItem first(Map<String, WorkflowModels.RuntimeConfigItem> javaItems, String... keys) {
        for (String key : keys) {
            WorkflowModels.RuntimeConfigItem item = javaItems.get(key);
            if (item != null) {
                return item;
            }
        }
        return null;
    }

    private WorkflowModels.RuntimeConfigItem firstNonNull(WorkflowModels.RuntimeConfigItem... items) {
        for (WorkflowModels.RuntimeConfigItem item : items) {
            if (item != null) {
                return item;
            }
        }
        return null;
    }

    private String valueOf(WorkflowModels.RuntimeConfigItem item) {
        return item == null ? null : item.valuePreview();
    }

    private Integer parseInteger(String rawValue, Integer defaultValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(rawValue.trim());
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private JdbcEndpoint parseJdbcEndpoint(String jdbcUrl) {
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            return new JdbcEndpoint(null, null, null);
        }
        String trimmed = jdbcUrl.trim();
        int prefixIndex = trimmed.indexOf("://");
        if (prefixIndex < 0) {
            return new JdbcEndpoint(null, null, null);
        }
        String target = trimmed.substring(prefixIndex + 3);
        int slashIndex = target.indexOf('/');
        if (slashIndex < 0) {
            return new JdbcEndpoint(null, null, null);
        }
        String hostPort = target.substring(0, slashIndex);
        String databasePart = target.substring(slashIndex + 1);
        int queryIndex = databasePart.indexOf('?');
        String databaseName = queryIndex >= 0 ? databasePart.substring(0, queryIndex) : databasePart;
        int colonIndex = hostPort.lastIndexOf(':');
        if (colonIndex < 0) {
            return new JdbcEndpoint(hostPort, 3306, databaseName);
        }
        Integer port = parseInteger(hostPort.substring(colonIndex + 1), 3306);
        return new JdbcEndpoint(hostPort.substring(0, colonIndex), port, databaseName);
    }

    private RedisEndpoint parseRedisEndpoint(String redisUrl) {
        if (redisUrl == null || redisUrl.isBlank()) {
            return new RedisEndpoint(null, null);
        }

        String normalized = redisUrl.trim();
        if (!normalized.contains("://")) {
            normalized = "redis://" + normalized;
        }

        try {
            URI uri = URI.create(normalized);
            return new RedisEndpoint(uri.getHost(), uri.getPort() > 0 ? uri.getPort() : 6379);
        } catch (IllegalArgumentException ignored) {
            return new RedisEndpoint(null, null);
        }
    }

    private record JdbcEndpoint(
            String host,
            Integer port,
            String databaseName
    ) {
    }

    private record RedisEndpoint(
            String host,
            Integer port
    ) {
    }
}
