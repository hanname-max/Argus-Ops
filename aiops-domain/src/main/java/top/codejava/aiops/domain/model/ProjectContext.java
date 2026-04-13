// aiops-domain/src/main/java/top/codejava/aiops/domain/model/ProjectContext.java
package top.codejava.aiops.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.nio.file.Path;
import java.util.List;

/**
 * 项目上下文领域模型
 * 存储本地扫描得到的项目环境信息，供AI生成部署计划时参考
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectContext {

    /**
     * 项目根目录路径
     */
    private Path rootPath;

    /**
     * 检测到的主要编程语言
     */
    private String primaryLanguage;

    /**
     * 项目使用的构建工具 (maven/gradle/none)
     */
    private String buildTool;

    /**
     * 是否存在 pom.xml
     */
    private boolean hasPomXml;

    /**
     * 是否存在 build.gradle
     */
    private boolean hasBuildGradle;

    /**
     * 是否存在 Dockerfile
     */
    private boolean hasDockerfile;

    /**
     * 是否存在 docker-compose.yml
     */
    private boolean hasDockerCompose;

    /**
     * 检测到的配置文件列表
     */
    private List<String> configFiles;

    /**
     * 项目大致代码行数统计（可选项）
     */
    private long estimatedLinesOfCode;
}
