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

import com.alibaba.nacos.config.server.modules.entity.QRoles;
import com.alibaba.nacos.config.server.modules.entity.Roles;
import com.alibaba.nacos.config.server.modules.repository.RolesRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/**
 * @author Nacos
 */
@Service
public class RolePersistServiceTmp {

    @Autowired
    private RolesRepository rolesRepository;

    public Page<Roles> getRoles(int pageNo, int pageSize) {
        return rolesRepository.findAll(null, PageRequest.of(pageNo, pageSize));
    }

    public Page<Roles> getRolesByUserName(String username, int pageNo, int pageSize) {
        return rolesRepository.findAll(QRoles.roles.username.eq(username), PageRequest.of(pageNo, pageSize));
    }

    public void addRole(String role, String userName) {
        rolesRepository.save(new Roles(userName, role));
    }


    public void deleteRole(String role) {
        Iterable<Roles> iterable = rolesRepository.findAll(QRoles.roles.role.eq(role));
        rolesRepository.deleteAll(iterable);
    }

    public void deleteRole(String role, String username) {
        QRoles qRoles = QRoles.roles;
        rolesRepository.findOne(qRoles.role.eq(role)
            .and(qRoles.username.eq(username)))
            .ifPresent(s -> rolesRepository.delete(s));
    }

}
