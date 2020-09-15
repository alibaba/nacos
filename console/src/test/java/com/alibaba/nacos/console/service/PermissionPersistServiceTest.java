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

package com.alibaba.nacos.console.service;

import com.alibaba.nacos.Nacos;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.config.server.auth.PermissionInfo;
import com.alibaba.nacos.config.server.auth.PermissionPersistService;
import com.alibaba.nacos.config.server.model.Page;
import com.alibaba.nacos.config.server.modules.entity.PermissionsEntity;
import com.alibaba.nacos.config.server.modules.entity.QPermissionsEntity;
import com.alibaba.nacos.config.server.modules.repository.PermissionsRepository;
import com.alibaba.nacos.console.BaseTest;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

/**
 * PermissionPersistServiceTest.
 *
 * @author Nacos
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = Nacos.class)
public class PermissionPersistServiceTest extends BaseTest {
    
    /**
     * permissions.
     */
    private PermissionsEntity permissionsEntity;
    
    @Autowired
    private PermissionPersistService permissionPersistService;
    
    @Autowired
    private PermissionsRepository permissionsRepository;
    
    /**
     * before.
     */
    @Before
    public void before() {
        permissionsEntity = JacksonUtils.toObj(TestData.PERMISSIONS_JSON, PermissionsEntity.class);
    }
    
    /**
     * getPermissionsTest.
     */
    @Test
    public void getPermissionsTest() {
        QPermissionsEntity qPermissions = QPermissionsEntity.permissionsEntity;
        PermissionsEntity result = permissionsRepository.findOne(qPermissions.role.eq(permissionsEntity.getRole()))
                .orElse(null);
        if (result == null) {
            addPermissionTest();
        }
        
        Page<PermissionInfo> page = permissionPersistService.getPermissions(permissionsEntity.getRole(), 0, 10);
        Assert.assertNotNull(page.getPageItems());
        Assert.assertTrue(page.getPageItems().size() > 0);
    }
    
    /**
     * addPermissionTest.
     */
    @Test
    public void addPermissionTest() {
        permissionPersistService.addPermission(permissionsEntity.getRole(), permissionsEntity.getResource(),
                permissionsEntity.getAction());
    }
    
    /**
     * deletePermissionTest.
     */
    @Test
    public void deletePermissionTest() {
        permissionPersistService.deletePermission(permissionsEntity.getRole(), permissionsEntity.getResource(),
                permissionsEntity.getAction());
    }
}
