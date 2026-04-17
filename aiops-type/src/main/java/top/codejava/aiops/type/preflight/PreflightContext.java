package top.codejava.aiops.type.preflight;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 预检上下文
 * 责任链传递上下文
 */
@Data
@Builder
public class PreflightContext {

    /**
     * 目标项目路径
     */
    private String projectPath;

    /**
     * 检查结果是否通过
     */
    @Builder.Default
    private boolean passed = true;

    /**
     * 检查发现的问题
     */
    @Builder.Default
    private List<String> findings = new ArrayList<>();

    /**
     * 添加检查发现
     */
    public void addFinding(String finding) {
        this.findings.add(finding);
        this.passed = false;
    }

    /**
     * 标记失败
     */
    public void fail() {
        this.passed = false;
    }
}
