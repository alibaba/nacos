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
 * Nacos auth plugin role service interface.
 *
 * @author xiweng.yy
 */
public interface NacosRoleService {
    
    /**
     * Determine if the user has permission of the resource.
     *
     * <p>Note if the user has many roles, this method returns true if any one role of the user has the desired
     * permission.
     *
     * @param nacosUser  user info
     * @param permission permission to auth
     * @return true if granted, false otherwise
     */
    boolean hasPermission(NacosUser nacosUser, Permission permission);
    
    /**
     * Add permission to tole.
     *
     * @param role     role name
     * @param resource resource
     * @param action   action
     */
    void addPermission(String role, String resource, String action);
    
    /**
     * Delete permission from role.
     *
     * @param role     role name
     * @param resource resource
     * @param action   action
     */
    void deletePermission(String role, String resource, String action);
    
    /**
     * Get all permissions of the role.
     *
     * @param role role name
     * @return List of {@link PermissionInfo} for the role
     */
    List<PermissionInfo> getPermissions(String role);
    
    /**
     * Accurate search permissions by role name pattern.
     *
     * @param role      role name pattern
     * @param pageNo    page number
     * @param pageSize  page size
     * @return List of {@link RoleInfo} match role name pattern
     */
    Page<PermissionInfo> getPermissions(String role, int pageNo, int pageSize);
    
    /**
     * Blur search permissions by role name pattern.
     *
     * @param role      role name pattern
     * @param pageNo    page number
     * @param pageSize  page size
     * @return List of {@link RoleInfo} match role name pattern
     */
    Page<PermissionInfo> findPermissions(String role, int pageNo, int pageSize);
    
    /**
     * Judge whether the permission is duplicate.
     *
     * @param role role name
     * @param resource resource
     * @param action action
     * @return true if duplicate, false otherwise
     */
    Result<Boolean> isDuplicatePermission(String role, String resource, String action);
    
    /**
     * Get All roles for target user.
     *
     * @param username username of target user
     * @return List of {@link RoleInfo} for target user
     */
    List<RoleInfo> getRoles(String username);
    
    /**
     * Accurate search roles by role name pattern.
     *
     * @param username  username of target user
     * @param role      role name
     * @param pageNo    page number
     * @param pageSize  page size
     * @return List of {@link RoleInfo} match role name pattern
     */
    Page<RoleInfo> getRoles(String username, String role, int pageNo, int pageSize);
    
    /**
     * Blur search roles by role name pattern.
     *
     * @param username  username of target user
     * @param role      role name pattern
     * @param pageNo    page number
     * @param pageSize  page size
     * @return List of {@link RoleInfo} match role name pattern
     */
    Page<RoleInfo> findRoles(String username, String role, int pageNo, int pageSize);
    
    /**
     * Blur search role names by role name pattern.
     *
     * @param role role name pattern
     * @return List of {@link RoleInfo} match role name pattern
     */
    List<String> findRoleNames(String role);
    
    /**
     * Get All roles in Nacos.
     *
     * @return List of {@link RoleInfo} in Nacos
     */
    List<RoleInfo> getAllRoles();
    
    /**
     * Add role to user.
     *
     * @param role     role name
     * @param username user name
     */
    void addRole(String role, String username);
    
    /**
     * Delete Role from user.
     *
     * @param role     role
     * @param userName userName
     */
    void deleteRole(String role, String userName);
    
    /**
     * Delete Role from Nacos.
     *
     * @param role role
     */
    void deleteRole(String role);
    
    /**
     * Add role.
     *
     * @param username user name
     */
    void addAdminRole(String username);
    
    /**
     * Check if user has admin role.
     *
     * @param userName user name
     * @return true if user has admin role.
     */
    boolean hasGlobalAdminRole(String userName);
    
    /**
     * Check if all user has at least one admin role.
     *
     * @return true if all user has at least one admin role.
     */
    boolean hasGlobalAdminRole();
}
