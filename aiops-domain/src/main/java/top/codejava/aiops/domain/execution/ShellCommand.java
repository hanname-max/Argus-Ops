// aiops-domain/src/main/java/top/codejava/aiops/domain/execution/ShellCommand.java
package top.codejava.aiops.domain.execution;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Shell 命令值对象
 * 封装要在远程服务器执行的命令
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShellCommand {

    /**
     * 工作目录（可选）
     */
    private String workingDirectory;

    /**
     * 命令和参数列表
     */
    @Builder.Default
    private List<String> arguments = new ArrayList<>();

    /**
     * 命令超时时间（毫秒）
     */
    @Builder.Default
    private long timeoutMs = 300000; // 5 minutes default

    /**
     * 从字符串创建简单命令
     */
    public static ShellCommand of(String command) {
        return ShellCommand.builder()
                .arguments(List.of(command.split("\\s+")))
                .build();
    }

    /**
     * 从参数列表创建命令
     */
    public static ShellCommand of(List<String> args) {
        return ShellCommand.builder()
                .arguments(args)
                .build();
    }

    /**
     * 获取拼接后的完整命令字符串
     */
    public String getFullCommand() {
        return String.join(" ", arguments);
    }
}
