/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.config.server.service.repository.embedded;

import com.alibaba.nacos.config.server.model.ConfigInfo;
import com.alibaba.nacos.config.server.model.ConfigInfoGrayWrapper;
import com.alibaba.nacos.config.server.model.ConfigInfoWrapper;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoGrayPersistService;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoPersistService;
import com.alibaba.nacos.core.distributed.id.IdGeneratorManager;
import com.alibaba.nacos.persistence.datasource.DataSourceService;
import com.alibaba.nacos.persistence.datasource.DynamicDataSource;
import com.alibaba.nacos.persistence.repository.embedded.EmbeddedStorageContextHolder;
import com.alibaba.nacos.persistence.repository.embedded.operate.DatabaseOperate;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
class EmbeddedConfigMigratePersistServiceImplTest {
    
    MockedStatic<DynamicDataSource> dynamicDataSourceMockedStatic;
    
    MockedStatic<EnvUtil> envUtilMockedStatic;
    
    MockedStatic<EmbeddedStorageContextHolder> embeddedStorageContextHolderMockedStatic;
    
    @Mock
    DynamicDataSource dynamicDataSource;
    
    @Mock
    private DataSourceService dataSourceService;
    
    @Mock
    private DatabaseOperate databaseOperate;
    
    @Mock
    private IdGeneratorManager idGeneratorManager;
    
    @Mock
    private ConfigInfoPersistService configInfoPersistService;
    
    @Mock
    private ConfigInfoGrayPersistService configInfoGrayPersistService;
    
    private EmbeddedConfigMigratePersistServiceImpl service;
    
    @BeforeEach
    void setUp() {
        dynamicDataSourceMockedStatic =
            Mockito.mockStatic(DynamicDataSource.class);
        envUtilMockedStatic = Mockito.mockStatic(EnvUtil.class);
        embeddedStorageContextHolderMockedStatic =
            Mockito.mockStatic(EmbeddedStorageContextHolder.class);
        when(DynamicDataSource.getInstance()).thenReturn(dynamicDataSource);
        when(dynamicDataSource.getDataSource()).thenReturn(dataSourceService);
        when(dataSourceService.getDataSourceType()).thenReturn("derby");
        envUtilMockedStatic.when(
            () -> EnvUtil.getProperty(anyString(), eq(Boolean.class),
                eq(false)))
            .thenReturn(false);
        service = new EmbeddedConfigMigratePersistServiceImpl(
            databaseOperate, idGeneratorManager,
            configInfoPersistService, configInfoGrayPersistService);
    }
    
    @AfterEach
    void tearDown() {
        dynamicDataSourceMockedStatic.close();
        envUtilMockedStatic.close();
        embeddedStorageContextHolderMockedStatic.close();
    }
    
    @Test
    void testCreatePaginationHelper() {
        assertNotNull(service.createPaginationHelper());
    }
    
    @Test
    void testConfigInfoConflictCount() {
        when(databaseOperate.queryOne(anyString(), any(Object[].class),
            eq(Integer.class))).thenReturn(5);
        Integer result = service.configInfoConflictCount("srcUser");
        assertEquals(5, result);
    }
    
    @Test
    void testConfigInfoConflictCountNull() {
        when(databaseOperate.queryOne(anyString(), any(Object[].class),
            eq(Integer.class))).thenReturn(null);
        assertThrows(IllegalArgumentException.class,
            () -> service.configInfoConflictCount("srcUser"));
    }
    
    @Test
    void testConfigInfoGrayConflictCount() {
        when(databaseOperate.queryOne(anyString(), any(Object[].class),
            eq(Integer.class))).thenReturn(3);
        Integer result = service.configInfoGrayConflictCount("srcUser");
        assertEquals(3, result);
    }
    
    @Test
    void testConfigInfoGrayConflictCountNull() {
        when(databaseOperate.queryOne(anyString(), any(Object[].class),
            eq(Integer.class))).thenReturn(null);
        assertThrows(IllegalArgumentException.class,
            () -> service.configInfoGrayConflictCount("srcUser"));
    }
    
    @Test
    void testGetMigrateConfigInsertIdList() {
        List<Long> ids = Arrays.asList(1L, 2L, 3L);
        when(databaseOperate.queryMany(anyString(), any(Object[].class),
            eq(Long.class))).thenReturn(ids);
        List<Long> result = service.getMigrateConfigInsertIdList(0L, 100);
        assertEquals(3, result.size());
    }
    
    @Test
    void testGetMigrateConfigGrayInsertIdList() {
        List<Long> ids = Arrays.asList(10L, 20L);
        when(databaseOperate.queryMany(anyString(), any(Object[].class),
            eq(Long.class))).thenReturn(ids);
        List<Long> result =
            service.getMigrateConfigGrayInsertIdList(0L, 100);
        assertEquals(2, result.size());
    }
    
    @SuppressWarnings("unchecked")
    @Test
    void testGetMigrateConfigUpdateList() {
        ConfigInfo info = new ConfigInfo("d", "g", "content");
        when(databaseOperate.queryMany(anyString(), any(Object[].class),
            any(RowMapper.class)))
            .thenReturn(Collections.singletonList(info));
        List<ConfigInfo> result =
            service.getMigrateConfigUpdateList(0L, 100, "", "public",
                "user");
        assertEquals(1, result.size());
    }
    
    @SuppressWarnings("unchecked")
    @Test
    void testGetMigrateConfigGrayUpdateList() {
        ConfigInfoGrayWrapper wrapper = new ConfigInfoGrayWrapper();
        when(databaseOperate.queryMany(anyString(), any(Object[].class),
            any(RowMapper.class)))
            .thenReturn(Collections.singletonList(wrapper));
        List<ConfigInfoGrayWrapper> result =
            service.getMigrateConfigGrayUpdateList(0L, 100, "", "public",
                "user");
        assertEquals(1, result.size());
    }
    
    @Test
    void testMigrateConfigInsertByIds() {
        List<Long> ids = Arrays.asList(1L, 2L);
        when(idGeneratorManager.nextId(anyString())).thenReturn(100L, 101L);
        service.migrateConfigInsertByIds(ids, "user");
        verify(databaseOperate).blockUpdate();
    }
    
    @Test
    void testMigrateConfigGrayInsertByIds() {
        List<Long> ids = Arrays.asList(1L, 2L);
        when(idGeneratorManager.nextId(anyString())).thenReturn(200L, 201L);
        service.migrateConfigGrayInsertByIds(ids, "user");
        verify(databaseOperate).blockUpdate();
    }
    
    @Test
    void testSyncConfigSourceNull() {
        when(configInfoPersistService.findConfigInfo("d", "g", ""))
            .thenReturn(null);
        service.syncConfig("d", "g", "", "public", "user");
        verify(configInfoPersistService)
            .removeConfigInfo(eq("d"), eq("g"), eq("public"), any(),
                eq("user"));
    }
    
    @Test
    void testSyncConfigTargetNullInserts() {
        ConfigInfoWrapper source = new ConfigInfoWrapper();
        source.setDataId("d");
        source.setGroup("g");
        source.setContent("content");
        source.setMd5("md5");
        source.setLastModified(200L);
        when(configInfoPersistService.findConfigInfo("d", "g", ""))
            .thenReturn(source);
        when(configInfoPersistService.findConfigInfo("d", "g", "public"))
            .thenReturn(null);
        when(idGeneratorManager.nextId(anyString())).thenReturn(100L);
        service.syncConfig("d", "g", "", "public", "user");
        verify(configInfoPersistService)
            .addConfigInfoAtomic(eq(100L), any(), eq("user"), any(),
                any());
    }
    
    @Test
    void testSyncConfigTargetExistsUpdates() {
        ConfigInfoWrapper source = new ConfigInfoWrapper();
        source.setDataId("d");
        source.setGroup("g");
        source.setContent("content");
        source.setMd5("md5");
        source.setLastModified(300L);
        ConfigInfoWrapper target = new ConfigInfoWrapper();
        target.setLastModified(100L);
        target.setMd5("oldmd5");
        when(configInfoPersistService.findConfigInfo("d", "g", ""))
            .thenReturn(source);
        when(configInfoPersistService.findConfigInfo("d", "g", "public"))
            .thenReturn(target);
        service.syncConfig("d", "g", "", "public", "user");
    }
    
    @Test
    void testSyncConfigGraySourceNull() {
        when(configInfoGrayPersistService.findConfigInfo4Gray(
            "d", "g", "", "beta")).thenReturn(null);
        service.syncConfigGray("d", "g", "", "beta", "public", "user");
        verify(databaseOperate).update(any());
    }
    
    @Test
    void testSyncConfigGrayTargetNullInserts() {
        ConfigInfoGrayWrapper source = new ConfigInfoGrayWrapper();
        source.setDataId("d");
        source.setGroup("g");
        source.setContent("content");
        source.setMd5("md5");
        source.setGrayName("beta");
        source.setGrayRule("rule");
        source.setLastModified(200L);
        when(configInfoGrayPersistService.findConfigInfo4Gray(
            "d", "g", "", "beta")).thenReturn(source);
        when(configInfoGrayPersistService.findConfigInfo4Gray(
            "d", "g", "public", "beta")).thenReturn(null);
        when(idGeneratorManager.nextId(anyString())).thenReturn(100L);
        service.syncConfigGray("d", "g", "", "beta", "public", "user");
        verify(configInfoGrayPersistService)
            .addConfigInfoGrayAtomic(eq(100L), any(), eq("beta"),
                eq("rule"), any(), eq("user"));
    }
    
    @Test
    void testSyncConfigGrayTargetExistsUpdates() {
        ConfigInfoGrayWrapper source = new ConfigInfoGrayWrapper();
        source.setDataId("d");
        source.setGroup("g");
        source.setContent("content");
        source.setMd5("md5");
        source.setGrayName("beta");
        source.setGrayRule("rule");
        source.setLastModified(300L);
        ConfigInfoGrayWrapper target = new ConfigInfoGrayWrapper();
        target.setLastModified(100L);
        target.setMd5("oldmd5");
        when(configInfoGrayPersistService.findConfigInfo4Gray(
            "d", "g", "", "beta")).thenReturn(source);
        when(configInfoGrayPersistService.findConfigInfo4Gray(
            "d", "g", "public", "beta")).thenReturn(target);
        service.syncConfigGray("d", "g", "", "beta", "public", "user");
        verify(databaseOperate).blockUpdate();
    }
    
    @Test
    void testRemoveConfigInfoGrayWithoutHistory() {
        service.removeConfigInfoGrayWithoutHistory("d", "g", "ns", "beta",
            "ip", "user");
        verify(databaseOperate).update(any());
    }
    
    @Test
    void testUpdateConfigInfo4GrayWithoutHistory() {
        ConfigInfo info = new ConfigInfo("d", "g", "content");
        info.setTenant("ns");
        service.updateConfigInfo4GrayWithoutHistory(info, "beta", "rule",
            "ip", "user", 100L, "oldmd5");
        verify(databaseOperate).blockUpdate();
    }
    
    @Test
    void testUpdateConfigInfoAtomic() {
        ConfigInfo info = new ConfigInfo("d", "g", "content");
        info.setTenant("ns");
        service.updateConfigInfoAtomic(info, "ip", "user", null, 100L,
            "oldmd5");
    }
}
