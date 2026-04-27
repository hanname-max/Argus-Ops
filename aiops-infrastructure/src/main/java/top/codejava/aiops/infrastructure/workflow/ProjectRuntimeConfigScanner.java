package top.codejava.aiops.infrastructure.workflow;

import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;
import top.codejava.aiops.application.dto.WorkflowModels;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
class ProjectRuntimeConfigScanner {

    private static final Pattern PROFILE_FILE_PATTERN = Pattern.compile("^(?:application|bootstrap)-([^.]+)\\.(?:ya?ml|properties)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$?\\{([^}:]+)(?::[^}]*)?}");
    private static final Set<String> JAVA_ROOT_NAMES = Set.of("application.yml", "application.yaml", "application.properties",
            "bootstrap.yml", "bootstrap.yaml", "bootstrap.properties");

    RuntimeConfigScanResult scanJava(Path projectRoot,
                                     String preferredJavaSubpath,
                                     String confirmedConfigChoice,
                                     List<WorkflowModels.ConfigOverride> overrides) {
        Map<String, String> overrideById = toOverrideMap(overrides);
        List<ParsedConfigEntry> entries = readJavaEntries(projectRoot, preferredJavaSubpath);
        if (entries.isEmpty()) {
            return new RuntimeConfigScanResult(
                    confirmedConfigChoice,
                    List.of(),
                    List.of(new WorkflowModels.ConfigEvidence("config-scan", "java.config", "No Java runtime config file found", true)),
                    List.of()
            );
        }

        String activeProfile = resolveActiveProfile(entries, overrideById);
        Map<String, ParsedConfigEntry> effectiveEntries = resolveEffectiveEntries(entries, preferredJavaSubpath, activeProfile);
        Set<String> interestingKeys = collectInterestingJavaKeys(effectiveEntries);

        List<WorkflowModels.RuntimeConfigItem> items = interestingKeys.stream()
                .map(key -> toJavaRuntimeItem(key, effectiveEntries, entries, overrideById))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(WorkflowModels.RuntimeConfigItem::key))
                .toList();

        List<WorkflowModels.ConfigEvidence> evidences = new ArrayList<>();
        evidences.add(new WorkflowModels.ConfigEvidence("config-scan", "java.active-profile",
                activeProfile == null || activeProfile.isBlank() ? "default" : activeProfile, activeProfile == null || activeProfile.isBlank()));
        evidences.add(new WorkflowModels.ConfigEvidence("config-scan", "java.runtime-config.count", String.valueOf(items.size()), false));

        List<WorkflowModels.WorkflowWarning> warnings = items.stream()
                .filter(WorkflowModels.RuntimeConfigItem::placeholder)
                .limit(3)
                .map(item -> new WorkflowModels.WorkflowWarning(
                        "CONFIG_CONFIRM_REQUIRED",
                        WorkflowModels.Severity.MEDIUM,
                        "Runtime config " + item.key() + " should be confirmed before deployment.",
                        "Review the detected source and confirm the value in the UI."
                ))
                .toList();

        return new RuntimeConfigScanResult(confirmedConfigChoice, items, List.copyOf(evidences), warnings);
    }

    RuntimeConfigScanResult scanNginx(Path projectRoot,
                                      String confirmedConfigChoice,
                                      List<WorkflowModels.ConfigOverride> overrides) {
        Map<String, String> overrideById = toOverrideMap(overrides);
        Path configFile = resolveNginxConfig(projectRoot);
        if (configFile == null) {
            return new RuntimeConfigScanResult(
                    confirmedConfigChoice,
                    List.of(),
                    List.of(new WorkflowModels.ConfigEvidence("config-scan", "nginx.config", "No nginx.conf detected", true)),
                    List.of()
            );
        }

        List<WorkflowModels.RuntimeConfigItem> items = readNginxItems(projectRoot, configFile, overrideById);
        List<WorkflowModels.ConfigEvidence> evidences = List.of(
                new WorkflowModels.ConfigEvidence("config-scan", "nginx.config.file", projectRoot.relativize(configFile).toString(), false),
                new WorkflowModels.ConfigEvidence("config-scan", "nginx.runtime-config.count", String.valueOf(items.size()), false)
        );
        return new RuntimeConfigScanResult(confirmedConfigChoice, items, evidences, List.of());
    }

    private Map<String, String> toOverrideMap(List<WorkflowModels.ConfigOverride> overrides) {
        if (overrides == null || overrides.isEmpty()) {
            return Map.of();
        }
        return overrides.stream()
                .filter(item -> item != null && item.id() != null && !item.id().isBlank())
                .collect(Collectors.toMap(
                        WorkflowModels.ConfigOverride::id,
                        item -> item.value() == null ? "" : item.value(),
                        (left, right) -> right,
                        LinkedHashMap::new
                ));
    }

    private List<ParsedConfigEntry> readJavaEntries(Path projectRoot, String preferredJavaSubpath) {
        try (Stream<Path> stream = Files.walk(projectRoot, 7)) {
            return stream.filter(Files::isRegularFile)
                    .filter(path -> !containsIgnoredSegment(projectRoot.relativize(path)))
                    .filter(this::looksLikeJavaConfigFile)
                    .flatMap(path -> parseJavaConfigFile(projectRoot, path, preferredJavaSubpath).stream())
                    .toList();
        } catch (IOException ignored) {
            return List.of();
        }
    }

    private boolean looksLikeJavaConfigFile(Path path) {
        String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (!JAVA_ROOT_NAMES.contains(fileName) && !fileName.startsWith("application-") && !fileName.startsWith("bootstrap-")) {
            return false;
        }
        String normalized = path.toString().replace('\\', '/').toLowerCase(Locale.ROOT);
        return normalized.contains("/src/main/resources/") || normalized.contains("/config/");
    }

    private List<ParsedConfigEntry> parseJavaConfigFile(Path projectRoot, Path file, String preferredJavaSubpath) {
        String relativeFile = projectRoot.relativize(file).toString().replace('\\', '/');
        String modulePath = resolveModulePath(projectRoot, file);
        String profile = resolveProfile(file.getFileName().toString());
        String content = readText(file);
        if (content.isBlank()) {
            return List.of();
        }

        Map<String, String> flattened = file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".properties")
                ? parseProperties(content)
                : parseYaml(content);
        if (flattened.isEmpty()) {
            return List.of();
        }

        return flattened.entrySet().stream()
                .map(entry -> new ParsedConfigEntry(
                        entry.getKey(),
                        entry.getValue(),
                        modulePath,
                        relativeFile,
                        profile,
                        null,
                        computeJavaPriority(modulePath, preferredJavaSubpath)
                ))
                .toList();
    }

    private String resolveActiveProfile(List<ParsedConfigEntry> entries, Map<String, String> overrideById) {
        String overridden = overrideById.get("java:spring.profiles.active");
        if (overridden != null && !overridden.isBlank()) {
            return overridden.trim();
        }

        return entries.stream()
                .filter(entry -> "spring.profiles.active".equals(entry.key()))
                .sorted(Comparator.comparingInt((ParsedConfigEntry entry) -> entry.priority()).reversed())
                .map(ParsedConfigEntry::value)
                .filter(value -> value != null && !value.isBlank())
                .filter(value -> !isPlaceholderValue(value))
                .findFirst()
                .orElse(null);
    }

    private Map<String, ParsedConfigEntry> resolveEffectiveEntries(List<ParsedConfigEntry> entries,
                                                                   String preferredJavaSubpath,
                                                                   String activeProfile) {
        Map<String, ParsedConfigEntry> effective = new LinkedHashMap<>();
        for (ParsedConfigEntry entry : entries) {
            ParsedConfigEntry existing = effective.get(entry.key());
            if (existing == null || javaEffectiveScore(entry, preferredJavaSubpath, activeProfile)
                    > javaEffectiveScore(existing, preferredJavaSubpath, activeProfile)) {
                effective.put(entry.key(), entry);
            }
        }
        return effective;
    }

    private int javaEffectiveScore(ParsedConfigEntry entry, String preferredJavaSubpath, String activeProfile) {
        int score = entry.priority() * 100;
        if (entry.profile() == null || entry.profile().isBlank()) {
            score += 20;
        } else if (activeProfile != null && activeProfile.equalsIgnoreCase(entry.profile())) {
            score += 40;
        } else {
            score += 5;
        }
        if ("spring.profiles.active".equals(entry.key()) && (entry.profile() == null || entry.profile().isBlank())) {
            score += 10;
        }
        if (preferredJavaSubpath != null && preferredJavaSubpath.equals(entry.modulePath())) {
            score += 15;
        }
        return score;
    }

    private Set<String> collectInterestingJavaKeys(Map<String, ParsedConfigEntry> effectiveEntries) {
        Set<String> keys = new LinkedHashSet<>();
        Deque<String> queue = new ArrayDeque<>();

        effectiveEntries.forEach((key, entry) -> {
            if (isInterestingJavaKey(key)) {
                Set<String> referencedKeys = extractPlaceholderKeys(entry.value());
                if (!referencedKeys.isEmpty()) {
                    boolean hasResolvableReference = referencedKeys.stream().anyMatch(effectiveEntries::containsKey);
                    referencedKeys.forEach(referencedKey -> {
                        if (keys.add(referencedKey)) {
                            queue.add(referencedKey);
                        }
                    });
                    if (!hasResolvableReference && keys.add(key)) {
                        queue.add(key);
                    }
                } else if (keys.add(key)) {
                    queue.add(key);
                }
            }
        });

        while (!queue.isEmpty()) {
            String currentKey = queue.removeFirst();
            ParsedConfigEntry entry = effectiveEntries.get(currentKey);
            if (entry == null) {
                continue;
            }
            for (String referencedKey : extractPlaceholderKeys(entry.value())) {
                if (keys.add(referencedKey)) {
                    queue.add(referencedKey);
                }
            }
        }

        return keys;
    }

    private WorkflowModels.RuntimeConfigItem toJavaRuntimeItem(String key,
                                                               Map<String, ParsedConfigEntry> effectiveEntries,
                                                               List<ParsedConfigEntry> allEntries,
                                                               Map<String, String> overrideById) {
        ParsedConfigEntry effective = effectiveEntries.get(key);
        if (effective == null) {
            return null;
        }

        String id = "java:" + key;
        String displayValue = overrideById.containsKey(id) ? overrideById.get(id) : effective.value();
        List<WorkflowModels.ConfigSourceEvidence> sources = allEntries.stream()
                .filter(entry -> key.equals(entry.key()))
                .sorted(Comparator.comparingInt(ParsedConfigEntry::priority).reversed())
                .map(entry -> new WorkflowModels.ConfigSourceEvidence(
                        entry.key(),
                        sanitizeValuePreview(entry.key(), entry.value()),
                        entry.modulePath(),
                        entry.sourceFile(),
                        entry.profile(),
                        entry.sourceLine(),
                        entry.equals(effective)
                ))
                .toList();

        return new WorkflowModels.RuntimeConfigItem(
                id,
                "JAVA",
                "SPRING_ENV",
                key,
                key,
                displayValue,
                effective.modulePath(),
                effective.sourceFile(),
                effective.profile(),
                effective.sourceLine(),
                effective.value(),
                isSensitiveKey(key),
                isPlaceholderValue(effective.value()),
                requiresJavaConfirmation(key, effective.value()),
                buildJavaOperatorHint(key, effective.value()),
                sources
        );
    }

    private List<WorkflowModels.RuntimeConfigItem> readNginxItems(Path projectRoot,
                                                                  Path configFile,
                                                                  Map<String, String> overrideById) {
        List<String> lines = readLines(configFile);
        if (lines.isEmpty()) {
            return List.of();
        }

        String relativeFile = projectRoot.relativize(configFile).toString().replace('\\', '/');
        String modulePath = resolveModulePath(projectRoot, configFile);
        List<WorkflowModels.RuntimeConfigItem> items = new ArrayList<>();
        Deque<BlockContext> contextStack = new ArrayDeque<>();

        for (int index = 0; index < lines.size(); index++) {
            String rawLine = lines.get(index);
            String stripped = stripInlineComment(rawLine).trim();
            if (stripped.isBlank()) {
                continue;
            }

            if (stripped.endsWith("{")) {
                parseBlockStart(stripped).ifPresent(contextStack::addLast);
                continue;
            }

            if (stripped.startsWith("}")) {
                if (!contextStack.isEmpty()) {
                    contextStack.removeLast();
                }
                continue;
            }

            Matcher directiveMatcher = Pattern.compile("^([a-zA-Z_]+)\\s+(.+);$").matcher(stripped);
            if (!directiveMatcher.matches()) {
                continue;
            }

            String directive = directiveMatcher.group(1).trim();
            String value = directiveMatcher.group(2).trim();
            String key = toNginxKey(directive, contextStack);
            if (key == null) {
                continue;
            }

            String id = "nginx:" + relativeFile + ":" + (index + 1) + ":" + directive;
            String displayValue = overrideById.containsKey(id) ? overrideById.get(id) : value;
            items.add(new WorkflowModels.RuntimeConfigItem(
                    id,
                    "NGINX",
                    "NGINX_DIRECTIVE",
                    key,
                    key,
                    displayValue,
                    modulePath,
                    relativeFile,
                    null,
                    index + 1,
                    value,
                    false,
                    isPlaceholderValue(value),
                    true,
                    buildNginxOperatorHint(directive, value),
                    List.of(new WorkflowModels.ConfigSourceEvidence(
                            key,
                            value,
                            modulePath,
                            relativeFile,
                            null,
                            index + 1,
                            true
                    ))
            ));
        }

        return List.copyOf(items);
    }

    private Optional<BlockContext> parseBlockStart(String strippedLine) {
        Matcher upstreamMatcher = Pattern.compile("^upstream\\s+([^\\s{]+)\\s*\\{$").matcher(strippedLine);
        if (upstreamMatcher.matches()) {
            return Optional.of(new BlockContext("upstream", upstreamMatcher.group(1).trim()));
        }
        Matcher locationMatcher = Pattern.compile("^location\\s+([^\\s{]+)\\s*\\{$").matcher(strippedLine);
        if (locationMatcher.matches()) {
            return Optional.of(new BlockContext("location", locationMatcher.group(1).trim()));
        }
        Matcher serverMatcher = Pattern.compile("^server\\s*\\{$").matcher(strippedLine);
        if (serverMatcher.matches()) {
            return Optional.of(new BlockContext("server", "server"));
        }
        return Optional.empty();
    }

    private String toNginxKey(String directive, Deque<BlockContext> contextStack) {
        if ("listen".equals(directive) || "root".equals(directive)) {
            return "nginx." + directive;
        }
        if ("proxy_pass".equals(directive)) {
            BlockContext location = contextStack.stream()
                    .filter(context -> "location".equals(context.type()))
                    .reduce((first, second) -> second)
                    .orElse(null);
            return location == null
                    ? "nginx.proxy_pass"
                    : "nginx.location[" + location.name() + "].proxy_pass";
        }
        if ("server".equals(directive)) {
            BlockContext upstream = contextStack.stream()
                    .filter(context -> "upstream".equals(context.type()))
                    .reduce((first, second) -> second)
                    .orElse(null);
            if (upstream != null) {
                return "nginx.upstream[" + upstream.name() + "].server";
            }
        }
        return null;
    }

    private String buildJavaOperatorHint(String key, String value) {
        String lowerKey = key.toLowerCase(Locale.ROOT);
        if ("spring.profiles.active".equals(lowerKey)) {
            return "确认运行时 profile，避免部署脚本覆盖项目默认激活环境。";
        }
        if (lowerKey.contains("datasource") || lowerKey.contains("jdbc")) {
            return "确认数据库地址、库名、用户名和密码与目标环境一致。";
        }
        if (lowerKey.contains("redis")) {
            return "确认 Redis 地址、端口和密码与目标环境一致。";
        }
        if ("server.port".equals(lowerKey)) {
            return "确认应用端口是否应继续由部署端口接管。";
        }
        if (isPlaceholderValue(value)) {
            return "当前值来自占位符引用，建议部署前人工确认。";
        }
        return "部署前确认该配置是否需要按目标环境覆写。";
    }

    private String buildNginxOperatorHint(String directive, String value) {
        if ("proxy_pass".equals(directive) || "server".equals(directive)) {
            return "确认回源地址与端口与后端实际部署结果一致。";
        }
        if ("listen".equals(directive)) {
            return "确认 Nginx 暴露端口与本次前端部署端口一致。";
        }
        if ("root".equals(directive)) {
            return "确认静态资源根目录与最终镜像内路径一致。";
        }
        if (isPlaceholderValue(value)) {
            return "当前值包含占位符，建议部署前人工确认。";
        }
        return "确认该 Nginx 指令是否需要按目标环境覆写。";
    }

    private boolean requiresJavaConfirmation(String key, String value) {
        String lowerKey = key.toLowerCase(Locale.ROOT);
        return isPlaceholderValue(value)
                || lowerKey.contains("datasource")
                || lowerKey.contains("redis")
                || "spring.profiles.active".equals(lowerKey)
                || "server.port".equals(lowerKey);
    }

    private boolean isInterestingJavaKey(String key) {
        String lowerKey = key.toLowerCase(Locale.ROOT);
        return "spring.profiles.active".equals(lowerKey)
                || "server.port".equals(lowerKey)
                || lowerKey.contains("datasource")
                || lowerKey.contains("redis")
                || lowerKey.contains("jdbc")
                || lowerKey.contains("mysql")
                || lowerKey.contains("postgres")
                || lowerKey.contains("oracle")
                || lowerKey.contains("mongodb")
                || lowerKey.contains("rabbit")
                || lowerKey.contains("kafka")
                || lowerKey.contains("oss")
                || lowerKey.contains("wechat");
    }

    private boolean isSensitiveKey(String key) {
        String lowerKey = key.toLowerCase(Locale.ROOT);
        return lowerKey.contains("password")
                || lowerKey.contains("secret")
                || lowerKey.endsWith("token")
                || lowerKey.contains("access-key")
                || lowerKey.contains("accesskey")
                || lowerKey.contains("private-key");
    }

    private Set<String> extractPlaceholderKeys(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(value);
        Set<String> keys = new LinkedHashSet<>();
        while (matcher.find()) {
            keys.add(matcher.group(1).trim());
        }
        return keys;
    }

    private boolean isPlaceholderValue(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        return value.contains("${") || (value.startsWith("{") && value.endsWith("}"));
    }

    private Map<String, String> parseProperties(String content) {
        Properties properties = new Properties();
        try {
            properties.load(new StringReader(content));
        } catch (IOException ignored) {
            return Map.of();
        }
        Map<String, String> flattened = new LinkedHashMap<>();
        properties.forEach((key, value) -> flattened.put(String.valueOf(key), String.valueOf(value)));
        return flattened;
    }

    private Map<String, String> parseYaml(String content) {
        Object loaded = new Yaml().load(normalizeYamlPlaceholders(content));
        if (!(loaded instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, String> flattened = new LinkedHashMap<>();
        flattenYaml("", map, flattened);
        return flattened;
    }

    private String normalizeYamlPlaceholders(String content) {
        List<String> normalized = new ArrayList<>();
        for (String line : content.split("\\R", -1)) {
            normalized.add(line.replaceAll("(:\\s*)(\\{[^{}]+})(\\s*)$", "$1\"$2\"$3"));
        }
        return String.join("\n", normalized);
    }

    @SuppressWarnings("unchecked")
    private void flattenYaml(String prefix, Object value, Map<String, String> flattened) {
        if (value instanceof Map<?, ?> map) {
            map.forEach((childKey, childValue) -> {
                String nextPrefix = prefix.isBlank()
                        ? String.valueOf(childKey)
                        : prefix + "." + childKey;
                flattenYaml(nextPrefix, childValue, flattened);
            });
            return;
        }
        if (value instanceof List<?> list) {
            for (int index = 0; index < list.size(); index++) {
                flattenYaml(prefix + "[" + index + "]", list.get(index), flattened);
            }
            return;
        }
        flattened.put(prefix, value == null ? "" : String.valueOf(value));
    }

    private String resolveModulePath(Path projectRoot, Path file) {
        Path current = file.getParent();
        while (current != null && current.startsWith(projectRoot)) {
            if (Files.isRegularFile(current.resolve("pom.xml"))
                    || Files.isRegularFile(current.resolve("build.gradle"))
                    || Files.isRegularFile(current.resolve("build.gradle.kts"))) {
                Path relative = projectRoot.relativize(current);
                return relative.toString().isBlank() ? "." : relative.toString().replace('\\', '/');
            }
            current = current.getParent();
        }
        return ".";
    }

    private int computeJavaPriority(String modulePath, String preferredJavaSubpath) {
        if (preferredJavaSubpath != null && preferredJavaSubpath.equals(modulePath)) {
            return 3;
        }
        if (".".equals(modulePath)) {
            return 2;
        }
        return 1;
    }

    private String resolveProfile(String fileName) {
        Matcher matcher = PROFILE_FILE_PATTERN.matcher(fileName);
        return matcher.matches() ? matcher.group(1).trim() : null;
    }

    private Path resolveNginxConfig(Path projectRoot) {
        Path preferred = projectRoot.resolve("conf/nginx.conf");
        if (Files.isRegularFile(preferred)) {
            return preferred;
        }
        Path fallback = projectRoot.resolve("nginx.conf");
        return Files.isRegularFile(fallback) ? fallback : null;
    }

    private String sanitizeValuePreview(String key, String value) {
        return isSensitiveKey(key) ? "******" : value;
    }

    private boolean containsIgnoredSegment(Path relativePath) {
        for (Path segment : relativePath) {
            String name = segment.toString().toLowerCase(Locale.ROOT);
            if (Set.of(".git", "target", "build", "node_modules", ".idea", ".build").contains(name)) {
                return true;
            }
        }
        return false;
    }

    private String readText(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException ignored) {
            return "";
        }
    }

    private List<String> readLines(Path path) {
        try {
            return Files.readAllLines(path);
        } catch (IOException ignored) {
            return List.of();
        }
    }

    private String stripInlineComment(String rawLine) {
        StringBuilder builder = new StringBuilder();
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        for (int index = 0; index < rawLine.length(); index++) {
            char current = rawLine.charAt(index);
            if (current == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote;
            } else if (current == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote;
            } else if (current == '#' && !inSingleQuote && !inDoubleQuote) {
                break;
            }
            builder.append(current);
        }
        return builder.toString();
    }

    record RuntimeConfigScanResult(
            String confirmedConfigChoice,
            List<WorkflowModels.RuntimeConfigItem> runtimeConfigItems,
            List<WorkflowModels.ConfigEvidence> evidences,
            List<WorkflowModels.WorkflowWarning> warnings
    ) {
    }

    private record ParsedConfigEntry(
            String key,
            String value,
            String modulePath,
            String sourceFile,
            String profile,
            Integer sourceLine,
            int priority
    ) {
    }

    private record BlockContext(
            String type,
            String name
    ) {
    }
}
