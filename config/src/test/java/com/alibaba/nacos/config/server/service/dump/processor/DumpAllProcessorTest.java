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

package com.alibaba.nacos.config.server.service.dump.processor;

import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.common.task.NacosTask;
import com.alibaba.nacos.config.server.model.ConfigInfoWrapper;
import com.alibaba.nacos.config.server.service.ClientIpWhiteList;
import com.alibaba.nacos.config.server.service.ConfigCacheService;
import com.alibaba.nacos.config.server.service.SwitchService;
import com.alibaba.nacos.config.server.service.dump.task.DumpAllTask;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoPersistService;
import com.alibaba.nacos.config.server.utils.GroupKey2;
import com.alibaba.nacos.config.server.utils.PropertyUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DumpAllProcessorTest {
    
    private static final int PAGE_SIZE = 100;
    
    @Mock
    private ConfigInfoPersistService configInfoPersistService;
    
    private MockedStatic<PropertyUtil> propertyUtilMockedStatic;
    
    private DumpAllProcessor dumpAllProcessor;
    
    @BeforeEach
    void setUp() {
        propertyUtilMockedStatic = Mockito.mockStatic(PropertyUtil.class);
        propertyUtilMockedStatic.when(PropertyUtil::getAllDumpPageSize).thenReturn(PAGE_SIZE);
        dumpAllProcessor = new DumpAllProcessor(configInfoPersistService);
    }
    
    @AfterEach
    void tearDown() {
        propertyUtilMockedStatic.close();
    }
    
    @Test
    void testProcessRejectsInvalidTask() {
        NacosTask task = () -> true;
        
        assertFalse(dumpAllProcessor.process(task));
        
        verify(configInfoPersistService, never()).findConfigMaxId();
    }
    
    @Test
    void testProcessReturnsTrueWhenNoConfig() {
        when(configInfoPersistService.findConfigMaxId()).thenReturn(0L);
        
        try (MockedConstruction<ThreadPoolExecutor> mockedExecutors =
            mockExecutorConstruction(0)) {
            assertTrue(dumpAllProcessor.process(new DumpAllTask(true)));
            
            assertEquals(1, mockedExecutors.constructed().size());
            verify(mockedExecutors.constructed().get(0)).shutdown();
        }
    }
    
    @Test
    void testProcessBreaksWhenPageItemsNull() {
        Page<ConfigInfoWrapper> page = new Page<>();
        when(configInfoPersistService.findConfigMaxId()).thenReturn(1L);
        when(configInfoPersistService.findAllConfigInfoFragment(0L, PAGE_SIZE, false))
            .thenReturn(page);
        
        try (MockedConstruction<ThreadPoolExecutor> mockedExecutors =
            mockExecutorConstruction(0)) {
            assertTrue(dumpAllProcessor.process(new DumpAllTask(false)));
            
            assertEquals(1, mockedExecutors.constructed().size());
            verify(mockedExecutors.constructed().get(0), never()).execute(any(Runnable.class));
        }
    }
    
    @Test
    void testProcessSkipsBlankTenant() {
        ConfigInfoWrapper configInfo = newConfigInfo(1L, "dataId", "group", " ", "content");
        when(configInfoPersistService.findConfigMaxId()).thenReturn(1L);
        when(configInfoPersistService.findAllConfigInfoFragment(0L, PAGE_SIZE, true))
            .thenReturn(newPage(configInfo));
        
        try (MockedConstruction<ThreadPoolExecutor> mockedExecutors =
            mockExecutorConstruction(0)) {
            assertTrue(dumpAllProcessor.process(new DumpAllTask(true)));
            
            verify(mockedExecutors.constructed().get(0), never()).execute(any(Runnable.class));
        }
    }
    
    @Test
    void testProcessStartupDumpsConfigAndLoadsMetadata() {
        ConfigInfoWrapper whiteList = newConfigInfo(1L,
            ClientIpWhiteList.CLIENT_IP_WHITELIST_METADATA, "group", "tenant", "127.0.0.1");
        ConfigInfoWrapper switchConfig = newConfigInfo(2L, SwitchService.SWITCH_META_DATA_ID,
            "group", "tenant", "switch=true");
        when(configInfoPersistService.findConfigMaxId()).thenReturn(2L);
        when(configInfoPersistService.findAllConfigInfoFragment(0L, PAGE_SIZE, true))
            .thenReturn(newPage(whiteList, switchConfig));
        
        try (MockedConstruction<ThreadPoolExecutor> ignored = mockExecutorConstruction(0);
            MockedStatic<ConfigCacheService> configCacheServiceMockedStatic =
                mockDumpWithMd5(true);
            MockedStatic<ClientIpWhiteList> clientIpWhiteListMockedStatic =
                Mockito.mockStatic(ClientIpWhiteList.class);
            MockedStatic<SwitchService> switchServiceMockedStatic =
                Mockito.mockStatic(SwitchService.class)) {
            assertTrue(dumpAllProcessor.process(new DumpAllTask(true)));
            
            clientIpWhiteListMockedStatic.verify(() -> ClientIpWhiteList.load("127.0.0.1"));
            switchServiceMockedStatic.verify(() -> SwitchService.load("switch=true"));
            configCacheServiceMockedStatic.verify(() -> ConfigCacheService.dumpWithMd5(
                eq(whiteList.getDataId()), eq("group"), eq("tenant"), eq("127.0.0.1"),
                anyString(), eq(10L), eq("text"), eq("key")));
            configCacheServiceMockedStatic.verify(() -> ConfigCacheService.dumpWithMd5(
                eq(switchConfig.getDataId()), eq("group"), eq("tenant"), eq("switch=true"),
                anyString(), eq(20L), eq("text"), eq("key")));
        }
    }
    
    @Test
    void testProcessContinuesWhenDumpFails() {
        ConfigInfoWrapper configInfo = newConfigInfo(1L, "dataId", "group", "tenant", "content");
        when(configInfoPersistService.findConfigMaxId()).thenReturn(1L);
        when(configInfoPersistService.findAllConfigInfoFragment(0L, PAGE_SIZE, true))
            .thenReturn(newPage(configInfo));
        
        try (MockedConstruction<ThreadPoolExecutor> ignored = mockExecutorConstruction(0);
            MockedStatic<ConfigCacheService> configCacheServiceMockedStatic =
                mockDumpWithMd5(false)) {
            assertTrue(dumpAllProcessor.process(new DumpAllTask(true)));
            
            configCacheServiceMockedStatic.verify(() -> ConfigCacheService.dumpWithMd5(
                eq("dataId"), eq("group"), eq("tenant"), eq("content"), anyString(),
                eq(10L), eq("text"), eq("key")));
        }
    }
    
    @Test
    void testProcessCheckSkipsWhenCacheUnchanged() {
        ConfigInfoWrapper configInfo = newConfigInfo(1L, "dataId", "group", "tenant", "content");
        configInfo.setMd5("sameMd5");
        when(configInfoPersistService.findConfigMaxId()).thenReturn(1L);
        when(configInfoPersistService.findAllConfigInfoFragment(0L, PAGE_SIZE, false))
            .thenReturn(newPage(configInfo));
        
        try (MockedConstruction<ThreadPoolExecutor> mockedExecutors =
            mockExecutorConstruction(0);
            MockedStatic<ConfigCacheService> configCacheServiceMockedStatic =
                Mockito.mockStatic(ConfigCacheService.class)) {
            String groupKey = GroupKey2.getKey("dataId", "group", "tenant");
            configCacheServiceMockedStatic
                .when(() -> ConfigCacheService.getLastModifiedTs(groupKey))
                .thenReturn(configInfo.getLastModified());
            configCacheServiceMockedStatic.when(() -> ConfigCacheService.getContentMd5(groupKey))
                .thenReturn("sameMd5");
            
            assertTrue(dumpAllProcessor.process(new DumpAllTask(false)));
            
            verify(configInfoPersistService, never()).findConfigInfo(anyString(), anyString(),
                anyString());
            verify(mockedExecutors.constructed().get(0), never()).execute(any(Runnable.class));
        }
    }
    
    @Test
    void testProcessCheckSkipsWhenChangedConfigDeleted() {
        ConfigInfoWrapper configInfo = newConfigInfo(1L, "dataId", "group", "tenant", "content");
        configInfo.setMd5("newMd5");
        when(configInfoPersistService.findConfigMaxId()).thenReturn(1L);
        when(configInfoPersistService.findAllConfigInfoFragment(0L, PAGE_SIZE, false))
            .thenReturn(newPage(configInfo));
        when(configInfoPersistService.findConfigInfo("dataId", "group", "tenant"))
            .thenReturn(null);
        
        try (MockedConstruction<ThreadPoolExecutor> mockedExecutors =
            mockExecutorConstruction(0);
            MockedStatic<ConfigCacheService> configCacheServiceMockedStatic =
                Mockito.mockStatic(ConfigCacheService.class)) {
            String groupKey = GroupKey2.getKey("dataId", "group", "tenant");
            configCacheServiceMockedStatic
                .when(() -> ConfigCacheService.getLastModifiedTs(groupKey))
                .thenReturn(0L);
            configCacheServiceMockedStatic.when(() -> ConfigCacheService.getContentMd5(groupKey))
                .thenReturn("oldMd5");
            
            assertTrue(dumpAllProcessor.process(new DumpAllTask(false)));
            
            verify(mockedExecutors.constructed().get(0), never()).execute(any(Runnable.class));
        }
    }
    
    @Test
    void testProcessCheckDumpsChangedConfig() {
        ConfigInfoWrapper configInfo = newConfigInfo(1L, "dataId", "group", "tenant", "summary");
        configInfo.setMd5("newMd5");
        ConfigInfoWrapper fullConfig = newConfigInfo(1L, "dataId", "group", "tenant", "content");
        when(configInfoPersistService.findConfigMaxId()).thenReturn(1L);
        when(configInfoPersistService.findAllConfigInfoFragment(0L, PAGE_SIZE, false))
            .thenReturn(newPage(configInfo));
        when(configInfoPersistService.findConfigInfo("dataId", "group", "tenant"))
            .thenReturn(fullConfig);
        
        try (MockedConstruction<ThreadPoolExecutor> ignored = mockExecutorConstruction(0);
            MockedStatic<ConfigCacheService> configCacheServiceMockedStatic =
                Mockito.mockStatic(ConfigCacheService.class)) {
            String groupKey = GroupKey2.getKey("dataId", "group", "tenant");
            configCacheServiceMockedStatic
                .when(() -> ConfigCacheService.getLastModifiedTs(groupKey))
                .thenReturn(0L);
            configCacheServiceMockedStatic.when(() -> ConfigCacheService.getContentMd5(groupKey))
                .thenReturn("oldMd5");
            configCacheServiceMockedStatic.when(() -> ConfigCacheService.dumpWithMd5(
                anyString(), anyString(), anyString(), anyString(), anyString(), anyLong(),
                anyString(), anyString())).thenReturn(true);
            
            assertTrue(dumpAllProcessor.process(new DumpAllTask(false)));
            
            configCacheServiceMockedStatic.verify(() -> ConfigCacheService.dumpWithMd5(
                eq("dataId"), eq("group"), eq("tenant"), eq("content"), anyString(),
                eq(10L), eq("text"), eq("key")));
        }
    }
    
    @Test
    void testProcessWaitsForUnfinishedTasks() {
        when(configInfoPersistService.findConfigMaxId()).thenReturn(0L);
        
        try (MockedConstruction<ThreadPoolExecutor> mockedExecutors =
            mockExecutorConstruction(1, 0)) {
            assertTrue(dumpAllProcessor.process(new DumpAllTask(true)));
            
            verify(mockedExecutors.constructed().get(0), atLeast(2)).getQueue();
        }
    }
    
    @Test
    void testProcessIgnoresWaitException() {
        when(configInfoPersistService.findConfigMaxId()).thenReturn(0L);
        
        try (MockedConstruction<ThreadPoolExecutor> mockedExecutors =
            mockExecutorConstructionWithWaitException()) {
            assertTrue(dumpAllProcessor.process(new DumpAllTask(true)));
            
            verify(mockedExecutors.constructed().get(0), never()).shutdown();
        }
    }
    
    private ConfigInfoWrapper newConfigInfo(long id, String dataId, String group, String tenant,
        String content) {
        ConfigInfoWrapper configInfo = new ConfigInfoWrapper();
        configInfo.setId(id);
        configInfo.setDataId(dataId);
        configInfo.setGroup(group);
        configInfo.setTenant(tenant);
        configInfo.setContent(content);
        configInfo.setLastModified(id * 10L);
        configInfo.setType("text");
        configInfo.setEncryptedDataKey("key");
        return configInfo;
    }
    
    private Page<ConfigInfoWrapper> newPage(ConfigInfoWrapper... items) {
        Page<ConfigInfoWrapper> page = new Page<>();
        page.setPageItems(Arrays.asList(items));
        return page;
    }
    
    private MockedStatic<ConfigCacheService> mockDumpWithMd5(boolean result) {
        MockedStatic<ConfigCacheService> configCacheServiceMockedStatic =
            Mockito.mockStatic(ConfigCacheService.class);
        configCacheServiceMockedStatic.when(() -> ConfigCacheService.dumpWithMd5(
            anyString(), anyString(), anyString(), anyString(), anyString(), anyLong(),
            anyString(), anyString())).thenReturn(result);
        return configCacheServiceMockedStatic;
    }
    
    @SuppressWarnings("unchecked")
    private MockedConstruction<ThreadPoolExecutor> mockExecutorConstruction(Integer... queueSizes) {
        return Mockito.mockConstruction(ThreadPoolExecutor.class, (mock, context) -> {
            doAnswer(invocation -> {
                invocation.getArgument(0, Runnable.class).run();
                return null;
            }).when(mock).execute(any(Runnable.class));
            BlockingQueue<Runnable> queue = Mockito.mock(BlockingQueue.class);
            Integer[] remaining = Arrays.copyOfRange(queueSizes, 1, queueSizes.length);
            when(queue.size()).thenReturn(queueSizes[0], remaining);
            when(mock.getQueue()).thenReturn(queue);
            when(mock.getActiveCount()).thenReturn(0);
        });
    }
    
    private MockedConstruction<ThreadPoolExecutor> mockExecutorConstructionWithWaitException() {
        return Mockito.mockConstruction(ThreadPoolExecutor.class,
            (mock, context) -> when(mock.getQueue()).thenThrow(new RuntimeException("failed")));
    }
}
