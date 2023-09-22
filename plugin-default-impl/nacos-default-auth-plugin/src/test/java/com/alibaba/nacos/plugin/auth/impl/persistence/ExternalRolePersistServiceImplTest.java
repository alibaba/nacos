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
import org.junit.After;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ExternalRolePersistServiceImplTest {
    
    @Mock
    private JdbcTemplate jdbcTemplate;
    
    @Mock
    private DataSourceService dataSourceService;
    
    private boolean embeddedStorageCache;
    
    private DataSourceService dataSourceServiceCache;
    
    private ExternalRolePersistServiceImpl externalRolePersistService;
    
    @Before
    public void setUp() throws Exception {
        externalRolePersistService = new ExternalRolePersistServiceImpl();
        when(jdbcTemplate.queryForObject(any(), any(), eq(Integer.class))).thenReturn(0);
        when(dataSourceService.getJdbcTemplate()).thenReturn(jdbcTemplate);
        embeddedStorageCache = DatasourceConfiguration.isEmbeddedStorage();
        DatasourceConfiguration.setEmbeddedStorage(false);
        Field datasourceField = DynamicDataSource.class.getDeclaredField("basicDataSourceService");
        datasourceField.setAccessible(true);
        dataSourceServiceCache = (DataSourceService) datasourceField.get(DynamicDataSource.getInstance());
        datasourceField.set(DynamicDataSource.getInstance(), dataSourceService);
        externalRolePersistService.init();
    }
    
    @After
    public void tearDown() throws NoSuchFieldException, IllegalAccessException {
        DatasourceConfiguration.setEmbeddedStorage(embeddedStorageCache);
        Field datasourceField = DynamicDataSource.class.getDeclaredField("basicDataSourceService");
        datasourceField.setAccessible(true);
        datasourceField.set(DynamicDataSource.getInstance(), dataSourceServiceCache);
    }
    
    @Test
    public void testGetRoles() {
        Page<RoleInfo> roles = externalRolePersistService.getRoles(1, 10);
        
        Assert.assertNotNull(roles);
    }
    
    @Test
    public void testGetRolesByUserName() {
        Page<RoleInfo> userName = externalRolePersistService
                .getRolesByUserNameAndRoleName("userName", "roleName", 1, 10);
        Assert.assertNotNull(userName);
    }
    
    @Test
    public void testAddRole() {
        externalRolePersistService.addRole("role", "userName");
        
        String sql = "INSERT INTO roles (role, username) VALUES (?, ?)";
        Mockito.verify(jdbcTemplate).update(sql, "role", "userName");
    }
    
    @Test
    public void testDeleteRole() {
        
        externalRolePersistService.deleteRole("role");
        String sql = "DELETE FROM roles WHERE role=?";
        Mockito.verify(jdbcTemplate).update(sql, "role");
        
        externalRolePersistService.deleteRole("role", "userName");
        String sql2 = "DELETE FROM roles WHERE role=? AND username=?";
        Mockito.verify(jdbcTemplate).update(sql2, "role", "userName");
        
    }
    
    @Test
    public void testFindRolesLikeRoleName() {
        List<String> role = externalRolePersistService.findRolesLikeRoleName("role");
        
        Assert.assertEquals(role.size(), 0);
    }
}
