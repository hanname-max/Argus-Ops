// aiops-application/src/main/java/top/codejava/aiops/application/port/LocalEnvironmentPort.java
package top.codejava.aiops.application.port;

import top.codejava.aiops.domain.model.ProjectContext;

import java.nio.file.Path;

/**
 * 本地环境扫描端口（出站端口）
 * 由基础设施层实现，负责扫描本地项目目录获取上下文信息
 */
public interface LocalEnvironmentPort {

    /**
     * 扫描指定目录，提取项目上下文信息
     *
     * @param rootPath 项目根目录路径
     * @return 扫描得到的项目上下文
     */
    ProjectContext scanProject(Path rootPath);

    /**
     * 扫描当前工作目录
     *
     * @return 扫描得到的项目上下文
     */
    default ProjectContext scanCurrentWorkingDirectory() {
        return scanProject(Path.of("").toAbsolutePath());
    }
}
