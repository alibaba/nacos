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

import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.config.server.configuration.ConditionOnExternalStorage;
import com.alibaba.nacos.config.server.model.Page;
import com.alibaba.nacos.config.server.modules.entity.QRolesEntity;
import com.alibaba.nacos.config.server.modules.entity.RolesEntity;
import com.alibaba.nacos.config.server.modules.mapstruct.RoleInfoMapStruct;
import com.alibaba.nacos.config.server.modules.repository.RolesRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;


/**
 * Implemetation of ExternalRolePersistServiceImpl.
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@Conditional(value = ConditionOnExternalStorage.class)
@Component
public class ExternalRolePersistServiceImpl implements RolePersistService {
    
    @Autowired
    private RolesRepository rolesRepository;
    
    public Page<RoleInfo> getRoles(int pageNo, int pageSize) {
        org.springframework.data.domain.Page<RolesEntity> sPage = rolesRepository
                .findAll(PageRequest.of(pageNo - 1, pageSize));
        Page<RoleInfo> page = new Page<>();
        page.setPageNumber(pageNo);
        page.setPagesAvailable(sPage.getTotalPages());
        page.setPageItems(RoleInfoMapStruct.INSTANCE.convertRoleInfoList(sPage.getContent()));
        page.setTotalCount((int) sPage.getTotalElements());
        return page;
    }
    
    public Page<RoleInfo> getRolesByUserName(String username, int pageNo, int pageSize) {
        org.springframework.data.domain.Page<RolesEntity> sPage;
        if (StringUtils.isNotBlank(username)) {
            sPage = rolesRepository
                    .findAll(QRolesEntity.rolesEntity.username.eq(username), PageRequest.of(pageNo - 1, pageSize));
        } else {
            sPage = rolesRepository.findAll(PageRequest.of(pageNo - 1, pageSize));
        }
        Page<RoleInfo> page = new Page<>();
        page.setPageNumber(pageNo);
        page.setPagesAvailable(sPage.getTotalPages());
        page.setPageItems(RoleInfoMapStruct.INSTANCE.convertRoleInfoList(sPage.getContent()));
        page.setTotalCount((int) sPage.getTotalElements());
        return page;
    }
    
    /**
     * Execute add role operation.
     *
     * @param role     role string value.
     * @param userName username string value.
     */
    public void addRole(String role, String userName) {
        
        rolesRepository.save(new RolesEntity(userName, role));
    }
    
    /**
     * Execute delete role operation.
     *
     * @param role role string value.
     */
    public void deleteRole(String role) {
        Iterable<RolesEntity> iterable = rolesRepository.findAll(QRolesEntity.rolesEntity.role.eq(role));
        rolesRepository.deleteAll(iterable);
    }
    
    /**
     * Execute delete role operation.
     *
     * @param role     role string value.
     * @param username username string value.
     */
    public void deleteRole(String role, String username) {
        QRolesEntity qRoles = QRolesEntity.rolesEntity;
        rolesRepository.findOne(qRoles.role.eq(role).and(qRoles.username.eq(username)))
                .ifPresent(s -> rolesRepository.delete(s));
    }
    
    @Override
    public List<String> findRolesLikeRoleName(String role) {
        List<RolesEntity> rolesEntities = (List<RolesEntity>) rolesRepository
                .findAll(QRolesEntity.rolesEntity.role.like(role));
        return rolesEntities.stream().map(s -> s.getRole()).collect(Collectors.toList());
    }
    
}
