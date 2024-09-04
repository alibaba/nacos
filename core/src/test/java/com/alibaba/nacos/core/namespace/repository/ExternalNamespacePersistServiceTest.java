/*
 * Copyright 1999-2024 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.core.namespace.repository;

import com.alibaba.nacos.core.namespace.model.TenantInfo;
import com.alibaba.nacos.persistence.configuration.DatasourceConfiguration;
import com.alibaba.nacos.persistence.datasource.DataSourceService;
import com.alibaba.nacos.persistence.datasource.DynamicDataSource;
import com.alibaba.nacos.persistence.exception.NJdbcException;
import com.alibaba.nacos.persistence.repository.embedded.operate.DatabaseOperate;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.alibaba.nacos.core.namespace.repository.NamespaceRowMapperInjector.TENANT_INFO_ROW_MAPPER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExternalNamespacePersistServiceTest {
    
    @Mock
    private DatabaseOperate databaseOperate;
    
    @Mock
    private DataSourceService dataSourceService;
    
    @Mock
    private JdbcTemplate jt;
    
    private ExternalNamespacePersistServiceImpl externalNamespacePersistService;
    
    private MockEnvironment environment;
    
    @BeforeEach
    void setUp() {
        EnvUtil.setIsStandalone(false);
        DatasourceConfiguration.setEmbeddedStorage(false);
        environment = new MockEnvironment();
        EnvUtil.setEnvironment(environment);
        DynamicDataSource instance = DynamicDataSource.getInstance();
        ReflectionTestUtils.setField(instance, "basicDataSourceService", dataSourceService);
        externalNamespacePersistService = new ExternalNamespacePersistServiceImpl();
        
        ReflectionTestUtils.setField(externalNamespacePersistService, "jt", jt);
    }
    
    @Test
    void insertTenantInfoAtomicTest() {
        String kp = "1";
        String namespaceId = "namespaceId";
        String namespaceName = "namespaceName";
        String namespaceDesc = "namespaceDesc";
        when(dataSourceService.getDataSourceType()).thenReturn("mysql");
        externalNamespacePersistService.insertTenantInfoAtomic(kp, namespaceId, namespaceName, namespaceDesc, "nacos",
                System.currentTimeMillis());
        
        when(jt.update(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyLong(), anyLong())).thenThrow(
                new NJdbcException("test"));
        assertThrows(DataAccessException.class,
                () -> externalNamespacePersistService.insertTenantInfoAtomic(kp, namespaceId, namespaceName, namespaceDesc, "nacos",
                        System.currentTimeMillis()));
        
    }
    
    @Test
    void removeTenantInfoAtomicTest() {
        String kp = "1";
        String namespaceId = "namespaceId";
        when(dataSourceService.getDataSourceType()).thenReturn("mysql");
        
        externalNamespacePersistService.removeTenantInfoAtomic(kp, namespaceId);
        
        when(jt.update(anyString(), anyString(), anyString())).thenThrow(new CannotGetJdbcConnectionException("test"));
        assertThrows(CannotGetJdbcConnectionException.class, () -> externalNamespacePersistService.removeTenantInfoAtomic(kp, namespaceId));
    }
    
    @Test
    void updateTenantNameAtomicTest() {
        String kp = "1";
        String namespaceId = "namespaceId";
        String namespaceName = "namespaceName";
        String namespaceDesc = "namespaceDesc";
        when(dataSourceService.getDataSourceType()).thenReturn("mysql");
        externalNamespacePersistService.updateTenantNameAtomic(kp, namespaceId, namespaceName, namespaceDesc);
        
        when(jt.update(anyString(), anyString(), anyString(), anyLong(), anyString(), anyString())).thenThrow(new NJdbcException("test"));
        assertThrows(DataAccessException.class,
                () -> externalNamespacePersistService.updateTenantNameAtomic(kp, namespaceId, namespaceName, namespaceDesc));
    }
    
    @Test
    void findTenantByKpTest() {
        String kp = "1";
        when(dataSourceService.getDataSourceType()).thenReturn("mysql");
        List<TenantInfo> tenantInfoList = new ArrayList<>(1);
        tenantInfoList.add(new TenantInfo());
        
        when(jt.query(anyString(), eq(new Object[] {kp}), eq(TENANT_INFO_ROW_MAPPER))).thenReturn(tenantInfoList);
        
        List<TenantInfo> tenantByKp = externalNamespacePersistService.findTenantByKp(kp);
        assertEquals(tenantInfoList.get(0), tenantByKp.get(0));
        
        when(jt.query(anyString(), eq(new Object[] {kp}), eq(TENANT_INFO_ROW_MAPPER))).thenThrow(
                new CannotGetJdbcConnectionException("test"));
        assertThrows(CannotGetJdbcConnectionException.class, () -> externalNamespacePersistService.findTenantByKp(kp));
        
        when(jt.query(anyString(), eq(new Object[] {kp}), eq(TENANT_INFO_ROW_MAPPER))).thenThrow(new EmptyResultDataAccessException(1));
        List<TenantInfo> tenantByKp1 = externalNamespacePersistService.findTenantByKp(kp);
        assertEquals(Collections.emptyList(), tenantByKp1);
        
        when(jt.query(anyString(), eq(new Object[] {kp}), eq(TENANT_INFO_ROW_MAPPER))).thenThrow(new RuntimeException("test"));
        assertThrows(RuntimeException.class, () -> externalNamespacePersistService.findTenantByKp(kp));
        
    }
    
    @Test
    void findTenantByKpTest2() {
        String kp = "1";
        String namespaceId = "namespaceId";
        when(dataSourceService.getDataSourceType()).thenReturn("mysql");
        TenantInfo tenantInfo = new TenantInfo();
        tenantInfo.setTenantId(namespaceId);
        
        when(jt.queryForObject(anyString(), eq(new Object[] {kp, namespaceId}), eq(TENANT_INFO_ROW_MAPPER))).thenReturn(tenantInfo);
        
        TenantInfo tenantByKp = externalNamespacePersistService.findTenantByKp(kp, namespaceId);
        assertEquals(tenantInfo.getTenantId(), tenantByKp.getTenantId());
        
        when(jt.queryForObject(anyString(), eq(new Object[] {kp, namespaceId}), eq(TENANT_INFO_ROW_MAPPER))).thenThrow(
                new CannotGetJdbcConnectionException("test"));
        assertThrows(CannotGetJdbcConnectionException.class, () -> externalNamespacePersistService.findTenantByKp(kp, namespaceId));
        
        when(jt.queryForObject(anyString(), eq(new Object[] {kp, namespaceId}), eq(TENANT_INFO_ROW_MAPPER))).thenThrow(
                new EmptyResultDataAccessException(1));
        TenantInfo tenantByKp1 = externalNamespacePersistService.findTenantByKp(kp, namespaceId);
        assertNull(tenantByKp1);
        
        when(jt.queryForObject(anyString(), eq(new Object[] {kp, namespaceId}), eq(TENANT_INFO_ROW_MAPPER))).thenThrow(
                new RuntimeException("test"));
        assertThrows(RuntimeException.class, () -> externalNamespacePersistService.findTenantByKp(kp, namespaceId));
        
    }
    
    @Test
    void generateLikeArgumentTest() {
        
        String test = externalNamespacePersistService.generateLikeArgument("test");
        
        String testB = externalNamespacePersistService.generateLikeArgument("test*");
        
        assertEquals("test", test);
        
        assertEquals("test%", testB);
    }
    
    @Test
    void isExistTableTest() {
        String tableName = "tableName";
        String sql = String.format("SELECT COUNT(*) FROM %s ", tableName);
        
        when(jt.queryForObject(eq(sql), eq(Integer.class))).thenReturn(1);
        boolean existTableA = externalNamespacePersistService.isExistTable(tableName);
        assertTrue(existTableA);
        
        when(jt.queryForObject(eq(sql), eq(Integer.class))).thenThrow(new RuntimeException("test"));
        boolean existTableB = externalNamespacePersistService.isExistTable(tableName);
        assertFalse(existTableB);
    }
    
    @Test
    void tenantInfoCountByTenantIdTest() {
        String tenantId = "tenantId";
        
        when(dataSourceService.getDataSourceType()).thenReturn("mysql");
        
        assertThrows(IllegalArgumentException.class, () -> externalNamespacePersistService.tenantInfoCountByTenantId(null));
        
        when(jt.queryForObject(anyString(), eq(new String[] {tenantId}), eq(Integer.class))).thenReturn(null);
        int i = externalNamespacePersistService.tenantInfoCountByTenantId(tenantId);
        assertEquals(0, i);
        
        when(jt.queryForObject(anyString(), eq(new String[] {tenantId}), eq(Integer.class))).thenReturn(1);
        int j = externalNamespacePersistService.tenantInfoCountByTenantId(tenantId);
        assertEquals(1, j);
    }
    
}
