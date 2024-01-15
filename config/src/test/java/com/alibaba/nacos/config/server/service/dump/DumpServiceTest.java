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

import com.alibaba.nacos.config.server.manager.TaskManager;
import com.alibaba.nacos.config.server.model.ConfigInfoChanged;
import com.alibaba.nacos.config.server.model.event.ConfigDataChangeEvent;
import com.alibaba.nacos.config.server.service.dump.task.DumpTask;
import com.alibaba.nacos.config.server.service.merge.MergeDatumService;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoAggrPersistService;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoBetaPersistService;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoPersistService;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoTagPersistService;
import com.alibaba.nacos.config.server.service.repository.HistoryConfigInfoPersistService;
import com.alibaba.nacos.config.server.utils.ConfigExecutor;
import com.alibaba.nacos.config.server.utils.GroupKey;
import com.alibaba.nacos.config.server.utils.PropertyUtil;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.core.namespace.repository.NamespacePersistService;
import com.alibaba.nacos.persistence.datasource.DataSourceService;
import com.alibaba.nacos.persistence.datasource.DynamicDataSource;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;

@RunWith(SpringJUnit4ClassRunner.class)
public class DumpServiceTest {
    
    @Mock
    ConfigInfoPersistService configInfoPersistService;
    
    @Mock
    
    NamespacePersistService namespacePersistService;
    
    @Mock
    HistoryConfigInfoPersistService historyConfigInfoPersistService;
    
    @Mock
    ConfigInfoAggrPersistService configInfoAggrPersistService;
    
    @Mock
    ConfigInfoBetaPersistService configInfoBetaPersistService;
    
    @Mock
    ConfigInfoTagPersistService configInfoTagPersistService;
    
    @Mock
    MergeDatumService mergeDatumService;
    
    @Mock
    ServerMemberManager memberManager;
    
    @Mock
    private DataSourceService dataSourceService;
    
    MockedStatic<EnvUtil> envUtilMockedStatic;
    
    MockedStatic<ConfigExecutor> configExecutorMocked;
    
    MockedStatic<PropertyUtil> propertyUtilMockedStatic;
    
    private DumpService dumpService;
    
    @Mock
    private TaskManager dumpTaskMgr;
    
    private static final String BETA_TABLE_NAME = "config_info_beta";
    
    private static final String TAG_TABLE_NAME = "config_info_tag";
    
    @Before
    public void setUp() {
        envUtilMockedStatic = Mockito.mockStatic(EnvUtil.class);
        propertyUtilMockedStatic = Mockito.mockStatic(PropertyUtil.class);
        propertyUtilMockedStatic.when(() -> PropertyUtil.getAllDumpPageSize()).thenReturn(100);
        propertyUtilMockedStatic.when(() -> PropertyUtil.getDumpChangeWorkerInterval()).thenReturn(1000 * 60L);
        
        ReflectionTestUtils.setField(DynamicDataSource.getInstance(), "localDataSourceService", dataSourceService);
        ReflectionTestUtils.setField(DynamicDataSource.getInstance(), "basicDataSourceService", dataSourceService);
        dumpService = new ExternalDumpService(configInfoPersistService, namespacePersistService,
                historyConfigInfoPersistService, configInfoAggrPersistService, configInfoBetaPersistService,
                configInfoTagPersistService, mergeDatumService, memberManager);
        configExecutorMocked = Mockito.mockStatic(ConfigExecutor.class);
        
    }
    
    @After
    public void after() {
        envUtilMockedStatic.close();
        configExecutorMocked.close();
        propertyUtilMockedStatic.close();
    }
    
    @Test
    public void dumpRequest() throws Throwable {
        String dataId = "12345667dataId";
        String group = "234445group";
        DumpRequest dumpRequest = DumpRequest.create(dataId, group, "testtenant", System.currentTimeMillis(),
                "127.0.0.1");
        // TaskManager dumpTaskMgr;
        ReflectionTestUtils.setField(dumpService, "dumpTaskMgr", dumpTaskMgr);
        Mockito.doNothing().when(dumpTaskMgr).addTask(any(), any());
        dumpService.dump(dumpRequest);
        Mockito.verify(dumpTaskMgr, times(1))
                .addTask(eq(GroupKey.getKeyTenant(dataId, group, dumpRequest.getTenant())), any(DumpTask.class));
        dumpRequest.setBeta(true);
        dumpService.dump(dumpRequest);
        Mockito.verify(dumpTaskMgr, times(1))
                .addTask(eq(GroupKey.getKeyTenant(dataId, group, dumpRequest.getTenant()) + "+beta"),
                        any(DumpTask.class));
        dumpRequest.setBeta(false);
        dumpRequest.setBatch(true);
        dumpService.dump(dumpRequest);
        Mockito.verify(dumpTaskMgr, times(1))
                .addTask(eq(GroupKey.getKeyTenant(dataId, group, dumpRequest.getTenant()) + "+batch"),
                        any(DumpTask.class));
        dumpRequest.setBatch(false);
        dumpRequest.setTag("testTag111");
        dumpService.dump(dumpRequest);
        Mockito.verify(dumpTaskMgr, times(1)).addTask(
                eq(GroupKey.getKeyTenant(dataId, group, dumpRequest.getTenant()) + "+tag+" + dumpRequest.getTag()),
                any(DumpTask.class));
        
    }
    
    @Test
    public void dumpOperate() throws Throwable {
        configExecutorMocked.when(
                () -> ConfigExecutor.scheduleConfigTask(any(Runnable.class), anyInt(), anyInt(), any(TimeUnit.class)))
                .thenAnswer(invocation -> null);
        configExecutorMocked.when(
                () -> ConfigExecutor.scheduleConfigChangeTask(any(Runnable.class), anyInt(), any(TimeUnit.class)))
                .thenAnswer(invocation -> null);
        Mockito.when(namespacePersistService.isExistTable(BETA_TABLE_NAME)).thenReturn(true);
        Mockito.when(namespacePersistService.isExistTable(TAG_TABLE_NAME)).thenReturn(true);
        ConfigInfoChanged hasDatum = new ConfigInfoChanged();
        hasDatum.setDataId("hasDatumdataId1");
        hasDatum.setTenant("tenant1");
        hasDatum.setGroup("group1");
        ConfigInfoChanged noDatum = new ConfigInfoChanged();
        noDatum.setDataId("dataId1");
        noDatum.setTenant("tenant1");
        noDatum.setGroup("group1");
        List<ConfigInfoChanged> configList = configInfoAggrPersistService.findAllAggrGroup();
        configList.add(hasDatum);
        configList.add(noDatum);
        Mockito.when(configInfoAggrPersistService.findAllAggrGroup()).thenReturn(configList);
        List<List<ConfigInfoChanged>> result = new ArrayList<>();
        result.add(Arrays.asList(hasDatum));
        result.add(Arrays.asList(noDatum));
        Mockito.when(mergeDatumService.splitList(anyList(), anyInt())).thenReturn(result);
        Mockito.doNothing().when(mergeDatumService).executeConfigsMerge(anyList());
        Mockito.when(configInfoPersistService.findConfigMaxId()).thenReturn(300L);
        dumpService.dumpOperate();
        
        // expect dump
        Mockito.verify(configInfoPersistService, times(1)).findAllConfigInfoFragment(0, 100, true);
        Mockito.verify(configInfoPersistService, times(1)).findConfigMaxId();
        Mockito.verify(configInfoBetaPersistService, times(1)).configInfoBetaCount();
        Mockito.verify(configInfoTagPersistService, times(1)).configInfoTagCount();
        
        Mockito.verify(mergeDatumService, times(2)).executeConfigsMerge(anyList());
        
        // expect dump formal,beta,tag,history clear,config change task to be scheduled.
        // expect config clear history task be scheduled.
        configExecutorMocked.verify(
                () -> ConfigExecutor.scheduleConfigTask(any(DumpService.DumpAllProcessorRunner.class), anyLong(),
                        anyLong(), eq(TimeUnit.MINUTES)), times(1));
        configExecutorMocked.verify(
                () -> ConfigExecutor.scheduleConfigTask(any(DumpService.DumpAllBetaProcessorRunner.class), anyLong(),
                        anyLong(), eq(TimeUnit.MINUTES)), times(1));
        configExecutorMocked.verify(
                () -> ConfigExecutor.scheduleConfigTask(any(DumpService.DumpAllTagProcessorRunner.class), anyLong(),
                        anyLong(), eq(TimeUnit.MINUTES)), times(1));
        configExecutorMocked.verify(
                () -> ConfigExecutor.scheduleConfigChangeTask(any(DumpChangeConfigWorker.class), anyLong(),
                        eq(TimeUnit.MILLISECONDS)), times(1));
        configExecutorMocked.verify(
                () -> ConfigExecutor.scheduleConfigTask(any(DumpService.ConfigHistoryClear.class), anyLong(), anyLong(),
                        eq(TimeUnit.MINUTES)), times(1));
    }
    
    @Test
    public void clearHistory() {
        envUtilMockedStatic.when(() -> EnvUtil.getProperty(eq("nacos.config.retention.days"))).thenReturn("10");
        Mockito.when(memberManager.isFirstIp()).thenReturn(true);
        dumpService.clearConfigHistory();
        Mockito.verify(historyConfigInfoPersistService, times(1)).removeConfigHistory(any(Timestamp.class), anyInt());
    }
    
    @Test
    public void testHandleConfigDataChange() {
        ConfigDataChangeEvent configDataChangeEvent = new ConfigDataChangeEvent("dataId", "group",
                System.currentTimeMillis());
        ReflectionTestUtils.setField(dumpService, "dumpTaskMgr", dumpTaskMgr);
        Mockito.doNothing().when(dumpTaskMgr).addTask(any(), any());
        
        dumpService.handleConfigDataChange(configDataChangeEvent);
        Mockito.verify(dumpTaskMgr, times(1)).addTask(
                eq(GroupKey.getKeyTenant(configDataChangeEvent.dataId, configDataChangeEvent.group,
                        configDataChangeEvent.tenant)), any(DumpTask.class));
    }
    
}
