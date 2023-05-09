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

import com.alibaba.nacos.persistence.model.Page;
import com.alibaba.nacos.persistence.repository.embedded.operate.DatabaseOperate;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.lang.reflect.Field;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class EmbeddedUserPersistServiceImplTest {
    
    @Mock
    private DatabaseOperate databaseOperate;
    
    private EmbeddedUserPersistServiceImpl embeddedUserPersistService;
    
    @Before
    public void setUp() throws Exception {
        when(databaseOperate.queryOne(any(String.class), any(Object[].class), eq(Integer.class))).thenReturn(0);
        embeddedUserPersistService = new EmbeddedUserPersistServiceImpl();
        Class<EmbeddedUserPersistServiceImpl> embeddedUserPersistServiceClass = EmbeddedUserPersistServiceImpl.class;
        
        Field databaseOperateField = embeddedUserPersistServiceClass.getDeclaredField("databaseOperate");
        databaseOperateField.setAccessible(true);
        databaseOperateField.set(embeddedUserPersistService, databaseOperate);
    }
    
    @Test
    public void testCreateUser() {
        embeddedUserPersistService.createUser("username", "password");
        
        Mockito.verify(databaseOperate).blockUpdate();
    }
    
    @Test
    public void testDeleteUser() {
        embeddedUserPersistService.deleteUser("username");
        
        Mockito.verify(databaseOperate).blockUpdate();
    }
    
    @Test
    public void testUpdateUserPassword() {
        embeddedUserPersistService.updateUserPassword("username", "password");
        
        Mockito.verify(databaseOperate).blockUpdate();
    }
    
    @Test
    public void testFindUserByUsername() {
        User user = embeddedUserPersistService.findUserByUsername("username");
        
        Assert.assertNull(user);
    }
    
    @Test
    public void testGetUsers() {
        Page<User> users = embeddedUserPersistService.getUsers(1, 10, "nacos");
        
        Assert.assertNotNull(users);
    }
    
    @Test
    public void testFindUserLikeUsername() {
        List<String> username = embeddedUserPersistService.findUserLikeUsername("username");
        Assert.assertEquals(username.size(), 0);
    }
}
