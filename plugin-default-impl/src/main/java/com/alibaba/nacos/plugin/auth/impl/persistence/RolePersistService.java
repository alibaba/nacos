/*
 * Copyright 1999-2021 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.auth.impl.persistence;

import java.util.List;

import com.alibaba.nacos.config.server.model.Page;

/**
 * Role CRUD service.
 *
 * @author nkorange
 * @since 1.2.0
 */
@SuppressWarnings("PMD.AbstractMethodOrInterfaceMethodMustUseJavadocRule")
public interface RolePersistService {

    /**
     * get roles by page.
     *
     * @param pageNo pageNo
     * @param pageSize pageSize
     * @return roles page info
     */
    Page<RoleInfo> getRoles(int pageNo, int pageSize);

    /**
     * query the user's roles by username.
     *
     * @param username username
     * @param pageNo pageNo
     * @param pageSize pageSize
     * @return roles page info
     */
    Page<RoleInfo> getRolesByUserNameAndRoleName(String username, String role, int pageNo, int pageSize);

    /**
     * assign role to user.
     *
     * @param role role
     * @param userName username
     */
    void addRole(String role, String userName);

    /**
     * delete role.
     *
     * @param role role
     */
    void deleteRole(String role);

    /**
     * delete user's role.
     *
     * @param role role
     * @param username username
     */
    void deleteRole(String role, String username);

    /**
     * fuzzy query roles by role name.
     *
     * @param role role
     * @return roles
     */
    List<String> findRolesLikeRoleName(String role);

    /**
     * Generate fuzzy search Sql.
     *
     * @param s origin string
     * @return fuzzy search Sql
     */
    String generateLikeArgument(String s);

    /**.
     * fuzzy query role information based on roleName and username
     *
     * @param username username of user
     * @param pageNo page number
     * @param pageSize page size
     * @return {@link Page} with {@link RoleInfo} generation
     */
    Page<RoleInfo> findRolesLike4Page(String username, String role, int pageNo, int pageSize);
}
