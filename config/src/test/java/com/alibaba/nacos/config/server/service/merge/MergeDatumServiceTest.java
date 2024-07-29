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

package com.alibaba.nacos.config.server.service.merge;

import com.alibaba.nacos.config.server.manager.TaskManager;
import com.alibaba.nacos.config.server.model.ConfigInfoAggr;
import com.alibaba.nacos.config.server.model.ConfigInfoChanged;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoAggrPersistService;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoPersistService;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoTagPersistService;
import com.alibaba.nacos.consistency.cp.CPProtocol;
import com.alibaba.nacos.core.distributed.ProtocolManager;
import com.alibaba.nacos.persistence.configuration.DatasourceConfiguration;
import com.alibaba.nacos.persistence.datasource.DataSourceService;
import com.alibaba.nacos.persistence.datasource.DynamicDataSource;
import com.alibaba.nacos.persistence.model.Page;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.alibaba.nacos.sys.utils.ApplicationUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static com.alibaba.nacos.persistence.constants.PersistenceConstant.CONFIG_MODEL_RAFT_GROUP;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
class MergeDatumServiceTest {
    
    @Mock
    ConfigInfoPersistService configInfoPersistService;
    
    @Mock
    ConfigInfoAggrPersistService configInfoAggrPersistService;
    
    @Mock
    ConfigInfoTagPersistService configInfoTagPersistService;
    
    @Mock
    ProtocolManager protocolManager;
    
    MockedStatic<EnvUtil> envUtilMockedStatic;
    
    MockedStatic<ApplicationUtils> applicationUtilsMockedStaticc;
    
    @Mock
    private DataSourceService dataSourceService;
    
    private MergeDatumService mergeDatumService;
    
    @BeforeEach
    void setUp() {
        envUtilMockedStatic = Mockito.mockStatic(EnvUtil.class);
        applicationUtilsMockedStaticc = Mockito.mockStatic(ApplicationUtils.class);
        applicationUtilsMockedStaticc.when(() -> ApplicationUtils.getBean(eq(ProtocolManager.class))).thenReturn(protocolManager);
        
        ReflectionTestUtils.setField(DynamicDataSource.getInstance(), "localDataSourceService", dataSourceService);
        ReflectionTestUtils.setField(DynamicDataSource.getInstance(), "basicDataSourceService", dataSourceService);
        mergeDatumService = new MergeDatumService(configInfoPersistService, configInfoAggrPersistService, configInfoTagPersistService);
        
    }
    
    @AfterEach
    void after() {
        envUtilMockedStatic.close();
        applicationUtilsMockedStaticc.close();
    }
    
    @Test
    void testSplitList() {
        String dataId = "dataID";
        int count = 5;
        List<ConfigInfoChanged> configList = new ArrayList<>();
        configList.add(create(dataId, 0));
        configList.add(create(dataId, 1));
        configList.add(create(dataId, 2));
        configList.add(create(dataId, 3));
        configList.add(create(dataId, 4));
        configList.add(create(dataId, 5));
        configList.add(create(dataId, 6));
        configList.add(create(dataId, 7));
        configList.add(create(dataId, 8));
        
        List<List<ConfigInfoChanged>> lists = mergeDatumService.splitList(configList, count);
        int originalCount = configList.size();
        int actualCount = 0;
        for (int i = 0; i < lists.size(); i++) {
            List<ConfigInfoChanged> indexList = lists.get(i);
            for (int j = 0; j < indexList.size(); j++) {
                ConfigInfoChanged configInfoChanged = indexList.get(j);
                actualCount++;
                assertEquals(configInfoChanged, configList.get((j * count) + i));
            }
        }
        
        assertEquals(originalCount, actualCount);
        
    }
    
    private ConfigInfoChanged create(String dataID, int i) {
        ConfigInfoChanged hasDatum = new ConfigInfoChanged();
        hasDatum.setDataId(dataID + i);
        hasDatum.setTenant("tenant1");
        hasDatum.setGroup("group1");
        return hasDatum;
    }
    
    @Test
    void executeMergeConfigTask() {
        envUtilMockedStatic.when(() -> EnvUtil.getProperty(eq("nacos.config.retention.days"))).thenReturn("10");
        ConfigInfoChanged hasDatum = new ConfigInfoChanged();
        hasDatum.setDataId("hasDatumdataId1");
        hasDatum.setTenant("tenant1");
        hasDatum.setGroup("group1");
        ConfigInfoChanged noDatum = new ConfigInfoChanged();
        noDatum.setDataId("dataId1");
        noDatum.setTenant("tenant1");
        noDatum.setGroup("group1");
        List<ConfigInfoChanged> configInfoList = new ArrayList<>();
        configInfoList.add(hasDatum);
        configInfoList.add(noDatum);
        
        when(configInfoAggrPersistService.aggrConfigInfoCount(eq(hasDatum.getDataId()), eq(hasDatum.getGroup()),
                eq(hasDatum.getTenant()))).thenReturn(2);
        Page<ConfigInfoAggr> datumPage = new Page<>();
        ConfigInfoAggr configInfoAggr1 = new ConfigInfoAggr();
        configInfoAggr1.setContent("12344");
        ConfigInfoAggr configInfoAggr2 = new ConfigInfoAggr();
        configInfoAggr2.setContent("12345666");
        datumPage.getPageItems().add(configInfoAggr1);
        datumPage.getPageItems().add(configInfoAggr2);
        
        when(configInfoAggrPersistService.findConfigInfoAggrByPage(eq(hasDatum.getDataId()), eq(hasDatum.getGroup()),
                eq(hasDatum.getTenant()), anyInt(), anyInt())).thenReturn(datumPage);
        
        when(configInfoAggrPersistService.aggrConfigInfoCount(eq(noDatum.getDataId()), eq(noDatum.getGroup()),
                eq(noDatum.getTenant()))).thenReturn(0);
        
        mergeDatumService.executeMergeConfigTask(configInfoList, 1000);
    }
    
    @Test
    void testAddMergeTaskExternalModel() {
        String dataId = "dataId12345";
        String group = "group123";
        String tenant = "tenant1234";
        String clientIp = "127.0.0.1";
        DatasourceConfiguration.setEmbeddedStorage(false);
        TaskManager mockTasker = Mockito.mock(TaskManager.class);
        ReflectionTestUtils.setField(mergeDatumService, "mergeTasks", mockTasker);
        mergeDatumService.addMergeTask(dataId, group, tenant, clientIp);
        Mockito.verify(mockTasker, times(1)).addTask(anyString(), any(MergeDataTask.class));
    }
    
    @Test
    void testAddMergeTaskEmbeddedAndStandAloneModel() {
        
        DatasourceConfiguration.setEmbeddedStorage(true);
        envUtilMockedStatic.when(() -> EnvUtil.getStandaloneMode()).thenReturn(true);
        TaskManager mockTasker = Mockito.mock(TaskManager.class);
        ReflectionTestUtils.setField(mergeDatumService, "mergeTasks", mockTasker);
        String dataId = "dataId12345";
        String group = "group123";
        String tenant = "tenant1234";
        String clientIp = "127.0.0.1";
        mergeDatumService.addMergeTask(dataId, group, tenant, clientIp);
        Mockito.verify(mockTasker, times(1)).addTask(anyString(), any(MergeDataTask.class));
    }
    
    @Test
    void testAddMergeTaskEmbeddedAndClusterModelLeader() {
        
        DatasourceConfiguration.setEmbeddedStorage(true);
        envUtilMockedStatic.when(() -> EnvUtil.getStandaloneMode()).thenReturn(false);
        TaskManager mockTasker = Mockito.mock(TaskManager.class);
        ReflectionTestUtils.setField(mergeDatumService, "mergeTasks", mockTasker);
        //mock is leader
        CPProtocol cpProtocol = Mockito.mock(CPProtocol.class);
        when(protocolManager.getCpProtocol()).thenReturn(cpProtocol);
        when(cpProtocol.isLeader(eq(CONFIG_MODEL_RAFT_GROUP))).thenReturn(true);
        String dataId = "dataId12345";
        String group = "group123";
        String tenant = "tenant1234";
        String clientIp = "127.0.0.1";
        mergeDatumService.addMergeTask(dataId, group, tenant, clientIp);
        Mockito.verify(mockTasker, times(1)).addTask(anyString(), any(MergeDataTask.class));
    }
    
    @Test
    void testAddMergeTaskEmbeddedAndClusterModelNotLeader() {
        
        DatasourceConfiguration.setEmbeddedStorage(true);
        envUtilMockedStatic.when(() -> EnvUtil.getStandaloneMode()).thenReturn(false);
        TaskManager mockTasker = Mockito.mock(TaskManager.class);
        ReflectionTestUtils.setField(mergeDatumService, "mergeTasks", mockTasker);
        //mock not leader
        CPProtocol cpProtocol = Mockito.mock(CPProtocol.class);
        when(protocolManager.getCpProtocol()).thenReturn(cpProtocol);
        when(cpProtocol.isLeader(eq(CONFIG_MODEL_RAFT_GROUP))).thenReturn(false);
        String dataId = "dataId12345";
        String group = "group123";
        String tenant = "tenant1234";
        String clientIp = "127.0.0.1";
        mergeDatumService.addMergeTask(dataId, group, tenant, clientIp);
        Mockito.verify(mockTasker, times(0)).addTask(anyString(), any(MergeDataTask.class));
    }
}
