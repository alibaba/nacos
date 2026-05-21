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
import com.alibaba.nacos.api.model.Page;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
        dataSourceServiceCache =
            (DataSourceService) datasourceField.get(DynamicDataSource.getInstance());
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
    void testFindUserByUsernameSuccessAndExceptions() {
        String sql = "SELECT username,password FROM users WHERE username=? ";
        User user = new User();
        user.setUsername("username");
        when(jdbcTemplate.queryForObject(eq(sql), any(Object[].class),
            eq(AuthRowMapperManager.USER_ROW_MAPPER))).thenReturn(user)
            .thenThrow(new EmptyResultDataAccessException(1))
            .thenThrow(new CannotGetJdbcConnectionException("down"))
            .thenThrow(new IllegalStateException("boom"));
        
        assertSame(user, externalUserPersistService.findUserByUsername("username"));
        assertNull(externalUserPersistService.findUserByUsername("missing"));
        assertThrows(CannotGetJdbcConnectionException.class,
            () -> externalUserPersistService.findUserByUsername("down"));
        assertThrows(RuntimeException.class,
            () -> externalUserPersistService.findUserByUsername("boom"));
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
    
    @Test
    void testFindUsersLikeAndGenerateLikeArgument() {
        assertEquals("na\\_me%", externalUserPersistService.generateLikeArgument("na_me*"));
        assertEquals("plain", externalUserPersistService.generateLikeArgument("plain"));
        
        Page<User> page = new Page<>();
        page.setPageItems(Collections.singletonList(new User()));
        page.setTotalCount(1);
        AuthPaginationHelper<User> helper = Mockito.mock(AuthPaginationHelper.class);
        ExternalUserPersistServiceImpl service = serviceWithHelper(helper);
        when(helper.fetchPage(any(), any(), any(), eq(1), eq(10), any())).thenReturn(page);
        
        assertSame(page, service.findUsersLike4Page("na_*", 1, 10));
        assertSame(page, service.findUsersLike4Page("", 1, 10));
    }
    
    @Test
    void testGetUsersReturnsEmptyPageWhenHelperReturnsNull() {
        AuthPaginationHelper<User> helper = Mockito.mock(AuthPaginationHelper.class);
        ExternalUserPersistServiceImpl service = serviceWithHelper(helper);
        when(helper.fetchPage(any(), any(), any(), eq(1), eq(10), any())).thenReturn(null);
        
        Page<User> result = service.getUsers(1, 10, "");
        
        assertEquals(0, result.getTotalCount());
        assertEquals(Collections.emptyList(), result.getPageItems());
    }
    
    @Test
    void testConnectionExceptionsAreRethrown() {
        CannotGetJdbcConnectionException exception =
            new CannotGetJdbcConnectionException("down");
        when(jdbcTemplate.update("INSERT INTO users (username, password, enabled) VALUES (?, ?, ?)",
            "username", "password", true)).thenThrow(exception);
        assertSame(exception, assertThrows(CannotGetJdbcConnectionException.class,
            () -> externalUserPersistService.createUser("username", "password")));
        
        CannotGetJdbcConnectionException deleteException =
            new CannotGetJdbcConnectionException("delete");
        when(jdbcTemplate.update("DELETE FROM users WHERE username=?",
            "username")).thenThrow(deleteException);
        assertSame(deleteException, assertThrows(CannotGetJdbcConnectionException.class,
            () -> externalUserPersistService.deleteUser("username")));
        
        CannotGetJdbcConnectionException updateException =
            new CannotGetJdbcConnectionException("update");
        when(jdbcTemplate.update("UPDATE users SET password = ? WHERE username=?", "password",
            "username")).thenThrow(updateException);
        assertSame(updateException, assertThrows(CannotGetJdbcConnectionException.class,
            () -> externalUserPersistService.updateUserPassword("username", "password")));
        
        AuthPaginationHelper<User> helper = Mockito.mock(AuthPaginationHelper.class);
        ExternalUserPersistServiceImpl service = serviceWithHelper(helper);
        CannotGetJdbcConnectionException getUsersException =
            new CannotGetJdbcConnectionException("get");
        when(helper.fetchPage(any(), any(), any(), eq(1), eq(10), any()))
            .thenThrow(getUsersException);
        assertSame(getUsersException, assertThrows(CannotGetJdbcConnectionException.class,
            () -> service.getUsers(1, 10, "username")));
        
        CannotGetJdbcConnectionException findUsersException =
            new CannotGetJdbcConnectionException("find");
        when(helper.fetchPage(any(), any(), any(), eq(2), eq(20), any()))
            .thenThrow(findUsersException);
        assertSame(findUsersException, assertThrows(CannotGetJdbcConnectionException.class,
            () -> service.findUsersLike4Page("username", 2, 20)));
    }
    
    private ExternalUserPersistServiceImpl serviceWithHelper(AuthPaginationHelper<User> helper) {
        return new ExternalUserPersistServiceImpl() {
            
            @Override
            @SuppressWarnings("unchecked")
            public <E> AuthPaginationHelper<E> createPaginationHelper() {
                return (AuthPaginationHelper<E>) helper;
            }
        };
    }
}
