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
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.auth.config.AuthConfigs;
import com.alibaba.nacos.common.utils.CollectionUtils;
import com.alibaba.nacos.common.utils.ConcurrentHashSet;
import com.alibaba.nacos.common.utils.StringUtils;
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
import com.alibaba.nacos.plugin.auth.impl.users.NacosUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import static com.alibaba.nacos.api.common.Constants.DEFAULT_NAMESPACE_ID;

/**
 * Nacos builtin role service, implemented by directly access to database.
 *
 * @author nkorange
 * @since 1.2.0
 */
@Service
public class NacosRoleServiceDirectImpl implements NacosRoleService {
    
    private static final int DEFAULT_PAGE_NO = 1;
    
    @Autowired
    private AuthConfigs authConfigs;
    
    @Autowired
    private RolePersistService rolePersistService;
    
    @Autowired
    private NacosUserService userDetailsService;
    
    @Autowired
    private PermissionPersistService permissionPersistService;
    
    private volatile Set<String> roleSet = new ConcurrentHashSet<>();
    
    private volatile Map<String, List<RoleInfo>> roleInfoMap = new ConcurrentHashMap<>();
    
    private volatile Map<String, List<PermissionInfo>> permissionInfoMap = new ConcurrentHashMap<>();
    
    @Scheduled(initialDelay = 5000, fixedDelay = 15000)
    private void reload() {
        try {
            Page<RoleInfo> roleInfoPage = rolePersistService.getRolesByUserNameAndRoleName(StringUtils.EMPTY,
                    StringUtils.EMPTY, DEFAULT_PAGE_NO, Integer.MAX_VALUE);
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
                Page<PermissionInfo> permissionInfoPage = permissionPersistService.getPermissions(role, DEFAULT_PAGE_NO,
                        Integer.MAX_VALUE);
                tmpPermissionInfoMap.put(role, permissionInfoPage.getPageItems());
            }
            
            roleSet = tmpRoleSet;
            roleInfoMap = tmpRoleInfoMap;
            permissionInfoMap = tmpPermissionInfoMap;
        } catch (Exception e) {
            Loggers.AUTH.warn("[LOAD-ROLES] load failed", e);
        }
    }
    
    @Override
    public boolean hasPermission(NacosUser nacosUser, Permission permission) {
        if (isUpdatePasswordPermission(permission)) {
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
                if (permissionAction.contains(permission.getAction()) && Pattern.matches(permissionResource,
                        joinResource(permission.getResource()))) {
                    return true;
                }
            }
        }
        return false;
    }
    
    /**
     * If API is update user password, don't do permission check, because there is permission check in API logic.
     */
    private boolean isUpdatePasswordPermission(Permission permission) {
        Properties properties = permission.getResource().getProperties();
        return null != properties && properties.contains(AuthConstants.UPDATE_PASSWORD_ENTRY_POINT);
    }
    
    @Override
    public List<RoleInfo> getRoles(String username) {
        List<RoleInfo> roleInfoList = roleInfoMap.get(username);
        if (!authConfigs.isCachingEnabled() || roleInfoList == null) {
            Page<RoleInfo> roleInfoPage = getRoles(username, StringUtils.EMPTY, DEFAULT_PAGE_NO, Integer.MAX_VALUE);
            if (roleInfoPage != null) {
                roleInfoList = roleInfoPage.getPageItems();
                if (!CollectionUtils.isEmpty(roleInfoList)) {
                    roleInfoMap.put(username, roleInfoList);
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
        List<PermissionInfo> permissionInfoList = permissionInfoMap.get(role);
        if (!authConfigs.isCachingEnabled() || permissionInfoList == null) {
            Page<PermissionInfo> permissionInfoPage = getPermissions(role, DEFAULT_PAGE_NO, Integer.MAX_VALUE);
            if (permissionInfoPage != null) {
                permissionInfoList = permissionInfoPage.getPageItems();
                if (!CollectionUtils.isEmpty(permissionInfoList)) {
                    permissionInfoMap.put(role, permissionInfoList);
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
        roleSet.add(role);
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
        roleSet.add(AuthConstants.GLOBAL_ADMIN_ROLE);
        authConfigs.setHasGlobalAdminRole(true);
    }
    
    @Override
    public void deleteRole(String role, String userName) {
        rolePersistService.deleteRole(role, userName);
    }
    
    @Override
    public void deleteRole(String role) {
        rolePersistService.deleteRole(role);
        roleSet.remove(role);
    }
    
    @Override
    public void addPermission(String role, String resource, String action) {
        if (!roleSet.contains(role)) {
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
    
    private String joinResource(Resource resource) {
        if (SignType.SPECIFIED.equals(resource.getType())) {
            return resource.getName();
        }
        StringBuilder result = new StringBuilder();
        String namespaceId = resource.getNamespaceId();
        if (StringUtils.isNotBlank(namespaceId)) {
            // https://github.com/alibaba/nacos/issues/10347
            if (!DEFAULT_NAMESPACE_ID.equals(namespaceId)) {
                result.append(namespaceId);
            }
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
    
    @Override
    public Page<PermissionInfo> findPermissions(String role, int pageNo, int pageSize) {
        return permissionPersistService.findPermissionsLike4Page(role, pageNo, pageSize);
    }
    
    @Override
    public boolean hasGlobalAdminRole(String userName) {
        List<RoleInfo> roles = getRoles(userName);
        
        return roles.stream().anyMatch(roleInfo -> AuthConstants.GLOBAL_ADMIN_ROLE.equals(roleInfo.getRole()));
    }
    
    @Override
    public boolean hasGlobalAdminRole() {
        if (authConfigs.isHasGlobalAdminRole()) {
            return true;
        }
        List<RoleInfo> roles = getAllRoles();
        boolean hasGlobalAdminRole = CollectionUtils.isNotEmpty(roles) && roles.stream()
                .anyMatch(roleInfo -> AuthConstants.GLOBAL_ADMIN_ROLE.equals(roleInfo.getRole()));
        authConfigs.setHasGlobalAdminRole(hasGlobalAdminRole);
        return hasGlobalAdminRole;
    }
    
    @Override
    public Result<Boolean> isDuplicatePermission(String role, String resource, String action) {
        List<PermissionInfo> permissionInfos = getPermissions(role);
        if (CollectionUtils.isEmpty(permissionInfos)) {
            return Result.success(Boolean.FALSE);
        }
        for (PermissionInfo permissionInfo : permissionInfos) {
            boolean resourceMatch = StringUtils.equals(resource, permissionInfo.getResource());
            boolean actionMatch =
                    StringUtils.equals(action, permissionInfo.getAction()) || "rw".equals(permissionInfo.getAction());
            if (resourceMatch && actionMatch) {
                return Result.success(Boolean.TRUE);
            }
        }
        return Result.success(Boolean.FALSE);
    }
    
    @Override
    public boolean isUserBoundToRole(String role, String username) {
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
