package top.codejava.aiops.infrastructure.adapter;

import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import top.codejava.aiops.application.port.LlmLocalAiPort;
import top.codejava.aiops.domain.analysis.LogAnalysisResult;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 动态本地AI适配器
 * 实现 LlmLocalAiPort，使用本地配置的 ChatLanguageModel 做错误日志分析
 * 本地主导架构：所有错误分析AI推理都在这里完成，远程不参与
 */
@Slf4j
@RequiredArgsConstructor
public class DynamicLocalAiAdapter implements LlmLocalAiPort {

    private final ChatLanguageModel localChatModel;

    @Override
    public LogAnalysisResult analyzeErrorLogs(String prompt) {
        String response = localChatModel.generate(prompt);
        return parseAnalysisResponse(response);
    }

    @Override
    public LogAnalysisResult parseAnalysisResponse(String response) {
        // 从AI响应中提取 ROOT CAUSE 和 FIX SUGGESTIONS
        String rootCause = extractSection(response, "ROOT CAUSE", "FIX SUGGESTIONS");
        String fixSuggestions = extractSection(response, "FIX SUGGESTIONS", null);

        return LogAnalysisResult.builder()
                .issueDescription(rootCause != null ? rootCause.trim() : response)
                .fixSuggestion(fixSuggestions != null ? fixSuggestions.trim() : null)
                .foundIssue(rootCause != null && !rootCause.isEmpty())
                .build();
    }

    /**
     * 提取指定section的内容
     */
    private String extractSection(String text, String startMarker, String endMarker) {
        String patternStr = "(?i)" + Pattern.quote(startMarker) + ".*?\\R(.*?)";
        if (endMarker != null) {
            patternStr += "(?=" + Pattern.quote(endMarker) + ")";
        }
        Pattern pattern = Pattern.compile(patternStr, Pattern.DOTALL);
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
}
