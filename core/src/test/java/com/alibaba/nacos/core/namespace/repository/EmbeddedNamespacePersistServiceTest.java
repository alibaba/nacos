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

import com.alibaba.nacos.api.exception.runtime.NacosRuntimeException;
import com.alibaba.nacos.core.namespace.model.TenantInfo;
import com.alibaba.nacos.persistence.configuration.DatasourceConfiguration;
import com.alibaba.nacos.persistence.datasource.DataSourceService;
import com.alibaba.nacos.persistence.datasource.DynamicDataSource;
import com.alibaba.nacos.persistence.repository.embedded.operate.DatabaseOperate;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static com.alibaba.nacos.core.namespace.repository.NamespaceRowMapperInjector.TENANT_INFO_ROW_MAPPER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmbeddedNamespacePersistServiceTest {
    
    @Mock
    private DatabaseOperate databaseOperate;
    
    @Mock
    private DataSourceService dataSourceService;
    
    private EmbeddedNamespacePersistServiceImpl embeddedNamespacePersistService;
    
    private MockEnvironment environment;
    
    @BeforeEach
    void setUp() {
        EnvUtil.setIsStandalone(true);
        DatasourceConfiguration.setEmbeddedStorage(true);
        environment = new MockEnvironment();
        EnvUtil.setEnvironment(environment);
        DynamicDataSource instance = DynamicDataSource.getInstance();
        ReflectionTestUtils.setField(instance, "localDataSourceService", dataSourceService);
        embeddedNamespacePersistService = new EmbeddedNamespacePersistServiceImpl(databaseOperate);
    }
    
    @Test
    void insertTenantInfoAtomicTest1() {
        
        String namespaceId = "testNsId";
        String kp = "1";
        String namespaceName = "testNs";
        String namespaceDesc = "testDes";
        String createRes = "nacos";
        when(databaseOperate.update(anyList())).thenReturn(true);
        when(dataSourceService.getDataSourceType()).thenReturn("derby");
        
        embeddedNamespacePersistService.insertTenantInfoAtomic(kp, namespaceId, namespaceName, namespaceDesc, createRes,
                System.currentTimeMillis());
        
        verify(databaseOperate).update(anyList());
    }
    
    @Test
    void insertTenantInfoAtomicTest2() {
        
        String namespaceId = "testNsId";
        String kp = "1";
        String namespaceName = "testNs";
        String namespaceDesc = "testDes";
        String createRes = "nacos";
        when(databaseOperate.update(anyList())).thenReturn(false);
        when(dataSourceService.getDataSourceType()).thenReturn("derby");
        
        assertThrows(NacosRuntimeException.class,
                () -> embeddedNamespacePersistService.insertTenantInfoAtomic(kp, namespaceId, namespaceName, namespaceDesc, createRes,
                        System.currentTimeMillis()));
        
        verify(databaseOperate).update(anyList());
    }
    
    @Test
    void removeTenantInfoAtomicTest() {
        String namespaceId = "testNsId";
        String kp = "1";
        
        when(databaseOperate.update(anyList())).thenReturn(true);
        when(dataSourceService.getDataSourceType()).thenReturn("derby");
        
        embeddedNamespacePersistService.removeTenantInfoAtomic(kp, namespaceId);
        
        verify(databaseOperate).update(anyList());
    }
    
    @Test
    void updateTenantNameAtomicTest1() {
        
        String namespaceId = "testNsId";
        String kp = "1";
        String namespaceName = "testNs";
        String namespaceDesc = "testDes";
        when(databaseOperate.update(anyList())).thenReturn(true);
        when(dataSourceService.getDataSourceType()).thenReturn("derby");
        
        embeddedNamespacePersistService.updateTenantNameAtomic(kp, namespaceId, namespaceName, namespaceDesc);
        
        verify(databaseOperate).update(anyList());
    }
    
    @Test
    void updateTenantNameAtomicTest2() {
        
        String namespaceId = "testNsId";
        String kp = "1";
        String namespaceName = "testNs";
        String namespaceDesc = "testDes";
        when(databaseOperate.update(anyList())).thenReturn(false);
        when(dataSourceService.getDataSourceType()).thenReturn("derby");
        
        assertThrows(NacosRuntimeException.class,
                () -> embeddedNamespacePersistService.updateTenantNameAtomic(kp, namespaceId, namespaceName, namespaceDesc));
        verify(databaseOperate).update(anyList());
    }
    
    @Test
    void findTenantByKpTest1() {
        String kp = "1";
        List<TenantInfo> tenantInfoList = new ArrayList<>(1);
        tenantInfoList.add(new TenantInfo());
        when(databaseOperate.queryMany(anyString(), eq(new Object[] {kp}), eq(TENANT_INFO_ROW_MAPPER))).thenReturn(tenantInfoList);
        when(dataSourceService.getDataSourceType()).thenReturn("derby");
        List<TenantInfo> tenantByKp = embeddedNamespacePersistService.findTenantByKp(kp);
        
        assertEquals(tenantByKp.get(0), tenantInfoList.get(0));
    }
    
    @Test
    void findTenantByKpTest2() {
        String kp = "1";
        String tenantId = "tenantId";
        TenantInfo tenantInfo = new TenantInfo();
        tenantInfo.setTenantId(tenantId);
        when(databaseOperate.queryOne(anyString(), eq(new Object[] {kp, tenantId}), eq(TENANT_INFO_ROW_MAPPER))).thenReturn(tenantInfo);
        when(dataSourceService.getDataSourceType()).thenReturn("derby");
        TenantInfo tenantByKp = embeddedNamespacePersistService.findTenantByKp(kp, tenantId);
        
        assertEquals(tenantInfo.getTenantId(), tenantByKp.getTenantId());
    }
    
    @Test
    void generateLikeArgumentTest() {
        
        String test = embeddedNamespacePersistService.generateLikeArgument("test");
        
        String testB = embeddedNamespacePersistService.generateLikeArgument("test*");
        
        assertEquals("test", test);
        
        assertEquals("test%", testB);
    }
    
    @Test
    void isExistTableTest() {
        String tableName = "tableName";
        String sql = String.format("SELECT 1 FROM %s FETCH FIRST ROW ONLY", tableName);
        
        when(databaseOperate.queryOne(sql, Integer.class)).thenReturn(1);
        boolean existTableA = embeddedNamespacePersistService.isExistTable(tableName);
        assertTrue(existTableA);
        
        when(databaseOperate.queryOne(sql, Integer.class)).thenThrow(new RuntimeException("test"));
        boolean existTableB = embeddedNamespacePersistService.isExistTable(tableName);
        assertFalse(existTableB);
    }
    
    @Test
    void tenantInfoCountByTenantIdTest() {
        String tenantId = "tenantId";
        
        when(dataSourceService.getDataSourceType()).thenReturn("derby");
        
        assertThrows(IllegalArgumentException.class, () -> embeddedNamespacePersistService.tenantInfoCountByTenantId(null));
        
        when(databaseOperate.queryOne(anyString(), eq(new String[] {tenantId}), eq(Integer.class))).thenReturn(null);
        int i = embeddedNamespacePersistService.tenantInfoCountByTenantId(tenantId);
        assertEquals(0, i);
        
        when(databaseOperate.queryOne(anyString(), eq(new String[] {tenantId}), eq(Integer.class))).thenReturn(1);
        int j = embeddedNamespacePersistService.tenantInfoCountByTenantId(tenantId);
        assertEquals(1, j);
    }
    
}
