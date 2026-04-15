package top.codejava.aiops.application.port;

import top.codejava.aiops.domain.analysis.LogAnalysisResult;

/**
 * 本地AI端口
 * 本地主导架构：所有AI推理都在这里，由本地节点完成
 * 所有AI能力都在本地，远程只执行命令不做AI推理
 */
public interface LlmLocalAiPort {

    /**
     * 分析远程错误日志 - 整合提示词构建、AI调用、解析
     * @param prompt 构建好的分析提示词
     * @return 结构化分析结果
     */
    LogAnalysisResult analyzeErrorLogs(String prompt);

    /**
     * 解析AI返回的分析响应（对内使用）
     * @param response AI原始响应
     * @return 结构化分析结果
     */
    LogAnalysisResult parseAnalysisResponse(String response);
}
