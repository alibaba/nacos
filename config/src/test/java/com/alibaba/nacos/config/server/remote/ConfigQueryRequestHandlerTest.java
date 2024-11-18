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

package com.alibaba.nacos.config.server.remote;

import com.alibaba.nacos.api.config.remote.request.ConfigQueryRequest;
import com.alibaba.nacos.api.config.remote.response.ConfigQueryResponse;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.common.utils.MD5Utils;
import com.alibaba.nacos.config.server.model.CacheItem;
import com.alibaba.nacos.config.server.model.ConfigCacheGray;
import com.alibaba.nacos.config.server.model.gray.BetaGrayRule;
import com.alibaba.nacos.config.server.model.gray.ConfigGrayPersistInfo;
import com.alibaba.nacos.config.server.model.gray.GrayRuleManager;
import com.alibaba.nacos.config.server.model.gray.TagGrayRule;
import com.alibaba.nacos.config.server.service.ConfigCacheService;
import com.alibaba.nacos.config.server.service.dump.disk.ConfigDiskServiceFactory;
import com.alibaba.nacos.config.server.service.dump.disk.ConfigRocksDbDiskService;
import com.alibaba.nacos.config.server.utils.GroupKey2;
import com.alibaba.nacos.config.server.utils.PropertyUtil;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.StandardEnvironment;

import java.io.IOException;

import static com.alibaba.nacos.api.common.Constants.VIPSERVER_TAG;
import static com.alibaba.nacos.api.config.remote.response.ConfigQueryResponse.CONFIG_NOT_FOUND;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfigQueryRequestHandlerTest {
    
    static MockedStatic<ConfigCacheService> configCacheServiceMockedStatic;
    
    static MockedStatic<PropertyUtil> propertyUtilMockedStatic;
    
    static MockedStatic<ConfigDiskServiceFactory> configDiskServiceFactoryMockedStatic;
    
    String dataId = "dataId" + System.currentTimeMillis();
    
    String group = "group" + System.currentTimeMillis();
    
    String tenant = "tenant" + System.currentTimeMillis();
    
    String content = "content" + System.currentTimeMillis();
    
    private ConfigQueryRequestHandler configQueryRequestHandler;
    
    @AfterEach
    void after() {
        configCacheServiceMockedStatic.close();
        propertyUtilMockedStatic.close();
        configDiskServiceFactoryMockedStatic.close();
        EnvUtil.setEnvironment(null);
    }
    
    @BeforeEach
    void setUp() throws IOException {
        EnvUtil.setEnvironment(new StandardEnvironment());
        configCacheServiceMockedStatic = Mockito.mockStatic(ConfigCacheService.class);
        propertyUtilMockedStatic = Mockito.mockStatic(PropertyUtil.class);
        configDiskServiceFactoryMockedStatic = Mockito.mockStatic(ConfigDiskServiceFactory.class);
        configQueryRequestHandler = new ConfigQueryRequestHandler();
        final String groupKey = GroupKey2.getKey(dataId, group, "");
        when(ConfigCacheService.tryConfigReadLock(groupKey)).thenReturn(1);
        propertyUtilMockedStatic.when(PropertyUtil::getMaxContent).thenReturn(1024 * 1000);
        
    }
    
    /**
     * get normal config from local disk.
     *
     * @throws Exception Exception.
     */
    @Test
    void testGetNormal() throws Exception {
        
        final String groupKey = GroupKey2.getKey(dataId, group, "");
        String content = "content_from_notdirectreadÄãºÃ" + System.currentTimeMillis();
        ConfigRocksDbDiskService configRocksDbDiskService = Mockito.mock(ConfigRocksDbDiskService.class);
        when(ConfigDiskServiceFactory.getInstance()).thenReturn(configRocksDbDiskService);
        
        CacheItem cacheItem = new CacheItem(groupKey);
        cacheItem.getConfigCache().setMd5Gbk(MD5Utils.md5Hex(content, "GBK"));
        cacheItem.getConfigCache().setMd5Utf8(MD5Utils.md5Hex(content, "UTF-8"));
        cacheItem.getConfigCache().setEncryptedDataKey("key_testGetNormal_NotDirectRead");
        when(ConfigCacheService.getContentCache(eq(groupKey))).thenReturn(cacheItem);
        
        ConfigQueryRequest configQueryRequest = new ConfigQueryRequest();
        configQueryRequest.setDataId(dataId);
        configQueryRequest.setGroup(group);
        RequestMeta requestMeta = new RequestMeta();
        requestMeta.setClientIp("127.0.0.1");
        
        when(configRocksDbDiskService.getContent(eq(dataId), eq(group), eq(null))).thenReturn(content);
        ConfigQueryResponse response = configQueryRequestHandler.handle(configQueryRequest, requestMeta);
        assertEquals(content, response.getContent());
        assertEquals(MD5Utils.md5Hex(content, "UTF-8"), response.getMd5());
        assertEquals("key_testGetNormal_NotDirectRead", response.getEncryptedDataKey());
        
        assertFalse(response.isBeta());
        assertNull(response.getTag());
        
        assertEquals(content, response.getContent());
    }
    
    
    /**
     * get beta config from local disk.
     *
     * @throws Exception Exception.
     */
    @Test
    void testGetBeta() throws Exception {
        
        final String groupKey = GroupKey2.getKey(dataId, group, "");
        ConfigRocksDbDiskService configRocksDbDiskService = Mockito.mock(ConfigRocksDbDiskService.class);
        when(ConfigDiskServiceFactory.getInstance()).thenReturn(configRocksDbDiskService);
        
        CacheItem cacheItem = new CacheItem(groupKey);
        cacheItem.initConfigGrayIfEmpty(BetaGrayRule.TYPE_BETA);
        String content = "content_from_beta_notdirectreadÄãºÃ" + System.currentTimeMillis();
        ConfigCacheGray configCacheGrayBeta = cacheItem.getConfigCacheGray().get(BetaGrayRule.TYPE_BETA);
        configCacheGrayBeta.setMd5Gbk(MD5Utils.md5Hex(content, "GBK"));
        configCacheGrayBeta.setMd5Utf8(MD5Utils.md5Hex(content, "UTF-8"));
        configCacheGrayBeta.setEncryptedDataKey("key_testGetBeta_NotDirectRead");
        ConfigGrayPersistInfo configGrayPersistInfo = new ConfigGrayPersistInfo(BetaGrayRule.TYPE_BETA,
                BetaGrayRule.VERSION, "127.0.0.1", -1000);
        configCacheGrayBeta.resetGrayRule(GrayRuleManager.serializeConfigGrayPersistInfo(configGrayPersistInfo));
        cacheItem.sortConfigGray();
        when(ConfigCacheService.getContentCache(eq(groupKey))).thenReturn(cacheItem);
        
        ConfigQueryRequest configQueryRequest = new ConfigQueryRequest();
        configQueryRequest.setDataId(dataId);
        configQueryRequest.setGroup(group);
        RequestMeta requestMeta = new RequestMeta();
        requestMeta.setClientIp("127.0.0.1");
        
        when(configRocksDbDiskService.getGrayContent(eq(dataId), eq(group), eq(null),
                eq(BetaGrayRule.TYPE_BETA))).thenReturn(content);
        ConfigQueryResponse response = configQueryRequestHandler.handle(configQueryRequest, requestMeta);
        //check content&md5
        assertEquals(content, response.getContent());
        assertEquals(MD5Utils.md5Hex(content, "UTF-8"), response.getMd5());
        //check flags.
        assertTrue(response.isBeta());
        assertNull(response.getTag());
        
    }
    
    /**
     * get tag config ,but not found.
     *
     * @throws Exception Exception.
     */
    @Test
    void testGetTagNotFound() throws Exception {
        
        final String groupKey = GroupKey2.getKey(dataId, group, "");
        String content = "content_from_tag_withtagÄãºÃ" + System.currentTimeMillis();
        ConfigRocksDbDiskService configRocksDbDiskService = Mockito.mock(ConfigRocksDbDiskService.class);
        when(ConfigDiskServiceFactory.getInstance()).thenReturn(configRocksDbDiskService);
        
        CacheItem cacheItem = new CacheItem(groupKey);
        cacheItem.getConfigCache().setMd5Gbk(MD5Utils.md5Hex(content, "GBK"));
        cacheItem.getConfigCache().setMd5Utf8(MD5Utils.md5Hex(content, "UTF-8"));
        cacheItem.getConfigCache().setEncryptedDataKey("key_testGetTag_NotFound");
        
        when(ConfigCacheService.getContentCache(eq(groupKey))).thenReturn(cacheItem);
        
        ConfigQueryRequest configQueryRequest = new ConfigQueryRequest();
        configQueryRequest.setDataId(dataId);
        configQueryRequest.setGroup(group);
        String specificTag = "specific_tag";
        configQueryRequest.setTag(specificTag);
        String autoTag = "auto_tag111";
        configQueryRequest.putHeader(VIPSERVER_TAG, autoTag);
        RequestMeta requestMeta = new RequestMeta();
        requestMeta.setClientIp("127.0.0.1");
        
        ConfigQueryResponse response = configQueryRequestHandler.handle(configQueryRequest, requestMeta);
        
        //check content&md5
        assertNull(response.getContent());
        assertNull(response.getMd5());
        assertEquals(CONFIG_NOT_FOUND, response.getErrorCode());
        assertNull(response.getEncryptedDataKey());
        
        //check flags.
        assertFalse(response.isBeta());
        assertEquals(response.getTag(), specificTag);
        
    }
    
    /**
     * get tag config from local disk.
     *
     * @throws Exception Exception.
     */
    @Test
    void testGetTagWithTag() throws Exception {
        
        final String groupKey = GroupKey2.getKey(dataId, group, "");
        String content = "content_from_tag_notdirectreadÄãºÃ" + System.currentTimeMillis();
        ConfigRocksDbDiskService configRocksDbDiskService = Mockito.mock(ConfigRocksDbDiskService.class);
        when(ConfigDiskServiceFactory.getInstance()).thenReturn(configRocksDbDiskService);
        
        CacheItem cacheItem = new CacheItem(groupKey);
        cacheItem.getConfigCache().setMd5Gbk(MD5Utils.md5Hex(content, "GBK"));
        cacheItem.getConfigCache().setMd5Utf8(MD5Utils.md5Hex(content, "UTF-8"));
        cacheItem.getConfigCache().setEncryptedDataKey("key_formal");
        
        String specificTag = "specific_tag";
        cacheItem.initConfigGrayIfEmpty(TagGrayRule.TYPE_TAG + "_" + specificTag);
        ConfigCacheGray configCacheGrayTag = cacheItem.getConfigCacheGray()
                .get(TagGrayRule.TYPE_TAG + "_" + specificTag);
        String tagContent = "content_from_specific_tag_directreadÄãºÃ" + System.currentTimeMillis();
        configCacheGrayTag.setMd5Gbk(MD5Utils.md5Hex(tagContent, "GBK"));
        configCacheGrayTag.setMd5Utf8(MD5Utils.md5Hex(tagContent, "UTF-8"));
        configCacheGrayTag.setEncryptedDataKey("key_testGetTag_NotDirectRead");
        ConfigGrayPersistInfo configGrayPersistInfo = new ConfigGrayPersistInfo(TagGrayRule.TYPE_TAG,
                TagGrayRule.VERSION, specificTag, -999);
        configCacheGrayTag.resetGrayRule(GrayRuleManager.serializeConfigGrayPersistInfo(configGrayPersistInfo));
        cacheItem.sortConfigGray();
        //specific tag to get
        when(ConfigCacheService.getContentCache(eq(groupKey))).thenReturn(cacheItem);
        
        ConfigQueryRequest configQueryRequest = new ConfigQueryRequest();
        configQueryRequest.setDataId(dataId);
        configQueryRequest.setGroup(group);
        configQueryRequest.setTag(specificTag);
        String autoTag = "auto_tag";
        configQueryRequest.putHeader(VIPSERVER_TAG, autoTag);
        RequestMeta requestMeta = new RequestMeta();
        requestMeta.setClientIp("127.0.0.1");
        
        //mock disk read.
        when(configRocksDbDiskService.getGrayContent(eq(dataId), eq(group), eq(null),
                eq(TagGrayRule.TYPE_TAG + "_" + specificTag))).thenReturn(tagContent);
        ConfigQueryResponse response = configQueryRequestHandler.handle(configQueryRequest, requestMeta);
        
        //check content&md5
        assertEquals(tagContent, response.getContent());
        assertEquals(MD5Utils.md5Hex(tagContent, "UTF-8"), response.getMd5());
        assertEquals("key_testGetTag_NotDirectRead", response.getEncryptedDataKey());
        //check flags.
        assertFalse(response.isBeta());
        assertEquals(response.getTag(), specificTag);
        
    }
    
    /**
     * get tao config of auto tag matchd from local disk.
     *
     * @throws Exception Exception.
     */
    @Test
    void testGetTagAutoTag() throws Exception {
        
        final String groupKey = GroupKey2.getKey(dataId, group, "");
        String content = "content_from_tag_notdirectreadÄãºÃ" + System.currentTimeMillis();
        ConfigRocksDbDiskService configRocksDbDiskService = Mockito.mock(ConfigRocksDbDiskService.class);
        when(ConfigDiskServiceFactory.getInstance()).thenReturn(configRocksDbDiskService);
        
        String autoTag = "auto_tag";
        CacheItem cacheItem = new CacheItem(groupKey);
        cacheItem.initConfigGrayIfEmpty(TagGrayRule.TYPE_TAG + "_" + autoTag);
        cacheItem.getConfigCache().setMd5Gbk(MD5Utils.md5Hex(content, "GBK"));
        cacheItem.getConfigCache().setMd5Utf8(MD5Utils.md5Hex(content, "UTF-8"));
        ConfigCacheGray configCacheGrayTag = cacheItem.getConfigCacheGray().get(TagGrayRule.TYPE_TAG + "_" + autoTag);
        String tagContent = "content_from_specific_tag_directreadÄãºÃ" + System.currentTimeMillis();
        configCacheGrayTag.setMd5Gbk(MD5Utils.md5Hex(tagContent, "GBK"));
        configCacheGrayTag.setMd5Utf8(MD5Utils.md5Hex(tagContent, "UTF-8"));
        configCacheGrayTag.setEncryptedDataKey("key_testGetTag_AutoTag_NotDirectRead");
        ConfigGrayPersistInfo configGrayPersistInfo = new ConfigGrayPersistInfo(TagGrayRule.TYPE_TAG,
                TagGrayRule.VERSION, autoTag, -999);
        configCacheGrayTag.resetGrayRule(GrayRuleManager.serializeConfigGrayPersistInfo(configGrayPersistInfo));
        cacheItem.sortConfigGray();
        when(ConfigCacheService.getContentCache(eq(groupKey))).thenReturn(cacheItem);
        
        ConfigQueryRequest configQueryRequest = new ConfigQueryRequest();
        configQueryRequest.setDataId(dataId);
        configQueryRequest.setGroup(group);
        RequestMeta requestMeta = new RequestMeta();
        requestMeta.setClientIp("127.0.0.1");
        requestMeta.getAppLabels().put(VIPSERVER_TAG, autoTag);
        //mock disk read.
        when(configRocksDbDiskService.getGrayContent(eq(dataId), eq(group), eq(null),
                eq(TagGrayRule.TYPE_TAG + "_" + autoTag))).thenReturn(tagContent);
        ConfigQueryResponse response = configQueryRequestHandler.handle(configQueryRequest, requestMeta);
        
        //check content&md5
        assertEquals(tagContent, response.getContent());
        assertEquals(MD5Utils.md5Hex(tagContent, "UTF-8"), response.getMd5());
        assertEquals("key_testGetTag_AutoTag_NotDirectRead", response.getEncryptedDataKey());
        
        //check flags.
        assertFalse(response.isBeta());
        assertEquals(response.getTag(), autoTag);
        
    }
    
    /**
     * get normal config from local disk.
     *
     * @throws Exception Exception.
     */
    @Test
    void testGetConfigNotExistAndConflict() throws Exception {
        
        String dataId = "dataId" + System.currentTimeMillis();
        String group = "group" + System.currentTimeMillis();
        String tenant = "tenant" + System.currentTimeMillis();
        //test config not exist
        configCacheServiceMockedStatic.when(
                () -> ConfigCacheService.tryConfigReadLock(GroupKey2.getKey(dataId, group, tenant))).thenReturn(0);
        final String groupKey = GroupKey2.getKey(dataId, group, tenant);
        when(ConfigCacheService.getContentCache(eq(groupKey))).thenReturn(null);
        
        ConfigQueryRequest configQueryRequest = new ConfigQueryRequest();
        configQueryRequest.setDataId(dataId);
        configQueryRequest.setGroup(group);
        configQueryRequest.setTenant(tenant);
        RequestMeta requestMeta = new RequestMeta();
        requestMeta.setClientIp("127.0.0.1");
        
        ConfigQueryResponse response = configQueryRequestHandler.handle(configQueryRequest, requestMeta);
        assertEquals(CONFIG_NOT_FOUND, response.getErrorCode());
        assertNull(response.getContent());
        assertNull(response.getMd5());
        assertFalse(response.isBeta());
        assertNull(response.getTag());
        
        //test config conflict
        when(ConfigCacheService.getContentCache(eq(groupKey))).thenReturn(new CacheItem(groupKey));
        configCacheServiceMockedStatic.when(
                () -> ConfigCacheService.tryConfigReadLock(GroupKey2.getKey(dataId, group, tenant))).thenReturn(-1);
        ConfigQueryResponse responseConflict = configQueryRequestHandler.handle(configQueryRequest, requestMeta);
        assertEquals(ConfigQueryResponse.CONFIG_QUERY_CONFLICT, responseConflict.getErrorCode());
        assertNull(responseConflict.getContent());
        assertNull(responseConflict.getMd5());
        assertFalse(responseConflict.isBeta());
        assertNull(responseConflict.getTag());
        
    }
    
}
