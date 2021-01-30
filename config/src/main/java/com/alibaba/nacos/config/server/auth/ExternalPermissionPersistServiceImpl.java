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
import com.alibaba.nacos.config.server.modules.entity.PermissionsEntity;
import com.alibaba.nacos.config.server.modules.entity.QPermissionsEntity;
import com.alibaba.nacos.config.server.modules.mapstruct.PermissionsMapStruct;
import com.alibaba.nacos.config.server.modules.repository.PermissionsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/**
 * Implemetation of ExternalPermissionPersistServiceImpl.
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@Conditional(value = ConditionOnExternalStorage.class)
@Component
public class ExternalPermissionPersistServiceImpl implements PermissionPersistService {
    
    @Autowired
    private PermissionsRepository permissionsRepository;
    
    public Page<PermissionInfo> getPermissions(String role, int pageNo, int pageSize) {
        org.springframework.data.domain.Page<PermissionsEntity> sPage;
        if (StringUtils.isNotBlank(role)) {
            sPage = permissionsRepository
                    .findAll(QPermissionsEntity.permissionsEntity.role.eq(role), PageRequest.of(pageNo - 1, pageSize));
        } else {
            sPage = permissionsRepository.findAll(PageRequest.of(pageNo - 1, pageSize));
        }
        Page<PermissionInfo> page = new Page<>();
        page.setPageNumber(pageNo);
        page.setPagesAvailable(sPage.getTotalPages());
        page.setPageItems(PermissionsMapStruct.INSTANCE.convertPermissionInfoList(sPage.getContent()));
        page.setTotalCount((int) sPage.getTotalElements());
        return page;
    }
    
    /**
     * Execute add permission operation.
     *
     * @param role role string value.
     * @param resource resource string value.
     * @param action action string value.
     */
    public void addPermission(String role, String resource, String action) {
    
        permissionsRepository.save(new PermissionsEntity(role, resource, action));
    }
    
    /**
     * Execute delete permission operation.
     *
     * @param role role string value.
     * @param resource resource string value.
     * @param action action string value.
     */
    public void deletePermission(String role, String resource, String action) {
    
        QPermissionsEntity qPermissions = QPermissionsEntity.permissionsEntity;
        permissionsRepository.findOne(
                qPermissions.role.eq(role).and(qPermissions.resource.eq(resource)).and(qPermissions.action.eq(action)))
                .ifPresent(p -> permissionsRepository.delete(p));
    }
    
}
