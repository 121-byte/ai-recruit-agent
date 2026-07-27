package com.example.recruit.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.recruit.dal.entity.SysRole;
import com.example.recruit.dal.entity.SysUser;
import com.example.recruit.dal.entity.SysUserRole;
import com.example.recruit.dal.mapper.SysRoleMapper;
import com.example.recruit.dal.mapper.SysUserMapper;
import com.example.recruit.dal.mapper.SysUserRoleMapper;
import com.example.recruit.service.AuthService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 用户管理 API (复刻自文档 §14.13)。需 HR 角色。
 *
 * <p>GET  /api/users 用户列表（password 置空）
 * <p>POST /api/users 创建用户（BCrypt 加密，默认关联 HR 角色）
 */
@RestController
@RequestMapping("/api/users")
@SaCheckRole("HR")
public class UserController {

    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final AuthService authService;

    public UserController(SysUserMapper sysUserMapper,
                          SysRoleMapper sysRoleMapper,
                          SysUserRoleMapper sysUserRoleMapper,
                          AuthService authService) {
        this.sysUserMapper = sysUserMapper;
        this.sysRoleMapper = sysRoleMapper;
        this.sysUserRoleMapper = sysUserRoleMapper;
        this.authService = authService;
    }

    @GetMapping
    public List<SysUser> list() {
        try {
            List<SysUser> users = sysUserMapper.selectList(null);
            for (SysUser u : users) {
                u.setPassword(null);
            }
            return users;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    @PostMapping
    public SysUser create(@RequestBody Map<String, Object> body) {
        SysUser user = new SysUser();
        user.setUsername(body.get("username") == null ? null : String.valueOf(body.get("username")));
        String password = body.get("password") == null ? null : String.valueOf(body.get("password"));
        user.setRealName(body.get("realName") == null ? null : String.valueOf(body.get("realName")));
        user.setDepartment(body.get("department") == null ? null : String.valueOf(body.get("department")));
        BCryptPasswordEncoder encoder = authService.passwordEncoder();
        user.setPassword(encoder.encode(password == null ? "" : password));
        user.setStatus("active");
        user.setCreatedAt(LocalDateTime.now());
        try {
            sysUserMapper.insert(user);
            // 默认关联 HR 角色
            SysRole hrRole = sysRoleMapper.selectOne(
                    new LambdaQueryWrapper<SysRole>().eq(SysRole::getCode, "HR"));
            if (hrRole != null) {
                SysUserRole userRole = new SysUserRole();
                userRole.setUserId(user.getId());
                userRole.setRoleId(hrRole.getId());
                sysUserRoleMapper.insert(userRole);
            }
        } catch (Exception ignored) {
        }
        return user;
    }
}
