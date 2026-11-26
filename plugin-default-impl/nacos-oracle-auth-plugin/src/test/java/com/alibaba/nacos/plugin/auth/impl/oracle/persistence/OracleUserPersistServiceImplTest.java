/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.auth.impl.oracle.persistence;

import com.alibaba.nacos.persistence.configuration.DatasourceConfiguration;
import com.alibaba.nacos.persistence.datasource.DataSourceService;
import com.alibaba.nacos.persistence.datasource.DynamicDataSource;
import com.alibaba.nacos.plugin.auth.impl.persistence.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.lang.reflect.Field;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OracleUserPersistServiceImplTest {
    
    @Mock
    private JdbcTemplate jdbcTemplate;
    
    @Mock
    private DataSourceService dataSourceService;
    
    private boolean embeddedStorageCache;
    
    private DataSourceService dataSourceServiceCache;
    
    private OracleUserPersistServiceImpl oracleUserPersistService;
    
    @BeforeEach
    void setUp() throws Exception {
        when(jdbcTemplate.queryForObject(any(), any(), eq(Integer.class))).thenReturn(1);
        when(dataSourceService.getJdbcTemplate()).thenReturn(jdbcTemplate);
        when(dataSourceService.getDataSourceType()).thenReturn("oracle");
        embeddedStorageCache = DatasourceConfiguration.isEmbeddedStorage();
        DatasourceConfiguration.setEmbeddedStorage(false);
        Field datasourceField = DynamicDataSource.class.getDeclaredField("basicDataSourceService");
        datasourceField.setAccessible(true);
        dataSourceServiceCache =
            (DataSourceService) datasourceField.get(DynamicDataSource.getInstance());
        datasourceField.set(DynamicDataSource.getInstance(), dataSourceService);
        oracleUserPersistService = new OracleUserPersistServiceImpl();
        oracleUserPersistService.init();
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
        oracleUserPersistService.createUser("username", "password");
        verify(jdbcTemplate).update(
            "INSERT INTO users (username, password, enabled) VALUES (?, ?, ?)",
            "username", "password", true);
    }
    
    @Test
    void testDeleteUser() {
        oracleUserPersistService.deleteUser("username");
        verify(jdbcTemplate).update("DELETE FROM users WHERE username=?", "username");
    }
    
    @Test
    void testUpdateUserPassword() {
        oracleUserPersistService.updateUserPassword("username", "new-password");
        verify(jdbcTemplate).update("UPDATE users SET password = ? WHERE username=?",
            "new-password",
            "username");
    }
    
    @Test
    void testFindUserByUsername() {
        User user = new User();
        user.setUsername("username");
        user.setPassword("password");
        when(jdbcTemplate.queryForObject(any(String.class), any(Object[].class),
            any(RowMapper.class))).thenReturn(user);
        
        assertEquals(user, oracleUserPersistService.findUserByUsername("username"));
        
        when(jdbcTemplate.queryForObject(any(String.class), any(Object[].class),
            any(RowMapper.class))).thenThrow(new EmptyResultDataAccessException(1));
        assertNull(oracleUserPersistService.findUserByUsername("username"));
    }
    
    @Test
    void testGetUsersAppendsOraclePaging() {
        when(jdbcTemplate.queryForObject(any(), any(), eq(Integer.class))).thenReturn(25);
        when(jdbcTemplate.query(any(String.class), any(Object[].class), any(RowMapper.class)))
            .thenReturn(Collections.emptyList());
        
        oracleUserPersistService.getUsers(2, 10, "");
        
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).query(sqlCaptor.capture(), argsCaptor.capture(), any(RowMapper.class));
        assertTrue(sqlCaptor.getValue().startsWith("SELECT username,password FROM users"));
        assertTrue(sqlCaptor.getValue().endsWith("OFFSET ? ROWS FETCH NEXT ? ROWS ONLY"));
        assertEquals(2, argsCaptor.getValue().length);
    }
    
    @Test
    void testFindUsersLike4PageAppendsOraclePaging() {
        when(jdbcTemplate.query(any(String.class), any(Object[].class), any(RowMapper.class)))
            .thenReturn(Collections.emptyList());
        
        oracleUserPersistService.findUsersLike4Page("user*", 1, 10);
        
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sqlCaptor.capture(), any(Object[].class), any(RowMapper.class));
        assertTrue(sqlCaptor.getValue().contains("username LIKE ? ESCAPE '\\' "));
        assertTrue(sqlCaptor.getValue().endsWith("OFFSET ? ROWS FETCH NEXT ? ROWS ONLY"));
    }
}
