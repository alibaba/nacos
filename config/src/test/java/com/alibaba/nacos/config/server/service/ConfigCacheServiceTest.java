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

package com.alibaba.nacos.config.server.service;

import com.alibaba.nacos.common.utils.MD5Utils;
import com.alibaba.nacos.config.server.model.CacheItem;
import com.alibaba.nacos.config.server.model.ConfigCacheGray;
import com.alibaba.nacos.config.server.model.gray.GrayRuleManager;
import com.alibaba.nacos.config.server.service.dump.disk.ConfigDiskService;
import com.alibaba.nacos.config.server.service.dump.disk.ConfigDiskServiceFactory;
import com.alibaba.nacos.config.server.utils.GroupKey2;
import com.alibaba.nacos.config.server.utils.PropertyUtil;
import com.alibaba.nacos.config.server.utils.SimpleReadWriteLock;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.stubbing.OngoingStubbing;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;

@ExtendWith(SpringExtension.class)
class ConfigCacheServiceTest {
    
    MockedStatic<PropertyUtil> propertyUtilMockedStatic;
    
    MockedStatic<ConfigDiskServiceFactory> configDiskServiceFactoryMockedStatic;
    
    @Mock
    ConfigDiskService configDiskService;
    
    MockedStatic<EnvUtil> envUtilMockedStatic;
    
    @BeforeEach
    void before() {
        envUtilMockedStatic = Mockito.mockStatic(EnvUtil.class);
        configDiskServiceFactoryMockedStatic = Mockito.mockStatic(ConfigDiskServiceFactory.class);
        configDiskServiceFactoryMockedStatic.when(() -> ConfigDiskServiceFactory.getInstance())
                .thenReturn(configDiskService);
        propertyUtilMockedStatic = Mockito.mockStatic(PropertyUtil.class);
    }
    
    @AfterEach
    void after() {
        envUtilMockedStatic.close();
        propertyUtilMockedStatic.close();
        configDiskServiceFactoryMockedStatic.close();
    }
    
    @Test
    void testDumpFormal() throws Exception {
        String dataId = "dataIdtestDumpMd5NewTsNewMd5123";
        String group = "group11";
        String tenant = "tenant112";
        String content = "mockContnet11";
        String md5 = "mockmd511";
        String groupKey = GroupKey2.getKey(dataId, group, tenant);
        //make sure not exist prev cache.
        CacheItem contentCache = ConfigCacheService.getContentCache(groupKey);
        assertTrue(contentCache == null);
        long ts = System.currentTimeMillis();
        String type = "json";
        String encryptedDataKey = "key12345";
        boolean result = ConfigCacheService.dumpWithMd5(dataId, group, tenant, content, md5, ts, type,
                encryptedDataKey);
        assertTrue(result);
        //verify cache.
        CacheItem contentCache1 = ConfigCacheService.getContentCache(groupKey);
        assertEquals(ts, contentCache1.getConfigCache().getLastModifiedTs());
        assertEquals(md5, contentCache1.getConfigCache().getMd5Utf8());
        assertEquals(type, contentCache1.getType());
        assertEquals(encryptedDataKey, contentCache1.getConfigCache().getEncryptedDataKey());
        Mockito.verify(configDiskService, times(1)).saveToDisk(eq(dataId), eq(group), eq(tenant), eq(content));
        
        //modified ts and content and md5
        String contentNew = content + "11";
        long newTs = System.currentTimeMillis() + 12L;
        ConfigCacheService.dump(dataId, group, tenant, contentNew, newTs, type, encryptedDataKey);
        //expect save to disk invoked.
        Mockito.verify(configDiskService, times(1)).saveToDisk(eq(dataId), eq(group), eq(tenant), eq(contentNew));
        assertEquals(newTs, contentCache1.getConfigCache().getLastModifiedTs());
        String newMd5 = MD5Utils.md5Hex(contentNew, "UTF-8");
        assertEquals(newMd5, contentCache1.getConfigCache().getMd5Utf8());
        
        //modified ts old
        long oldTs2 = newTs - 123L;
        String contentWithOldTs = contentNew + "123456";
        ConfigCacheService.dump(dataId, group, tenant, contentWithOldTs, oldTs2, type, encryptedDataKey);
        //expect save to disk invoked.
        Mockito.verify(configDiskService, times(0)).saveToDisk(eq(dataId), eq(group), eq(tenant), eq(contentWithOldTs));
        //not change ts and md5
        assertEquals(newTs, contentCache1.getConfigCache().getLastModifiedTs());
        assertEquals(newMd5, contentCache1.getConfigCache().getMd5Utf8());
        
        //modified ts new only
        long newTs2 = newTs + 123L;
        ConfigCacheService.dump(dataId, group, tenant, contentNew, newTs2, type, encryptedDataKey);
        assertEquals(newTs2, contentCache1.getConfigCache().getLastModifiedTs());
        
        //save to disk error
        doThrow(new IOException("No space left on device")).when(configDiskService)
                .saveToDisk(anyString(), anyString(), anyString(), anyString());
        try {
            long newTs3 = newTs2 + 123L;
            boolean dumpErrorResult = ConfigCacheService.dump(dataId, group, tenant, contentNew + "234567", newTs3,
                    type, encryptedDataKey);
            envUtilMockedStatic.verify(() -> EnvUtil.systemExit(), times(1));
            assertFalse(dumpErrorResult);
        } catch (Throwable throwable) {
            assertFalse(true);
        }
        
        //test remove
        boolean remove = ConfigCacheService.remove(dataId, group, tenant);
        assertTrue(remove);
        Mockito.verify(configDiskService, times(1)).removeConfigInfo(dataId, group, tenant);
        CacheItem contentCacheAfterRemove = ConfigCacheService.getContentCache(groupKey);
        assertNull(contentCacheAfterRemove);
        
    }
    
    @Test
    public void testDumpGray() throws Exception {
        String dataId = "dataIdtestDumpBetaNewCache123";
        String group = "group11";
        String tenant = "tenant112";
        String grayName = "grayName";
        String grayRule = "{\"type\":\"tag\",\"version\":\"1.0.0\",\"expr\":\"dgray123\",\"priority\":1}";
        String content = "mockContent11";
        
        String md5 = MD5Utils.md5Hex(content, "UTF-8");
        String groupKey = GroupKey2.getKey(dataId, group, tenant);
        String encryptedDataKey = "key12345";
        long ts = System.currentTimeMillis();
        //init gray cache
        boolean result = ConfigCacheService.dumpGray(dataId, group, tenant, grayName, grayRule, content, ts,
                encryptedDataKey);
        assertTrue(result);
        CacheItem contentCache = ConfigCacheService.getContentCache(groupKey);
        assertEquals(md5, contentCache.getConfigCacheGray().get(grayName).getMd5Utf8());
        assertEquals(ts, contentCache.getConfigCacheGray().get(grayName).getLastModifiedTs());
        assertEquals(encryptedDataKey, contentCache.getConfigCacheGray().get(grayName).getEncryptedDataKey());
        Mockito.verify(configDiskService, times(1))
                .saveGrayToDisk(eq(dataId), eq(group), eq(tenant), eq(grayName), eq(content));
        
        //ts newer ,md5 update
        long tsNew = System.currentTimeMillis();
        String contentNew = content + tsNew;
        String md5New = MD5Utils.md5Hex(contentNew, "UTF-8");
        boolean resultNew = ConfigCacheService.dumpGray(dataId, group, tenant, grayName, grayRule, contentNew, tsNew,
                encryptedDataKey);
        assertTrue(resultNew);
        assertEquals(md5New, contentCache.getConfigCacheGray().get(grayName).getMd5Utf8());
        assertEquals(tsNew, contentCache.getConfigCacheGray().get(grayName).getLastModifiedTs());
        assertEquals(encryptedDataKey, contentCache.getConfigCacheGray().get(grayName).getEncryptedDataKey());
        Mockito.verify(configDiskService, times(1))
                .saveGrayToDisk(eq(dataId), eq(group), eq(tenant), eq(grayName), eq(contentNew));
        
        //ts old ,md5 update
        long tsOld = tsNew - 1;
        String contentWithOldTs = "contentWithOldTs" + tsOld;
        boolean resultOld = ConfigCacheService.dumpGray(dataId, group, tenant, grayName, grayRule, contentWithOldTs,
                tsOld, encryptedDataKey);
        assertTrue(resultOld);
        assertEquals(md5New, contentCache.getConfigCacheGray().get(grayName).getMd5Utf8());
        assertEquals(tsNew, contentCache.getConfigCacheGray().get(grayName).getLastModifiedTs());
        assertEquals(encryptedDataKey, contentCache.getConfigCacheGray().get(grayName).getEncryptedDataKey());
        Mockito.verify(configDiskService, times(0))
                .saveGrayToDisk(eq(dataId), eq(group), eq(tenant), eq(grayName), eq(contentWithOldTs));
        
        //ts new ,md5 not update,grayRule changes
        long tsNew2 = tsNew + 1;
        String grayRuleNew = "{\"type\":\"tag\",\"version\":\"1.0.0\",\"expr\":\"gray1234\",\"priority\":1}";
        
        String contentWithPrev = contentNew;
        boolean resultNew2 = ConfigCacheService.dumpGray(dataId, group, tenant, grayName, grayRuleNew, contentWithPrev,
                tsNew2, encryptedDataKey);
        assertTrue(resultNew2);
        assertEquals(md5New, contentCache.getConfigCacheGray().get(grayName).getMd5Utf8());
        assertEquals(tsNew2, contentCache.getConfigCacheGray().get(grayName).getLastModifiedTs());
        assertEquals(encryptedDataKey, contentCache.getConfigCacheGray().get(grayName).getEncryptedDataKey());
        assertEquals(GrayRuleManager.constructGrayRule(GrayRuleManager.deserializeConfigGrayPersistInfo(grayRuleNew)),
                contentCache.getConfigCacheGray().get(grayName).getGrayRule());
        
        //ts new only,md5 not update,beta ips not change
        long tsNew3 = tsNew2 + 1;
        String contentWithPrev2 = contentNew;
        String grayRulePrev = grayRuleNew;
        boolean resultNew3 = ConfigCacheService.dumpGray(dataId, group, tenant, grayName, grayRulePrev,
                contentWithPrev2, tsNew3, encryptedDataKey);
        assertTrue(resultNew3);
        assertEquals(md5New, contentCache.getConfigCacheGray().get(grayName).getMd5Utf8());
        assertEquals(tsNew3, contentCache.getConfigCacheGray().get(grayName).getLastModifiedTs());
        assertEquals(encryptedDataKey, contentCache.getConfigCacheGray().get(grayName).getEncryptedDataKey());
        assertEquals(GrayRuleManager.constructGrayRule(GrayRuleManager.deserializeConfigGrayPersistInfo(grayRuleNew)),
                contentCache.getConfigCacheGray().get(grayName).getGrayRule());
        
        //ts not update,md5 not update,beta ips not change
        long tsNew4 = tsNew3;
        String contentWithPrev4 = contentNew;
        boolean resultNew4 = ConfigCacheService.dumpGray(dataId, group, tenant, grayName, grayRulePrev,
                contentWithPrev4, tsNew4, encryptedDataKey);
        assertTrue(resultNew4);
        assertEquals(md5New, contentCache.getConfigCacheGray().get(grayName).getMd5Utf8());
        assertEquals(tsNew3, contentCache.getConfigCacheGray().get(grayName).getLastModifiedTs());
        assertEquals(encryptedDataKey, contentCache.getConfigCacheGray().get(grayName).getEncryptedDataKey());
        assertEquals(GrayRuleManager.constructGrayRule(GrayRuleManager.deserializeConfigGrayPersistInfo(grayRuleNew)),
                contentCache.getConfigCacheGray().get(grayName).getGrayRule());
        
        //test remove
        boolean removeBeta = ConfigCacheService.removeGray(dataId, group, tenant, grayName);
        assertTrue(removeBeta);
        Mockito.verify(configDiskService, times(1)).removeConfigInfo4Gray(dataId, group, tenant, grayName);
        Map<String, ConfigCacheGray> grayCacheAfterRemove = ConfigCacheService.getContentCache(groupKey)
                .getConfigCacheGray();
        assertNull(grayCacheAfterRemove);
    }
    
    @Test
    void testTryConfigReadLock() throws Exception {
        String dataId = "123testTryConfigReadLock";
        String group = "1234";
        String tenant = "1234";
        CacheItem cacheItem = Mockito.mock(CacheItem.class);
        SimpleReadWriteLock lock = Mockito.mock(SimpleReadWriteLock.class);
        Mockito.when(cacheItem.getRwLock()).thenReturn(lock);
        String groupKey = GroupKey2.getKey(dataId, group, tenant);
        Field cache1 = ConfigCacheService.class.getDeclaredField("CACHE");
        cache1.setAccessible(true);
        ConcurrentHashMap<String, CacheItem> cache = (ConcurrentHashMap<String, CacheItem>) cache1.get(null);
        cache.put(groupKey, cacheItem);
        
        // lock ==0,not exist
        int readLock = ConfigCacheService.tryConfigReadLock(groupKey + "3245");
        assertEquals(0, readLock);
        
        //lock == 1 , success get lock
        Mockito.when(lock.tryReadLock()).thenReturn(true);
        int readLockSuccess = ConfigCacheService.tryConfigReadLock(groupKey);
        assertEquals(1, readLockSuccess);
        
        //lock ==-1 fail after spin all times;
        OngoingStubbing<Boolean> when = Mockito.when(lock.tryReadLock());
        for (int i = 0; i < 10; i++) {
            when = when.thenReturn(false);
        }
        int readLockFail = ConfigCacheService.tryConfigReadLock(groupKey);
        assertEquals(-1, readLockFail);
        
        //lock ==1 success after serval spin  times;
        OngoingStubbing<Boolean> when2 = Mockito.when(lock.tryReadLock());
        for (int i = 0; i < 5; i++) {
            when2 = when2.thenReturn(false);
        }
        when2.thenReturn(true);
        int readLockSuccessAfterRetry = ConfigCacheService.tryConfigReadLock(groupKey);
        assertEquals(1, readLockSuccessAfterRetry);
    }
}
