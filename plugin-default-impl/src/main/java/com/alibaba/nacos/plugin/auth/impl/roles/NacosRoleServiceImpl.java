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

import com.alibaba.nacos.auth.config.AuthConfigs;
import com.alibaba.nacos.common.utils.CollectionUtils;
import com.alibaba.nacos.common.utils.ConcurrentHashSet;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.config.server.model.Page;
import com.alibaba.nacos.core.utils.Loggers;
import com.alibaba.nacos.plugin.auth.api.Permission;
import com.alibaba.nacos.plugin.auth.api.Resource;
import com.alibaba.nacos.plugin.auth.constant.Constants;
import com.alibaba.nacos.plugin.auth.constant.SignType;
import com.alibaba.nacos.plugin.auth.impl.constant.AuthConstants;
import com.alibaba.nacos.plugin.auth.impl.persistence.PermissionInfo;
import com.alibaba.nacos.plugin.auth.impl.persistence.PermissionPersistService;
import com.alibaba.nacos.plugin.auth.impl.persistence.RoleInfo;
import com.alibaba.nacos.plugin.auth.impl.persistence.RolePersistService;
import com.alibaba.nacos.plugin.auth.impl.users.NacosUser;
import com.alibaba.nacos.plugin.auth.impl.users.NacosUserDetailsServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Nacos builtin role service.
 *
 * @author nkorange
 * @since 1.2.0
 */
@Service
public class NacosRoleServiceImpl {
    
    private static final int DEFAULT_PAGE_NO = 1;
    
    @Autowired
    private AuthConfigs authConfigs;
    
    @Autowired
    private RolePersistService rolePersistService;
    
    @Autowired
    private NacosUserDetailsServiceImpl userDetailsService;
    
    @Autowired
    private PermissionPersistService permissionPersistService;
    
    private volatile Set<String> roleSet = new ConcurrentHashSet<>();
    
    private volatile Map<String, List<RoleInfo>> roleInfoMap = new ConcurrentHashMap<>();
    
    private volatile Map<String, List<PermissionInfo>> permissionInfoMap = new ConcurrentHashMap<>();
    
    @Scheduled(initialDelay = 5000, fixedDelay = 15000)
    private void reload() {
        try {
            Page<RoleInfo> roleInfoPage = rolePersistService
                    .getRolesByUserNameAndRoleName(StringUtils.EMPTY, StringUtils.EMPTY, DEFAULT_PAGE_NO, Integer.MAX_VALUE);
            if (roleInfoPage == null) {
                return;
            }
            Set<String> tmpRoleSet = new HashSet<>(16);
            Map<String, List<RoleInfo>> tmpRoleInfoMap = new ConcurrentHashMap<>(16);
            for (RoleInfo roleInfo : roleInfoPage.getPageItems()) {
                if (!tmpRoleInfoMap.containsKey(roleInfo.getUsername())) {
                    tmpRoleInfoMap.put(roleInfo.getUsername(), new ArrayList<>());
                }
                tmpRoleInfoMap.get(roleInfo.getUsername()).add(roleInfo);
                tmpRoleSet.add(roleInfo.getRole());
            }
            
            Map<String, List<PermissionInfo>> tmpPermissionInfoMap = new ConcurrentHashMap<>(16);
            for (String role : tmpRoleSet) {
                Page<PermissionInfo> permissionInfoPage = permissionPersistService
                        .getPermissions(role, DEFAULT_PAGE_NO, Integer.MAX_VALUE);
                tmpPermissionInfoMap.put(role, permissionInfoPage.getPageItems());
            }
            
            roleSet = tmpRoleSet;
            roleInfoMap = tmpRoleInfoMap;
            permissionInfoMap = tmpPermissionInfoMap;
        } catch (Exception e) {
            Loggers.AUTH.warn("[LOAD-ROLES] load failed", e);
        }
    }
    
    /**
     * Determine if the user has permission of the resource.
     *
     * <p>Note if the user has many roles, this method returns true if any one role of the user has the desired
     * permission.
     *
     * @param nacosUser   user info
     * @param permission permission to auth
     * @return true if granted, false otherwise
     */
    public boolean hasPermission(NacosUser nacosUser, Permission permission) {
        //update password
        if (AuthConstants.UPDATE_PASSWORD_ENTRY_POINT.equals(permission.getResource().getName())) {
            return true;
        }
        
        List<RoleInfo> roleInfoList = getRoles(nacosUser.getUserName());
        if (CollectionUtils.isEmpty(roleInfoList)) {
            return false;
        }
        
        // Global admin pass:
        for (RoleInfo roleInfo : roleInfoList) {
            if (AuthConstants.GLOBAL_ADMIN_ROLE.equals(roleInfo.getRole())) {
                nacosUser.setGlobalAdmin(true);
                return true;
            }
        }
        
        // Old global admin can pass resource 'console/':
        if (permission.getResource().getName().startsWith(AuthConstants.CONSOLE_RESOURCE_NAME_PREFIX)) {
            return false;
        }
        
        // For other roles, use a pattern match to decide if pass or not.
        for (RoleInfo roleInfo : roleInfoList) {
            List<PermissionInfo> permissionInfoList = getPermissions(roleInfo.getRole());
            if (CollectionUtils.isEmpty(permissionInfoList)) {
                continue;
            }
            for (PermissionInfo permissionInfo : permissionInfoList) {
                String permissionResource = permissionInfo.getResource().replaceAll("\\*", ".*");
                String permissionAction = permissionInfo.getAction();
                if (permissionAction.contains(permission.getAction()) && Pattern
                        .matches(permissionResource, joinResource(permission.getResource()))) {
                    return true;
                }
            }
        }
        return false;
    }
    
    public List<RoleInfo> getRoles(String username) {
        List<RoleInfo> roleInfoList = roleInfoMap.get(username);
        if (!authConfigs.isCachingEnabled() || roleInfoList == null) {
            Page<RoleInfo> roleInfoPage = getRolesFromDatabase(username, StringUtils.EMPTY, DEFAULT_PAGE_NO, Integer.MAX_VALUE);
            if (roleInfoPage != null) {
                roleInfoList = roleInfoPage.getPageItems();
                if (!CollectionUtils.isEmpty(roleInfoList)) {
                    roleInfoMap.put(username, roleInfoList);
                }
            }
        }
        return roleInfoList;
    }
    
    public Page<RoleInfo> getRolesFromDatabase(String userName, String role, int pageNo, int pageSize) {
        Page<RoleInfo> roles = rolePersistService.getRolesByUserNameAndRoleName(userName, role, pageNo, pageSize);
        if (roles == null) {
            return new Page<>();
        }
        return roles;
    }
    
    public List<PermissionInfo> getPermissions(String role) {
        List<PermissionInfo> permissionInfoList = permissionInfoMap.get(role);
        if (!authConfigs.isCachingEnabled() || permissionInfoList == null) {
            Page<PermissionInfo> permissionInfoPage = getPermissionsFromDatabase(role, DEFAULT_PAGE_NO,
                    Integer.MAX_VALUE);
            if (permissionInfoPage != null) {
                permissionInfoList = permissionInfoPage.getPageItems();
                if (!CollectionUtils.isEmpty(permissionInfoList)) {
                    permissionInfoMap.put(role, permissionInfoList);
                }
            }
        }
        return permissionInfoList;
    }
    
    public Page<PermissionInfo> getPermissionsByRoleFromDatabase(String role, int pageNo, int pageSize) {
        return permissionPersistService.getPermissions(role, pageNo, pageSize);
    }
    
    /**
     * Add role.
     *
     * @param role     role name
     * @param username user name
     */
    public void addRole(String role, String username) {
        if (userDetailsService.getUserFromDatabase(username) == null) {
            throw new IllegalArgumentException("user '" + username + "' not found!");
        }
        if (AuthConstants.GLOBAL_ADMIN_ROLE.equals(role)) {
            throw new IllegalArgumentException(
                    "role '" + AuthConstants.GLOBAL_ADMIN_ROLE + "' is not permitted to create!");
        }
        rolePersistService.addRole(role, username);
        roleSet.add(role);
    }
    
    public void deleteRole(String role, String userName) {
        rolePersistService.deleteRole(role, userName);
    }
    
    public void deleteRole(String role) {
        rolePersistService.deleteRole(role);
        roleSet.remove(role);
    }
    
    public Page<PermissionInfo> getPermissionsFromDatabase(String role, int pageNo, int pageSize) {
        Page<PermissionInfo> pageInfo = permissionPersistService.getPermissions(role, pageNo, pageSize);
        if (pageInfo == null) {
            return new Page<>();
        }
        return pageInfo;
    }
    
    /**
     * Add permission.
     *
     * @param role     role name
     * @param resource resource
     * @param action   action
     */
    public void addPermission(String role, String resource, String action) {
        if (!roleSet.contains(role)) {
            throw new IllegalArgumentException("role " + role + " not found!");
        }
        permissionPersistService.addPermission(role, resource, action);
    }
    
    public void deletePermission(String role, String resource, String action) {
        permissionPersistService.deletePermission(role, resource, action);
    }
    
    public List<String> findRolesLikeRoleName(String role) {
        return rolePersistService.findRolesLikeRoleName(role);
    }
    
    private String joinResource(Resource resource) {
        if (SignType.SPECIFIED.equals(resource.getType())) {
            return resource.getName();
        }
        StringBuilder result = new StringBuilder();
        String namespaceId = resource.getNamespaceId();
        if (StringUtils.isNotBlank(namespaceId)) {
            result.append(namespaceId);
        }
        String group = resource.getGroup();
        if (StringUtils.isBlank(group)) {
            result.append(Constants.Resource.SPLITTER).append('*');
        } else {
            result.append(Constants.Resource.SPLITTER).append(group);
        }
        String resourceName = resource.getName();
        if (StringUtils.isBlank(resourceName)) {
            result.append(Constants.Resource.SPLITTER).append(resource.getType().toLowerCase()).append("/*");
        } else {
            result.append(Constants.Resource.SPLITTER).append(resource.getType().toLowerCase()).append('/')
                    .append(resourceName);
        }
        return result.toString();
    }

    public Page<RoleInfo> findRolesLike4Page(String username, String role, int pageNo, int pageSize) {
        return rolePersistService.findRolesLike4Page(username, role, pageNo, pageSize);
    }

    public Page<PermissionInfo> findPermissionsLike4Page(String role, int pageNo, int pageSize) {
        return permissionPersistService.findPermissionsLike4Page(role, pageNo, pageSize);
    }
}
