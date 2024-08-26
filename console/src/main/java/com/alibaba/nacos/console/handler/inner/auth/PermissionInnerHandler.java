/*
 * Copyright 1999-2024 Alibaba Group Holding Ltd.
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
 *
 */

package com.alibaba.nacos.console.handler.inner.auth;

import com.alibaba.nacos.console.handler.auth.PermissionHandler;
import com.alibaba.nacos.persistence.model.Page;
import com.alibaba.nacos.plugin.auth.impl.persistence.PermissionInfo;
import com.alibaba.nacos.plugin.auth.impl.roles.NacosRoleServiceImpl;
import org.springframework.stereotype.Service;

/**
 * Implementation of PermissionHandler that handles permission-related operations.
 *
 * @author zhangyukun
 */
@Service
public class PermissionInnerHandler implements PermissionHandler {
    
    private final NacosRoleServiceImpl nacosRoleService;
    
    /**
     * Constructs a new PermissionInnerHandler with the provided dependencies.
     *
     * @param nacosRoleService the service for role-related operations
     */
    public PermissionInnerHandler(NacosRoleServiceImpl nacosRoleService) {
        this.nacosRoleService = nacosRoleService;
    }
    
    /**
     * Adds a permission to a role.
     *
     * @param role     the role
     * @param resource the related resource
     * @param action   the related action
     * @return true if the operation was successful
     */
    @Override
    public boolean createPermission(String role, String resource, String action) {
        nacosRoleService.addPermission(role, resource, action);
        return true;
    }
    
    /**
     * Deletes a permission from a role.
     *
     * @param role     the role
     * @param resource the related resource
     * @param action   the related action
     * @return true if the operation was successful
     */
    @Override
    public boolean deletePermission(String role, String resource, String action) {
        nacosRoleService.deletePermission(role, resource, action);
        return true;
    }
    
    /**
     * Retrieves a paginated list of permissions for a role with the option for accurate or fuzzy search.
     *
     * @param role     the role
     * @param pageNo   the page number
     * @param pageSize the size of the page
     * @param search   the type of search: "accurate" or "blur"
     * @return a paginated list of permissions
     */
    @Override
    public Page<PermissionInfo> getPermissionList(String role, int pageNo, int pageSize, String search) {
        if ("blur".equalsIgnoreCase(search)) {
            return nacosRoleService.findPermissionsLike4Page(role, pageNo, pageSize);
        } else {
            return nacosRoleService.getPermissionsFromDatabase(role, pageNo, pageSize);
        }
    }
}
