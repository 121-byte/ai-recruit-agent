package com.example.recruit.agent.event;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AgentEventSseMapper} PII 脱敏安全测试 (OpenSpec p5-tests §2 task 5)。
 *
 * <p>纯单元测试 {@link AgentEventSseMapper#maskPii(String)}，验证 SSE 输出层对
 * 手机号 / 身份证 / 邮箱的脱敏 (复刻自文档 §7.5)。AgentScope 的 Msg 不可变，
 * 中间件无法改输出，SSE 映射层是输出到用户的最后一站，故 maskPii 是关键防线。
 */
class AgentEventSseMapperSecurityTest {

    @Test
    void maskPii_masksPhone() {
        String out = AgentEventSseMapper.maskPii("联系人手机13888888888");
        assertTrue(out.contains("138****8888"), "手机号应脱敏为 138****8888: " + out);
        assertFalse(out.contains("13888888888"), "原始手机号不应残留");
    }

    @Test
    void maskPii_masksEmail() {
        String out = AgentEventSseMapper.maskPii("邮箱a.b@c.com请联系");
        // 邮箱 a.b@c.com → a***@c.com
        assertTrue(out.contains("a***@c.com"), "邮箱应脱敏为 a***@c.com: " + out);
        assertFalse(out.contains("a.b@c.com"), "原始邮箱不应残留");
    }

    @Test
    void maskPii_masksIdCard() {
        String out = AgentEventSseMapper.maskPii("身份证110101199003071234");
        // 身份证 → 前6位 + ******** + 后4位
        assertTrue(out.contains("110101********1234"), "身份证应脱敏: " + out);
        assertFalse(out.contains("110101199003071234"), "原始身份证不应残留");
    }

    @Test
    void maskPii_masksMultiplePiiInOneText() {
        String out = AgentEventSseMapper.maskPii("手机13888888888邮箱a@b.com");
        assertTrue(out.contains("138****8888"), "手机号已脱敏: " + out);
        assertTrue(out.contains("a***@b.com"), "邮箱已脱敏: " + out);
        assertFalse(out.contains("13888888888"));
        assertFalse(out.contains("a@b.com"));
    }

    @Test
    void maskPii_preservesNonPiiText() {
        String out = AgentEventSseMapper.maskPii("你好，请发送简历到系统");
        assertEquals("你好，请发送简历到系统", out, "无 PII 文本应原样返回");
    }

    @Test
    void maskPii_handlesNull() {
        assertNull(AgentEventSseMapper.maskPii(null), "null 入参应返回 null");
    }

    @Test
    void maskPii_doesNotMaskShortNumber() {
        // 11 位以下非手机号 (不以 1[3-9] 开头或长度不足) 不应误脱敏
        String out = AgentEventSseMapper.maskPii("订单号123456");
        assertEquals("订单号123456", out, "短数字不应被误脱敏");
    }

    @Test
    void toSse_canBeInstantiated() {
        // 确认无参构造可用 (ObjectMapper 内部 new)
        AgentEventSseMapper mapper = new AgentEventSseMapper();
        assertNotNull(mapper, "AgentEventSseMapper 可实例化");
        // null 事件 → 静默跳过
        assertNull(mapper.toSse(null), "null 事件应返回 null");
    }
}
