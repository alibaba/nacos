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

import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.plugin.auth.api.Permission;
import com.alibaba.nacos.plugin.auth.impl.persistence.PermissionInfo;
import com.alibaba.nacos.plugin.auth.impl.persistence.RoleInfo;
import com.alibaba.nacos.plugin.auth.impl.users.NacosUser;

import java.util.List;

/**
 * Nacos builtin role service, implemented by remote request to nacos server.
 * // TODO real to implement.
 *
 * @author xiweng.yy
 */
public class NacosRoleServiceRemoteImpl implements NacosRoleService {
    
    @Override
    public boolean hasPermission(NacosUser nacosUser, Permission permission) {
        return true;
    }
    
    @Override
    public void addPermission(String role, String resource, String action) {
    
    }
    
    @Override
    public void deletePermission(String role, String resource, String action) {
    
    }
    
    @Override
    public List<PermissionInfo> getPermissions(String role) {
        return List.of();
    }
    
    @Override
    public Page<PermissionInfo> getPermissions(String role, int pageNo, int pageSize) {
        return new Page<>();
    }
    
    @Override
    public Page<PermissionInfo> findPermissions(String role, int pageNo, int pageSize) {
        return new Page<>();
    }
    
    @Override
    public Result<Boolean> isDuplicatePermission(String role, String resource, String action) {
        return Result.success(true);
    }
    
    @Override
    public boolean isUserBoundToRole(String role, String username) {
        return false;
    }
    
    @Override
    public List<RoleInfo> getRoles(String username) {
        return List.of();
    }
    
    @Override
    public Page<RoleInfo> getRoles(String username, String role, int pageNo, int pageSize) {
        return new Page<>();
    }
    
    @Override
    public Page<RoleInfo> findRoles(String username, String role, int pageNo, int pageSize) {
        return new Page<>();
    }
    
    @Override
    public List<String> findRoleNames(String role) {
        return List.of();
    }
    
    @Override
    public List<RoleInfo> getAllRoles() {
        return List.of();
    }
    
    @Override
    public void addRole(String role, String username) {
    
    }
    
    @Override
    public void deleteRole(String role, String userName) {
    
    }
    
    @Override
    public void deleteRole(String role) {
    
    }
    
    @Override
    public void addAdminRole(String username) {
    
    }
    
    @Override
    public boolean hasGlobalAdminRole(String userName) {
        return true;
    }
    
    @Override
    public boolean hasGlobalAdminRole() {
        return true;
    }
}
