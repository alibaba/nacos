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
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.stubbing.OngoingStubbing;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.alibaba.nacos.config.server.constant.Constants.NULL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
    
    @MockitoBean
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
        boolean result =
            ConfigCacheService.dumpWithMd5(dataId, group, tenant, content, md5, ts, type,
                encryptedDataKey);
        assertTrue(result);
        //verify cache.
        CacheItem contentCache1 = ConfigCacheService.getContentCache(groupKey);
        assertEquals(ts, contentCache1.getConfigCache().getLastModifiedTs());
        assertEquals(md5, contentCache1.getConfigCache().getMd5());
        assertEquals(type, contentCache1.getType());
        assertEquals(encryptedDataKey, contentCache1.getConfigCache().getEncryptedDataKey());
        Mockito.verify(configDiskService, times(1)).saveToDisk(eq(dataId), eq(group), eq(tenant),
            eq(content));
        
        //modified ts and content and md5
        String contentNew = content + "11";
        long newTs = System.currentTimeMillis() + 12L;
        ConfigCacheService.dump(dataId, group, tenant, contentNew, newTs, type, encryptedDataKey);
        //expect save to disk invoked.
        Mockito.verify(configDiskService, times(1)).saveToDisk(eq(dataId), eq(group), eq(tenant),
            eq(contentNew));
        assertEquals(newTs, contentCache1.getConfigCache().getLastModifiedTs());
        String newMd5 = MD5Utils.md5Hex(contentNew, "UTF-8");
        assertEquals(newMd5, contentCache1.getConfigCache().getMd5());
        
        //modified ts old
        long oldTs2 = newTs - 123L;
        String contentWithOldTs = contentNew + "123456";
        ConfigCacheService.dump(dataId, group, tenant, contentWithOldTs, oldTs2, type,
            encryptedDataKey);
        //expect save to disk invoked.
        Mockito.verify(configDiskService, times(0)).saveToDisk(eq(dataId), eq(group), eq(tenant),
            eq(contentWithOldTs));
        //not change ts and md5
        assertEquals(newTs, contentCache1.getConfigCache().getLastModifiedTs());
        assertEquals(newMd5, contentCache1.getConfigCache().getMd5());
        
        //modified ts new only
        long newTs2 = newTs + 123L;
        ConfigCacheService.dump(dataId, group, tenant, contentNew, newTs2, type, encryptedDataKey);
        assertEquals(newTs2, contentCache1.getConfigCache().getLastModifiedTs());
        
        //save to disk error
        doThrow(new IOException("No space left on device")).when(configDiskService)
            .saveToDisk(anyString(), anyString(), anyString(), anyString());
        try {
            long newTs3 = newTs2 + 123L;
            boolean dumpErrorResult =
                ConfigCacheService.dump(dataId, group, tenant, contentNew + "234567", newTs3,
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
        String grayRule =
            "{\"type\":\"tag\",\"version\":\"1.0.0\",\"expr\":\"dgray123\",\"priority\":1}";
        String content = "mockContent11";
        
        String md5 = MD5Utils.md5Hex(content, "UTF-8");
        String groupKey = GroupKey2.getKey(dataId, group, tenant);
        String encryptedDataKey = "key12345";
        long ts = System.currentTimeMillis();
        //init gray cache
        boolean result =
            ConfigCacheService.dumpGray(dataId, group, tenant, grayName, grayRule, content, ts,
                encryptedDataKey);
        assertTrue(result);
        CacheItem contentCache = ConfigCacheService.getContentCache(groupKey);
        assertEquals(md5, contentCache.getConfigCacheGray().get(grayName).getMd5());
        assertEquals(ts, contentCache.getConfigCacheGray().get(grayName).getLastModifiedTs());
        assertEquals(encryptedDataKey,
            contentCache.getConfigCacheGray().get(grayName).getEncryptedDataKey());
        Mockito.verify(configDiskService, times(1))
            .saveGrayToDisk(eq(dataId), eq(group), eq(tenant), eq(grayName), eq(content));
        
        //ts newer ,md5 update
        long tsNew = System.currentTimeMillis();
        String contentNew = content + tsNew;
        String md5New = MD5Utils.md5Hex(contentNew, "UTF-8");
        boolean resultNew = ConfigCacheService.dumpGray(dataId, group, tenant, grayName, grayRule,
            contentNew, tsNew,
            encryptedDataKey);
        assertTrue(resultNew);
        assertEquals(md5New, contentCache.getConfigCacheGray().get(grayName).getMd5());
        assertEquals(tsNew, contentCache.getConfigCacheGray().get(grayName).getLastModifiedTs());
        assertEquals(encryptedDataKey,
            contentCache.getConfigCacheGray().get(grayName).getEncryptedDataKey());
        Mockito.verify(configDiskService, times(1))
            .saveGrayToDisk(eq(dataId), eq(group), eq(tenant), eq(grayName), eq(contentNew));
        
        //ts old ,md5 update
        long tsOld = tsNew - 1;
        String contentWithOldTs = "contentWithOldTs" + tsOld;
        boolean resultOld =
            ConfigCacheService.dumpGray(dataId, group, tenant, grayName, grayRule, contentWithOldTs,
                tsOld, encryptedDataKey);
        assertTrue(resultOld);
        assertEquals(md5New, contentCache.getConfigCacheGray().get(grayName).getMd5());
        assertEquals(tsNew, contentCache.getConfigCacheGray().get(grayName).getLastModifiedTs());
        assertEquals(encryptedDataKey,
            contentCache.getConfigCacheGray().get(grayName).getEncryptedDataKey());
        Mockito.verify(configDiskService, times(0))
            .saveGrayToDisk(eq(dataId), eq(group), eq(tenant), eq(grayName), eq(contentWithOldTs));
        
        //ts new ,md5 not update,grayRule changes
        long tsNew2 = tsNew + 1;
        String grayRuleNew =
            "{\"type\":\"tag\",\"version\":\"1.0.0\",\"expr\":\"gray1234\",\"priority\":1}";
        
        String contentWithPrev = contentNew;
        boolean resultNew2 = ConfigCacheService.dumpGray(dataId, group, tenant, grayName,
            grayRuleNew, contentWithPrev,
            tsNew2, encryptedDataKey);
        assertTrue(resultNew2);
        assertEquals(md5New, contentCache.getConfigCacheGray().get(grayName).getMd5());
        assertEquals(tsNew2, contentCache.getConfigCacheGray().get(grayName).getLastModifiedTs());
        assertEquals(encryptedDataKey,
            contentCache.getConfigCacheGray().get(grayName).getEncryptedDataKey());
        assertEquals(
            GrayRuleManager
                .constructGrayRule(GrayRuleManager.deserializeConfigGrayPersistInfo(grayRuleNew)),
            contentCache.getConfigCacheGray().get(grayName).getGrayRule());
        
        //ts new only,md5 not update,beta ips not change
        long tsNew3 = tsNew2 + 1;
        String contentWithPrev2 = contentNew;
        String grayRulePrev = grayRuleNew;
        boolean resultNew3 =
            ConfigCacheService.dumpGray(dataId, group, tenant, grayName, grayRulePrev,
                contentWithPrev2, tsNew3, encryptedDataKey);
        assertTrue(resultNew3);
        assertEquals(md5New, contentCache.getConfigCacheGray().get(grayName).getMd5());
        assertEquals(tsNew3, contentCache.getConfigCacheGray().get(grayName).getLastModifiedTs());
        assertEquals(encryptedDataKey,
            contentCache.getConfigCacheGray().get(grayName).getEncryptedDataKey());
        assertEquals(
            GrayRuleManager
                .constructGrayRule(GrayRuleManager.deserializeConfigGrayPersistInfo(grayRuleNew)),
            contentCache.getConfigCacheGray().get(grayName).getGrayRule());
        
        //ts not update,md5 not update,beta ips not change
        long tsNew4 = tsNew3;
        String contentWithPrev4 = contentNew;
        boolean resultNew4 =
            ConfigCacheService.dumpGray(dataId, group, tenant, grayName, grayRulePrev,
                contentWithPrev4, tsNew4, encryptedDataKey);
        assertTrue(resultNew4);
        assertEquals(md5New, contentCache.getConfigCacheGray().get(grayName).getMd5());
        assertEquals(tsNew3, contentCache.getConfigCacheGray().get(grayName).getLastModifiedTs());
        assertEquals(encryptedDataKey,
            contentCache.getConfigCacheGray().get(grayName).getEncryptedDataKey());
        assertEquals(
            GrayRuleManager
                .constructGrayRule(GrayRuleManager.deserializeConfigGrayPersistInfo(grayRuleNew)),
            contentCache.getConfigCacheGray().get(grayName).getGrayRule());
        
        //test remove
        boolean removeBeta = ConfigCacheService.removeGray(dataId, group, tenant, grayName);
        assertTrue(removeBeta);
        Mockito.verify(configDiskService, times(1)).removeConfigInfo4Gray(dataId, group, tenant,
            grayName);
        Map<String, ConfigCacheGray> grayCacheAfterRemove =
            ConfigCacheService.getContentCache(groupKey)
                .getConfigCacheGray();
        assertNull(grayCacheAfterRemove);
    }
    
    @Test
    void testGetContentMd5WithIpAndTag() {
        String dataId = "testMd5IpTag";
        String group = "g1";
        String tenant = "t1";
        String content = "content";
        String groupKey = GroupKey2.getKey(dataId, group, tenant);
        long ts = System.currentTimeMillis();
        String md5 = "formalMd5";
        
        ConfigCacheService.dumpWithMd5(dataId, group, tenant, content, md5, ts,
            "text", "");
        
        assertEquals(md5,
            ConfigCacheService.getContentMd5(groupKey, "", "", null));
        assertEquals(md5,
            ConfigCacheService.getContentMd5(groupKey, "1.1.1.1", null, null));
        assertEquals(md5,
            ConfigCacheService.getContentMd5(groupKey, null, "tagVal", null));
        assertEquals(md5,
            ConfigCacheService.getContentMd5(groupKey, "1.1.1.1", "tagVal",
                null));
        
        ConfigCacheService.remove(dataId, group, tenant);
    }
    
    @Test
    void testGetContentMd5NonExistentKey() {
        assertEquals(NULL, ConfigCacheService.getContentMd5("nonExistent"));
    }
    
    @Test
    void testDumpGrayWithUnknownGrayRule() {
        String grayRule =
            "{\"type\":\"unknown\",\"version\":\"9.9.9\","
                + "\"expr\":\"test\",\"priority\":1}";
        boolean result = ConfigCacheService.dumpGray("d", "g", "t",
            "grayName", grayRule, "content", System.currentTimeMillis(), "");
        assertFalse(result);
    }
    
    @Test
    void testDumpGrayIoException() throws IOException {
        String dataId = "testDumpGrayIO";
        String group = "g1";
        String tenant = "t1";
        String grayName = "grayIO";
        String grayRule =
            "{\"type\":\"tag\",\"version\":\"1.0.0\","
                + "\"expr\":\"test\",\"priority\":1}";
        long ts = System.currentTimeMillis();
        
        ConfigCacheService.dumpGray(dataId, group, tenant, grayName, grayRule,
            "content1", ts, "");
        
        doThrow(new IOException("disk error")).when(configDiskService)
            .saveGrayToDisk(anyString(), anyString(), anyString(), anyString(),
                anyString());
        
        long ts2 = ts + 1000;
        boolean result = ConfigCacheService.dumpGray(dataId, group, tenant,
            grayName, grayRule, "different-content", ts2, "");
        assertFalse(result);
        
        ConfigCacheService.remove(dataId, group, tenant);
    }
    
    @Test
    void testRemoveGrayNonExistent() {
        boolean result =
            ConfigCacheService.removeGray("noKey", "g", "t", "gray");
        assertTrue(result);
    }
    
    @Test
    void testRemoveNonExistent() {
        boolean result = ConfigCacheService.remove("noKey", "g", "t");
        assertTrue(result);
    }
    
    @Test
    void testIsUptodate() {
        String dataId = "testUptodate";
        String group = "g1";
        String tenant = "t1";
        String groupKey = GroupKey2.getKey(dataId, group, tenant);
        String content = "uptodateContent";
        String md5 = MD5Utils.md5Hex(content, "UTF-8");
        
        ConfigCacheService.dumpWithMd5(dataId, group, tenant, content, md5,
            System.currentTimeMillis(), "text", "");
        
        assertTrue(ConfigCacheService.isUptodate(groupKey, md5));
        assertFalse(ConfigCacheService.isUptodate(groupKey, "wrong"));
        assertTrue(
            ConfigCacheService.isUptodate(groupKey, md5, null, null));
        assertTrue(
            ConfigCacheService.isUptodate(groupKey, md5, "1.1.1.1", "tag"));
        assertTrue(ConfigCacheService.isUptodate(groupKey, md5, "1.1.1.1",
            "tag", null));
        
        ConfigCacheService.remove(dataId, group, tenant);
    }
    
    @Test
    void testGetGrayLastModifiedTs() {
        String dataId = "testGrayTs";
        String group = "g1";
        String tenant = "t1";
        String groupKey = GroupKey2.getKey(dataId, group, tenant);
        String grayName = "gray1";
        String grayRule =
            "{\"type\":\"tag\",\"version\":\"1.0.0\","
                + "\"expr\":\"test\",\"priority\":1}";
        long ts = System.currentTimeMillis();
        
        ConfigCacheService.dumpGray(dataId, group, tenant, grayName, grayRule,
            "content", ts, "");
        
        assertEquals(ts,
            ConfigCacheService.getGrayLastModifiedTs(groupKey, grayName));
        assertEquals(0,
            ConfigCacheService.getGrayLastModifiedTs(groupKey, "noGray"));
        
        ConfigCacheService.remove(dataId, group, tenant);
    }
    
    @Test
    void testGetContentGrayMd5() {
        String dataId = "testGrayMd5Get";
        String group = "g1";
        String tenant = "t1";
        String groupKey = GroupKey2.getKey(dataId, group, tenant);
        String grayName = "gray1";
        String content = "grayContent";
        String grayRule =
            "{\"type\":\"tag\",\"version\":\"1.0.0\","
                + "\"expr\":\"test\",\"priority\":1}";
        long ts = System.currentTimeMillis();
        
        ConfigCacheService.dumpGray(dataId, group, tenant, grayName, grayRule,
            content, ts, "");
        
        String expectedMd5 = MD5Utils.md5Hex(content, "UTF-8");
        assertEquals(expectedMd5,
            ConfigCacheService.getContentGrayMd5(groupKey, grayName));
        assertEquals(NULL,
            ConfigCacheService.getContentGrayMd5(groupKey, "noGray"));
        assertEquals(NULL,
            ConfigCacheService.getContentGrayMd5("noKey", grayName));
        
        ConfigCacheService.remove(dataId, group, tenant);
    }
    
    @Test
    void testGetGrayRule() {
        String dataId = "testGrayRuleGet";
        String group = "g1";
        String tenant = "t1";
        String groupKey = GroupKey2.getKey(dataId, group, tenant);
        String grayName = "gray1";
        String grayRule =
            "{\"type\":\"tag\",\"version\":\"1.0.0\","
                + "\"expr\":\"test\",\"priority\":1}";
        long ts = System.currentTimeMillis();
        
        ConfigCacheService.dumpGray(dataId, group, tenant, grayName, grayRule,
            "content", ts, "");
        
        assertNotNull(ConfigCacheService.getGrayRule(groupKey, grayName));
        assertNull(ConfigCacheService.getGrayRule(groupKey, "noGray"));
        assertNull(ConfigCacheService.getGrayRule("noKey", grayName));
        
        ConfigCacheService.remove(dataId, group, tenant);
    }
    
    @Test
    void testGetLastModifiedTs() {
        String dataId = "testLastModTs";
        String group = "g1";
        String tenant = "t1";
        String groupKey = GroupKey2.getKey(dataId, group, tenant);
        long ts = System.currentTimeMillis();
        
        ConfigCacheService.dumpWithMd5(dataId, group, tenant, "c", "md5",
            ts, "text", "");
        
        assertEquals(ts, ConfigCacheService.getLastModifiedTs(groupKey));
        assertEquals(0L, ConfigCacheService.getLastModifiedTs("noKey"));
        
        ConfigCacheService.remove(dataId, group, tenant);
    }
    
    @Test
    void testGroupCount() {
        int before = ConfigCacheService.groupCount();
        ConfigCacheService.dumpWithMd5("gcD", "gcG", "gcT", "c", "md5",
            System.currentTimeMillis(), "text", "");
        assertEquals(before + 1, ConfigCacheService.groupCount());
        ConfigCacheService.remove("gcD", "gcG", "gcT");
    }
    
    @Test
    void testConstructor() {
        assertNotNull(new ConfigCacheService());
    }
    
    @Test
    void testDumpWithSameMd5AndTimestampKeepsCache() throws Exception {
        String dataId = "sameMd5D";
        String group = "sameMd5G";
        String tenant = "sameMd5T";
        String content = "same-content";
        String md5 = MD5Utils.md5Hex(content, "UTF-8");
        long timestamp = System.currentTimeMillis();
        
        assertTrue(ConfigCacheService.dumpWithMd5(dataId, group, tenant, content, md5,
            timestamp, "text", ""));
        assertTrue(ConfigCacheService.dumpWithMd5(dataId, group, tenant, content, md5,
            timestamp, "text", ""));
        
        Mockito.verify(configDiskService, times(1)).saveToDisk(dataId, group, tenant, content);
        ConfigCacheService.remove(dataId, group, tenant);
    }
    
    @Test
    void testTryReadLock() {
        assertEquals(0, ConfigCacheService.tryReadLock("noExistKey"));
    }
    
    @Test
    void testReleaseReadLockNoItem() {
        ConfigCacheService.releaseReadLock("noExistKey");
    }
    
    @Test
    void testReleaseReadLockExistingItem() {
        ConfigCacheService.dumpWithMd5("rlD", "rlG", "rlT", "c", "md5",
            System.currentTimeMillis(), "text", "");
        String gk = GroupKey2.getKey("rlD", "rlG", "rlT");
        assertEquals(1, ConfigCacheService.tryReadLock(gk));
        ConfigCacheService.releaseReadLock(gk);
        ConfigCacheService.remove("rlD", "rlG", "rlT");
    }
    
    @Test
    void testTryWriteLock() {
        assertEquals(0, ConfigCacheService.tryWriteLock("noExistKeyWrite"));
    }
    
    @Test
    void testTryWriteLockExistingItem() {
        ConfigCacheService.dumpWithMd5("wlD", "wlG", "wlT", "c", "md5",
            System.currentTimeMillis(), "text", "");
        String gk = GroupKey2.getKey("wlD", "wlG", "wlT");
        int result = ConfigCacheService.tryWriteLock(gk);
        assertEquals(1, result);
        ConfigCacheService.releaseWriteLock(gk);
        ConfigCacheService.remove("wlD", "wlG", "wlT");
    }
    
    @Test
    void testReleaseWriteLockNoItem() {
        ConfigCacheService.releaseWriteLock("noExistKeyReleaseWrite");
    }
    
    @Test
    void testGetContentMd5WithConnLabels() {
        ConfigCacheService.dumpWithMd5("clD", "clG", "clT", "content", "md5val",
            System.currentTimeMillis(), "text", "");
        String gk = GroupKey2.getKey("clD", "clG", "clT");
        Map<String, String> labels = new HashMap<>();
        labels.put("clientIp", "1.1.1.1");
        String md5 = ConfigCacheService.getContentMd5(gk, null, null, labels);
        assertEquals("md5val", md5);
        ConfigCacheService.remove("clD", "clG", "clT");
    }
    
    @Test
    void testGetContentMd5FallsBackWhenGrayRuleDoesNotMatch() {
        String dataId = "grayMissD";
        String group = "grayMissG";
        String tenant = "grayMissT";
        String grayRule = "{\"type\":\"tag\",\"version\":\"1.0.0\",\"expr\":\"blue\","
            + "\"priority\":1}";
        ConfigCacheService.dumpWithMd5(dataId, group, tenant, "formal", "formalMd5",
            System.currentTimeMillis(), "text", "");
        ConfigCacheService.dumpGray(dataId, group, tenant, "tag_blue", grayRule,
            "grayContent", System.currentTimeMillis(), "");
        
        String gk = GroupKey2.getKey(dataId, group, tenant);
        assertEquals("formalMd5", ConfigCacheService.getContentMd5(gk, null, "red", null));
        ConfigCacheService.remove(dataId, group, tenant);
    }
    
    @Test
    void testGetContentMd5ReturnsNullConstantWhenMd5IsNull() {
        String dataId = "nullMd5D";
        String group = "nullMd5G";
        String tenant = "nullMd5T";
        ConfigCacheService.dumpWithMd5(dataId, group, tenant, "content", "md5",
            System.currentTimeMillis(), "text", "");
        String gk = GroupKey2.getKey(dataId, group, tenant);
        ConfigCacheService.getContentCache(gk).getConfigCache().setMd5(null);
        
        assertEquals(NULL, ConfigCacheService.getContentMd5(gk));
        ConfigCacheService.remove(dataId, group, tenant);
    }
    
    @Test
    void testGetContentMd5WithIpCreatesLabels() {
        ConfigCacheService.dumpWithMd5("ipD", "ipG", "ipT", "content", "md5ip",
            System.currentTimeMillis(), "text", "");
        String gk = GroupKey2.getKey("ipD", "ipG", "ipT");
        String md5 = ConfigCacheService.getContentMd5(gk, "10.0.0.1", null, null);
        assertEquals("md5ip", md5);
        ConfigCacheService.remove("ipD", "ipG", "ipT");
    }
    
    @Test
    void testIsUptodateWithIpAndTag() {
        ConfigCacheService.dumpWithMd5("upD", "upG", "upT", "c", "upMd5",
            System.currentTimeMillis(), "text", "");
        String gk = GroupKey2.getKey("upD", "upG", "upT");
        assertTrue(ConfigCacheService.isUptodate(gk, "upMd5", "1.1.1.1", "tag1"));
        assertFalse(ConfigCacheService.isUptodate(gk, "wrongMd5", "1.1.1.1", "tag1"));
        ConfigCacheService.remove("upD", "upG", "upT");
    }
    
    @Test
    void testDumpWriteLockFailed() throws Exception {
        String dataId = "lockFailD";
        String group = "lockFailG";
        String tenant = "lockFailT";
        SimpleReadWriteLock lock = Mockito.mock(SimpleReadWriteLock.class);
        Mockito.when(lock.tryWriteLock()).thenReturn(false);
        String groupKey = putMockCacheItem(dataId, group, tenant, lock);
        boolean result = ConfigCacheService.dump(dataId, group, tenant,
            "content", System.currentTimeMillis(), "text", "key");
        assertFalse(result);
        cache().remove(groupKey);
    }
    
    @Test
    void testDumpGrayWriteLockFailed() throws Exception {
        String dataId = "grayLockD";
        String group = "grayLockG";
        String tenant = "grayLockT";
        SimpleReadWriteLock lock = Mockito.mock(SimpleReadWriteLock.class);
        Mockito.when(lock.tryWriteLock()).thenReturn(false);
        String groupKey = putMockCacheItem(dataId, group, tenant, lock);
        boolean result = ConfigCacheService.dumpGray(dataId, group, tenant,
            "gray1", "{}", "content", System.currentTimeMillis(), "key");
        assertFalse(result);
        cache().remove(groupKey);
    }
    
    @Test
    void testRemoveWriteLockFailed() throws Exception {
        String dataId = "rmLockD";
        String group = "rmLockG";
        String tenant = "rmLockT";
        SimpleReadWriteLock lock = Mockito.mock(SimpleReadWriteLock.class);
        Mockito.when(lock.tryWriteLock()).thenReturn(false);
        String groupKey = putMockCacheItem(dataId, group, tenant, lock);
        boolean result = ConfigCacheService.remove(dataId, group, tenant);
        assertFalse(result);
        cache().remove(groupKey);
    }
    
    @Test
    void testRemoveGrayWriteLockFailed() throws Exception {
        String dataId = "rmGrayLockD";
        String group = "rmGrayLockG";
        String tenant = "rmGrayLockT";
        SimpleReadWriteLock lock = Mockito.mock(SimpleReadWriteLock.class);
        Mockito.when(lock.tryWriteLock()).thenReturn(false);
        String groupKey = putMockCacheItem(dataId, group, tenant, lock);
        boolean result = ConfigCacheService.removeGray(dataId, group, tenant, "gray1");
        assertFalse(result);
        cache().remove(groupKey);
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
        ConcurrentHashMap<String, CacheItem> cache = cache();
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
    
    @Test
    void testTryConfigReadLockWhenSleepInterrupted() throws Exception {
        String dataId = "interruptReadLockD";
        String group = "interruptReadLockG";
        String tenant = "interruptReadLockT";
        SimpleReadWriteLock lock = Mockito.mock(SimpleReadWriteLock.class);
        Mockito.when(lock.tryReadLock()).thenReturn(false);
        String groupKey = putMockCacheItem(dataId, group, tenant, lock);
        
        Thread.currentThread().interrupt();
        assertEquals(-1, ConfigCacheService.tryConfigReadLock(groupKey));
        assertFalse(Thread.currentThread().isInterrupted());
        cache().remove(groupKey);
    }
    
    @Test
    void testGetContentMd5MatchesGrayRule() {
        String dataId = "grayMatchD";
        String group = "grayMatchG";
        String tenant = "grayMatchT";
        String grayName = "tag_myTag";
        String grayRule =
            "{\"type\":\"tag\",\"version\":\"1.0.0\",\"expr\":\"myTag\","
                + "\"priority\":-999}";
        String content = "grayContent";
        String grayMd5 = MD5Utils.md5Hex(content, "UTF-8");
        ConfigCacheService.dumpWithMd5(dataId, group, tenant, "formal", "formalMd5",
            System.currentTimeMillis(), "text", "");
        ConfigCacheService.dumpGray(dataId, group, tenant, grayName, grayRule,
            content, System.currentTimeMillis(), "");
        String gk = GroupKey2.getKey(dataId, group, tenant);
        String md5 = ConfigCacheService.getContentMd5(gk, null, "myTag", null);
        assertEquals(grayMd5, md5);
        ConfigCacheService.remove(dataId, group, tenant);
    }
    
    @Test
    void testRemoveGraySortsRemainingGrayRules() {
        String dataId = "graySortD";
        String group = "graySortG";
        String tenant = "graySortT";
        String highPriorityRule = "{\"type\":\"tag\",\"version\":\"1.0.0\",\"expr\":\"high\","
            + "\"priority\":2}";
        String lowPriorityRule = "{\"type\":\"tag\",\"version\":\"1.0.0\",\"expr\":\"low\","
            + "\"priority\":1}";
        ConfigCacheService.dumpWithMd5(dataId, group, tenant, "formal", "formalMd5",
            System.currentTimeMillis(), "text", "");
        ConfigCacheService.dumpGray(dataId, group, tenant, "tag_high", highPriorityRule,
            "highContent", System.currentTimeMillis(), "");
        ConfigCacheService.dumpGray(dataId, group, tenant, "tag_low", lowPriorityRule,
            "lowContent", System.currentTimeMillis(), "");
        
        assertTrue(ConfigCacheService.removeGray(dataId, group, tenant, "tag_high"));
        String groupKey = GroupKey2.getKey(dataId, group, tenant);
        assertEquals("tag_low",
            ConfigCacheService.getContentCache(groupKey).getSortConfigGrays().get(0).getGrayName());
        ConfigCacheService.remove(dataId, group, tenant);
    }
    
    private String putMockCacheItem(String dataId, String group, String tenant,
        SimpleReadWriteLock lock) throws Exception {
        String groupKey = GroupKey2.getKey(dataId, group, tenant);
        CacheItem cacheItem = Mockito.mock(CacheItem.class);
        Mockito.when(cacheItem.getRwLock()).thenReturn(lock);
        cache().put(groupKey, cacheItem);
        return groupKey;
    }
    
    private ConcurrentHashMap<String, CacheItem> cache() throws Exception {
        Field cacheField = ConfigCacheService.class.getDeclaredField("CACHE");
        cacheField.setAccessible(true);
        return (ConcurrentHashMap<String, CacheItem>) cacheField.get(null);
    }
}
