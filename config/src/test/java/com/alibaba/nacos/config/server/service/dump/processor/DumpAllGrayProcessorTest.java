/*
 * Copyright 1999-$toady.year Alibaba Group Holding Ltd.
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
import com.alibaba.nacos.common.utils.MD5Utils;
import com.alibaba.nacos.config.server.model.CacheItem;
import com.alibaba.nacos.config.server.model.ConfigInfoGrayWrapper;
import com.alibaba.nacos.config.server.service.ConfigCacheService;
import com.alibaba.nacos.config.server.service.dump.ExternalDumpService;
import com.alibaba.nacos.config.server.service.dump.disk.ConfigDiskServiceFactory;
import com.alibaba.nacos.config.server.service.dump.task.DumpAllGrayTask;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoGrayPersistService;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoPersistService;
import com.alibaba.nacos.config.server.utils.GroupKey2;
import com.alibaba.nacos.config.server.utils.PropertyUtil;
import com.alibaba.nacos.persistence.datasource.DataSourceService;
import com.alibaba.nacos.persistence.datasource.DynamicDataSource;
import com.alibaba.nacos.plugin.datasource.constants.CommonConstant;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DumpAllGrayProcessorTest {
    
    private static final int PAGE_SIZE = 100;
    
    @Mock
    DynamicDataSource dynamicDataSource;
    
    @Mock
    DataSourceService dataSourceService;
    
    @Mock
    ConfigInfoGrayPersistService configInfoGrayPersistService;
    
    DumpAllProcessor dumpAllProcessor;
    
    DumpAllGrayProcessor dumpAllGrayProcessor;
    
    ExternalDumpService dumpService;
    
    MockedStatic<DynamicDataSource> dynamicDataSourceMockedStatic;
    
    MockedStatic<PropertyUtil> propertyUtilMockedStatic;
    
    @Mock
    ConfigInfoPersistService configInfoPersistService;
    
    MockedStatic<EnvUtil> envUtilMockedStatic;
    
    private String mockMem = "tmpmocklimitfile.txt";
    
    @BeforeEach
    void init() throws Exception {
        dynamicDataSourceMockedStatic = Mockito.mockStatic(DynamicDataSource.class);
        envUtilMockedStatic = Mockito.mockStatic(EnvUtil.class);
        propertyUtilMockedStatic = Mockito.mockStatic(PropertyUtil.class);
        propertyUtilMockedStatic.when(PropertyUtil::getAllDumpPageSize).thenReturn(100);
        dumpAllGrayProcessor = new DumpAllGrayProcessor(configInfoGrayPersistService);
        when(EnvUtil.getNacosHome()).thenReturn(System.getProperty("user.home"));
        when(EnvUtil.getProperty(eq(CommonConstant.NACOS_PLUGIN_DATASOURCE_LOG), eq(Boolean.class),
                eq(false))).thenReturn(false);
        dynamicDataSourceMockedStatic.when(DynamicDataSource::getInstance).thenReturn(dynamicDataSource);
        
        when(dynamicDataSource.getDataSource()).thenReturn(dataSourceService);
        
        dumpService = new ExternalDumpService(configInfoPersistService, null, null, configInfoGrayPersistService, null,
                null);
        
        dumpAllProcessor = new DumpAllProcessor(configInfoPersistService);
        envUtilMockedStatic.when(() -> EnvUtil.getProperty(eq("memory_limit_file_path"),
                eq("/sys/fs/cgroup/memory/memory.limit_in_bytes"))).thenReturn(mockMem);
        
    }
    
    @AfterEach
    void after() {
        dynamicDataSourceMockedStatic.close();
        envUtilMockedStatic.close();
        propertyUtilMockedStatic.close();
    }
    
    @Test
    void testProcessWithInvalidTaskType() {
        NacosTask invalidTask = mock(NacosTask.class);
        
        boolean result = dumpAllGrayProcessor.process(invalidTask);
        
        assertFalse(result);
        verify(configInfoGrayPersistService, never()).configInfoGrayCount();
    }
    
    @Test
    void testProcessWithValidTaskType() throws Exception {
        final DumpAllGrayTask validTask = mock(DumpAllGrayTask.class);
        List<ConfigInfoGrayWrapper> configList = new ArrayList<>();
        configList.add(createGrayWrapper("dataId-1", "group-1"));
        
        Page<ConfigInfoGrayWrapper> page = new Page<>();
        when(configInfoGrayPersistService.configInfoGrayCount()).thenReturn(1);
        when(configInfoGrayPersistService.findAllConfigInfoGrayForDumpAll(anyInt(), anyInt())).thenReturn(page);
        
        boolean result = dumpAllGrayProcessor.process(validTask);
        
        assertTrue(result);
        verify(configInfoGrayPersistService, times(1)).configInfoGrayCount();
        verify(configInfoGrayPersistService, times(1)).findAllConfigInfoGrayForDumpAll(anyInt(), anyInt());
    }
    
    @Test
    void testPaginationLogic() {
        int totalConfigs = PAGE_SIZE * 2 + 50;
        int expectedPage = (int) Math.ceil(totalConfigs * 1.0 / PAGE_SIZE);
        System.out.println("totalConfigs: " + totalConfigs + " , expectedPage: " + expectedPage);
        reset(configInfoGrayPersistService);
        when(configInfoGrayPersistService.configInfoGrayCount()).thenReturn(totalConfigs);
        
        Page<ConfigInfoGrayWrapper> pageOne = new Page<>();
        when(configInfoGrayPersistService.findAllConfigInfoGrayForDumpAll(eq(1), anyInt())).thenReturn(pageOne);
        
        DumpAllGrayTask task = mock(DumpAllGrayTask.class);
        boolean result = dumpAllGrayProcessor.process(task);
        
        assertTrue(result);
        verify(configInfoGrayPersistService, atLeastOnce()).findAllConfigInfoGrayForDumpAll(anyInt(), anyInt());
    }
    
    @Test
    void testInteractionWithConfigCacheService() {
        DumpAllGrayTask task = mock(DumpAllGrayTask.class);
        Page<ConfigInfoGrayWrapper> page = new Page<>();
        when(configInfoGrayPersistService.configInfoGrayCount()).thenReturn(1);
        when(configInfoGrayPersistService.findAllConfigInfoGrayForDumpAll(anyInt(), anyInt())).thenReturn(page);
        boolean result = dumpAllGrayProcessor.process(task);
        assertTrue(result);
    }
    
    /**
     * test dump all for all check task.
     */
    @Test
    void testDumpAllGrayOnCheckAll() throws Exception {
        ConfigInfoGrayWrapper configInfoGrayWrapper1 = createGrayWrapper("data-1", "group-1");
        ConfigInfoGrayWrapper configInfoGrayWrapper2 = createGrayWrapper("data-2", "group-2");
        long timestamp = System.currentTimeMillis();
        configInfoGrayWrapper1.setLastModified(timestamp);
        configInfoGrayWrapper2.setLastModified(timestamp);
        
        Page<ConfigInfoGrayWrapper> page = new Page<>();
        page.setTotalCount(2);
        page.setPagesAvailable(2);
        page.setPageNumber(1);
        List<ConfigInfoGrayWrapper> list = Arrays.asList(configInfoGrayWrapper1, configInfoGrayWrapper2);
        page.setPageItems(list);
        
        Mockito.when(configInfoGrayPersistService.configInfoGrayCount()).thenReturn(2);
        Mockito.when(configInfoGrayPersistService.findAllConfigInfoGrayForDumpAll(anyInt(), anyInt())).thenReturn(page);
        
        final String md51 = MD5Utils.md5Hex(configInfoGrayWrapper1.getContent(), "UTF-8");
        final String md52 = MD5Utils.md5Hex(configInfoGrayWrapper2.getContent(), "UTF-8");
        long latterTimestamp = timestamp + 999;
        long earlierTimestamp = timestamp - 999;
        String encryptedDataKey = "testEncryptedDataKey";
        
        String dataId1 = configInfoGrayWrapper1.getDataId();
        String group1 = configInfoGrayWrapper1.getGroup();
        String grayName1 = configInfoGrayWrapper1.getGrayName();
        String grayRule1 = configInfoGrayWrapper1.getGrayRule();
        String tenant1 = configInfoGrayWrapper1.getTenant();
        String content1 = configInfoGrayWrapper1.getContent();
        
        String dataId2 = configInfoGrayWrapper2.getDataId();
        String group2 = configInfoGrayWrapper2.getGroup();
        String grayName2 = configInfoGrayWrapper2.getGrayName();
        String grayRule2 = configInfoGrayWrapper2.getGrayRule();
        String tenant2 = configInfoGrayWrapper2.getTenant();
        String content2 = configInfoGrayWrapper2.getContent();
        
        ConfigCacheService.dumpGray(dataId1, group1, tenant1, grayName1, grayRule1, content1, latterTimestamp,
                encryptedDataKey);
        ConfigCacheService.dumpGray(dataId2, group2, tenant2, grayName2, grayRule2, content2, earlierTimestamp,
                encryptedDataKey);
        
        DumpAllGrayTask dumpAllTask = new DumpAllGrayTask();
        boolean process = dumpAllGrayProcessor.process(dumpAllTask);
        
        assertTrue(process);
        
        CacheItem contentCache1 = ConfigCacheService.getContentCache(
                GroupKey2.getKey(configInfoGrayWrapper1.getDataId(), configInfoGrayWrapper1.getGroup(),
                        configInfoGrayWrapper1.getTenant()));
        assertEquals(md51, contentCache1.getConfigCacheGray().get(grayName1).getMd5());
        assertEquals(latterTimestamp, contentCache1.getConfigCacheGray().get(grayName1).getLastModifiedTs());
        
        String contentFromDisk1 = ConfigDiskServiceFactory.getInstance()
                .getGrayContent(configInfoGrayWrapper1.getDataId(), configInfoGrayWrapper1.getGroup(),
                        configInfoGrayWrapper1.getTenant(), configInfoGrayWrapper1.getGrayName());
        assertEquals(configInfoGrayWrapper1.getContent(), contentFromDisk1);
        
        CacheItem contentCache2 = ConfigCacheService.getContentCache(
                GroupKey2.getKey(configInfoGrayWrapper2.getDataId(), configInfoGrayWrapper2.getGroup(),
                        configInfoGrayWrapper2.getTenant()));
        assertEquals(md52, contentCache2.getConfigCacheGray().get(grayName2).getMd5());
        assertEquals(configInfoGrayWrapper2.getLastModified(),
                contentCache2.getConfigCacheGray().get(grayName2).getLastModifiedTs());
        
        String contentFromDisk2 = ConfigDiskServiceFactory.getInstance()
                .getGrayContent(configInfoGrayWrapper2.getDataId(), configInfoGrayWrapper2.getGroup(),
                        configInfoGrayWrapper2.getTenant(), configInfoGrayWrapper2.getGrayName());
        assertEquals(configInfoGrayWrapper2.getContent(), contentFromDisk2);
    }
    
    private ConfigInfoGrayWrapper createGrayWrapper(String dataId, String group) {
        ConfigInfoGrayWrapper wrapper = new ConfigInfoGrayWrapper();
        wrapper.setDataId(dataId);
        wrapper.setGroup(group);
        wrapper.setTenant("tenant");
        wrapper.setGrayName("gray-" + dataId);
        String grayRule = "{\"type\":\"beta\",\"version\":\"1.0.0\",\"expr\":\"0 0/5 * * * ?\",\"priority\":1}";
        wrapper.setGrayRule(grayRule);
        wrapper.setContent("content");
        wrapper.setLastModified(System.currentTimeMillis());
        wrapper.setEncryptedDataKey("enc-key");
        wrapper.setMd5(MD5Utils.md5Hex(wrapper.getContent(), "UTF-8"));
        return wrapper;
    }
}