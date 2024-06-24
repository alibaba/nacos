/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

import com.alibaba.nacos.persistence.configuration.DatasourceConfiguration;
import com.alibaba.nacos.persistence.datasource.DataSourceService;
import com.alibaba.nacos.persistence.datasource.DynamicDataSource;
import com.alibaba.nacos.persistence.model.Page;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
// todo remove this
@MockitoSettings(strictness = Strictness.LENIENT)
class ExternalUserPersistServiceImplTest {
    
    @Mock
    private JdbcTemplate jdbcTemplate;
    
    @Mock
    private DataSourceService dataSourceService;
    
    private boolean embeddedStorageCache;
    
    private DataSourceService dataSourceServiceCache;
    
    private ExternalUserPersistServiceImpl externalUserPersistService;
    
    @BeforeEach
    void setUp() throws Exception {
        externalUserPersistService = new ExternalUserPersistServiceImpl();
        when(jdbcTemplate.queryForObject(any(), any(), eq(Integer.class))).thenReturn(0);
        when(dataSourceService.getJdbcTemplate()).thenReturn(jdbcTemplate);
        embeddedStorageCache = DatasourceConfiguration.isEmbeddedStorage();
        DatasourceConfiguration.setEmbeddedStorage(false);
        Field datasourceField = DynamicDataSource.class.getDeclaredField("basicDataSourceService");
        datasourceField.setAccessible(true);
        dataSourceServiceCache = (DataSourceService) datasourceField.get(DynamicDataSource.getInstance());
        datasourceField.set(DynamicDataSource.getInstance(), dataSourceService);
        externalUserPersistService.init();
    }
    
    @AfterEach
    void tearDown() throws NoSuchFieldException, IllegalAccessException {
        DatasourceConfiguration.setEmbeddedStorage(embeddedStorageCache);
        Field datasourceField = DynamicDataSource.class.getDeclaredField("basicDataSourceService");
        datasourceField.setAccessible(true);
        datasourceField.set(DynamicDataSource.getInstance(), dataSourceServiceCache);
    }
    
    @Test
    void testCreateUser() {
        externalUserPersistService.createUser("username", "password");
        
        String sql = "INSERT INTO users (username, password, enabled) VALUES (?, ?, ?)";
        Mockito.verify(jdbcTemplate).update(sql, "username", "password", true);
    }
    
    @Test
    void testDeleteUser() {
        externalUserPersistService.deleteUser("username");
        
        String sql = "DELETE FROM users WHERE username=?";
        Mockito.verify(jdbcTemplate).update(sql, "username");
    }
    
    @Test
    void testUpdateUserPassword() {
        externalUserPersistService.updateUserPassword("username", "password");
        
        String sql = "UPDATE users SET password = ? WHERE username=?";
        Mockito.verify(jdbcTemplate).update(sql, "password", "username");
    }
    
    @Test
    void testFindUserByUsername() {
        User username = externalUserPersistService.findUserByUsername("username");
        
        assertNull(username);
    }
    
    @Test
    void testGetUsers() {
        Page<User> users = externalUserPersistService.getUsers(1, 10, "nacos");
        
        assertNotNull(users);
    }
    
    @Test
    void testFindUserLikeUsername() {
        List<String> username = externalUserPersistService.findUserLikeUsername("username");
        
        assertEquals(0, username.size());
    }
}
