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
import com.alibaba.nacos.config.server.auth.RolePersistServiceTmp;
import com.alibaba.nacos.config.server.modules.entity.RolesEntity;
import com.alibaba.nacos.console.BaseTest;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.test.context.junit4.SpringRunner;

/**
 * @author zhangshun
 * @version $Id: RolePersistServiceTest.java,v 0.1 2020年06月06日 14:59 $Exp
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = Nacos.class)
public class RolePersistServiceTest extends BaseTest {

    private RolesEntity roles;

    @Before
    public void before() {
        roles = JacksonUtils.toObj(TestData.ROLES_JSON, RolesEntity.class);
    }


    @Autowired
    private RolePersistServiceTmp rolePersistServiceTmp;

    @Test
    public void getRolesTest() {
        rolePersistServiceTmp.addRole(roles.getRole(), roles.getUsername());
        Page<RolesEntity> page = rolePersistServiceTmp.getRoles(0, 10);
        Assert.assertNotNull(page.getContent());
        Assert.assertTrue(page.getContent().size() > 0);
    }

    @Test
    public void getRolesByUserNameTest() {
        Page<RolesEntity> page = rolePersistServiceTmp.getRolesByUserName(roles.getUsername(), 0, 10);
        Assert.assertNotNull(page.getContent());
        Assert.assertTrue(page.getContent().size() > 0);
    }

    @Test
    public void addRoleTest() {
        rolePersistServiceTmp.addRole(roles.getRole(), roles.getUsername());
    }


    @Test
    public void deleteRole1Test() {
        rolePersistServiceTmp.deleteRole(roles.getRole());
    }

    @Test
    public void deleteRole2Test() {
        rolePersistServiceTmp.deleteRole(roles.getRole(), roles.getUsername());
    }

}
