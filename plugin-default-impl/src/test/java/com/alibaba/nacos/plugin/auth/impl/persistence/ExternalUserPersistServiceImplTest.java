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

import com.alibaba.nacos.config.server.model.Page;
import com.alibaba.nacos.config.server.service.repository.PaginationHelper;
import com.alibaba.nacos.config.server.service.repository.extrnal.ExternalStoragePersistServiceImpl;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.jdbc.core.JdbcTemplate;

import java.lang.reflect.Field;
import java.util.List;

@RunWith(MockitoJUnitRunner.class)
public class ExternalUserPersistServiceImplTest {
    
    @Mock
    private ExternalStoragePersistServiceImpl persistService;
    
    @Mock
    private JdbcTemplate jdbcTemplate;
    
    @Mock
    private PaginationHelper paginationHelper;
    
    private ExternalUserPersistServiceImpl externalUserPersistService;
    
    @Before
    public void setUp() throws Exception {
        externalUserPersistService = new ExternalUserPersistServiceImpl();
        
        Class<ExternalUserPersistServiceImpl> externalUserPersistServiceClass = ExternalUserPersistServiceImpl.class;
        Field persistServiceClassDeclaredField = externalUserPersistServiceClass.getDeclaredField("persistService");
        persistServiceClassDeclaredField.setAccessible(true);
        persistServiceClassDeclaredField.set(externalUserPersistService, persistService);
        
        Mockito.when(persistService.getJdbcTemplate()).thenReturn(jdbcTemplate);
        Mockito.when(persistService.createPaginationHelper()).thenReturn(paginationHelper);
        externalUserPersistService.init();
    }
    
    @Test
    public void testCreateUser() {
        externalUserPersistService.createUser("username", "password");
        
        String sql = "INSERT INTO users (username, password, enabled) VALUES (?, ?, ?)";
        Mockito.verify(jdbcTemplate).update(sql, "username", "password", true);
    }
    
    @Test
    public void testDeleteUser() {
        externalUserPersistService.deleteUser("username");
        
        String sql = "DELETE FROM users WHERE username=?";
        Mockito.verify(jdbcTemplate).update(sql, "username");
    }
    
    @Test
    public void testUpdateUserPassword() {
        externalUserPersistService.updateUserPassword("username", "password");
        
        String sql = "UPDATE users SET password = ? WHERE username=?";
        Mockito.verify(jdbcTemplate).update(sql, "password", "username");
    }
    
    @Test
    public void testFindUserByUsername() {
        User username = externalUserPersistService.findUserByUsername("username");
        
        Assert.assertNull(username);
    }
    
    @Test
    public void testGetUsers() {
        Page<User> users = externalUserPersistService.getUsers(1, 10, "nacos");
        
        Assert.assertNotNull(users);
    }
    
    @Test
    public void testFindUserLikeUsername() {
        List<String> username = externalUserPersistService.findUserLikeUsername("username");
        
        Assert.assertEquals(username.size(), 0);
    }
}
