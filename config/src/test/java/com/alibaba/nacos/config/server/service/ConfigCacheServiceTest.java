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
import com.alibaba.nacos.config.server.model.ConfigCache;
import com.alibaba.nacos.config.server.service.dump.disk.ConfigDiskService;
import com.alibaba.nacos.config.server.service.dump.disk.ConfigDiskServiceFactory;
import com.alibaba.nacos.config.server.utils.GroupKey2;
import com.alibaba.nacos.config.server.utils.PropertyUtil;
import com.alibaba.nacos.config.server.utils.SimpleReadWriteLock;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.stubbing.OngoingStubbing;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;

@RunWith(SpringJUnit4ClassRunner.class)
public class ConfigCacheServiceTest {
    
    MockedStatic<PropertyUtil> propertyUtilMockedStatic;
    
    MockedStatic<ConfigDiskServiceFactory> configDiskServiceFactoryMockedStatic;
    
    @Mock
    ConfigDiskService configDiskService;
    
    MockedStatic<EnvUtil> envUtilMockedStatic;
    
    @Before
    public void before() {
        envUtilMockedStatic = Mockito.mockStatic(EnvUtil.class);
        configDiskServiceFactoryMockedStatic = Mockito.mockStatic(ConfigDiskServiceFactory.class);
        configDiskServiceFactoryMockedStatic.when(() -> ConfigDiskServiceFactory.getInstance())
                .thenReturn(configDiskService);
        propertyUtilMockedStatic = Mockito.mockStatic(PropertyUtil.class);
    }
    
    @After
    public void after() {
        envUtilMockedStatic.close();
        propertyUtilMockedStatic.close();
        configDiskServiceFactoryMockedStatic.close();
    }
    
    @Test
    public void testDumpFormal() throws Exception {
        String dataId = "dataIdtestDumpMd5NewTsNewMd5123";
        String group = "group11";
        String tenant = "tenant112";
        String content = "mockContnet11";
        String md5 = "mockmd511";
        String groupKey = GroupKey2.getKey(dataId, group, tenant);
        //make sure not exist prev cache.
        CacheItem contentCache = ConfigCacheService.getContentCache(groupKey);
        Assert.assertTrue(contentCache == null);
        long ts = System.currentTimeMillis();
        String type = "json";
        String encryptedDataKey = "key12345";
        boolean result = ConfigCacheService.dumpWithMd5(dataId, group, tenant, content, md5, ts, type,
                encryptedDataKey);
        Assert.assertTrue(result);
        //verify cache.
        CacheItem contentCache1 = ConfigCacheService.getContentCache(groupKey);
        Assert.assertEquals(ts, contentCache1.getConfigCache().getLastModifiedTs());
        Assert.assertEquals(md5, contentCache1.getConfigCache().getMd5Utf8());
        Assert.assertEquals(type, contentCache1.getType());
        Assert.assertEquals(encryptedDataKey, contentCache1.getConfigCache().getEncryptedDataKey());
        Mockito.verify(configDiskService, times(1)).saveToDisk(eq(dataId), eq(group), eq(tenant), eq(content));
        
        //modified ts and content and md5
        String contentNew = content + "11";
        long newTs = System.currentTimeMillis() + 12L;
        ConfigCacheService.dump(dataId, group, tenant, contentNew, newTs, type, encryptedDataKey);
        //expect save to disk invoked.
        Mockito.verify(configDiskService, times(1)).saveToDisk(eq(dataId), eq(group), eq(tenant), eq(contentNew));
        Assert.assertEquals(newTs, contentCache1.getConfigCache().getLastModifiedTs());
        String newMd5 = MD5Utils.md5Hex(contentNew, "UTF-8");
        Assert.assertEquals(newMd5, contentCache1.getConfigCache().getMd5Utf8());
        
        //modified ts old
        long oldTs2 = newTs - 123L;
        String contentWithOldTs = contentNew + "123456";
        ConfigCacheService.dump(dataId, group, tenant, contentWithOldTs, oldTs2, type, encryptedDataKey);
        //expect save to disk invoked.
        Mockito.verify(configDiskService, times(0)).saveToDisk(eq(dataId), eq(group), eq(tenant), eq(contentWithOldTs));
        //not change ts and md5
        Assert.assertEquals(newTs, contentCache1.getConfigCache().getLastModifiedTs());
        Assert.assertEquals(newMd5, contentCache1.getConfigCache().getMd5Utf8());
        
        //modified ts new only
        long newTs2 = newTs + 123L;
        ConfigCacheService.dump(dataId, group, tenant, contentNew, newTs2, type, encryptedDataKey);
        Assert.assertEquals(newTs2, contentCache1.getConfigCache().getLastModifiedTs());
        
        //save to disk error
        doThrow(new IOException("No space left on device")).when(configDiskService)
                .saveToDisk(anyString(), anyString(), anyString(), anyString());
        try {
            long newTs3 = newTs2 + 123L;
            boolean dumpErrorResult = ConfigCacheService.dump(dataId, group, tenant, contentNew + "234567", newTs3,
                    type, encryptedDataKey);
            envUtilMockedStatic.verify(() -> EnvUtil.systemExit(), times(1));
            Assert.assertFalse(dumpErrorResult);
        } catch (Throwable throwable) {
            Assert.assertFalse(true);
        }
        
        //test remove
        boolean remove = ConfigCacheService.remove(dataId, group, tenant);
        Assert.assertTrue(remove);
        Mockito.verify(configDiskService, times(1)).removeConfigInfo(dataId, group, tenant);
        CacheItem contentCacheAfterRemove = ConfigCacheService.getContentCache(groupKey);
        Assert.assertNull(contentCacheAfterRemove);
        
    }
    
    @Test
    public void testDumpBeta() throws Exception {
        String dataId = "dataIdtestDumpBetaNewCache123";
        String group = "group11";
        String tenant = "tenant112";
        String content = "mockContnet11";
        String md5 = MD5Utils.md5Hex(content, "UTF-8");
        String groupKey = GroupKey2.getKey(dataId, group, tenant);
        String encryptedDataKey = "key12345";
        List<String> betaIps = Arrays.asList("127.0.0.1", "127.0.0.2");
        long ts = System.currentTimeMillis();
        //init beta cache
        boolean result = ConfigCacheService.dumpBeta(dataId, group, tenant, content, ts, String.join(",", betaIps),
                encryptedDataKey);
        Assert.assertTrue(result);
        CacheItem contentCache = ConfigCacheService.getContentCache(groupKey);
        Assert.assertEquals(md5, contentCache.getConfigCacheBeta().getMd5Utf8());
        Assert.assertEquals(ts, contentCache.getConfigCacheBeta().getLastModifiedTs());
        Assert.assertEquals(betaIps, contentCache.getIps4Beta());
        Assert.assertEquals(encryptedDataKey, contentCache.getConfigCacheBeta().getEncryptedDataKey());
        Mockito.verify(configDiskService, times(1)).saveBetaToDisk(eq(dataId), eq(group), eq(tenant), eq(content));
        
        //ts newer ,md5 update
        long tsNew = System.currentTimeMillis();
        String contentNew = content + tsNew;
        String md5New = MD5Utils.md5Hex(contentNew, "UTF-8");
        List<String> betaIpsNew = Arrays.asList("127.0.0.1", "127.0.0.2", "127.0.0.3");
        boolean resultNew = ConfigCacheService.dumpBeta(dataId, group, tenant, contentNew, tsNew,
                String.join(",", betaIpsNew), encryptedDataKey);
        Assert.assertTrue(resultNew);
        Assert.assertEquals(md5New, contentCache.getConfigCacheBeta().getMd5Utf8());
        Assert.assertEquals(tsNew, contentCache.getConfigCacheBeta().getLastModifiedTs());
        Assert.assertEquals(encryptedDataKey, contentCache.getConfigCacheBeta().getEncryptedDataKey());
        Assert.assertEquals(betaIpsNew, contentCache.getIps4Beta());
        Mockito.verify(configDiskService, times(1)).saveBetaToDisk(eq(dataId), eq(group), eq(tenant), eq(contentNew));
        
        //ts old ,md5 update
        long tsOld = tsNew - 1;
        String contentWithOldTs = "contentWithOldTs" + tsOld;
        List<String> betaIpsWithOldTs = Arrays.asList("127.0.0.1", "127.0.0.2", "127.0.0.4");
        boolean resultOld = ConfigCacheService.dumpBeta(dataId, group, tenant, contentWithOldTs, tsOld,
                String.join(",", betaIpsWithOldTs), encryptedDataKey);
        Assert.assertTrue(resultOld);
        Assert.assertEquals(md5New, contentCache.getConfigCacheBeta().getMd5Utf8());
        Assert.assertEquals(tsNew, contentCache.getConfigCacheBeta().getLastModifiedTs());
        Assert.assertEquals(encryptedDataKey, contentCache.getConfigCacheBeta().getEncryptedDataKey());
        Assert.assertEquals(betaIpsNew, contentCache.getIps4Beta());
        Mockito.verify(configDiskService, times(0))
                .saveBetaToDisk(eq(dataId), eq(group), eq(tenant), eq(contentWithOldTs));
        
        //ts new ,md5 not update,beta ips list changes
        long tsNew2 = tsNew + 1;
        String contentWithPrev = contentNew;
        List<String> betaIpsNew2 = Arrays.asList("127.0.0.1", "127.0.0.2", "127.0.0.4", "127.0.0.5");
        boolean resultNew2 = ConfigCacheService.dumpBeta(dataId, group, tenant, contentWithPrev, tsNew2,
                String.join(",", betaIpsNew2), encryptedDataKey);
        Assert.assertTrue(resultNew2);
        Assert.assertEquals(md5New, contentCache.getConfigCacheBeta().getMd5Utf8());
        Assert.assertEquals(tsNew2, contentCache.getConfigCacheBeta().getLastModifiedTs());
        Assert.assertEquals(encryptedDataKey, contentCache.getConfigCacheBeta().getEncryptedDataKey());
        Assert.assertEquals(betaIpsNew2, contentCache.getIps4Beta());
        
        //ts new only,md5 not update,beta ips not change
        long tsNew3 = tsNew2 + 1;
        String contentWithPrev2 = contentNew;
        List<String> betaIpsNew3 = betaIpsNew2;
        boolean resultNew3 = ConfigCacheService.dumpBeta(dataId, group, tenant, contentWithPrev2, tsNew3,
                String.join(",", betaIpsNew3), encryptedDataKey);
        Assert.assertTrue(resultNew3);
        Assert.assertEquals(md5New, contentCache.getConfigCacheBeta().getMd5Utf8());
        Assert.assertEquals(tsNew3, contentCache.getConfigCacheBeta().getLastModifiedTs());
        Assert.assertEquals(encryptedDataKey, contentCache.getConfigCacheBeta().getEncryptedDataKey());
        Assert.assertEquals(betaIpsNew2, contentCache.getIps4Beta());
        
        //ts not update,md5 not update,beta ips not change
        long tsNew4 = tsNew3;
        String contentWithPrev4 = contentNew;
        List<String> betaIpsNew4 = betaIpsNew2;
        boolean resultNew4 = ConfigCacheService.dumpBeta(dataId, group, tenant, contentWithPrev4, tsNew4,
                String.join(",", betaIpsNew4), encryptedDataKey);
        Assert.assertTrue(resultNew4);
        Assert.assertEquals(md5New, contentCache.getConfigCacheBeta().getMd5Utf8());
        Assert.assertEquals(tsNew3, contentCache.getConfigCacheBeta().getLastModifiedTs());
        Assert.assertEquals(encryptedDataKey, contentCache.getConfigCacheBeta().getEncryptedDataKey());
        Assert.assertEquals(betaIpsNew4, contentCache.getIps4Beta());
        
        //test remove
        boolean removeBeta = ConfigCacheService.removeBeta(dataId, group, tenant);
        Assert.assertTrue(removeBeta);
        Mockito.verify(configDiskService, times(1)).removeConfigInfo4Beta(dataId, group, tenant);
        ConfigCache betaCacheAfterRemove = ConfigCacheService.getContentCache(groupKey).getConfigCacheBeta();
        Assert.assertNull(betaCacheAfterRemove);
    }
    
    @Test
    public void testDumpTag() throws Exception {
        String dataId = "dataIdtestDumpTag133323";
        String group = "group11";
        String tenant = "tenant112";
        String content = "mockContnet11";
        String tag = "tag12345";
        String groupKey = GroupKey2.getKey(dataId, group, tenant);
        String encryptedDataKey = "key12345";
        long ts = System.currentTimeMillis();
        
        //init dump tag
        boolean dumpTagResult = ConfigCacheService.dumpTag(dataId, group, tenant, tag, content, ts, encryptedDataKey);
        Assert.assertTrue(dumpTagResult);
        Mockito.verify(configDiskService, times(1))
                .saveTagToDisk(eq(dataId), eq(group), eq(tenant), eq(tag), eq(content));
        CacheItem contentCache = ConfigCacheService.getContentCache(groupKey);
        ConfigCache configCacheTag = contentCache.getConfigCacheTags().get(tag);
        Assert.assertEquals(ts, configCacheTag.getLastModifiedTs());
        String md5 = MD5Utils.md5Hex(content, "UTF-8");
        Assert.assertEquals(md5, configCacheTag.getMd5Utf8());
        
        //ts newer ,md5 update
        long tsNew = System.currentTimeMillis();
        String contentNew = content + tsNew;
        String md5New = MD5Utils.md5Hex(contentNew, "UTF-8");
        boolean resultNew = ConfigCacheService.dumpTag(dataId, group, tenant, tag, contentNew, tsNew, encryptedDataKey);
        Assert.assertTrue(resultNew);
        Assert.assertEquals(md5New, configCacheTag.getMd5Utf8());
        Assert.assertEquals(tsNew, configCacheTag.getLastModifiedTs());
        Assert.assertEquals(encryptedDataKey, configCacheTag.getEncryptedDataKey());
        Mockito.verify(configDiskService, times(1))
                .saveTagToDisk(eq(dataId), eq(group), eq(tenant), eq(tag), eq(contentNew));
        
        //ts old ,md5 update
        long tsOld = tsNew - 1;
        String contentWithOldTs = "contentWithOldTs" + tsOld;
        boolean resultOld = ConfigCacheService.dumpTag(dataId, group, tenant, tag, contentWithOldTs, tsOld,
                encryptedDataKey);
        Assert.assertTrue(resultOld);
        Assert.assertEquals(md5New, configCacheTag.getMd5Utf8());
        Assert.assertEquals(tsNew, configCacheTag.getLastModifiedTs());
        Assert.assertEquals(encryptedDataKey, configCacheTag.getEncryptedDataKey());
        Mockito.verify(configDiskService, times(0))
                .saveTagToDisk(eq(dataId), eq(group), eq(tenant), eq(tag), eq(contentWithOldTs));
        
        //ts new only,md5 not update
        long tsNew2 = tsNew + 1;
        String contentWithPrev2 = contentNew;
        boolean resultNew2 = ConfigCacheService.dumpTag(dataId, group, tenant, tag, contentWithPrev2, tsNew2,
                encryptedDataKey);
        Assert.assertTrue(resultNew2);
        Assert.assertEquals(md5New, configCacheTag.getMd5Utf8());
        Assert.assertEquals(tsNew2, configCacheTag.getLastModifiedTs());
        Assert.assertEquals(encryptedDataKey, configCacheTag.getEncryptedDataKey());
        
        //ts not update,md5 not update
        long tsNew3 = tsNew2;
        String contentWithPrev3 = contentNew;
        boolean resultNew3 = ConfigCacheService.dumpTag(dataId, group, tenant, tag, contentWithPrev3, tsNew3,
                encryptedDataKey);
        Assert.assertTrue(resultNew3);
        Assert.assertEquals(md5New, configCacheTag.getMd5Utf8());
        Assert.assertEquals(tsNew3, configCacheTag.getLastModifiedTs());
        Assert.assertEquals(encryptedDataKey, configCacheTag.getEncryptedDataKey());
        
        //test remove
        boolean removeTag = ConfigCacheService.removeTag(dataId, group, tenant, tag);
        Assert.assertTrue(removeTag);
        Mockito.verify(configDiskService, times(1)).removeConfigInfo4Tag(dataId, group, tenant, tag);
        Map<String, ConfigCache> configCacheTags = ConfigCacheService.getContentCache(groupKey).getConfigCacheTags();
        Assert.assertNull(configCacheTags);
    }
    
    @Test
    public void testTryConfigReadLock() throws Exception {
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
        Assert.assertEquals(0, readLock);
        
        //lock == 1 , success get lock
        Mockito.when(lock.tryReadLock()).thenReturn(true);
        int readLockSuccess = ConfigCacheService.tryConfigReadLock(groupKey);
        Assert.assertEquals(1, readLockSuccess);
        
        //lock ==-1 fail after spin all times;
        OngoingStubbing<Boolean> when = Mockito.when(lock.tryReadLock());
        for (int i = 0; i < 10; i++) {
            when = when.thenReturn(false);
        }
        int readLockFail = ConfigCacheService.tryConfigReadLock(groupKey);
        Assert.assertEquals(-1, readLockFail);
        
        //lock ==1 success after serval spin  times;
        OngoingStubbing<Boolean> when2 = Mockito.when(lock.tryReadLock());
        for (int i = 0; i < 5; i++) {
            when2 = when2.thenReturn(false);
        }
        when2.thenReturn(true);
        int readLockSuccessAfterRetry = ConfigCacheService.tryConfigReadLock(groupKey);
        Assert.assertEquals(1, readLockSuccessAfterRetry);
    }
}
