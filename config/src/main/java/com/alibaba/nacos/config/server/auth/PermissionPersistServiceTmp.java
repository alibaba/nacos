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

import com.alibaba.nacos.config.server.modules.entity.PermissionsEntity;
import com.alibaba.nacos.config.server.modules.entity.QPermissions;
import com.alibaba.nacos.config.server.modules.repository.PermissionsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/**
 * @author Nacos
 */
@Service
public class PermissionPersistServiceTmp {

    @Autowired
    private PermissionsRepository permissionsRepository;

    public Page<PermissionsEntity> getPermissions(String role, int pageNo, int pageSize) {
        return permissionsRepository.findAll(QPermissions.permissions.role.eq(role),
            PageRequest.of(pageNo, pageSize));
    }

    public void addPermission(String role, String resource, String action) {
        permissionsRepository.save(new PermissionsEntity(role, resource, action));
    }


    public void deletePermission(String role, String resource, String action) {
        QPermissions qPermissions = QPermissions.permissions;
        permissionsRepository.findOne(qPermissions.role.eq(role)
            .and(qPermissions.resource.eq(resource))
            .and(qPermissions.action.eq(action)))
            .ifPresent(p -> permissionsRepository.delete(p));
    }
}
