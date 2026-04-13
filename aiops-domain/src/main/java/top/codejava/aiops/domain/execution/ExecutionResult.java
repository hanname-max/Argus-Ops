// aiops-domain/src/main/java/top/codejava/aiops/domain/execution/ExecutionResult.java
package top.codejava.aiops.domain.execution;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 命令执行结果领域实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionResult {

    /**
     * 命令退出码
     */
    private int exitCode;

    /**
     * 标准输出日志
     */
    private String stdout;

    /**
     * 标准错误日志
     */
    private String stderr;

    /**
     * 执行耗时（毫秒）
     */
    private long durationMs;

    /**
     * 是否执行成功（exitCode == 0）
     */
    public boolean isSuccess() {
        return exitCode == 0;
    }

    /**
     * 获取合并后的完整日志
     */
    public String getCombinedOutput() {
        StringBuilder sb = new StringBuilder();
        if (stdout != null && !stdout.isEmpty()) {
            sb.append(stdout);
        }
        if (stderr != null && !stderr.isEmpty()) {
            if (!sb.isEmpty()) sb.append("\n");
            sb.append(stderr);
        }
        return sb.toString();
    }

    public static ExecutionResult success(String output, long durationMs) {
        return ExecutionResult.builder()
                .exitCode(0)
                .stdout(output)
                .durationMs(durationMs)
                .build();
    }

    public static ExecutionResult failure(int exitCode, String output, long durationMs) {
        return ExecutionResult.builder()
                .exitCode(exitCode)
                .stdout(output)
                .durationMs(durationMs)
                .build();
    }
}
