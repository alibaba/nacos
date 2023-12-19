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

import com.alibaba.nacos.config.server.model.ConfigInfoAggr;
import com.alibaba.nacos.config.server.model.ConfigInfoChanged;
import com.alibaba.nacos.config.server.service.dump.processor.DumpAllBetaProcessor;
import com.alibaba.nacos.config.server.service.dump.processor.DumpAllProcessor;
import com.alibaba.nacos.config.server.service.dump.processor.DumpAllTagProcessor;
import com.alibaba.nacos.config.server.service.dump.processor.DumpProcessor;
import com.alibaba.nacos.config.server.service.merge.MergeDatumService;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoAggrPersistService;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoBetaPersistService;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoPersistService;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoTagPersistService;
import com.alibaba.nacos.config.server.service.repository.HistoryConfigInfoPersistService;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.core.namespace.repository.NamespacePersistService;
import com.alibaba.nacos.persistence.datasource.DataSourceService;
import com.alibaba.nacos.persistence.datasource.DynamicDataSource;
import com.alibaba.nacos.persistence.model.Page;
import com.alibaba.nacos.plugin.datasource.constants.CommonConstant;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.LatencyUtils.TimeServices;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

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
    
    private DumpService dumpService;
    
    @Before
    public void setUp() {
        envUtilMockedStatic = Mockito.mockStatic(EnvUtil.class);
        ReflectionTestUtils.setField(DynamicDataSource.getInstance(), "localDataSourceService", dataSourceService);
        ReflectionTestUtils.setField(DynamicDataSource.getInstance(), "basicDataSourceService", dataSourceService);
        dumpService = new ExternalDumpService(configInfoPersistService, namespacePersistService,
                historyConfigInfoPersistService, configInfoAggrPersistService, configInfoBetaPersistService,
                configInfoTagPersistService, mergeDatumService, memberManager);
    }
    
    @After
    public void after() {
        envUtilMockedStatic.close();
    }
    
    @Test
    public void dumpOperate() throws Throwable {
        dumpService.dumpOperate();
    }
    
    @Test
    public void clearHistory() {
        envUtilMockedStatic.when(() -> EnvUtil.getProperty(eq("nacos.config.retention.days"))).thenReturn("10");
        Mockito.when(memberManager.isFirstIp()).thenReturn(true);
        dumpService.clearConfigHistory();
        Mockito.verify(historyConfigInfoPersistService, times(1)).removeConfigHistory(any(Timestamp.class), anyInt());
    }
    
    
}
