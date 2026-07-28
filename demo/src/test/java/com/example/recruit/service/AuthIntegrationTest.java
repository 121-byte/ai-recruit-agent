package com.example.recruit.service;

import com.example.recruit.dal.entity.SysUser;
import com.example.recruit.dal.mapper.SysRoleMapper;
import com.example.recruit.dal.mapper.SysUserMapper;
import com.example.recruit.dal.mapper.SysUserRoleMapper;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * AuthService 集成测试 (OpenSpec p5-tests §2 task 3)。
 *
 * <p>原计划 {@code @SpringBootTest(webEnvironment=NONE)} + {@code @MockBean}
 * 隔离 SysUserMapper 等真实 DB 调用，验证真实 BCrypt + Sa-Token 登录全流程。
 *
 * <p><b>禁用原因</b>：application.properties 配置了远程 PostgreSQL (pgvector) +
 * Redis + {@code spring.sql.init.mode=always}，Spring 上下文启动时会尝试连接远程
 * DB/Redis 执行 schema 初始化；离线/CI 环境不可达会导致上下文启动失败。
 * 此外 Sa-Token {@code StpUtil.login} 依赖 HTTP 请求上下文 + Redis 会话存储，
 * {@code webEnvironment=NONE} 下无 Servlet 上下文，{@code StpUtil.login} 会抛
 * {@code SaTokenContextException}。
 *
 * <p>真实登录全流程的端到端验证需在有远程基础设施的开发环境手动运行。
 * BCrypt 密码校验逻辑由 {@link AuthServiceTest} 覆盖 (含用户不存在/密码错误/账号禁用分支)。
 */
@Disabled("需远程 PostgreSQL/Redis + Sa-Token HTTP 上下文，离线测试不适用")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class AuthIntegrationTest {

    @MockBean
    private SysUserMapper sysUserMapper;
    @MockBean
    private SysUserRoleMapper sysUserRoleMapper;
    @MockBean
    private SysRoleMapper sysRoleMapper;

    @Autowired
    private AuthService authService;

    @Test
    void login_success_returnsTokenAndUser() {
        SysUser user = new SysUser();
        user.setId(1L);
        user.setUsername("hr");
        user.setRealName("HR 张三");
        user.setDepartment("招聘部");
        user.setPassword(new BCryptPasswordEncoder().encode("123456"));
        user.setStatus("active");
        when(sysUserMapper.selectOne(any())).thenReturn(user);

        Map<String, Object> result = authService.login("hr", "123456");
        assertNotNull(result.get("token"), "登录应返回 token");
    }
}
