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
import com.alibaba.nacos.config.server.auth.ExternalUserPersistServiceImpl2;
import com.alibaba.nacos.config.server.modules.entity.UsersEntity;
import com.alibaba.nacos.console.BaseTest;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Random;

/**
 * @author zhangshun
 * @version $Id: UserPersistServiceTest.java,v 0.1 2020年06月06日 15:21 $Exp
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = Nacos.class)
public class UserPersistServiceTest extends BaseTest {


    private UsersEntity users;

    @Before
    public void before() {
        users = JacksonUtils.toObj(TestData.USERS_JSON, UsersEntity.class);
    }


    @Autowired
    private ExternalUserPersistServiceImpl2 userPersistServiceTmp;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    public void createUser() {
        userPersistServiceTmp.createUser(users.getUsername(), passwordEncoder.encode(users.getPassword()));
    }

    @Test
    public void deleteUserTest() {
        userPersistServiceTmp.deleteUser(users.getUsername());
    }

    @Test
    public void updateUserPassword() {
        userPersistServiceTmp.updateUserPassword(users.getUsername(),
            passwordEncoder.encode(users.getPassword() + new Random().nextInt()));
    }

    @Test
    public void findUserByUsernameTest() {
        UsersEntity result = userPersistServiceTmp.findUserByUsername(users.getUsername());
        Assert.assertNotNull(result);
    }

    @Test
    public void getUsersTest() {
        userPersistServiceTmp.createUser(users.getUsername(), passwordEncoder.encode(users.getPassword()));
        Page<UsersEntity> page = userPersistServiceTmp.getUsers(0, 10);
        Assert.assertNotNull(page.getContent());
        Assert.assertTrue(page.getContent().size() > 0);
    }

}
