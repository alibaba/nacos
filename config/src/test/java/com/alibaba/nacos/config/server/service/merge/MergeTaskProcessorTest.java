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

import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.notify.listener.Subscriber;
import com.alibaba.nacos.config.server.model.ConfigInfo;
import com.alibaba.nacos.config.server.model.ConfigInfoAggr;
import com.alibaba.nacos.config.server.model.ConfigOperateResult;
import com.alibaba.nacos.config.server.model.event.ConfigDataChangeEvent;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoAggrPersistService;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoPersistService;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoTagPersistService;
import com.alibaba.nacos.persistence.datasource.DataSourceService;
import com.alibaba.nacos.persistence.datasource.DynamicDataSource;
import com.alibaba.nacos.persistence.model.Page;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.alibaba.nacos.sys.utils.InetUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)

public class MergeTaskProcessorTest {
    
    @Mock
    ConfigInfoPersistService configInfoPersistService;
    
    @Mock
    ConfigInfoAggrPersistService configInfoAggrPersistService;
    
    @Mock
    ConfigInfoTagPersistService configInfoTagPersistService;
    
    MockedStatic<EnvUtil> envUtilMockedStatic;
    
    MergeTaskProcessor mergeTaskProcessor;
    
    MockedStatic<InetUtils> inetUtilsMockedStatic;
    
    @Mock
    private DataSourceService dataSourceService;
    
    @Mock
    private MergeDatumService mergeDatumService;
    
    @BeforeEach
    void setUp() {
        envUtilMockedStatic = Mockito.mockStatic(EnvUtil.class);
        inetUtilsMockedStatic = Mockito.mockStatic(InetUtils.class);
        ReflectionTestUtils.setField(DynamicDataSource.getInstance(), "localDataSourceService", dataSourceService);
        ReflectionTestUtils.setField(DynamicDataSource.getInstance(), "basicDataSourceService", dataSourceService);
        mergeTaskProcessor = new MergeTaskProcessor(configInfoPersistService, configInfoAggrPersistService, configInfoTagPersistService,
                mergeDatumService);
        inetUtilsMockedStatic.when(InetUtils::getSelfIP).thenReturn("127.0.0.1");
        after();
    }
    
    public void after() {
        envUtilMockedStatic.close();
        inetUtilsMockedStatic.close();
    }
    
    /**
     * test aggr has datum and merge it expect: 1.config to be inserted 2.config data change event to be published
     */
    @Test
    void testMergerExistAggrConfig() throws InterruptedException {
        String dataId = "dataId12345";
        String group = "group123";
        String tenant = "tenant1234";
        when(configInfoAggrPersistService.aggrConfigInfoCount(eq(dataId), eq(group), eq(tenant))).thenReturn(2);
        Page<ConfigInfoAggr> datumPage = new Page<>();
        ConfigInfoAggr configInfoAggr1 = new ConfigInfoAggr();
        configInfoAggr1.setContent("12344");
        ConfigInfoAggr configInfoAggr2 = new ConfigInfoAggr();
        configInfoAggr2.setContent("12345666");
        datumPage.getPageItems().add(configInfoAggr1);
        datumPage.getPageItems().add(configInfoAggr2);
        
        when(configInfoAggrPersistService.findConfigInfoAggrByPage(eq(dataId), eq(group), eq(tenant), anyInt(), anyInt())).thenReturn(
                datumPage);
        
        when(configInfoPersistService.insertOrUpdate(eq(null), eq(null), any(ConfigInfo.class), eq(null))).thenReturn(
                new ConfigOperateResult());
        
        AtomicReference<ConfigDataChangeEvent> reference = new AtomicReference<>();
        NotifyCenter.registerSubscriber(new Subscriber() {
            
            @Override
            public void onEvent(Event event) {
                ConfigDataChangeEvent event1 = (ConfigDataChangeEvent) event;
                if (event1.dataId.equals(dataId) && event1.group.equals(group) && tenant.equals(event1.tenant)) {
                    reference.set((ConfigDataChangeEvent) event);
                }
            }
            
            @Override
            public Class<? extends Event> subscribeType() {
                return ConfigDataChangeEvent.class;
            }
        });
        
        MergeDataTask mergeDataTask = new MergeDataTask(dataId, group, tenant, "127.0.0.1");
        mergeTaskProcessor.process(mergeDataTask);
        
        Mockito.verify(configInfoPersistService, times(1)).insertOrUpdate(eq(null), eq(null), any(ConfigInfo.class), eq(null));
        Thread.sleep(1000L);
        assertTrue(reference.get() != null);
        
    }
    
    /**
     * test aggr has datum and remove it.
     */
    @Test
    void testMergerNotExistAggrConfig() throws InterruptedException {
        String dataId = "dataId12345";
        String group = "group123";
        String tenant = "tenant1234";
        when(configInfoAggrPersistService.aggrConfigInfoCount(eq(dataId), eq(group), eq(tenant))).thenReturn(0);
        
        AtomicReference<ConfigDataChangeEvent> reference = new AtomicReference<>();
        NotifyCenter.registerSubscriber(new Subscriber() {
            
            @Override
            public void onEvent(Event event) {
                ConfigDataChangeEvent event1 = (ConfigDataChangeEvent) event;
                if (event1.dataId.equals(dataId) && event1.group.equals(group) && tenant.equals(event1.tenant)) {
                    reference.set((ConfigDataChangeEvent) event);
                }
            }
            
            @Override
            public Class<? extends Event> subscribeType() {
                return ConfigDataChangeEvent.class;
            }
        });
        
        MergeDataTask mergeDataTask = new MergeDataTask(dataId, group, tenant, "127.0.0.1");
        Mockito.doNothing().when(configInfoPersistService).removeConfigInfo(eq(dataId), eq(group), eq(tenant), eq("127.0.0.1"), eq(null));
        //Mockito.doNothing().when(configInfoTagPersistService).removeConfigInfoTag(eq(dataId), eq(group), eq(tenant),eq(),eq("127.0.0.1"),eq(null));
        mergeTaskProcessor.process(mergeDataTask);
        Mockito.verify(configInfoPersistService, times(1)).removeConfigInfo(eq(dataId), eq(group), eq(tenant), eq("127.0.0.1"), eq(null));
        Thread.sleep(1000L);
        assertTrue(reference.get() != null);
    }
    
    /**
     * test aggr has no datum and remove it tag.
     */
    @Test
    void testTagMergerNotExistAggrConfig() throws InterruptedException {
        String dataId = "dataId12345";
        String group = "group123";
        String tenant = "tenant1234";
        String tag = "23456789";
        when(configInfoAggrPersistService.aggrConfigInfoCount(eq(dataId), eq(group), eq(tenant))).thenReturn(0);
        
        AtomicReference<ConfigDataChangeEvent> reference = new AtomicReference<>();
        NotifyCenter.registerSubscriber(new Subscriber() {
            
            @Override
            public void onEvent(Event event) {
                ConfigDataChangeEvent event1 = (ConfigDataChangeEvent) event;
                if (event1.dataId.equals(dataId) && event1.group.equals(group) && tenant.equals(event1.tenant) && tag.equals(event1.tag)) {
                    reference.set((ConfigDataChangeEvent) event);
                }
            }
            
            @Override
            public Class<? extends Event> subscribeType() {
                return ConfigDataChangeEvent.class;
            }
        });
        
        MergeDataTask mergeDataTask = new MergeDataTask(dataId, group, tenant, tag, "127.0.0.1");
        
        Mockito.doNothing().when(configInfoTagPersistService)
                .removeConfigInfoTag(eq(dataId), eq(group), eq(tenant), eq(tag), eq("127.0.0.1"), eq(null));
        mergeTaskProcessor.process(mergeDataTask);
        Mockito.verify(configInfoTagPersistService, times(1))
                .removeConfigInfoTag(eq(dataId), eq(group), eq(tenant), eq(tag), eq("127.0.0.1"), eq(null));
        Thread.sleep(1000L);
        assertTrue(reference.get() != null);
    }
    
    /**
     * test aggr has no datum and remove it tag.
     */
    @Test
    void testTagMergerError() throws InterruptedException {
        String dataId = "dataId12345";
        String group = "group123";
        String tenant = "tenant1234";
        when(configInfoAggrPersistService.aggrConfigInfoCount(eq(dataId), eq(group), eq(tenant))).thenThrow(new NullPointerException());
        
        MergeDataTask mergeDataTask = new MergeDataTask(dataId, group, tenant, "127.0.0.1");
        
        mergeTaskProcessor.process(mergeDataTask);
        Mockito.verify(mergeDatumService, times(1)).addMergeTask(eq(dataId), eq(group), eq(tenant), eq("127.0.0.1"));
        
    }
}
