/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.common.utils.CollectionUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.auth.api.Permission;
import com.alibaba.nacos.plugin.auth.api.Resource;
import com.alibaba.nacos.plugin.auth.constant.Constants;
import com.alibaba.nacos.plugin.auth.constant.SignType;
import com.alibaba.nacos.plugin.auth.impl.configuration.AuthConfigs;
import com.alibaba.nacos.plugin.auth.impl.constant.AuthConstants;
import com.alibaba.nacos.plugin.auth.impl.persistence.PermissionInfo;
import com.alibaba.nacos.plugin.auth.impl.persistence.RoleInfo;
import com.alibaba.nacos.plugin.auth.impl.users.NacosUser;

import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;

import static com.alibaba.nacos.api.common.Constants.DEFAULT_NAMESPACE_ID;

/**
 * Nacos abstract cached role service.
 *
 * @author xiweng.yy
 */
public abstract class AbstractCheckedRoleService extends AbstractCachedRoleService implements NacosRoleService {
    
    private final AuthConfigs authConfigs;
    
    protected AbstractCheckedRoleService(AuthConfigs authConfigs) {
        this.authConfigs = authConfigs;
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
                if (permissionResource.startsWith(":")) {
                    permissionResource = DEFAULT_NAMESPACE_ID + permissionResource;
                }
                String permissionAction = permissionInfo.getAction();
                if (permissionAction.contains(permission.getAction()) && Pattern.matches(permissionResource,
                        joinResource(permission.getResource()))) {
                    return true;
                }
            }
        }
        return false;
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
    public boolean hasGlobalAdminRole(String userName) {
        List<RoleInfo> roles = getRoles(userName);
        if (CollectionUtils.isEmpty(roles)) {
            return false;
        }
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
    
    /**
     * If API is update user password, don't do permission check, because there is permission check in API logic.
     */
    private boolean isUpdatePasswordPermission(Permission permission) {
        Properties properties = permission.getResource().getProperties();
        return null != properties && properties.contains(AuthConstants.UPDATE_PASSWORD_ENTRY_POINT);
    }
    
    private String joinResource(Resource resource) {
        if (SignType.SPECIFIED.equals(resource.getType())) {
            return resource.getName();
        }
        StringBuilder result = new StringBuilder();
        String namespaceId = resource.getNamespaceId();
        if (StringUtils.isBlank(namespaceId)) {
            namespaceId = DEFAULT_NAMESPACE_ID;
        }
        
        result.append(namespaceId);
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
}
