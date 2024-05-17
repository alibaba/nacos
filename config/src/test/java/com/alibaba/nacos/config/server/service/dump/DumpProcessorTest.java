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

import com.alibaba.nacos.common.utils.MD5Utils;
import com.alibaba.nacos.config.server.model.CacheItem;
import com.alibaba.nacos.config.server.model.ConfigInfoBetaWrapper;
import com.alibaba.nacos.config.server.model.ConfigInfoTagWrapper;
import com.alibaba.nacos.config.server.model.ConfigInfoWrapper;
import com.alibaba.nacos.config.server.service.ConfigCacheService;
import com.alibaba.nacos.config.server.service.dump.disk.ConfigDiskService;
import com.alibaba.nacos.config.server.service.dump.disk.ConfigDiskServiceFactory;
import com.alibaba.nacos.config.server.service.dump.disk.ConfigRocksDbDiskService;
import com.alibaba.nacos.config.server.service.dump.processor.DumpProcessor;
import com.alibaba.nacos.config.server.service.dump.task.DumpTask;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoBetaPersistService;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoPersistService;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoTagPersistService;
import com.alibaba.nacos.config.server.utils.GroupKey2;
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

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DumpProcessorTest {
    
    @Mock
    DynamicDataSource dynamicDataSource;
    
    @Mock
    DataSourceService dataSourceService;
    
    @Mock
    ConfigInfoPersistService configInfoPersistService;
    
    @Mock
    ConfigInfoBetaPersistService configInfoBetaPersistService;
    
    @Mock
    ConfigInfoTagPersistService configInfoTagPersistService;
    
    ExternalDumpService dumpService;
    
    DumpProcessor dumpProcessor;
    
    MockedStatic<DynamicDataSource> dynamicDataSourceMockedStatic;
    
    MockedStatic<EnvUtil> envUtilMockedStatic;
    
    @BeforeEach
    void init() throws Exception {
        dynamicDataSourceMockedStatic = Mockito.mockStatic(DynamicDataSource.class);
        envUtilMockedStatic = Mockito.mockStatic(EnvUtil.class);
        when(EnvUtil.getNacosHome()).thenReturn(System.getProperty("user.home"));
        when(EnvUtil.getProperty(eq(CommonConstant.NACOS_PLUGIN_DATASOURCE_LOG), eq(Boolean.class), eq(false))).thenReturn(false);
        dynamicDataSourceMockedStatic.when(DynamicDataSource::getInstance).thenReturn(dynamicDataSource);
        
        when(dynamicDataSource.getDataSource()).thenReturn(dataSourceService);
        
        dumpService = new ExternalDumpService(configInfoPersistService, null, null, null, configInfoBetaPersistService,
                configInfoTagPersistService, null, null);
        dumpProcessor = new DumpProcessor(configInfoPersistService, configInfoBetaPersistService, configInfoTagPersistService);
        Field[] declaredFields = ConfigDiskServiceFactory.class.getDeclaredFields();
        for (Field filed : declaredFields) {
            if (filed.getName().equals("configDiskService")) {
                filed.setAccessible(true);
                filed.set(null, createDiskService());
            }
        }
        
    }
    
    protected ConfigDiskService createDiskService() {
        return new ConfigRocksDbDiskService();
    }
    
    @AfterEach
    void after() {
        dynamicDataSourceMockedStatic.close();
        envUtilMockedStatic.close();
        ConfigDiskServiceFactory.getInstance().clearAll();
        ConfigDiskServiceFactory.getInstance().clearAllBeta();
        ConfigDiskServiceFactory.getInstance().clearAllTag();
        
    }
    
    @Test
    void testDumpNormalAndRemove() throws IOException {
        String dataId = "testDataId";
        String group = "testGroup";
        String tenant = "testTenant";
        String content = "testContent你好" + System.currentTimeMillis();
        long time = System.currentTimeMillis();
        ConfigInfoWrapper configInfoWrapper = new ConfigInfoWrapper();
        configInfoWrapper.setDataId(dataId);
        configInfoWrapper.setGroup(group);
        configInfoWrapper.setTenant(tenant);
        configInfoWrapper.setContent(content);
        configInfoWrapper.setLastModified(time);
        
        Mockito.when(configInfoPersistService.findConfigInfo(eq(dataId), eq(group), eq(tenant))).thenReturn(configInfoWrapper);
        
        String handlerIp = "127.0.0.1";
        long lastModified = System.currentTimeMillis();
        DumpTask dumpTask = new DumpTask(GroupKey2.getKey(dataId, group, tenant), false, false, false, null, lastModified, handlerIp);
        boolean process = dumpProcessor.process(dumpTask);
        assertTrue(process);
        
        //Check cache
        CacheItem contentCache = ConfigCacheService.getContentCache(GroupKey2.getKey(dataId, group, tenant));
        assertEquals(MD5Utils.md5Hex(content, "UTF-8"), contentCache.getConfigCache().getMd5Utf8());
        assertEquals(time, contentCache.getConfigCache().getLastModifiedTs());
        //check disk
        String contentFromDisk = ConfigDiskServiceFactory.getInstance().getContent(dataId, group, tenant);
        assertEquals(content, contentFromDisk);
        
        // remove
        Mockito.when(configInfoPersistService.findConfigInfo(eq(dataId), eq(group), eq(tenant))).thenReturn(null);
        
        boolean processRemove = dumpProcessor.process(dumpTask);
        assertTrue(processRemove);
        
        //Check cache
        CacheItem contentCacheAfterRemove = ConfigCacheService.getContentCache(GroupKey2.getKey(dataId, group, tenant));
        assertTrue(contentCacheAfterRemove == null);
        //check disk
        String contentFromDiskAfterRemove = ConfigDiskServiceFactory.getInstance().getContent(dataId, group, tenant);
        assertNull(contentFromDiskAfterRemove);
        
    }
    
    @Test
    void testDumpBetaAndRemove() throws IOException {
        String dataId = "testDataIdBeta";
        String group = "testGroup";
        String tenant = "testTenant";
        String content = "testContentBeta你好" + System.currentTimeMillis();
        long time = System.currentTimeMillis();
        ConfigInfoBetaWrapper configInfoWrapper = new ConfigInfoBetaWrapper();
        configInfoWrapper.setDataId(dataId);
        configInfoWrapper.setGroup(group);
        configInfoWrapper.setTenant(tenant);
        configInfoWrapper.setContent(content);
        configInfoWrapper.setLastModified(time);
        String betaIps = "127.0.0.1123,127.0.0.11";
        configInfoWrapper.setBetaIps(betaIps);
        
        Mockito.when(configInfoBetaPersistService.findConfigInfo4Beta(eq(dataId), eq(group), eq(tenant))).thenReturn(configInfoWrapper);
        
        String handlerIp = "127.0.0.1";
        long lastModified = System.currentTimeMillis();
        DumpTask dumpTask = new DumpTask(GroupKey2.getKey(dataId, group, tenant), true, false, false, null, lastModified, handlerIp);
        boolean process = dumpProcessor.process(dumpTask);
        assertTrue(process);
        
        //Check cache
        CacheItem contentCache = ConfigCacheService.getContentCache(GroupKey2.getKey(dataId, group, tenant));
        assertEquals(MD5Utils.md5Hex(content, "UTF-8"), contentCache.getConfigCacheBeta().getMd5Utf8());
        assertEquals(time, contentCache.getConfigCacheBeta().getLastModifiedTs());
        assertTrue(contentCache.ips4Beta.containsAll(Arrays.asList(betaIps.split(","))));
        //check disk
        String contentFromDisk = ConfigDiskServiceFactory.getInstance().getBetaContent(dataId, group, tenant);
        assertEquals(content, contentFromDisk);
        
        // remove
        Mockito.when(configInfoBetaPersistService.findConfigInfo4Beta(eq(dataId), eq(group), eq(tenant))).thenReturn(null);
        boolean processRemove = dumpProcessor.process(dumpTask);
        assertTrue(processRemove);
        
        //Check cache
        CacheItem contentCacheAfterRemove = ConfigCacheService.getContentCache(GroupKey2.getKey(dataId, group, tenant));
        assertTrue(contentCacheAfterRemove == null || contentCacheAfterRemove.getConfigCacheBeta() == null);
        //check disk
        String contentFromDiskAfterRemove = ConfigDiskServiceFactory.getInstance().getBetaContent(dataId, group, tenant);
        assertNull(contentFromDiskAfterRemove);
        
    }
    
    @Test
    void testDumpTagAndRemove() throws IOException {
        String dataId = "testDataIdBeta";
        String group = "testGroup";
        String tenant = "testTenant";
        String tag = "testTag111";
        String content = "testContentBeta你好" + System.currentTimeMillis();
        long time = System.currentTimeMillis();
        ConfigInfoTagWrapper configInfoWrapper = new ConfigInfoTagWrapper();
        configInfoWrapper.setDataId(dataId);
        configInfoWrapper.setGroup(group);
        configInfoWrapper.setTenant(tenant);
        configInfoWrapper.setContent(content);
        configInfoWrapper.setLastModified(time);
        configInfoWrapper.setTag(tag);
        Mockito.when(configInfoTagPersistService.findConfigInfo4Tag(eq(dataId), eq(group), eq(tenant), eq(tag)))
                .thenReturn(configInfoWrapper);
        
        String handlerIp = "127.0.0.1";
        long lastModified = System.currentTimeMillis();
        DumpTask dumpTask = new DumpTask(GroupKey2.getKey(dataId, group, tenant), false, false, true, tag, lastModified, handlerIp);
        boolean process = dumpProcessor.process(dumpTask);
        assertTrue(process);
        
        //Check cache
        CacheItem contentCache = ConfigCacheService.getContentCache(GroupKey2.getKey(dataId, group, tenant));
        assertEquals(MD5Utils.md5Hex(content, "UTF-8"), contentCache.getConfigCacheTags().get(tag).getMd5Utf8());
        assertEquals(time, contentCache.getConfigCacheTags().get(tag).getLastModifiedTs());
        //check disk
        String contentFromDisk = ConfigDiskServiceFactory.getInstance().getTagContent(dataId, group, tenant, tag);
        assertEquals(content, contentFromDisk);
        
        // remove
        Mockito.when(configInfoTagPersistService.findConfigInfo4Tag(eq(dataId), eq(group), eq(tenant), eq(tag))).thenReturn(null);
        boolean processRemove = dumpProcessor.process(dumpTask);
        assertTrue(processRemove);
        
        //Check cache
        CacheItem contentCacheAfterRemove = ConfigCacheService.getContentCache(GroupKey2.getKey(dataId, group, tenant));
        assertTrue(contentCacheAfterRemove == null || contentCache.getConfigCacheTags() == null
                || contentCache.getConfigCacheTags().get(tag) == null);
        //check disk
        String contentFromDiskAfterRemove = ConfigDiskServiceFactory.getInstance().getTagContent(dataId, group, tenant, tag);
        assertNull(contentFromDiskAfterRemove);
        
    }
}
