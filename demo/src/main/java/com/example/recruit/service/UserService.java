package com.example.recruit.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.recruit.dal.entity.SysRole;
import com.example.recruit.dal.entity.SysUser;
import com.example.recruit.dal.entity.SysUserRole;
import com.example.recruit.dal.mapper.SysRoleMapper;
import com.example.recruit.dal.mapper.SysUserMapper;
import com.example.recruit.dal.mapper.SysUserRoleMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 用户管理 CRUD 服务 (复刻对齐清单 §2)。
 * 密码用 BCrypt 加密，新建用户默认关联 HR 角色。
 */
@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMapper roleMapper;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public UserService(SysUserMapper userMapper,
                      SysUserRoleMapper userRoleMapper,
                      SysRoleMapper roleMapper) {
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.roleMapper = roleMapper;
    }

    /** 新建用户 (rawPassword 明文, BCrypt 加密后入库, 默认关联 HR 角色)。 */
    public SysUser create(SysUser user, String rawPassword) {
        if (user == null) {
            return null;
        }
        try {
            if (user.getStatus() == null || user.getStatus().isBlank()) {
                user.setStatus("active");
            }
            if (user.getCreatedAt() == null) {
                user.setCreatedAt(LocalDateTime.now());
            }
            String pwd = rawPassword == null || rawPassword.isBlank() ? "123456" : rawPassword;
            user.setPassword(passwordEncoder.encode(pwd));
            userMapper.insert(user);
            // 默认关联 HR 角色
            Long hrRoleId = findRoleIdByCode("HR");
            if (hrRoleId != null) {
                SysUserRole rel = new SysUserRole();
                rel.setUserId(user.getId());
                rel.setRoleId(hrRoleId);
                userRoleMapper.insertOne(user.getId(), hrRoleId);
            }
            return user;
        } catch (Exception e) {
            log.warn("create user failed: {}", e.getMessage());
            return null;
        }
    }

    /** 更新用户 (不清空密码, 传入为空时保留原密码)。 */
    public boolean update(SysUser user) {
        if (user == null || user.getId() == null) {
            return false;
        }
        try {
            if (user.getPassword() == null || user.getPassword().isBlank()) {
                SysUser existing = userMapper.selectById(user.getId());
                if (existing != null) {
                    user.setPassword(existing.getPassword());
                }
            } else if (!user.getPassword().startsWith("$2")) {
                // 非哈希值, 视为明文加密
                user.setPassword(passwordEncoder.encode(user.getPassword()));
            }
            return userMapper.update(user) > 0;
        } catch (Exception e) {
            log.warn("update user failed: {}", e.getMessage());
            return false;
        }
    }

    /** 删除用户 (同时清理角色关联)。 */
    public boolean delete(Long id) {
        if (id == null) {
            return false;
        }
        try {
            try {
                userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>()
                        .eq(SysUserRole::getUserId, id));
            } catch (Exception ignored) {
                // 角色关联清理失败不阻断主流程
            }
            return userMapper.deleteById(id) > 0;
        } catch (Exception e) {
            log.warn("delete user failed: {}", e.getMessage());
            return false;
        }
    }

    /** 查询全部用户。 */
    public List<SysUser> listAll() {
        try {
            return userMapper.selectAll();
        } catch (Exception e) {
            log.warn("listAll user failed: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 重置用户角色 (先删除原有角色关联, 再按 roleCodes 重建)。
     * roleCodes 为空时仅清空。
     */
    public boolean assignRoles(Long userId, List<String> roleCodes) {
        if (userId == null) {
            return false;
        }
        try {
            // 清空原有角色关联
            userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>()
                    .eq(SysUserRole::getUserId, userId));
            if (roleCodes == null || roleCodes.isEmpty()) {
                return true;
            }
            List<SysUserRole> toInsert = new ArrayList<>();
            for (String code : roleCodes) {
                Long roleId = findRoleIdByCode(code);
                if (roleId != null) {
                    SysUserRole rel = new SysUserRole();
                    rel.setUserId(userId);
                    rel.setRoleId(roleId);
                    toInsert.add(rel);
                }
            }
            if (!toInsert.isEmpty()) {
                userRoleMapper.insertBatch(toInsert);
            }
            return true;
        } catch (Exception e) {
            log.warn("assignRoles failed: {}", e.getMessage());
            return false;
        }
    }

    /** 按 code 查询角色 ID。 */
    private Long findRoleIdByCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        try {
            List<SysRole> roles = roleMapper.selectAll();
            for (SysRole r : roles) {
                if (code.equalsIgnoreCase(r.getCode())) {
                    return r.getId();
                }
            }
        } catch (Exception e) {
            log.warn("findRoleIdByCode failed: {}", e.getMessage());
        }
        return null;
    }

    /** 暴露密码编码器 (供 AuthService 等复用)。 */
    public BCryptPasswordEncoder passwordEncoder() {
        return passwordEncoder;
    }
}
