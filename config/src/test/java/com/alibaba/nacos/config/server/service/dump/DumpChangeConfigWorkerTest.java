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
import com.alibaba.nacos.config.server.model.ConfigInfoStateWrapper;
import com.alibaba.nacos.config.server.model.ConfigInfoWrapper;
import com.alibaba.nacos.config.server.service.ConfigCacheService;
import com.alibaba.nacos.config.server.service.dump.disk.ConfigDiskService;
import com.alibaba.nacos.config.server.service.dump.disk.ConfigDiskServiceFactory;
import com.alibaba.nacos.config.server.service.dump.disk.ConfigRocksDbDiskService;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoPersistService;
import com.alibaba.nacos.config.server.service.repository.HistoryConfigInfoPersistService;
import com.alibaba.nacos.config.server.utils.GroupKey;
import com.alibaba.nacos.config.server.utils.PropertyUtil;
import com.alibaba.nacos.persistence.datasource.DataSourceService;
import com.alibaba.nacos.persistence.datasource.DynamicDataSource;
import com.alibaba.nacos.plugin.datasource.constants.CommonConstant;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.File;
import java.lang.reflect.Field;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class DumpChangeConfigWorkerTest {
    
    @Mock
    DynamicDataSource dynamicDataSource;
    
    @Mock
    DataSourceService dataSourceService;
    
    @Mock
    ConfigInfoPersistService configInfoPersistService;
    
    @Mock
    HistoryConfigInfoPersistService historyConfigInfoPersistService;
    
    DumpChangeConfigWorker dumpChangeConfigWorker;
    
    MockedStatic<DynamicDataSource> dynamicDataSourceMockedStatic;
    
    MockedStatic<EnvUtil> envUtilMockedStatic;
    
    @Before
    public void init() throws Exception {
        dynamicDataSourceMockedStatic = Mockito.mockStatic(DynamicDataSource.class);
        envUtilMockedStatic = Mockito.mockStatic(EnvUtil.class);
        when(EnvUtil.getNacosHome()).thenReturn(System.getProperty("user.home") + File.separator + "tmp");
        when(EnvUtil.getProperty(eq(CommonConstant.NACOS_PLUGIN_DATASOURCE_LOG), eq(Boolean.class),
                eq(false))).thenReturn(false);
        dynamicDataSourceMockedStatic.when(DynamicDataSource::getInstance).thenReturn(dynamicDataSource);
        
        Field[] declaredFields = ConfigDiskServiceFactory.class.getDeclaredFields();
        for (Field filed : declaredFields) {
            if (filed.getName().equals("configDiskService")) {
                filed.setAccessible(true);
                filed.set(null, createDiskService());
            }
        }
        
        dumpChangeConfigWorker = new DumpChangeConfigWorker(configInfoPersistService, historyConfigInfoPersistService,
                new Timestamp(System.currentTimeMillis()));
    }
    
    protected ConfigDiskService createDiskService() {
        return new ConfigRocksDbDiskService();
    }
    
    @After
    public void after() throws IllegalAccessException {
        dynamicDataSourceMockedStatic.close();
        envUtilMockedStatic.close();
        ConfigDiskServiceFactory.getInstance().clearAll();
        ConfigDiskServiceFactory.getInstance().clearAllBeta();
        ConfigDiskServiceFactory.getInstance().clearAllTag();
        
        Field[] declaredFields = ConfigDiskServiceFactory.class.getDeclaredFields();
        for (Field filed : declaredFields) {
            if (filed.getName().equals("configDiskService")) {
                filed.setAccessible(true);
                filed.set(null, null);
            }
        }
    }
    
    @Test
    public void testDumpChangeIfOff() {
        PropertyUtil.setDumpChangeOn(false);
        dumpChangeConfigWorker.run();
        Mockito.verify(historyConfigInfoPersistService, times(0)).findDeletedConfig(any(), anyLong(), anyInt());
    }
    
    @Test
    public void testDumpChangeOfDeleteConfigs() {
        PropertyUtil.setDumpChangeOn(true);
        dumpChangeConfigWorker.setPageSize(3);
        //mock delete first page
        List<ConfigInfoStateWrapper> firstPageDeleted = new ArrayList<>();
        Timestamp startTime = dumpChangeConfigWorker.startTime;
        String dataIdPrefix = "d12345";
        
        firstPageDeleted.add(createConfigInfoStateWrapper(dataIdPrefix, 1, startTime.getTime() + 1));
        firstPageDeleted.add(createConfigInfoStateWrapper(dataIdPrefix, 2, startTime.getTime() + 2));
        firstPageDeleted.add(createConfigInfoStateWrapper(dataIdPrefix, 3, startTime.getTime() + 3));
        //pre set cache for id1
        preSetCache(dataIdPrefix, 1, System.currentTimeMillis());
        Assert.assertEquals("encrykey" + 1,
                ConfigCacheService.getContentCache(GroupKey.getKeyTenant(dataIdPrefix + 1, "group" + 1, "tenant" + 1))
                        .getConfigCache().getEncryptedDataKey());
        Mockito.when(historyConfigInfoPersistService.findDeletedConfig(eq(startTime), eq(0L), eq(3)))
                .thenReturn(firstPageDeleted);
        //mock delete config query is null
        Mockito.when(
                        configInfoPersistService.findConfigInfoState(eq(dataIdPrefix + 1), eq("group" + 1), eq("tenant" + 1)))
                .thenReturn(null);
        Mockito.when(
                        configInfoPersistService.findConfigInfoState(eq(dataIdPrefix + 2), eq("group" + 2), eq("tenant" + 2)))
                .thenReturn(null);
        dumpChangeConfigWorker.run();
        
        //expect delete page return pagesize and will select second page
        Mockito.verify(historyConfigInfoPersistService, times(1)).findDeletedConfig(eq(startTime), eq(3L), eq(3));
        //expect cache to be cleared.
        Assert.assertNull(
                ConfigCacheService.getContentCache(GroupKey.getKeyTenant(dataIdPrefix + 1, "group" + 1, "tenant" + 1)));
    }
    
    @Test
    public void testDumpChangeOfChangedConfigsNewTimestampOverride() {
        PropertyUtil.setDumpChangeOn(true);
        dumpChangeConfigWorker.setPageSize(3);
        //mock delete first page
        
        Timestamp startTime = dumpChangeConfigWorker.startTime;
        String dataIdPrefix = "dataId6789087";
        //pre set cache for id1 with old timestamp
        preSetCache(dataIdPrefix, 1, startTime.getTime() - 1);
        
        Assert.assertEquals(startTime.getTime() - 1,
                ConfigCacheService.getContentCache(GroupKey.getKeyTenant(dataIdPrefix + 1, "group" + 1, "tenant" + 1))
                        .getConfigCache().getLastModifiedTs());
        List<ConfigInfoStateWrapper> firstChanged = new ArrayList<>();
        firstChanged.add(createConfigInfoStateWrapper(dataIdPrefix, 1, startTime.getTime() + 1));
        
        Mockito.when(configInfoPersistService.findChangeConfig(eq(startTime), eq(0L), eq(3))).thenReturn(firstChanged);
        
        //mock change config query obj
        //1 timestamp-new&content-new
        ConfigInfoWrapper configInfoWrapperNewForId1 = createConfigInfoWrapper(dataIdPrefix, 1,
                startTime.getTime() + 2);
        configInfoWrapperNewForId1.setContent("content" + System.currentTimeMillis());
        Mockito.when(configInfoPersistService.findConfigInfo(eq(dataIdPrefix + 1), eq("group" + 1), eq("tenant" + 1)))
                .thenReturn(configInfoWrapperNewForId1);
        
        dumpChangeConfigWorker.run();
        
        //expect cache to be cleared.
        Assert.assertEquals(startTime.getTime() + 2,
                ConfigCacheService.getContentCache(GroupKey.getKeyTenant(dataIdPrefix + 1, "group" + 1, "tenant" + 1))
                        .getConfigCache().getLastModifiedTs());
        Assert.assertEquals(MD5Utils.md5Hex(configInfoWrapperNewForId1.getContent(), "UTF-8"),
                ConfigCacheService.getContentCache(GroupKey.getKeyTenant(dataIdPrefix + 1, "group" + 1, "tenant" + 1))
                        .getConfigCache().getMd5Utf8());
    }
    
    @Test
    public void testDumpChangeOfChangedConfigsNewTimestampEqualMd5() {
        PropertyUtil.setDumpChangeOn(true);
        dumpChangeConfigWorker.setPageSize(3);
        //mock delete first page
        
        Timestamp startTime = dumpChangeConfigWorker.startTime;
        String dataIdPrefix = "dataIdnewtimestamp";
        //pre set cache for id1 with old timestamp
        preSetCache(dataIdPrefix, 1, startTime.getTime() - 1);
        
        Assert.assertEquals(startTime.getTime() - 1,
                ConfigCacheService.getContentCache(GroupKey.getKeyTenant(dataIdPrefix + 1, "group" + 1, "tenant" + 1))
                        .getConfigCache().getLastModifiedTs());
        List<ConfigInfoStateWrapper> firstChanged = new ArrayList<>();
        firstChanged.add(createConfigInfoStateWrapper(dataIdPrefix, 1, startTime.getTime() + 1));
        
        Mockito.when(configInfoPersistService.findChangeConfig(eq(startTime), eq(0L), eq(3))).thenReturn(firstChanged);
        
        //mock change config query obj
        //1 timestamp-new&content-old
        ConfigInfoWrapper configInfoWrapperNewForId1 = createConfigInfoWrapper(dataIdPrefix, 1,
                startTime.getTime() + 2);
        Mockito.when(configInfoPersistService.findConfigInfo(eq(dataIdPrefix + 1), eq("group" + 1), eq("tenant" + 1)))
                .thenReturn(configInfoWrapperNewForId1);
        
        dumpChangeConfigWorker.run();
        
        //expect cache
        Assert.assertEquals(startTime.getTime() + 2,
                ConfigCacheService.getContentCache(GroupKey.getKeyTenant(dataIdPrefix + 1, "group" + 1, "tenant" + 1))
                        .getConfigCache().getLastModifiedTs());
        Assert.assertEquals(MD5Utils.md5Hex(configInfoWrapperNewForId1.getContent(), "UTF-8"),
                ConfigCacheService.getContentCache(GroupKey.getKeyTenant(dataIdPrefix + 1, "group" + 1, "tenant" + 1))
                        .getConfigCache().getMd5Utf8());
        
    }
    
    @Test
    public void testDumpChangeOfChangedConfigsOldTimestamp() {
        PropertyUtil.setDumpChangeOn(true);
        dumpChangeConfigWorker.setPageSize(3);
        //mock delete first page
        
        Timestamp startTime = dumpChangeConfigWorker.startTime;
        String dataIdPrefix = "dataIdOldTimestamp";
        
        //pre set cache for id1 with old timestamp
        preSetCache(dataIdPrefix, 1, startTime.getTime() - 1);
        
        Assert.assertEquals(startTime.getTime() - 1,
                ConfigCacheService.getContentCache(GroupKey.getKeyTenant(dataIdPrefix + 1, "group" + 1, "tenant" + 1))
                        .getConfigCache().getLastModifiedTs());
        List<ConfigInfoStateWrapper> firstChanged = new ArrayList<>();
        firstChanged.add(createConfigInfoStateWrapper(dataIdPrefix, 1, startTime.getTime() - 2));
        
        Mockito.when(configInfoPersistService.findChangeConfig(eq(startTime), eq(0L), eq(3))).thenReturn(firstChanged);
        
        //mock change config query obj
        //1 timestamp-new&content-new
        ConfigInfoWrapper configInfoWrapperNewForId1 = createConfigInfoWrapper(dataIdPrefix, 1,
                startTime.getTime() - 2);
        configInfoWrapperNewForId1.setContent("content" + System.currentTimeMillis());
        Mockito.when(configInfoPersistService.findConfigInfo(eq(dataIdPrefix + 1), eq("group" + 1), eq("tenant" + 1)))
                .thenReturn(configInfoWrapperNewForId1);
        
        dumpChangeConfigWorker.run();
        
        //expect cache to be cleared.
        Assert.assertEquals(startTime.getTime() - 1,
                ConfigCacheService.getContentCache(GroupKey.getKeyTenant(dataIdPrefix + 1, "group" + 1, "tenant" + 1))
                        .getConfigCache().getLastModifiedTs());
        Assert.assertEquals(MD5Utils.md5Hex("content" + 1, "UTF-8"),
                ConfigCacheService.getContentCache(GroupKey.getKeyTenant(dataIdPrefix + 1, "group" + 1, "tenant" + 1))
                        .getConfigCache().getMd5Utf8());
        
    }
    
    @Test
    public void testDumpChangeOfChangedConfigsEqualsTimestampMd5Update() {
        PropertyUtil.setDumpChangeOn(true);
        dumpChangeConfigWorker.setPageSize(3);
        //mock delete first page
        
        Timestamp startTime = dumpChangeConfigWorker.startTime;
        String dataIdPrefix = "dataIdEqualsTimestampMd5Update";
        
        //pre set cache for id1 with old timestamp
        preSetCache(dataIdPrefix, 1, startTime.getTime() - 1);
        
        Assert.assertEquals(startTime.getTime() - 1,
                ConfigCacheService.getContentCache(GroupKey.getKeyTenant(dataIdPrefix + 1, "group" + 1, "tenant" + 1))
                        .getConfigCache().getLastModifiedTs());
        List<ConfigInfoStateWrapper> firstChanged = new ArrayList<>();
        firstChanged.add(createConfigInfoStateWrapper(dataIdPrefix, 1, startTime.getTime() - 1));
        
        Mockito.when(configInfoPersistService.findChangeConfig(eq(startTime), eq(0L), eq(3))).thenReturn(firstChanged);
        
        //mock change config query obj
        //1 timestamp-new&content-new
        ConfigInfoWrapper configInfoWrapperNewForId1 = createConfigInfoWrapper(dataIdPrefix, 1,
                startTime.getTime() - 1);
        configInfoWrapperNewForId1.setContent("content" + System.currentTimeMillis());
        Mockito.when(configInfoPersistService.findConfigInfo(eq(dataIdPrefix + 1), eq("group" + 1), eq("tenant" + 1)))
                .thenReturn(configInfoWrapperNewForId1);
        
        dumpChangeConfigWorker.run();
        
        //expect cache to be cleared.
        Assert.assertEquals(startTime.getTime() - 1,
                ConfigCacheService.getContentCache(GroupKey.getKeyTenant(dataIdPrefix + 1, "group" + 1, "tenant" + 1))
                        .getConfigCache().getLastModifiedTs());
        Assert.assertEquals(MD5Utils.md5Hex(configInfoWrapperNewForId1.getContent(), "UTF-8"),
                ConfigCacheService.getContentCache(GroupKey.getKeyTenant(dataIdPrefix + 1, "group" + 1, "tenant" + 1))
                        .getConfigCache().getMd5Utf8());
        
    }
    
    private void preSetCache(String dataIdPrefix, long id, long timeStamp) {
        ConfigCacheService.dumpWithMd5(dataIdPrefix + id, "group" + id, "tenant" + id, "content" + id,
                MD5Utils.md5Hex("content" + id, "UTF-8"), timeStamp, "json", "encrykey" + id);
    }
    
    private ConfigInfoStateWrapper createConfigInfoStateWrapper(String dataIdPreFix, long id, long timeStamp) {
        ConfigInfoStateWrapper configInfoWrapper = new ConfigInfoStateWrapper();
        configInfoWrapper.setDataId(dataIdPreFix + id);
        configInfoWrapper.setGroup("group" + id);
        configInfoWrapper.setTenant("md5" + id);
        configInfoWrapper.setTenant("tenant" + id);
        configInfoWrapper.setId(id);
        configInfoWrapper.setLastModified(timeStamp);
        return configInfoWrapper;
    }
    
    private ConfigInfoWrapper createConfigInfoWrapper(String dataIdPreFix, long id, long timeStamp) {
        ConfigInfoWrapper configInfoWrapper = new ConfigInfoWrapper();
        configInfoWrapper.setDataId(dataIdPreFix + id);
        configInfoWrapper.setGroup("group" + id);
        configInfoWrapper.setMd5("md5" + id);
        configInfoWrapper.setContent("content" + id);
        configInfoWrapper.setTenant("tenant" + id);
        configInfoWrapper.setId(id);
        configInfoWrapper.setLastModified(timeStamp);
        return configInfoWrapper;
    }
}
