// aiops-infrastructure/src/main/java/top/codejava/aiops/infrastructure/adapter/SpringAiLlmRemoteBrainAdapter.java
package top.codejava.aiops.infrastructure.adapter;

import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.StreamingChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import top.codejava.aiops.application.port.LlmRemoteBrainPort;
import top.codejava.aiops.domain.model.PlanDraft;
import top.codejava.aiops.domain.model.ProjectContext;

/**
 * 基于 Spring AI 的 LLM 远程大脑适配器
 * 支持 OpenAI 兼容协议（火山方舟、Doubao 等都兼容）
 * 支持流式输出，用于 Web SSE 打字机效果
 * 适配 Spring AI 0.8.x API
 */
@Component
@org.springframework.context.annotation.Primary
public class SpringAiLlmRemoteBrainAdapter implements LlmRemoteBrainPort {

    private final ChatClient remoteChatClient;
    private final ChatClient localChatClient;
    private final StreamingChatClient remoteStreamingChatClient;

    public SpringAiLlmRemoteBrainAdapter(
            @Qualifier("remoteChatClient") ChatClient remoteChatClient,
            @Qualifier("localChatClient") ChatClient localChatClient,
            @Qualifier("remoteStreamingChatClient") StreamingChatClient remoteStreamingChatClient) {
        this.remoteChatClient = remoteChatClient;
        this.localChatClient = localChatClient;
        this.remoteStreamingChatClient = remoteStreamingChatClient;
    }

    @Override
    public PlanDraft generateDeploymentPlan(ProjectContext context) {
        String prompt = buildDeploymentPlanPrompt(context);

        String response = remoteChatClient.call(new Prompt(new UserMessage(prompt)))
                .getResult()
                .getOutput()
                .getContent();

        return PlanDraft.builder()
                .projectContext(context)
                .generatedContent(response)
                .generatedAt(java.time.LocalDateTime.now())
                .build();
    }

    @Override
    public Flux<String> generateDeploymentPlanStream(ProjectContext context) {
        String prompt = buildDeploymentPlanPrompt(context);

        return remoteStreamingChatClient.stream(new Prompt(new UserMessage(prompt)))
                .map(response -> response.getResult().getOutput().getContent());
    }

    @Override
    public PlanDraft auditPlan(PlanDraft planDraft) {
        String prompt = buildAuditPrompt(planDraft);

        String response = localChatClient.call(new Prompt(new UserMessage(prompt)))
                .getResult()
                .getOutput()
                .getContent();

        planDraft.setAuditResult(response);
        return planDraft;
    }

    @Override
    public Flux<String> analyzeErrorLogStream(String logContent) {
        String prompt = buildLogAnalysisPrompt(logContent);

        return remoteStreamingChatClient.stream(new Prompt(new UserMessage(prompt)))
                .map(response -> response.getResult().getOutput().getContent());
    }

    private String buildDeploymentPlanPrompt(ProjectContext context) {
        return String.format("""
                你是一位经验丰富的架构师和 DevOps 工程师。请为以下项目生成一份完整的容器化部署计划：

                项目路径: %s
                检测到语言: %s
                估算代码行数: %d
                已有 Dockerfile: %b

                请输出：
                1. 架构总结（项目类型、技术栈判断）
                2. 完整的 Dockerfile（带详细注释）
                3. 简要的部署说明

                请直接输出内容，不需要多余开场白。
                """,
                context.getRootPath().toAbsolutePath().toString(),
                context.getPrimaryLanguage(),
                context.getEstimatedLinesOfCode(),
                context.isHasDockerfile()
        );
    }

    private String buildAuditPrompt(PlanDraft draft) {
        return String.format("""
                你是一位安全专家，请对下面 AI 生成的 Dockerfile 进行安全审计。
                找出其中存在的安全问题、不良实践，并给出修复建议。

                生成的 Dockerfile 内容：
                %s

                请按照以下格式输出：
                - 如果没有问题，输出 "PASSED"
                - 如果有问题，列出每个问题，包含：Finding, Risk, Recommendation
                """,
                draft.getGeneratedContent()
        );
    }

    private String buildLogAnalysisPrompt(String logContent) {
        return String.format("""
                这是一份部署过程中的错误日志，请分析错误原因，并给出具体的修复步骤建议。

                错误日志内容：
                ```
                %s
                ```

                请逐步分析原因并给出修复建议。
                """,
                logContent
        );
    }
}
