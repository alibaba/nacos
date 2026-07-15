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

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.config.server.manager.TaskManager;
import com.alibaba.nacos.config.server.model.event.ConfigDataChangeEvent;
import com.alibaba.nacos.config.server.service.dump.disk.ConfigDiskService;
import com.alibaba.nacos.config.server.service.dump.disk.ConfigDiskServiceFactory;
import com.alibaba.nacos.config.server.service.dump.task.DumpAllTask;
import com.alibaba.nacos.config.server.service.dump.task.DumpTask;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoGrayPersistService;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoPersistService;
import com.alibaba.nacos.config.server.service.repository.HistoryConfigInfoPersistService;
import com.alibaba.nacos.config.server.utils.ConfigExecutor;
import com.alibaba.nacos.config.server.utils.GroupKey;
import com.alibaba.nacos.config.server.utils.PropertyUtil;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.core.namespace.repository.NamespacePersistService;
import com.alibaba.nacos.persistence.datasource.DataSourceService;
import com.alibaba.nacos.persistence.datasource.DynamicDataSource;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;

@ExtendWith(SpringExtension.class)
class DumpServiceTest {
    
    @MockitoBean
    DefaultHistoryConfigCleaner defaultHistoryConfigCleaner = new DefaultHistoryConfigCleaner();
    
    @MockitoBean
    ConfigInfoPersistService configInfoPersistService;
    
    @MockitoBean
    NamespacePersistService namespacePersistService;
    
    @MockitoBean
    HistoryConfigInfoPersistService historyConfigInfoPersistService;
    
    @MockitoBean
    ConfigInfoGrayPersistService configInfoGrayPersistService;
    
    @MockitoBean
    ServerMemberManager memberManager;
    
    MockedStatic<EnvUtil> envUtilMockedStatic;
    
    MockedStatic<ConfigExecutor> configExecutorMocked;
    
    MockedStatic<PropertyUtil> propertyUtilMockedStatic;
    
    MockedStatic<HistoryConfigCleanerManager> historyConfigCleanerManagerMockedStatic;
    
    @MockitoBean
    private DataSourceService dataSourceService;
    
    private DumpService dumpService;
    
    @MockitoBean
    private TaskManager dumpTaskMgr;
    
    @BeforeEach
    void setUp() {
        envUtilMockedStatic = Mockito.mockStatic(EnvUtil.class);
        propertyUtilMockedStatic = Mockito.mockStatic(PropertyUtil.class);
        propertyUtilMockedStatic.when(() -> PropertyUtil.getAllDumpPageSize()).thenReturn(100);
        propertyUtilMockedStatic.when(() -> PropertyUtil.getDumpChangeWorkerInterval())
            .thenReturn(1000 * 60L);
        
        ReflectionTestUtils.setField(DynamicDataSource.getInstance(), "localDataSourceService",
            dataSourceService);
        ReflectionTestUtils.setField(DynamicDataSource.getInstance(), "basicDataSourceService",
            dataSourceService);
        dumpService = new ExternalDumpService(configInfoPersistService, namespacePersistService,
            historyConfigInfoPersistService, configInfoGrayPersistService, memberManager);
        configExecutorMocked = Mockito.mockStatic(ConfigExecutor.class);
        historyConfigCleanerManagerMockedStatic =
            Mockito.mockStatic(HistoryConfigCleanerManager.class);
        historyConfigCleanerManagerMockedStatic.when(
            () -> HistoryConfigCleanerManager.getHistoryConfigCleaner(anyString()))
            .thenReturn(defaultHistoryConfigCleaner);
        
    }
    
    @AfterEach
    void after() {
        envUtilMockedStatic.close();
        configExecutorMocked.close();
        propertyUtilMockedStatic.close();
        historyConfigCleanerManagerMockedStatic.close();
        ReflectionTestUtils.setField(ConfigDiskServiceFactory.class, "configDiskService", null);
    }
    
    @Test
    void dumpRequest() throws Throwable {
        String dataId = "12345667dataId";
        String group = "234445group";
        DumpRequest dumpRequest =
            DumpRequest.create(dataId, group, "testtenant", System.currentTimeMillis(),
                "127.0.0.1");
        // TaskManager dumpTaskMgr;
        ReflectionTestUtils.setField(dumpService, "dumpTaskMgr", dumpTaskMgr);
        Mockito.doNothing().when(dumpTaskMgr).addTask(any(), any());
        dumpService.dump(dumpRequest);
        Mockito.verify(dumpTaskMgr, times(1))
            .addTask(eq(GroupKey.getKeyTenant(dataId, group, dumpRequest.getTenant())),
                any(DumpTask.class));
        
        dumpRequest.setGrayName("tag_123");
        dumpService.dump(dumpRequest);
        Mockito.verify(dumpTaskMgr, times(1)).addTask(
            eq(GroupKey.getKeyTenant(dataId, group, dumpRequest.getTenant()) + "+gray+"
                + dumpRequest.getGrayName()),
            any(DumpTask.class));
        
    }
    
    @Test
    void dumpOperate() throws Throwable {
        configExecutorMocked.when(
            () -> ConfigExecutor.scheduleConfigTask(any(Runnable.class), anyInt(), anyInt(),
                any(TimeUnit.class)))
            .thenAnswer(invocation -> null);
        configExecutorMocked.when(
            () -> ConfigExecutor.scheduleConfigChangeTask(any(Runnable.class), anyInt(),
                any(TimeUnit.class)))
            .thenAnswer(invocation -> null);
        Mockito.when(configInfoPersistService.findConfigMaxId()).thenReturn(300L);
        dumpService.init();
        
        // expect dump
        Mockito.verify(configInfoPersistService, times(1)).findAllConfigInfoFragment(0, 100, true);
        Mockito.verify(configInfoPersistService, times(1)).findConfigMaxId();
        Mockito.verify(configInfoGrayPersistService, times(1)).configInfoGrayCount();
        
        // expect dump formal,beta,tag,history clear,config change task to be scheduled.
        // expect config clear history task be scheduled.
        configExecutorMocked.verify(
            () -> ConfigExecutor.scheduleConfigTask(any(DumpService.DumpAllProcessorRunner.class),
                anyLong(),
                anyLong(), eq(TimeUnit.MINUTES)),
            times(1));
        
        configExecutorMocked.verify(
            () -> ConfigExecutor.scheduleConfigTask(
                any(DumpService.DumpAllGrayProcessorRunner.class), anyLong(),
                anyLong(), eq(TimeUnit.MINUTES)),
            times(1));
        configExecutorMocked.verify(
            () -> ConfigExecutor.scheduleConfigChangeTask(any(DumpChangeConfigWorker.class),
                anyLong(),
                eq(TimeUnit.MILLISECONDS)),
            times(1));
        configExecutorMocked.verify(
            () -> ConfigExecutor.scheduleConfigTask(any(DumpService.ConfigHistoryClear.class),
                anyLong(), anyLong(),
                eq(TimeUnit.MINUTES)),
            times(1));
    }
    
    @Test
    void dumpOperateThrowsNacosExceptionWhenClearAllFails() {
        ConfigDiskService configDiskService = Mockito.mock(ConfigDiskService.class);
        Mockito.doThrow(new RuntimeException("clear failed")).when(configDiskService).clearAll();
        ReflectionTestUtils.setField(ConfigDiskServiceFactory.class, "configDiskService",
            configDiskService);
        
        NacosException exception =
            assertThrows(NacosException.class, () -> dumpService.dumpOperate());
        
        assertTrue(exception.getMessage().contains("bean construction failure"));
    }
    
    @Test
    void dumpOperateThrowsNacosExceptionWhenClearAllGrayFails() {
        ConfigDiskService configDiskService = Mockito.mock(ConfigDiskService.class);
        Mockito.doThrow(new RuntimeException("clear gray failed")).when(configDiskService)
            .clearAllGray();
        ReflectionTestUtils.setField(ConfigDiskServiceFactory.class, "configDiskService",
            configDiskService);
        Mockito.when(configInfoPersistService.findConfigMaxId()).thenReturn(0L);
        
        NacosException exception =
            assertThrows(NacosException.class, () -> dumpService.dumpOperate());
        
        assertTrue(exception.getMessage().contains("bean construction failure"));
    }
    
    @Test
    void clearHistory() {
        envUtilMockedStatic.when(() -> EnvUtil.getProperty(eq("nacos.config.retention.days")))
            .thenReturn("10");
        Mockito.when(memberManager.isFirstIp()).thenReturn(true);
        DumpService.ConfigHistoryClear configHistoryClear = dumpService.new ConfigHistoryClear(
            defaultHistoryConfigCleaner);
        configHistoryClear.run();
        Mockito.verify(defaultHistoryConfigCleaner, times(1)).cleanHistoryConfig();
    }
    
    @Test
    void clearHistoryNotFirstIp() {
        Mockito.when(memberManager.isFirstIp()).thenReturn(false);
        DumpService.ConfigHistoryClear configHistoryClear = dumpService.new ConfigHistoryClear(
            defaultHistoryConfigCleaner);
        configHistoryClear.run();
        Mockito.verify(defaultHistoryConfigCleaner, times(0)).cleanHistoryConfig();
    }
    
    @Test
    void clearHistoryThrowsException() {
        Mockito.when(memberManager.isFirstIp()).thenReturn(true);
        envUtilMockedStatic.when(() -> EnvUtil.getProperty(eq("nacos.config.retention.days")))
            .thenReturn("10");
        Mockito.doThrow(new RuntimeException("db error")).when(defaultHistoryConfigCleaner)
            .cleanHistoryConfig();
        DumpService.ConfigHistoryClear configHistoryClear = dumpService.new ConfigHistoryClear(
            defaultHistoryConfigCleaner);
        configHistoryClear.run();
        Mockito.verify(defaultHistoryConfigCleaner, times(1)).cleanHistoryConfig();
    }
    
    @Test
    void testDumpAllProcessorRunner() {
        ReflectionTestUtils.setField(dumpService, "dumpAllTaskMgr", dumpTaskMgr);
        DumpService.DumpAllProcessorRunner runner = dumpService.new DumpAllProcessorRunner();
        runner.run();
        Mockito.verify(dumpTaskMgr, times(1)).addTask(any(), any());
    }
    
    @Test
    void testDumpAllGrayProcessorRunner() {
        ReflectionTestUtils.setField(dumpService, "dumpAllTaskMgr", dumpTaskMgr);
        DumpService.DumpAllGrayProcessorRunner runner =
            dumpService.new DumpAllGrayProcessorRunner();
        runner.run();
        Mockito.verify(dumpTaskMgr, times(1)).addTask(any(), any());
    }
    
    @Test
    void testHandleConfigDataChangeWithGrayName() {
        ConfigDataChangeEvent evt = new ConfigDataChangeEvent("dataId", "group",
            null, "gray1", System.currentTimeMillis());
        ReflectionTestUtils.setField(dumpService, "dumpTaskMgr", dumpTaskMgr);
        Mockito.doNothing().when(dumpTaskMgr).addTask(any(), any());
        dumpService.handleConfigDataChange(evt);
        Mockito.verify(dumpTaskMgr, times(1)).addTask(
            eq(com.alibaba.nacos.config.server.utils.GroupKey
                .getKeyTenant("dataId", "group", null) + "+gray+gray1"),
            any());
    }
    
    @Test
    void testHandleConfigDataChange() {
        ConfigDataChangeEvent configDataChangeEvent =
            new ConfigDataChangeEvent("dataId", "group", null,
                System.currentTimeMillis());
        ReflectionTestUtils.setField(dumpService, "dumpTaskMgr", dumpTaskMgr);
        Mockito.doNothing().when(dumpTaskMgr).addTask(any(), any());
        
        dumpService.handleConfigDataChange(configDataChangeEvent);
        Mockito.verify(dumpTaskMgr, times(1)).addTask(
            eq(GroupKey.getKeyTenant(configDataChangeEvent.dataId, configDataChangeEvent.group,
                configDataChangeEvent.tenant)),
            any(DumpTask.class));
    }
    
    @Test
    void testDumpAll() {
        ReflectionTestUtils.setField(dumpService, "dumpAllTaskMgr", dumpTaskMgr);
        Mockito.doNothing().when(dumpTaskMgr).addTask(any(), any());
        dumpService.dumpAll();
        Mockito.verify(dumpTaskMgr, times(1)).addTask(
            eq(DumpAllTask.TASK_ID), any(DumpAllTask.class));
    }
    
}
