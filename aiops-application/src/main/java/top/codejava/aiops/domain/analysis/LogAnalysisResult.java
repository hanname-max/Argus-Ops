package top.codejava.aiops.domain.analysis;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 本地AI日志分析结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogAnalysisResult {

    /**
     * 问题根因诊断
     */
    private String issueDescription;

    /**
     * 修复建议（一步步操作）
     */
    private String fixSuggestion;

    /**
     * 是否确认找到了问题
     */
    @Builder.Default
    private boolean foundIssue = true;
}
