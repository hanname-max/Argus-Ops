package top.codejava.aiops.infrastructure.workflow.inspection;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Order(20)
public class ConfigPlaceholderInspectionHandler implements LocalProjectInspectionHandler {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{([^}]+)}");
    private static final Pattern JAVA_ENV_PATTERN = Pattern.compile("System\\.getenv\\([\"']([^\"']+)[\"']\\)");
    private static final Pattern PYTHON_ENV_PATTERN = Pattern.compile("os\\.getenv\\([\"']([^\"']+)[\"']\\)");
    private static final Pattern NODE_ENV_PATTERN = Pattern.compile("process\\.env(?:\\.([A-Za-z0-9_]+)|\\[['\"]([A-Za-z0-9_]+)['\"]])");

    @Override
    public boolean supports(LocalProjectInspectionContext context) {
        return true;
    }

    @Override
    public void handle(LocalProjectInspectionContext context) {
        for (ConfigFileSnapshot configFile : context.configFiles()) {
            collectMatches(context, configFile.path().toString(), configFile.content(), PLACEHOLDER_PATTERN);
            collectMatches(context, configFile.path().toString(), configFile.content(), JAVA_ENV_PATTERN);
            collectMatches(context, configFile.path().toString(), configFile.content(), PYTHON_ENV_PATTERN);
            collectNodeMatches(context, configFile.path().toString(), configFile.content());
        }
    }

    private void collectMatches(LocalProjectInspectionContext context,
                                String location,
                                String content,
                                Pattern pattern) {
        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            String rawKey = matcher.group(1);
            if (rawKey == null || rawKey.isBlank()) {
                continue;
            }
            String key = rawKey.split("[:,-]", 2)[0].trim();
            if (!looksSecretLike(key)) {
                continue;
            }
            context.addRequiredInput(
                    key,
                    "Runtime Secret: " + key,
                    "Detected a placeholder secret in " + location + ". Provide the runtime value before deployment.",
                    key
            );
            context.addEvidence("config-placeholder", key, location, true);
        }
    }

    private void collectNodeMatches(LocalProjectInspectionContext context, String location, String content) {
        Matcher matcher = NODE_ENV_PATTERN.matcher(content);
        while (matcher.find()) {
            String key = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            if (key == null || key.isBlank() || !looksSecretLike(key)) {
                continue;
            }
            context.addRequiredInput(
                    key,
                    "Runtime Secret: " + key,
                    "Detected a Node.js environment lookup in " + location + ". Provide the runtime value before deployment.",
                    key
            );
            context.addEvidence("env-access", key, location, true);
        }
    }

    private boolean looksSecretLike(String key) {
        String normalized = key.toLowerCase(Locale.ROOT);
        return normalized.contains("api")
                || normalized.contains("key")
                || normalized.contains("token")
                || normalized.contains("secret")
                || normalized.contains("openai")
                || normalized.contains("deepseek")
                || normalized.contains("dashscope")
                || normalized.contains("glm")
                || normalized.contains("qwen")
                || normalized.contains("ai");
    }
}
