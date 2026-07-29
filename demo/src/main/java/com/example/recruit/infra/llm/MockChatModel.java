package com.example.recruit.infra.llm;

import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.ChatUsage;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ToolSchema;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * Mock ChatModel —— 让 HarnessAgent 在无 API Key 时也能跑通。
 *
 * <p>实现 AgentScope {@link Model} 接口，返回桩 ChatResponse。
 * 当用户提到工具相关需求时，Mock 回复引导其直接查看结果；
 * 否则给出一句话兜底回复。仅用于演示/开发，生产应切换真实 OpenAIChatModel。
 */
public class MockChatModel implements Model {

    private final String modelName;

    public MockChatModel(String modelName) {
        this.modelName = modelName;
    }

    @Override
    public Flux<ChatResponse> stream(List<Msg> messages, List<ToolSchema> tools, GenerateOptions options) {
        String userText = extractLastUser(messages);
        String reply = replyFor(userText, tools);
        ChatResponse response = ChatResponse.builder()
                .content(List.of(TextBlock.builder().text(reply).build()))
                .usage(new ChatUsage(0, 0, 0.0))
                .finishReason("stop")
                .build();
        return Flux.just(response);
    }

    @Override
    public String getModelName() {
        return modelName;
    }

    @Override
    public boolean supportsNativeStructuredOutput() {
        return false;
    }

    @Override
    public int getContextWindowSize() {
        return 8192;
    }

    private String extractLastUser(List<Msg> messages) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        for (int i = messages.size() - 1; i >= 0; i--) {
            Msg m = messages.get(i);
            try {
                if (m.getRole() != null && m.getRole().name().equalsIgnoreCase("USER")) {
                    return m.getTextContent();
                }
            } catch (Throwable ignored) {
                return m.getTextContent();
            }
        }
        return messages.get(messages.size() - 1).getTextContent();
    }

    private String replyFor(String userText, List<ToolSchema> tools) {
        if (userText == null || userText.isBlank()) {
            return "[Mock Agent] 已收到，请描述你的招聘需求。";
        }
        // 检测是否是工具可解决的需求，给出引导
        if (userText.contains("岗位") || userText.contains("分析岗位")) {
            return "[Mock Agent] 我可以帮你分析岗位。请提供岗位 ID，或我先调用 listJobs 查询岗位列表。\n"
                    + "(Mock 模式: 配置 app.ai.api-key 后 Agent 将真正调用工具完成全流程)";
        }
        if (userText.contains("匹配") || userText.contains("候选人")) {
            return "[Mock Agent] 我可以帮你匹配候选人。请提供岗位 ID，我将执行四阶段匹配。\n(Mock 模式)";
        }
        if (userText.contains("面试") || userText.contains("出题")) {
            return "[Mock Agent] 我可以生成面试题或启动 AI 初面。请提供面试 ID。\n(Mock 模式)";
        }
        return "[Mock Agent] 我理解你的需求:「" + ellipsize(userText, 80)
                + "」。当前为 Mock 模式（未配置 app.ai.api-key），"
                + "配置后我将通过 ReAct 调用工具链路完成岗位分析→候选人匹配→面试→触达全流程。";
    }

    private String ellipsize(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    @SuppressWarnings("unused")
    private void useContentBlock(ContentBlock b) {
        // 仅占位以保留 ContentBlock 导入，便于扩展多模态
    }
}
