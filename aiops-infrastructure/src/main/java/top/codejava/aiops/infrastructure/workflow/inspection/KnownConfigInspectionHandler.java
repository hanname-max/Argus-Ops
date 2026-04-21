package top.codejava.aiops.infrastructure.workflow.inspection;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Order(30)
public class KnownConfigInspectionHandler implements LocalProjectInspectionHandler {

    private static final Pattern KEY_VALUE_PATTERN = Pattern.compile("(?m)^\\s*([A-Za-z0-9._-]+)\\s*[:=]\\s*(.+?)\\s*$");

    @Override
    public boolean supports(LocalProjectInspectionContext context) {
        return context.isBackendProject() && context.requiredInputs().isEmpty();
    }

    @Override
    public void handle(LocalProjectInspectionContext context) {
        for (ConfigFileSnapshot configFile : context.configFiles()) {
            Matcher matcher = KEY_VALUE_PATTERN.matcher(configFile.content());
            while (matcher.find()) {
                String key = matcher.group(1).trim();
                String value = matcher.group(2).trim();
                String category = classifyCategory(key, value);
                if (category == null) {
                    continue;
                }
                context.addDetectedCandidate(
                        category,
                        configFile.path().toString(),
                        key,
                        abbreviate(value),
                        "Detected " + category + " configuration candidate from " + configFile.path(),
                        false,
                        true
                );
                context.addEvidence("config-scan", key, configFile.path().toString(), false);
            }
        }
    }

    private String classifyCategory(String key, String value) {
        String combined = (key + " " + value).toLowerCase(Locale.ROOT);
        if (combined.contains("mysql") || combined.contains("jdbc:") || combined.contains("datasource") || combined.contains("postgres")) {
            return "MYSQL";
        }
        if (combined.contains("redis") || combined.contains("lettuce") || combined.contains("jedis")) {
            return "REDIS";
        }
        if (combined.contains("nacos")
                || combined.contains("eureka")
                || combined.contains("consul")
                || combined.contains("zookeeper")
                || combined.contains("rabbitmq")
                || combined.contains("kafka")
                || combined.contains("rocketmq")
                || combined.contains("dubbo")
                || combined.contains("feign")
                || combined.contains("seata")
                || combined.contains("xxl")
                || combined.contains("minio")
                || combined.contains("oss")) {
            return "DISTRIBUTED";
        }
        if (combined.contains("openai")
                || combined.contains("deepseek")
                || combined.contains("dashscope")
                || combined.contains("glm")
                || combined.contains("qwen")
                || combined.contains("api-key")
                || combined.contains("apikey")) {
            return "AI";
        }
        return null;
    }

    private String abbreviate(String value) {
        if (value.length() <= 120) {
            return value;
        }
        return value.substring(0, 117) + "...";
    }
}
