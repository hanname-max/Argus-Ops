// aiops-infrastructure/src/main/java/top/codejava/aiops/infrastructure/adapter/LocalEnvironmentScannerAdapter.java
package top.codejava.aiops.infrastructure.adapter;

import lombok.extern.slf4j.Slf4j;
import top.codejava.aiops.application.port.LocalEnvironmentPort;
import top.codejava.aiops.domain.model.ProjectContext;

import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;

/**
 * 本地环境扫描适配器
 * 使用 Java NIO 扫描目标目录，识别项目特征
 */
@Slf4j
public class LocalEnvironmentScannerAdapter implements LocalEnvironmentPort {

    private static final int MAX_DEPTH = 5;
    private static final long MAX_FILES_TO_SCAN = 1000;

    @Override
    public ProjectContext scanProject(Path rootPath) {
        ProjectContext.ProjectContextBuilder builder = ProjectContext.builder()
                .rootPath(rootPath);

        // 检测根目录下的关键文件
        builder.hasPomXml(Files.exists(rootPath.resolve("pom.xml")));
        builder.hasBuildGradle(Files.exists(rootPath.resolve("build.gradle")) ||
                               Files.exists(rootPath.resolve("build.gradle.kts")));
        builder.hasDockerfile(Files.exists(rootPath.resolve("Dockerfile")));
        builder.hasDockerCompose(Files.exists(rootPath.resolve("docker-compose.yml")) ||
                                 Files.exists(rootPath.resolve("docker-compose.yaml")));

        // 检测构建工具
        if (builder.build().isHasPomXml()) {
            builder.buildTool("maven");
        } else if (builder.build().isHasBuildGradle()) {
            builder.buildTool("gradle");
        } else {
            builder.buildTool("none");
        }

        // 深度扫描检测语言和配置文件
        try {
            List<String> configFiles = new ArrayList<>();
            detectLanguageAndConfig(rootPath, builder, configFiles);
            builder.configFiles(configFiles);
        } catch (IOException e) {
            log.warn("Failed to deep scan directory: {}", e.getMessage());
        }

        return builder.build();
    }

    /**
     * 深度扫描检测编程语言和配置文件
     */
    private void detectLanguageAndConfig(Path rootPath, ProjectContext.ProjectContextBuilder builder, List<String> configFiles) throws IOException {
        final int[] counts = new int[3]; // javaCount[0], pythonCount[1], jsCount[2]
        final long[] totalLines = new long[1];
        counts[0] = 0;
        counts[1] = 0;
        counts[2] = 0;
        totalLines[0] = 0;

        BiPredicate<Path, BasicFileAttributes> matcher = (path, attrs) -> {
            if (!attrs.isRegularFile()) return false;

            String fileName = path.getFileName().toString().toLowerCase();

            // 统计语言扩展名
            if (fileName.endsWith(".java")) counts[0]++;
            else if (fileName.endsWith(".py")) counts[1]++;
            else if (fileName.endsWith(".js") || fileName.endsWith(".ts")) counts[2]++;

            // 收集配置文件
            if (isConfigFile(fileName)) {
                configFiles.add(rootPath.relativize(path).toString());
            }

            return true;
        };

        // 限制扫描深度和文件数量避免性能问题
        List<Path> matchedFiles = Files.find(rootPath, MAX_DEPTH, matcher)
                .limit(MAX_FILES_TO_SCAN)
                .collect(Collectors.toList());

        // 估算代码行数（粗略）
        for (Path file : matchedFiles) {
            try {
                long lines = Files.lines(file).count();
                totalLines[0] += lines;
            } catch (IOException ignored) {
                // 跳过无法读取的文件
            }
        }

        // 判断主要语言
        int javaCount = counts[0];
        int pythonCount = counts[1];
        int jsCount = counts[2];
        if (javaCount >= pythonCount && javaCount >= jsCount) {
            builder.primaryLanguage("Java");
        } else if (pythonCount >= javaCount && pythonCount >= jsCount) {
            builder.primaryLanguage("Python");
        } else if (jsCount >= javaCount && jsCount >= pythonCount) {
            builder.primaryLanguage("JavaScript/TypeScript");
        } else {
            builder.primaryLanguage("Unknown");
        }

        builder.estimatedLinesOfCode(totalLines[0]);
    }

    private boolean isConfigFile(String fileName) {
        return fileName.equals("pom.xml") ||
               fileName.equals("build.gradle") ||
               fileName.equals("build.gradle.kts") ||
               fileName.equals("package.json") ||
               fileName.equals("requirements.txt") ||
               fileName.equals("go.mod") ||
               fileName.equals("Cargo.toml") ||
               fileName.endsWith(".yaml") ||
               fileName.endsWith(".yml") ||
               fileName.endsWith(".json") && !fileName.equals("package-lock.json");
    }
}
