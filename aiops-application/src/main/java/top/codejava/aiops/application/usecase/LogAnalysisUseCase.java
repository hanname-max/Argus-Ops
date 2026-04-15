package top.codejava.aiops.application.usecase;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import top.codejava.aiops.application.port.LlmLocalAiPort;
import top.codejava.aiops.domain.analysis.LogAnalysisResult;

import java.util.List;

/**
 * 远程错误日志本地AI分析用例
 * 本地主导架构：错误日志回传给本地，由本地AI分析给出修复建议
 */
@Slf4j
@RequiredArgsConstructor
public class LogAnalysisUseCase {

    private final LlmLocalAiPort llmLocalAiPort;

    /**
     * 分析远程执行错误日志
     * 所有AI推理都在本地完成，远程不需要任何AI能力
     * @param errorLogs 远程错误日志列表
     * @param relatedSourceFiles 关联的源代码文件（可选，帮助AI定位问题）
     * @return 分析结果，包含问题诊断和修复建议
     */
    public LogAnalysisResult analyzeErrorLogs(List<String> errorLogs, List<String> relatedSourceFiles) {
        log.info("Starting local AI analysis of remote error logs, {} error lines", errorLogs.size());

        String prompt = buildAnalysisPrompt(errorLogs, relatedSourceFiles);
        LogAnalysisResult result = llmLocalAiPort.analyzeErrorLogs(prompt);
        log.info("Local AI analysis completed: issue found={}, hasFixSuggestion={}",
                result.getIssueDescription() != null,
                result.getFixSuggestion() != null);

        return result;
    }

    /**
     * 构建分析提示词
     */
    private String buildAnalysisPrompt(List<String> errorLogs, List<String> relatedSourceFiles) {
        StringBuilder sb = new StringBuilder();
        sb.append("""
                You are an experienced DevOps and debugging expert. Please analyze the error logs from remote server execution and provide debugging suggestions.

                --- ERROR LOGS FROM REMOTE EXECUTION ---
                """);

        for (String error : errorLogs) {
            sb.append(error).append("\n");
        }

        if (relatedSourceFiles != null && !relatedSourceFiles.isEmpty()) {
            sb.append("\n--- RELATED SOURCE CODE REFERENCES ---\n");
            for (String source : relatedSourceFiles) {
                sb.append(source).append("\n\n");
            }
        }

        sb.append("""

                Please analyze:
                1. What is the root cause of this error?
                2. What specific fix suggestions do you have?
                3. If it's a dependency/network/environment issue, give the exact commands to fix it.

                Format your response clearly with:
                ROOT CAUSE: <your diagnosis>
                FIX SUGGESTIONS: <step-by-step instructions>
                """);

        return sb.toString();
    }
}
