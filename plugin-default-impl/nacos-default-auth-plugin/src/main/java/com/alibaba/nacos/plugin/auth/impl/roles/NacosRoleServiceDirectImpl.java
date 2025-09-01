/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.nacos.plugin.auth.impl.roles;

import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.plugin.auth.impl.configuration.AuthConfigs;
import com.alibaba.nacos.common.utils.CollectionUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.auth.impl.constant.AuthConstants;
import com.alibaba.nacos.plugin.auth.impl.persistence.PermissionInfo;
import com.alibaba.nacos.plugin.auth.impl.persistence.PermissionPersistService;
import com.alibaba.nacos.plugin.auth.impl.persistence.RoleInfo;
import com.alibaba.nacos.plugin.auth.impl.persistence.RolePersistService;
import com.alibaba.nacos.plugin.auth.impl.users.NacosUserService;

import java.util.List;

/**
 * Nacos builtin role service, implemented by directly access to database.
 *
 * @author nkorange
 * @since 1.2.0
 */
public class NacosRoleServiceDirectImpl extends AbstractCheckedRoleService implements NacosRoleService {
    
    private static final int DEFAULT_PAGE_NO = 1;
    
    private final AuthConfigs authConfigs;
    
    private final RolePersistService rolePersistService;
    
    private final NacosUserService userDetailsService;
    
    private final PermissionPersistService permissionPersistService;
    
    public NacosRoleServiceDirectImpl(AuthConfigs authConfigs, RolePersistService rolePersistService,
            NacosUserService userDetailsService, PermissionPersistService permissionPersistService) {
        super(authConfigs);
        this.authConfigs = authConfigs;
        this.rolePersistService = rolePersistService;
        this.userDetailsService = userDetailsService;
        this.permissionPersistService = permissionPersistService;
    }
    
    @Override
    public List<RoleInfo> getRoles(String username) {
        List<RoleInfo> roleInfoList = getCachedRoleInfoMap().get(username);
        if (!authConfigs.isCachingEnabled() || roleInfoList == null) {
            Page<RoleInfo> roleInfoPage = getRoles(username, StringUtils.EMPTY, DEFAULT_PAGE_NO, Integer.MAX_VALUE);
            if (roleInfoPage != null) {
                roleInfoList = roleInfoPage.getPageItems();
                if (!CollectionUtils.isEmpty(roleInfoList)) {
                    getCachedRoleInfoMap().put(username, roleInfoList);
                }
            }
        }
        return roleInfoList;
    }
    
    @Override
    public Page<RoleInfo> getRoles(String username, String role, int pageNo, int pageSize) {
        Page<RoleInfo> roles = rolePersistService.getRolesByUserNameAndRoleName(username, role, pageNo, pageSize);
        if (roles == null) {
            return new Page<>();
        }
        return roles;
    }
    
    @Override
    public List<RoleInfo> getAllRoles() {
        Page<RoleInfo> roleInfoPage = rolePersistService.getRolesByUserNameAndRoleName(StringUtils.EMPTY,
                StringUtils.EMPTY, DEFAULT_PAGE_NO, Integer.MAX_VALUE);
        if (roleInfoPage == null) {
            return null;
        }
        return roleInfoPage.getPageItems();
    }
    
    @Override
    public List<PermissionInfo> getPermissions(String role) {
        List<PermissionInfo> permissionInfoList = getCachedPermissionInfoMap().get(role);
        if (!authConfigs.isCachingEnabled() || permissionInfoList == null) {
            Page<PermissionInfo> permissionInfoPage = getPermissions(role, DEFAULT_PAGE_NO, Integer.MAX_VALUE);
            if (permissionInfoPage != null) {
                permissionInfoList = permissionInfoPage.getPageItems();
                if (!CollectionUtils.isEmpty(permissionInfoList)) {
                    getCachedPermissionInfoMap().put(role, permissionInfoList);
                }
            }
        }
        return permissionInfoList;
    }
    
    @Override
    public Page<PermissionInfo> getPermissions(String role, int pageNo, int pageSize) {
        Page<PermissionInfo> pageInfo = permissionPersistService.getPermissions(role, pageNo, pageSize);
        if (pageInfo == null) {
            return new Page<>();
        }
        return pageInfo;
    }
    
    @Override
    public void addRole(String role, String username) {
        if (userDetailsService.getUser(username) == null) {
            throw new IllegalArgumentException("user '" + username + "' not found!");
        }
        
        if (AuthConstants.GLOBAL_ADMIN_ROLE.equals(role)) {
            throw new IllegalArgumentException(
                    "role '" + AuthConstants.GLOBAL_ADMIN_ROLE + "' is not permitted to create!");
        }
        
        if (isUserBoundToRole(role, username)) {
            throw new IllegalArgumentException("user '" + username + "' already bound to the role '" + role + "'!");
        }
        
        rolePersistService.addRole(role, username);
        getCachedRoleSet().add(role);
    }
    
    @Override
    public void addAdminRole(String username) {
        if (userDetailsService.getUser(username) == null) {
            throw new IllegalArgumentException("user '" + username + "' not found!");
        }
        if (hasGlobalAdminRole()) {
            throw new IllegalArgumentException("role '" + AuthConstants.GLOBAL_ADMIN_ROLE + "' already exist !");
        }
        
        rolePersistService.addRole(AuthConstants.GLOBAL_ADMIN_ROLE, username);
        getCachedRoleSet().add(AuthConstants.GLOBAL_ADMIN_ROLE);
        authConfigs.setHasGlobalAdminRole(true);
    }
    
    @Override
    public void deleteRole(String role, String userName) {
        if (AuthConstants.GLOBAL_ADMIN_ROLE.equals(role)) {
            throw new IllegalArgumentException(
                    "role '" + AuthConstants.GLOBAL_ADMIN_ROLE + "' is not permitted to delete!");
        }
        rolePersistService.deleteRole(role, userName);
    }
    
    @Override
    public void deleteRole(String role) {
        if (AuthConstants.GLOBAL_ADMIN_ROLE.equals(role)) {
            throw new IllegalArgumentException(
                    "role '" + AuthConstants.GLOBAL_ADMIN_ROLE + "' is not permitted to delete!");
        }
        rolePersistService.deleteRole(role);
        getCachedRoleInfoMap().remove(role);
    }
    
    @Override
    public void addPermission(String role, String resource, String action) {
        if (!getCachedRoleSet().contains(role)) {
            throw new IllegalArgumentException("role " + role + " not found!");
        }
        permissionPersistService.addPermission(role, resource, action);
    }
    
    @Override
    public void deletePermission(String role, String resource, String action) {
        permissionPersistService.deletePermission(role, resource, action);
    }
    
    @Override
    public Page<RoleInfo> findRoles(String username, String role, int pageNo, int pageSize) {
        return rolePersistService.findRolesLike4Page(username, role, pageNo, pageSize);
    }
    
    @Override
    public List<String> findRoleNames(String role) {
        return rolePersistService.findRolesLikeRoleName(role);
    }
    
    @Override
    public Page<PermissionInfo> findPermissions(String role, int pageNo, int pageSize) {
        return permissionPersistService.findPermissionsLike4Page(role, pageNo, pageSize);
    }
    
    boolean isUserBoundToRole(String role, String username) {
        Page<RoleInfo> roleInfoPage = rolePersistService.getRolesByUserNameAndRoleName(username, role, DEFAULT_PAGE_NO,
                1);
        if (roleInfoPage == null) {
            return false;
        }
        List<RoleInfo> roleInfos = roleInfoPage.getPageItems();
        return CollectionUtils.isNotEmpty(roleInfos) && roleInfos.stream()
                .anyMatch(roleInfo -> role.equals(roleInfo.getRole()));
    }
}
