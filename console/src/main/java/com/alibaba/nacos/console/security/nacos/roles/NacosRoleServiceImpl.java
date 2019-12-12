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
package com.alibaba.nacos.console.security.nacos.roles;


import com.alibaba.nacos.config.server.auth.PermissionInfo;
import com.alibaba.nacos.config.server.auth.PermissionPersistService;
import com.alibaba.nacos.config.server.auth.RolePersistService;
import com.alibaba.nacos.config.server.model.Page;
import com.alibaba.nacos.console.security.nacos.NacosAuthConfig;
import com.alibaba.nacos.core.auth.Resource;
import io.jsonwebtoken.lang.Collections;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
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

    private static final String GLOBAL_ADMIN_ROLE = "GLOBAL_ADMIN";

    @Autowired
    private RolePersistService rolePersistService;

    @Autowired
    private PermissionPersistService permissionPersistService;

    private Map<String, String> namespaceAdminRoles = new ConcurrentHashMap<>();

    /**
     * Determine if the user has permission of the resource.
     * <p>
     * Note if the user has many roles, this method returns true if any one role of the user has the
     * desired permission.
     *
     * @param username user info
     * @param resource resource to access
     * @return true if granted, false otherwise
     */
    public boolean hasPermission(String username, Resource resource) {

        Page<String> stringPage = getRoles(username, 1, Integer.MAX_VALUE);
        if (stringPage==null || Collections.isEmpty(stringPage.getPageItems())) {
            return false;
        }
        List<String> roles = stringPage.getPageItems();

        // Global admin pass:
        if (roles.contains(GLOBAL_ADMIN_ROLE)) {
            return true;
        }

        // Old global admin can pass resource 'console/':
        if (resource.getKey().startsWith(NacosAuthConfig.CONSOLE_RESOURCE_NAME_PREFIX)) {
            return false;
        }

        String[] segs = resource.getKey().split(Resource.SPLITTER);

        // Namespace admin pass:
        if (roles.contains(namespaceAdminRoles.get(segs[0]))) {
            return true;
        }

        // For other roles, use a pattern match to decide if pass or not.
        for (String role : roles) {
            Page<PermissionInfo> pageResult = getPermissionsByRole(role, 1, Integer.MAX_VALUE);
            if (pageResult==null || pageResult.getPageItems()==null) {
                continue;
            }
            for (PermissionInfo permissionInfo : pageResult.getPageItems()) {
                String resourceKey = permissionInfo.getPermission().replaceAll("\\*", ".*");

                String[] resourceSegs = resourceKey.split(Resource.SPLITTER);
                String action = resourceSegs[resourceSegs.length-1];
                if (action.contains(segs[segs.length-1]) &&
                    Pattern.matches(resourceKey.substring(0, resourceKey.lastIndexOf(Resource.SPLITTER)),
                    resource.parseName())) {
                    return true;
                }
            }
        }
        return false;
    }

    public Page<String> getRoles(String userName, int pageNo, int pageSize) {
        Page<String> roles = rolePersistService.getRolesByUserName(userName, pageNo, pageSize);
        return roles;
    }

    public Page<PermissionInfo> getPermissionsByRole(String role, int pageNo, int pageSize) {
        return permissionPersistService.getPermissions(role, pageNo, pageSize);
    }

    public void addRole(String role, String userName) {
        rolePersistService.addRole(role, userName);
    }

    public void deleteRole(String role, String userName) {
        rolePersistService.deleteRole(role, userName);
    }

    public void deleteRole(String role) {
        rolePersistService.deleteRole(role);
    }

    public Page<PermissionInfo> getPermissions(String role, int pageNo, int pageSize) {
        Page<PermissionInfo> pageInfo = permissionPersistService.getPermissions(role, pageNo, pageSize);
        return pageInfo;
    }

    public void addPermission(String role, String permission) {
        permissionPersistService.addPermission(role,  permission);
    }

    public void deletePermission(String role, String permission) {
        permissionPersistService.deletePermission(role,  permission);
    }
}
