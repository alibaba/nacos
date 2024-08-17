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
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
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
        when(configInfoGrayPersistService.findChangeConfig(any(Timestamp.class), any(long.class), eq(100))).thenReturn(
                mockList);
        configCacheServiceMockedStatic.when(() -> ConfigCacheService.getContentMd5(
                eq(GroupKey.getKeyTenant(mock1.getDataId(), mock1.getGroup(), mock1.getTenant())))).thenReturn("");
        
        dumpGrayConfigWorker.run();
        //verify dump gray executed
        configCacheServiceMockedStatic.verify(
                () -> ConfigCacheService.dumpGray(eq(mock1.getDataId()), eq(mock1.getGroup()), eq(mock1.getTenant()),
                        eq(mock1.getGrayName()), eq(mock1.getGrayRule()), eq(mock1.getContent()),
                        eq(mock1.getLastModified()), eq(mock1.getEncryptedDataKey())));
        //verify task scheduled
        configExecutorMockedStatic.verify(() -> ConfigExecutor.scheduleConfigChangeTask(any(DumpChangeGrayConfigWorker.class),
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
                "{\"type\":\"tagv2\",\"version\":\"1.0.0\",\"expr\":\"middleware.server.key\\u003dgray123\",\"priority\":1}");
        return configInfoGrayWrapper;
    }
}
