package com.example.recruit.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.recruit.dal.entity.SysRole;
import com.example.recruit.dal.entity.SysUserRole;
import com.example.recruit.dal.mapper.SysRoleMapper;
import com.example.recruit.dal.mapper.SysUserRoleMapper;
import cn.dev33.satoken.stp.StpInterface;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Sa-Token 权限/角色实现 (复刻自文档 §7.4 StpInterfaceImpl)。
 *
 * <p>实现 {@link StpInterface} 的 getPermissionList / getRoleList，
 * 从 sys_user_role → sys_role 查询用户角色 code 列表 (HR / OPS)。
 */
@Component
public class StpInterfaceImpl implements StpInterface {

    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMapper roleMapper;

    public StpInterfaceImpl(SysUserRoleMapper userRoleMapper, SysRoleMapper roleMapper) {
        this.userRoleMapper = userRoleMapper;
        this.roleMapper = roleMapper;
    }

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        // 角色即权限 (RBAC: 角色 code 作为权限标识)
        return getRoleList(loginId, loginType);
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        try {
            Long userId = Long.parseLong(String.valueOf(loginId));
            // 1. 查用户角色关联
            List<SysUserRole> userRoles = userRoleMapper.selectList(
                    new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId));
            if (userRoles.isEmpty()) {
                return new ArrayList<>();
            }
            List<Long> roleIds = userRoles.stream()
                    .map(SysUserRole::getRoleId)
                    .collect(Collectors.toList());
            // 2. 查角色 code
            List<SysRole> roles = roleMapper.selectList(
                    new LambdaQueryWrapper<SysRole>().in(SysRole::getId, roleIds));
            return roles.stream().map(SysRole::getCode).collect(Collectors.toList());
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
}
