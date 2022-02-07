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

package com.alibaba.nacos.config.server.auth;

import com.alibaba.nacos.config.server.model.Page;

/**
 * Permission CRUD service.
 *
 * @author nkorange
 * @since 1.2.0
 */
@SuppressWarnings("PMD.AbstractMethodOrInterfaceMethodMustUseJavadocRule")
public interface PermissionPersistService {

    /**
     * get the permissions of role by page.
     *
     * @param role role
     * @param pageNo pageNo
     * @param pageSize pageSize
     * @return permissions page info
     */
    Page<PermissionInfo> getPermissions(String role, int pageNo, int pageSize);

    /**
     * assign permission to role.
     *
     * @param role role
     * @param resource resource
     * @param action action
     */
    void addPermission(String role, String resource, String action);

    /**
     * delete the role's permission.
     *
     * @param role role
     * @param resource resource
     * @param action action
     */
    void deletePermission(String role, String resource, String action);
}
