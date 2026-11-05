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

import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.persistence.configuration.DatasourceConfiguration;
import com.alibaba.nacos.persistence.datasource.DataSourceService;
import com.alibaba.nacos.persistence.datasource.DynamicDataSource;
import com.alibaba.nacos.plugin.auth.impl.persistence.PermissionInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.lang.reflect.Field;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OraclePermissionPersistServiceImplTest {
    
    @Mock
    private JdbcTemplate jdbcTemplate;
    
    @Mock
    private DataSourceService dataSourceService;
    
    private boolean embeddedStorageCache;
    
    private DataSourceService dataSourceServiceCache;
    
    private OraclePermissionPersistServiceImpl oraclePermissionPersistService;
    
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
        oraclePermissionPersistService = new OraclePermissionPersistServiceImpl();
        oraclePermissionPersistService.init();
    }
    
    @AfterEach
    void tearDown() throws NoSuchFieldException, IllegalAccessException {
        DatasourceConfiguration.setEmbeddedStorage(embeddedStorageCache);
        Field datasourceField = DynamicDataSource.class.getDeclaredField("basicDataSourceService");
        datasourceField.setAccessible(true);
        datasourceField.set(DynamicDataSource.getInstance(), dataSourceServiceCache);
    }
    
    @Test
    void testAddPermissionUsesResourcesColumn() {
        oraclePermissionPersistService.addPermission("role", "resource", "action");
        verify(jdbcTemplate)
            .update("INSERT INTO permissions (role, resources, action) VALUES (?, ?, ?)", "role",
                "resource", "action");
    }
    
    @Test
    void testDeletePermissionUsesResourcesColumn() {
        oraclePermissionPersistService.deletePermission("role", "resource", "action");
        verify(jdbcTemplate)
            .update("DELETE FROM permissions WHERE role=? AND resources=? AND action=?", "role",
                "resource", "action");
    }
    
    @Test
    void testGetPermissionsSelectsQuotedAliasWithOraclePaging() {
        Page<PermissionInfo> page = oraclePermissionPersistService.getPermissions("role", 1, 10);
        
        assertEquals(1, page.getTotalCount());
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sqlCaptor.capture(), any(Object[].class), any(RowMapper.class));
        assertTrue(sqlCaptor.getValue()
            .contains("SELECT role,resources AS \"resource\",action FROM permissions WHERE"));
        assertTrue(sqlCaptor.getValue().endsWith("OFFSET ? ROWS FETCH NEXT ? ROWS ONLY"));
    }
    
    @Test
    void testGetPermissionsPagingArgsContainOffsetAndFetch() {
        when(jdbcTemplate.queryForObject(any(), any(), eq(Integer.class))).thenReturn(25);
        when(jdbcTemplate.query(any(String.class), any(Object[].class), any(RowMapper.class)))
            .thenReturn(Collections.emptyList());
        
        oraclePermissionPersistService.getPermissions("role", 2, 10);
        
        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).query(any(String.class), argsCaptor.capture(), any(RowMapper.class));
        assertEquals(3, argsCaptor.getValue().length);
        assertEquals("role", argsCaptor.getValue()[0]);
        assertEquals(10, argsCaptor.getValue()[1]);
        assertEquals(10, argsCaptor.getValue()[2]);
    }
    
    @Test
    void testFindPermissionsLike4PageUsesQuotedAliasAndOraclePaging() {
        when(jdbcTemplate.query(any(String.class), any(Object[].class), any(RowMapper.class)))
            .thenReturn(Collections.emptyList());
        
        oraclePermissionPersistService.findPermissionsLike4Page("rol*", 1, 10);
        
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sqlCaptor.capture(), any(Object[].class), any(RowMapper.class));
        assertTrue(sqlCaptor.getValue()
            .contains("SELECT role,resources AS \"resource\",action FROM permissions"));
        assertTrue(sqlCaptor.getValue().contains("role LIKE ? ESCAPE '\\' "));
        assertTrue(sqlCaptor.getValue().endsWith("OFFSET ? ROWS FETCH NEXT ? ROWS ONLY"));
    }
    
    @Test
    void testInitFailsFastOnNonOracleDialect() {
        when(dataSourceService.getDataSourceType()).thenReturn("mysql");
        OraclePermissionPersistServiceImpl service = new OraclePermissionPersistServiceImpl();
        assertThrows(IllegalStateException.class, service::init);
    }
    
    @Test
    void testGenerateLikeArgument() {
        assertEquals("ro\\_le%", oraclePermissionPersistService.generateLikeArgument("ro_le*"));
        assertEquals("plain", oraclePermissionPersistService.generateLikeArgument("plain"));
    }
}
