package com.example.recruit.security;

import cn.dev33.satoken.stp.StpInterface;
import com.example.recruit.dal.mapper.SysRoleMapper;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Sa-Token 权限/角色实现 (复刻自文档 §7.4 StpInterfaceImpl)。
 *
 * <p>getPermissionList → {@link PermissionMapping#getPermissions}（按角色合并权限码）。
 * <p>getRoleList → {@link SysRoleMapper#selectRoleCodesByUserId}（join sys_user_role + sys_role）。
 */
@Component
public class StpInterfaceImpl implements StpInterface {

    private final SysRoleMapper sysRoleMapper;

    public StpInterfaceImpl(SysRoleMapper sysRoleMapper) {
        this.sysRoleMapper = sysRoleMapper;
    }

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        List<String> roles = getRoleList(loginId, loginType);
        return PermissionMapping.getPermissions(roles);
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        try {
            Long userId = Long.parseLong(String.valueOf(loginId));
            return sysRoleMapper.selectRoleCodesByUserId(userId);
        } catch (Exception e) {
            return List.of();
        }
    }
}
