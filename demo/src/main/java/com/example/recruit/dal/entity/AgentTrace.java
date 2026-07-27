package com.example.recruit.dal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Agent 追踪表 (schema.sql §3.1.x)。记录每次 Agent 步骤。
 */
@Data
@TableName("agent_trace")
public class AgentTrace {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String sessionId;

    private String agentName;

    private Integer stepNo;

    /** thinking/tool_call/tool_result/text */
    private String stepType;

    private String toolName;

    private String inputText;

    private String outputText;

    private String model;

    private Integer tokens;

    private Long latencyMs;

    /** success/error */
    private String status;

    private LocalDateTime createdAt;

    /** 输入文本截断到 2000 字符 (AgentTraceAspect 使用) */
    public static String truncate(String text) {
        if (text == null) {
            return null;
        }
        return text.length() > 2000 ? text.substring(0, 2000) : text;
    }
}
