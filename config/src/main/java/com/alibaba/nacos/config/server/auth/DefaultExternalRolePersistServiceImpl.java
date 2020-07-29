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
import com.alibaba.nacos.config.server.modules.entity.QRolesEntity;
import com.alibaba.nacos.config.server.modules.entity.RolesEntity;
import com.alibaba.nacos.config.server.modules.mapstruct.RoleInfoMapStruct;
import com.alibaba.nacos.config.server.modules.repository.RolesRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/**
 * @author Nacos
 */
@Service
public class DefaultExternalRolePersistServiceImpl {
    
    @Autowired
    private RolesRepository rolesRepository;
    
    public Page<RoleInfo> getRoles(int pageNo, int pageSize) {
        org.springframework.data.domain.Page<RolesEntity> sPage = rolesRepository
                .findAll(null, PageRequest.of(pageNo, pageSize));
        Page<RoleInfo> page = new Page<>();
        page.setPageNumber(sPage.getNumber());
        page.setPagesAvailable(sPage.getTotalPages());
        page.setPageItems(RoleInfoMapStruct.INSTANCE.convertRoleInfoList(sPage.getContent()));
        page.setTotalCount((int) sPage.getTotalElements());
        return page;
    }

    public Page<RoleInfo> getRolesByUserName(String username, int pageNo, int pageSize) {
        org.springframework.data.domain.Page<RolesEntity> sPage = rolesRepository
            .findAll(QRolesEntity.rolesEntity.username.eq(username), PageRequest.of(pageNo, pageSize));
        Page<RoleInfo> page = new Page<>();
        page.setPageNumber(sPage.getNumber());
        page.setPagesAvailable(sPage.getTotalPages());
        page.setPageItems(RoleInfoMapStruct.INSTANCE.convertRoleInfoList(sPage.getContent()));
        page.setTotalCount((int) sPage.getTotalElements());
        return page;
    }

    public void addRole(String role, String userName) {
        rolesRepository.save(new RolesEntity(userName, role));
    }


    public void deleteRole(String role) {
        Iterable<RolesEntity> iterable = rolesRepository.findAll(QRolesEntity.rolesEntity.role.eq(role));
        rolesRepository.deleteAll(iterable);
    }

    public void deleteRole(String role, String username) {
        QRolesEntity qRoles = QRolesEntity.rolesEntity;
        rolesRepository.findOne(qRoles.role.eq(role)
            .and(qRoles.username.eq(username)))
            .ifPresent(s -> rolesRepository.delete(s));
    }

}
