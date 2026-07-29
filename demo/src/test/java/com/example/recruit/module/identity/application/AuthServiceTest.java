package com.example.recruit.module.identity.application;

import com.example.recruit.config.AppProperties;
import com.example.recruit.dal.entity.SysUser;
import com.example.recruit.dal.mapper.SysRoleMapper;
import com.example.recruit.dal.mapper.SysUserMapper;
import com.example.recruit.dal.mapper.SysUserRoleMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * {@link AuthService} 单元测试 (OpenSpec p5-tests §2 task 2)。
 *
 * <p>纯单元 + Mockito mock SysUserMapper/SysUserRoleMapper/SysRoleMapper 隔离 DB。
 * AppProperties 手工 new：
 * <ul>
 *   <li>mock=true → 走 mockLogin 桩分支 (不触达 StpUtil，无需 Sa-Token 上下文)；</li>
 *   <li>mock=false → 走真实 BCrypt 校验路径，测用户不存在 / 密码错误等异常分支
 *       (这些分支在调用 StpUtil.login 之前抛出，因此无需 Sa-Token 上下文)。</li>
 * </ul>
 *
 * <p>Sa-Token {@link cn.dev33.satoken.stp.StpUtil#login} 依赖 HTTP 请求上下文 + Redis，
 * 离线单元测试不可用；故真实登录成功全流程由 {@link AuthIntegrationTest} (已 @Disabled) 覆盖。
 * BCrypt 密码校验逻辑通过 {@link AuthService#passwordEncoder()} 直接验证。
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private SysUserMapper userMapper;
    @Mock
    private SysUserRoleMapper userRoleMapper;
    @Mock
    private SysRoleMapper roleMapper;

    private AuthService authService;
    private AuthService realAuthService; // mock=false

    @BeforeEach
    void setUp() {
        AppProperties mockProps = new AppProperties();
        mockProps.getMock().setEnabled(true);
        authService = new AuthService(userMapper, userRoleMapper, roleMapper, mockProps);

        AppProperties realProps = new AppProperties();
        realProps.getMock().setEnabled(false);
        realAuthService = new AuthService(userMapper, userRoleMapper, roleMapper, realProps);
    }

    @Test
    void login_mockMode_returnsTokenAndHrUser() {
        Map<String, Object> result = authService.login("hr_user", "anything");
        assertNotNull(result, "mock 登录应返回结果");
        Object token = result.get("token");
        assertNotNull(token, "应返回 token");
        assertTrue(String.valueOf(token).startsWith("mock-token-"),
                "mock token 前缀正确: " + token);
        @SuppressWarnings("unchecked")
        Map<String, Object> user = (Map<String, Object>) result.get("user");
        assertNotNull(user);
        assertEquals("hr_user", user.get("username"));
        @SuppressWarnings("unchecked")
        var roles = (java.util.List<String>) user.get("roles");
        assertTrue(roles.contains("HR"), "mock 用户角色应含 HR");
    }

    @Test
    void login_userNotFound_throws() {
        when(userMapper.selectOne(any())).thenReturn(null);
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> realAuthService.login("nobody", "x"));
        assertTrue(ex.getMessage().contains("用户不存在"), ex.getMessage());
    }

    @Test
    void login_wrongPassword_throws() {
        SysUser user = new SysUser();
        user.setId(1L);
        user.setUsername("hr");
        // 正确哈希 (encode "123456")，但传入错误密码 → 应抛密码错误
        user.setPassword(new BCryptPasswordEncoder().encode("123456"));
        user.setStatus("active");
        when(userMapper.selectOne(any())).thenReturn(user);
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> realAuthService.login("hr", "wrong-password"));
        assertTrue(ex.getMessage().contains("密码错误"), ex.getMessage());
    }

    @Test
    void login_disabledAccount_throws() {
        SysUser user = new SysUser();
        user.setId(1L);
        user.setUsername("hr");
        BCryptPasswordEncoder enc = new BCryptPasswordEncoder();
        user.setPassword(enc.encode("123456"));
        user.setStatus("disabled");
        when(userMapper.selectOne(any())).thenReturn(user);
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> realAuthService.login("hr", "123456"));
        assertTrue(ex.getMessage().contains("禁用"), ex.getMessage());
    }

    @Test
    void passwordEncoder_verifiesCorrectBcrypt() {
        BCryptPasswordEncoder encoder = realAuthService.passwordEncoder();
        String hash = encoder.encode("123456");
        assertTrue(encoder.matches("123456", hash), "BCrypt 正确密码应匹配");
        assertFalse(encoder.matches("wrong", hash), "错误密码不应匹配");
    }
}
