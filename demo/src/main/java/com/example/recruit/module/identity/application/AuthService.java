package com.example.recruit.module.identity.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.recruit.config.AppProperties;
import com.example.recruit.dal.entity.SysUser;
import com.example.recruit.dal.entity.SysUserRole;
import com.example.recruit.dal.mapper.SysRoleMapper;
import com.example.recruit.dal.mapper.SysUserMapper;
import com.example.recruit.dal.mapper.SysUserRoleMapper;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 认证服务 (复刻自文档 §7.4 AuthService)。
 *
 * <p>流程：username + password → 查 sys_user → BCrypt.matches → StpUtil.login → 返回 token。
 * 密码加密使用 Spring Security Crypto 的 BCryptPasswordEncoder (不引入完整 Spring Security)。
 *
 * <p>Mock 模式 (无 DB/Redis) 下直接放行登录，返回桩 token + HR 用户，便于浏览器全流程演示。
 */
@Service
public class AuthService {

    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMapper roleMapper;
    private final AppProperties appProperties;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthService(SysUserMapper userMapper,
                       SysUserRoleMapper userRoleMapper,
                       SysRoleMapper roleMapper,
                       AppProperties appProperties) {
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.roleMapper = roleMapper;
        this.appProperties = appProperties;
    }

    /** 登录。返回 {token, user}。 */
    public Map<String, Object> login(String username, String password) {
        // Mock 模式: 无 DB/Redis 时直接放行, 返回桩 token
        if (appProperties.useMock()) {
            return mockLogin(username);
        }
        SysUser user = userMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        if (!passwordEncoder.matches(password == null ? "" : password, user.getPassword())) {
            throw new RuntimeException("密码错误");
        }
        if ("disabled".equalsIgnoreCase(user.getStatus())) {
            throw new RuntimeException("账号已禁用");
        }
        StpUtil.login(user.getId());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("token", StpUtil.getTokenValue());
        Map<String, Object> userInfo = new LinkedHashMap<>();
        userInfo.put("id", user.getId());
        userInfo.put("username", user.getUsername());
        userInfo.put("realName", user.getRealName());
        userInfo.put("department", user.getDepartment());
        userInfo.put("roles", rolesOf(user.getId()));
        result.put("user", userInfo);
        return result;
    }

    public void logout() {
        StpUtil.logout();
    }

    public Map<String, Object> currentUserInfo() {
        if (appProperties.useMock()) {
            return mockUserInfo();
        }
        Object loginId = StpUtil.getLoginIdDefaultNull();
        if (loginId == null) {
            throw new RuntimeException("未登录");
        }
        Long userId = Long.parseLong(String.valueOf(loginId));
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("id", user.getId());
        info.put("username", user.getUsername());
        info.put("realName", user.getRealName());
        info.put("email", user.getEmail());
        info.put("phone", user.getPhone());
        info.put("department", user.getDepartment());
        info.put("roles", rolesOf(userId));
        return info;
    }

    private List<String> rolesOf(Long userId) {
        try {
            List<SysUserRole> userRoles = userRoleMapper.selectList(
                    new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId));
            if (userRoles.isEmpty()) return List.of();
            List<Long> roleIds = userRoles.stream().map(SysUserRole::getRoleId).collect(Collectors.toList());
            return roleMapper.selectList(
                            new LambdaQueryWrapper<com.example.recruit.dal.entity.SysRole>()
                                    .in(com.example.recruit.dal.entity.SysRole::getId, roleIds))
                    .stream()
                    .map(com.example.recruit.dal.entity.SysRole::getCode)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return List.of();
        }
    }

    public BCryptPasswordEncoder passwordEncoder() {
        return passwordEncoder;
    }

    // ─────────────────── Mock 桩 ───────────────────

    private Map<String, Object> mockLogin(String username) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("token", "mock-token-" + System.currentTimeMillis());
        Map<String, Object> userInfo = new LinkedHashMap<>();
        userInfo.put("id", 1);
        userInfo.put("username", username == null || username.isBlank() ? "hr_user" : username);
        userInfo.put("realName", "HR 张三");
        userInfo.put("department", "招聘部");
        userInfo.put("roles", List.of("HR"));
        result.put("user", userInfo);
        return result;
    }

    private Map<String, Object> mockUserInfo() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("id", 1);
        info.put("username", "hr_user");
        info.put("realName", "HR 张三");
        info.put("department", "招聘部");
        info.put("roles", List.of("HR"));
        return info;
    }
}
