/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.config.server.service.dump;

import com.alibaba.nacos.config.server.model.ConfigInfoGrayWrapper;
import com.alibaba.nacos.config.server.model.ConfigInfoStateWrapper;
import com.alibaba.nacos.config.server.service.ConfigCacheService;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoGrayPersistService;
import com.alibaba.nacos.config.server.service.repository.HistoryConfigInfoPersistService;
import com.alibaba.nacos.config.server.utils.ConfigExecutor;
import com.alibaba.nacos.config.server.utils.GroupKey;
import com.alibaba.nacos.config.server.utils.PropertyUtil;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class DumpChangeGrayConfigWorkerTest {
    
    DumpChangeGrayConfigWorker dumpGrayConfigWorker;
    
    @Mock
    ConfigInfoGrayPersistService configInfoGrayPersistService;
    
    @Mock
    HistoryConfigInfoPersistService historyConfigInfoPersistService;
    
    static MockedStatic<EnvUtil> envUtilMockedStatic;
    
    static MockedStatic<ConfigCacheService> configCacheServiceMockedStatic;
    
    static MockedStatic<ConfigExecutor> configExecutorMockedStatic;
    
    /**
     * Clean up.
     */
    @AfterEach
    public void after() {
        envUtilMockedStatic.close();
        configCacheServiceMockedStatic.close();
        configExecutorMockedStatic.close();
        
    }
    
    @BeforeEach
    public void setUp() {
        envUtilMockedStatic = Mockito.mockStatic(EnvUtil.class);
        configCacheServiceMockedStatic = Mockito.mockStatic(ConfigCacheService.class);
        configExecutorMockedStatic = Mockito.mockStatic(ConfigExecutor.class);
        
        envUtilMockedStatic.when(() -> EnvUtil.getAvailableProcessors(anyInt())).thenReturn(2);
        dumpGrayConfigWorker = new DumpChangeGrayConfigWorker(configInfoGrayPersistService,
            new Timestamp(System.currentTimeMillis()), historyConfigInfoPersistService);
    }
    
    @Test
    public void testdumpGrayConfigWorkerRun() {
        List<ConfigInfoGrayWrapper> mockList = new ArrayList<>();
        ConfigInfoGrayWrapper mock1 = mock(1);
        mockList.add(mock1);
        when(configInfoGrayPersistService.findChangeConfig(any(Timestamp.class), any(long.class),
            eq(100))).thenReturn(
                mockList);
        configCacheServiceMockedStatic.when(() -> ConfigCacheService.getContentMd5(
            eq(GroupKey.getKeyTenant(mock1.getDataId(), mock1.getGroup(), mock1.getTenant()))))
            .thenReturn("");
        
        dumpGrayConfigWorker.run();
        //verify dump gray executed
        configCacheServiceMockedStatic.verify(
            () -> ConfigCacheService.dumpGray(eq(mock1.getDataId()), eq(mock1.getGroup()),
                eq(mock1.getTenant()),
                eq(mock1.getGrayName()), eq(mock1.getGrayRule()), eq(mock1.getContent()),
                eq(mock1.getLastModified()), eq(mock1.getEncryptedDataKey())));
        //verify task scheduled
        configExecutorMockedStatic.verify(
            () -> ConfigExecutor.scheduleConfigChangeTask(any(DumpChangeGrayConfigWorker.class),
                eq(PropertyUtil.getDumpChangeWorkerInterval()), eq(TimeUnit.MILLISECONDS)));
        
    }
    
    @Test
    public void testRunDumpChangeOff() {
        MockedStatic<PropertyUtil> propertyUtilMockedStatic =
            Mockito.mockStatic(PropertyUtil.class);
        propertyUtilMockedStatic.when(PropertyUtil::isDumpChangeOn)
            .thenReturn(false);
        
        dumpGrayConfigWorker.run();
        
        configCacheServiceMockedStatic.verify(
            () -> ConfigCacheService.dumpGray(anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString(),
                anyLong(), anyString()),
            never());
        
        propertyUtilMockedStatic.close();
    }
    
    @Test
    public void testRunWithDeletedConfigs() {
        ConfigInfoStateWrapper deleted = new ConfigInfoStateWrapper();
        deleted.setDataId("deletedDataId");
        deleted.setGroup("deletedGroup");
        deleted.setTenant("deletedTenant");
        deleted.setGrayName("grayToDelete");
        deleted.setId(1L);
        
        when(historyConfigInfoPersistService.findDeletedConfig(
            any(Timestamp.class), eq(0L), eq(100), anyString()))
            .thenReturn(Collections.singletonList(deleted));
        
        when(configInfoGrayPersistService.findConfigInfo4GrayState(
            eq("deletedDataId"), eq("deletedGroup"), eq("deletedTenant"),
            eq("grayToDelete")))
            .thenReturn(null);
        
        when(configInfoGrayPersistService.findChangeConfig(
            any(Timestamp.class), anyLong(), eq(100)))
            .thenReturn(Collections.emptyList());
        
        dumpGrayConfigWorker.run();
        
        configCacheServiceMockedStatic.verify(
            () -> ConfigCacheService.removeGray(eq("deletedDataId"),
                eq("deletedGroup"), eq("deletedTenant"),
                eq("grayToDelete")));
    }
    
    @Test
    public void testRunWithDeletedConfigBlankGrayName() {
        ConfigInfoStateWrapper deleted = new ConfigInfoStateWrapper();
        deleted.setDataId("d");
        deleted.setGroup("g");
        deleted.setTenant("t");
        deleted.setGrayName("");
        deleted.setId(1L);
        
        when(historyConfigInfoPersistService.findDeletedConfig(
            any(Timestamp.class), eq(0L), eq(100), anyString()))
            .thenReturn(Collections.singletonList(deleted));
        when(configInfoGrayPersistService.findChangeConfig(
            any(Timestamp.class), anyLong(), eq(100)))
            .thenReturn(Collections.emptyList());
        
        dumpGrayConfigWorker.run();
        
        configCacheServiceMockedStatic.verify(
            () -> ConfigCacheService.removeGray(anyString(), anyString(),
                anyString(), anyString()),
            never());
    }
    
    @Test
    public void testRunWithDeletedConfigStillExistsInDb() {
        ConfigInfoStateWrapper deleted = new ConfigInfoStateWrapper();
        deleted.setDataId("d");
        deleted.setGroup("g");
        deleted.setTenant("t");
        deleted.setGrayName("gray1");
        deleted.setId(1L);
        
        when(historyConfigInfoPersistService.findDeletedConfig(
            any(Timestamp.class), eq(0L), eq(100), anyString()))
            .thenReturn(Collections.singletonList(deleted));
        
        ConfigInfoStateWrapper existing = new ConfigInfoStateWrapper();
        when(configInfoGrayPersistService.findConfigInfo4GrayState(
            eq("d"), eq("g"), eq("t"), eq("gray1")))
            .thenReturn(existing);
        
        when(configInfoGrayPersistService.findChangeConfig(
            any(Timestamp.class), anyLong(), eq(100)))
            .thenReturn(Collections.emptyList());
        
        dumpGrayConfigWorker.run();
        
        configCacheServiceMockedStatic.verify(
            () -> ConfigCacheService.removeGray(anyString(), anyString(),
                anyString(), anyString()),
            never());
    }
    
    @Test
    public void testRunWithChangedConfigBlankTenant() {
        ConfigInfoGrayWrapper changed = new ConfigInfoGrayWrapper();
        changed.setDataId("d");
        changed.setGroup("g");
        changed.setTenant("");
        changed.setGrayName("gray1");
        changed.setContent("content");
        changed.setGrayRule("{\"type\":\"tag\",\"version\":\"1.0.0\","
            + "\"expr\":\"test\",\"priority\":1}");
        
        when(historyConfigInfoPersistService.findDeletedConfig(
            any(Timestamp.class), anyLong(), eq(100), anyString()))
            .thenReturn(Collections.emptyList());
        when(configInfoGrayPersistService.findChangeConfig(
            any(Timestamp.class), eq(0L), eq(100)))
            .thenReturn(Collections.singletonList(changed));
        
        dumpGrayConfigWorker.run();
        
        configCacheServiceMockedStatic.verify(
            () -> ConfigCacheService.dumpGray(anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString(),
                anyLong(), anyString()),
            never());
    }
    
    @Test
    public void testRunWithDeletedConfigNextPage() {
        dumpGrayConfigWorker.pageSize = 1;
        ConfigInfoStateWrapper deleted = new ConfigInfoStateWrapper();
        deleted.setDataId("d");
        deleted.setGroup("g");
        deleted.setTenant("t");
        deleted.setGrayName("");
        deleted.setId(10L);
        
        when(historyConfigInfoPersistService.findDeletedConfig(
            any(Timestamp.class), eq(0L), eq(1), anyString()))
            .thenReturn(Collections.singletonList(deleted));
        when(historyConfigInfoPersistService.findDeletedConfig(
            any(Timestamp.class), eq(10L), eq(1), anyString()))
            .thenReturn(Collections.emptyList());
        when(configInfoGrayPersistService.findChangeConfig(
            any(Timestamp.class), anyLong(), eq(1)))
            .thenReturn(Collections.emptyList());
        
        dumpGrayConfigWorker.run();
        
        verify(historyConfigInfoPersistService).findDeletedConfig(
            any(Timestamp.class), eq(10L), eq(1), anyString());
    }
    
    @Test
    public void testRunWithChangedConfigNextPage() {
        dumpGrayConfigWorker.pageSize = 1;
        ConfigInfoGrayWrapper changed = mock(2);
        changed.setTenant("");
        changed.setId(20L);
        
        when(historyConfigInfoPersistService.findDeletedConfig(
            any(Timestamp.class), anyLong(), eq(1), anyString()))
            .thenReturn(Collections.emptyList());
        when(configInfoGrayPersistService.findChangeConfig(
            any(Timestamp.class), eq(0L), eq(1)))
            .thenReturn(Collections.singletonList(changed));
        when(configInfoGrayPersistService.findChangeConfig(
            any(Timestamp.class), eq(20L), eq(1)))
            .thenReturn(Collections.emptyList());
        
        dumpGrayConfigWorker.run();
        
        verify(configInfoGrayPersistService).findChangeConfig(
            any(Timestamp.class), eq(20L), eq(1));
    }
    
    @Test
    public void testRunWithExceptionStillSchedulesNextTask() {
        when(historyConfigInfoPersistService.findDeletedConfig(
            any(Timestamp.class), anyLong(), eq(100), anyString()))
            .thenThrow(new RuntimeException("query failed"));
        
        dumpGrayConfigWorker.run();
        
        configExecutorMockedStatic.verify(
            () -> ConfigExecutor.scheduleConfigChangeTask(any(DumpChangeGrayConfigWorker.class),
                eq(PropertyUtil.getDumpChangeWorkerInterval()), eq(TimeUnit.MILLISECONDS)));
    }
    
    ConfigInfoGrayWrapper mock(int id) {
        ConfigInfoGrayWrapper configInfoGrayWrapper = new ConfigInfoGrayWrapper();
        configInfoGrayWrapper.setDataId("mockdataid" + id);
        configInfoGrayWrapper.setGroup("mockgroup" + id);
        configInfoGrayWrapper.setTenant("tenant" + id);
        configInfoGrayWrapper.setContent("content" + id);
        configInfoGrayWrapper.setGrayName("graytags1" + id);
        configInfoGrayWrapper.setGrayRule(
            "{\"type\":\"tagv2\",\"version\":\"1.0.0\","
                + "\"expr\":\"middleware.server.key\\u003dgray123\",\"priority\":1}");
        return configInfoGrayWrapper;
    }
}
