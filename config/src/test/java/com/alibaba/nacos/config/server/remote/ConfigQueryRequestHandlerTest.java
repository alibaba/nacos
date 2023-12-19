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
import com.alibaba.nacos.config.server.model.ConfigCache;
import com.alibaba.nacos.config.server.model.ConfigInfoBetaWrapper;
import com.alibaba.nacos.config.server.model.ConfigInfoTagWrapper;
import com.alibaba.nacos.config.server.model.ConfigInfoWrapper;
import com.alibaba.nacos.config.server.service.ConfigCacheService;
import com.alibaba.nacos.config.server.service.dump.disk.ConfigDiskServiceFactory;
import com.alibaba.nacos.config.server.service.dump.disk.ConfigRocksDbDiskService;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoBetaPersistService;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoPersistService;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoTagPersistService;
import com.alibaba.nacos.config.server.utils.GroupKey2;
import com.alibaba.nacos.config.server.utils.PropertyUtil;
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
import org.springframework.core.env.StandardEnvironment;

import java.io.IOException;
import java.util.Arrays;

import static com.alibaba.nacos.api.common.Constants.VIPSERVER_TAG;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ConfigQueryRequestHandlerTest {
    
    private ConfigQueryRequestHandler configQueryRequestHandler;
    
    @Mock
    private ConfigInfoPersistService configInfoPersistService;
    
    @Mock
    private ConfigInfoTagPersistService configInfoTagPersistService;
    
    @Mock
    private ConfigInfoBetaPersistService configInfoBetaPersistService;
    
    static MockedStatic<ConfigCacheService> configCacheServiceMockedStatic;
    
    static MockedStatic<PropertyUtil> propertyUtilMockedStatic;
    
    static MockedStatic<ConfigDiskServiceFactory> configDiskServiceFactoryMockedStatic;
    
    String dataId = "dataId" + System.currentTimeMillis();
    
    String group = "group" + System.currentTimeMillis();
    
    String content = "content" + System.currentTimeMillis();
    
    @After
    public void after() {
        configCacheServiceMockedStatic.close();
        propertyUtilMockedStatic.close();
        configDiskServiceFactoryMockedStatic.close();
        EnvUtil.setEnvironment(null);
    }
    
    @Before
    public void setUp() throws IOException {
        EnvUtil.setEnvironment(new StandardEnvironment());
        configCacheServiceMockedStatic = Mockito.mockStatic(ConfigCacheService.class);
        propertyUtilMockedStatic = Mockito.mockStatic(PropertyUtil.class);
        configDiskServiceFactoryMockedStatic = Mockito.mockStatic(ConfigDiskServiceFactory.class);
        configQueryRequestHandler = new ConfigQueryRequestHandler(configInfoPersistService, configInfoTagPersistService,
                configInfoBetaPersistService);
        final String groupKey = GroupKey2.getKey(dataId, group, "");
        when(ConfigCacheService.tryReadLock(groupKey)).thenReturn(1);
        propertyUtilMockedStatic.when(PropertyUtil::getMaxContent).thenReturn(1024 * 1000);
        
    }
    
    /**
     * get normal config from direct read.
     *
     * @throws Exception Exception.
     */
    @Test
    public void testGetNormalDirectRead() throws Exception {
        final String groupKey = GroupKey2.getKey(dataId, group, "");
        String content = "content_from_directreadÄãºÃ" + System.currentTimeMillis();
        ConfigRocksDbDiskService configRocksDbDiskService = Mockito.mock(ConfigRocksDbDiskService.class);
        when(ConfigDiskServiceFactory.getInstance()).thenReturn(configRocksDbDiskService);
        when(PropertyUtil.isDirectRead()).thenReturn(true);
        
        CacheItem cacheItem = new CacheItem(groupKey);
        cacheItem.getConfigCache().setMd5Gbk(MD5Utils.md5Hex(content, "GBK"));
        cacheItem.getConfigCache().setMd5Utf8(MD5Utils.md5Hex(content, "UTF-8"));
        
        when(ConfigCacheService.getContentCache(eq(groupKey))).thenReturn(cacheItem);
        
        ConfigQueryRequest configQueryRequest = new ConfigQueryRequest();
        configQueryRequest.setDataId(dataId);
        configQueryRequest.setGroup(group);
        RequestMeta requestMeta = new RequestMeta();
        requestMeta.setClientIp("127.0.0.1");
        ConfigInfoWrapper configInfoBase = new ConfigInfoWrapper();
        configInfoBase.setDataId(dataId);
        configInfoBase.setGroup(group);
        configInfoBase.setContent(content);
        when(configInfoPersistService.findConfigInfo(eq(dataId), eq(group), eq(null))).thenReturn(configInfoBase);
        ConfigQueryResponse response = configQueryRequestHandler.handle(configQueryRequest, requestMeta);
        Assert.assertEquals(content, response.getContent());
        Assert.assertEquals(MD5Utils.md5Hex(content, "UTF-8"), response.getMd5());
        Assert.assertFalse(response.isBeta());
        Assert.assertNull(response.getTag());
        
    }
    
    /**
     * get normal config from local disk.
     *
     * @throws Exception Exception.
     */
    @Test
    public void testGetNormalNotDirectRead() throws Exception {
        
        final String groupKey = GroupKey2.getKey(dataId, group, "");
        String content = "content_from_notdirectreadÄãºÃ" + System.currentTimeMillis();
        ConfigRocksDbDiskService configRocksDbDiskService = Mockito.mock(ConfigRocksDbDiskService.class);
        when(ConfigDiskServiceFactory.getInstance()).thenReturn(configRocksDbDiskService);
        when(PropertyUtil.isDirectRead()).thenReturn(false);
        
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
        Assert.assertEquals(content, response.getContent());
        Assert.assertEquals(MD5Utils.md5Hex(content, "UTF-8"), response.getMd5());
        Assert.assertEquals("key_testGetNormal_NotDirectRead", response.getEncryptedDataKey());
        
        Assert.assertFalse(response.isBeta());
        Assert.assertNull(response.getTag());
        
        Assert.assertEquals(content, response.getContent());
    }
    
    
    /**
     * get beta config from local disk.
     *
     * @throws Exception Exception.
     */
    @Test
    public void testGetBetaNotDirectRead() throws Exception {
        
        final String groupKey = GroupKey2.getKey(dataId, group, "");
        ConfigRocksDbDiskService configRocksDbDiskService = Mockito.mock(ConfigRocksDbDiskService.class);
        when(ConfigDiskServiceFactory.getInstance()).thenReturn(configRocksDbDiskService);
        when(PropertyUtil.isDirectRead()).thenReturn(false);
        
        CacheItem cacheItem = new CacheItem(groupKey);
        cacheItem.initBetaCacheIfEmpty();
        String content = "content_from_beta_notdirectreadÄãºÃ" + System.currentTimeMillis();
        cacheItem.getConfigCacheBeta().setMd5Gbk(MD5Utils.md5Hex(content, "GBK"));
        cacheItem.getConfigCacheBeta().setMd5Utf8(MD5Utils.md5Hex(content, "UTF-8"));
        cacheItem.getConfigCacheBeta().setEncryptedDataKey("key_testGetBeta_NotDirectRead");
        cacheItem.setBeta(true);
        cacheItem.setIps4Beta(Arrays.asList("127.0.0.1"));
        
        when(ConfigCacheService.getContentCache(eq(groupKey))).thenReturn(cacheItem);
        
        ConfigQueryRequest configQueryRequest = new ConfigQueryRequest();
        configQueryRequest.setDataId(dataId);
        configQueryRequest.setGroup(group);
        RequestMeta requestMeta = new RequestMeta();
        requestMeta.setClientIp("127.0.0.1");
        
        when(configRocksDbDiskService.getBetaContent(eq(dataId), eq(group), eq(null))).thenReturn(content);
        ConfigQueryResponse response = configQueryRequestHandler.handle(configQueryRequest, requestMeta);
        //check content&md5
        Assert.assertEquals(content, response.getContent());
        Assert.assertEquals(MD5Utils.md5Hex(content, "UTF-8"), response.getMd5());
        //check flags.
        Assert.assertTrue(response.isBeta());
        Assert.assertNull(response.getTag());
        
    }
    
    /**
     * get beta config from direct read.
     *
     * @throws Exception Exception.
     */
    @Test
    public void testGetBetaDirectRead() throws Exception {
        
        final String groupKey = GroupKey2.getKey(dataId, group, "");
        ConfigRocksDbDiskService configRocksDbDiskService = Mockito.mock(ConfigRocksDbDiskService.class);
        when(ConfigDiskServiceFactory.getInstance()).thenReturn(configRocksDbDiskService);
        when(PropertyUtil.isDirectRead()).thenReturn(true);
        
        CacheItem cacheItem = new CacheItem(groupKey);
        cacheItem.initBetaCacheIfEmpty();
        String content = "content_from_beta_directreadÄãºÃ" + System.currentTimeMillis();
        cacheItem.getConfigCacheBeta().setMd5Gbk(MD5Utils.md5Hex(content, "GBK"));
        cacheItem.getConfigCacheBeta().setMd5Utf8(MD5Utils.md5Hex(content, "UTF-8"));
        cacheItem.getConfigCacheBeta().setEncryptedDataKey("key_testGetBeta_DirectRead");
        
        cacheItem.setBeta(true);
        cacheItem.setIps4Beta(Arrays.asList("127.0.0.1"));
        
        when(ConfigCacheService.getContentCache(eq(groupKey))).thenReturn(cacheItem);
        
        ConfigQueryRequest configQueryRequest = new ConfigQueryRequest();
        configQueryRequest.setDataId(dataId);
        configQueryRequest.setGroup(group);
        RequestMeta requestMeta = new RequestMeta();
        requestMeta.setClientIp("127.0.0.1");
        
        //mock direct read.
        ConfigInfoBetaWrapper configInfoBase = new ConfigInfoBetaWrapper();
        configInfoBase.setDataId(dataId);
        configInfoBase.setGroup(group);
        configInfoBase.setContent(content);
        when(configInfoBetaPersistService.findConfigInfo4Beta(eq(dataId), eq(group), eq(null))).thenReturn(
                configInfoBase);
        ConfigQueryResponse response = configQueryRequestHandler.handle(configQueryRequest, requestMeta);
        
        //check content&md5
        Assert.assertEquals(content, response.getContent());
        Assert.assertEquals(MD5Utils.md5Hex(content, "UTF-8"), response.getMd5());
        Assert.assertEquals("key_testGetBeta_DirectRead", response.getEncryptedDataKey());
        //check flags.
        Assert.assertTrue(response.isBeta());
        Assert.assertNull(response.getTag());
        
    }
    
    
    /**
     * get tag config ,but not found.
     *
     * @throws Exception Exception.
     */
    @Test
    public void testGetTagNotFound() throws Exception {
        
        final String groupKey = GroupKey2.getKey(dataId, group, "");
        String content = "content_from_tag_directreadÄãºÃ" + System.currentTimeMillis();
        ConfigRocksDbDiskService configRocksDbDiskService = Mockito.mock(ConfigRocksDbDiskService.class);
        when(ConfigDiskServiceFactory.getInstance()).thenReturn(configRocksDbDiskService);
        when(PropertyUtil.isDirectRead()).thenReturn(true);

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
        String autoTag = "specific_tag";
        configQueryRequest.putHeader(VIPSERVER_TAG, autoTag);
        RequestMeta requestMeta = new RequestMeta();
        requestMeta.setClientIp("127.0.0.1");
        
        //mock direct read.
        when(configInfoTagPersistService.findConfigInfo4Tag(eq(dataId), eq(group), eq(null),
                eq(specificTag))).thenReturn(null);
        ConfigQueryResponse response = configQueryRequestHandler.handle(configQueryRequest, requestMeta);
        
        //check content&md5
        Assert.assertNull(response.getContent());
        Assert.assertNull(response.getMd5());
        System.out.println(response.getMessage());
        Assert.assertEquals(response.getErrorCode(), ConfigQueryResponse.CONFIG_NOT_FOUND);
        Assert.assertNull(response.getEncryptedDataKey());
        
        //check flags.
        Assert.assertFalse(response.isBeta());
        Assert.assertEquals(response.getTag(), specificTag);
        
    }
    
    /**
     * get tag config from direct read.
     *
     * @throws Exception Exception.
     */
    @Test
    public void testGetTagDirectRead() throws Exception {
        
        final String groupKey = GroupKey2.getKey(dataId, group, "");
        ConfigRocksDbDiskService configRocksDbDiskService = Mockito.mock(ConfigRocksDbDiskService.class);
        when(ConfigDiskServiceFactory.getInstance()).thenReturn(configRocksDbDiskService);
        when(PropertyUtil.isDirectRead()).thenReturn(true);
        
        CacheItem cacheItem = new CacheItem(groupKey);
        String content = "content_from_tag_directreadÄãºÃ" + System.currentTimeMillis();
        cacheItem.getConfigCache().setMd5Gbk(MD5Utils.md5Hex(content, "GBK"));
        cacheItem.getConfigCache().setEncryptedDataKey("key_testGetTag_DirectRead");
        
        cacheItem.initConfigTagsIfEmpty();
        ConfigCache configCacheTag = new ConfigCache();
        String tagContent = "content_from_specific_tag_directreadÄãºÃ" + System.currentTimeMillis();
        configCacheTag.setMd5Gbk(MD5Utils.md5Hex(tagContent, "GBK"));
        configCacheTag.setMd5Utf8(MD5Utils.md5Hex(tagContent, "UTF-8"));
        configCacheTag.setEncryptedDataKey("key_testGetTag_DirectRead");
        cacheItem.initConfigTagsIfEmpty();
        //specific tag to get
        String specificTag = "specific_tag";
        cacheItem.getConfigCacheTags().put(specificTag, configCacheTag);
        when(ConfigCacheService.getContentCache(eq(groupKey))).thenReturn(cacheItem);
        
        ConfigQueryRequest configQueryRequest = new ConfigQueryRequest();
        configQueryRequest.setDataId(dataId);
        configQueryRequest.setGroup(group);
        configQueryRequest.setTag(specificTag);
        //just for compare.
        String autoTag = "specific_tag";
        configQueryRequest.putHeader(VIPSERVER_TAG, autoTag);
        RequestMeta requestMeta = new RequestMeta();
        requestMeta.setClientIp("127.0.0.1");
        
        //mock direct read.
        ConfigInfoTagWrapper configInfoBase = new ConfigInfoTagWrapper();
        configInfoBase.setDataId(dataId);
        configInfoBase.setGroup(group);
        configInfoBase.setContent(tagContent);
        configInfoBase.setMd5(MD5Utils.md5Hex(tagContent, "UTF-8"));
        when(configInfoTagPersistService.findConfigInfo4Tag(eq(dataId), eq(group), eq(null),
                eq(specificTag))).thenReturn(configInfoBase);
        ConfigQueryResponse response = configQueryRequestHandler.handle(configQueryRequest, requestMeta);
        
        //check content&md5
        Assert.assertEquals(tagContent, response.getContent());
        Assert.assertEquals("key_testGetTag_DirectRead", response.getEncryptedDataKey());
        Assert.assertEquals(MD5Utils.md5Hex(tagContent, "UTF-8"), response.getMd5());
        //check flags.
        Assert.assertFalse(response.isBeta());
        Assert.assertEquals(response.getTag(), specificTag);
        
    }
    
    /**
     * get tag config from local disk.
     *
     * @throws Exception Exception.
     */
    @Test
    public void testGetTagNotDirectRead() throws Exception {
        
        final String groupKey = GroupKey2.getKey(dataId, group, "");
        String content = "content_from_tag_notdirectreadÄãºÃ" + System.currentTimeMillis();
        ConfigRocksDbDiskService configRocksDbDiskService = Mockito.mock(ConfigRocksDbDiskService.class);
        when(ConfigDiskServiceFactory.getInstance()).thenReturn(configRocksDbDiskService);
        when(PropertyUtil.isDirectRead()).thenReturn(false);
        
        CacheItem cacheItem = new CacheItem(groupKey);
        cacheItem.getConfigCache().setMd5Gbk(MD5Utils.md5Hex(content, "GBK"));
        cacheItem.getConfigCache().setMd5Utf8(MD5Utils.md5Hex(content, "UTF-8"));
        cacheItem.getConfigCache().setEncryptedDataKey("key_formal");
        
        ConfigCache configCacheTag = new ConfigCache();
        String tagContent = "content_from_specific_tag_directreadÄãºÃ" + System.currentTimeMillis();
        configCacheTag.setMd5Gbk(MD5Utils.md5Hex(tagContent, "GBK"));
        configCacheTag.setMd5Utf8(MD5Utils.md5Hex(tagContent, "UTF-8"));
        configCacheTag.setEncryptedDataKey("key_testGetTag_NotDirectRead");
        cacheItem.initConfigTagsIfEmpty();
        //specific tag to get
        String specificTag = "specific_tag";
        //just for compare.
        cacheItem.getConfigCacheTags().put(specificTag, configCacheTag);
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
        when(configRocksDbDiskService.getTagContent(eq(dataId), eq(group), eq(null), eq(specificTag))).thenReturn(
                tagContent);
        ConfigQueryResponse response = configQueryRequestHandler.handle(configQueryRequest, requestMeta);
        
        //check content&md5
        Assert.assertEquals(tagContent, response.getContent());
        Assert.assertEquals(MD5Utils.md5Hex(tagContent, "UTF-8"), response.getMd5());
        Assert.assertEquals("key_testGetTag_NotDirectRead", response.getEncryptedDataKey());
        //check flags.
        Assert.assertFalse(response.isBeta());
        Assert.assertEquals(response.getTag(), specificTag);
        
    }
    
    /**
     * get tao config of auto tag matchd from local disk.
     *
     * @throws Exception Exception.
     */
    @Test
    public void testGetTagAutoTagNotDirectRead() throws Exception {
        
        final String groupKey = GroupKey2.getKey(dataId, group, "");
        String content = "content_from_tag_notdirectreadÄãºÃ" + System.currentTimeMillis();
        ConfigRocksDbDiskService configRocksDbDiskService = Mockito.mock(ConfigRocksDbDiskService.class);
        when(ConfigDiskServiceFactory.getInstance()).thenReturn(configRocksDbDiskService);
        when(PropertyUtil.isDirectRead()).thenReturn(false);
        
        //just for compare.
        
        CacheItem cacheItem = new CacheItem(groupKey);
        cacheItem.getConfigCache().setMd5Gbk(MD5Utils.md5Hex(content, "GBK"));
        cacheItem.getConfigCache().setMd5Utf8(MD5Utils.md5Hex(content, "UTF-8"));
        ConfigCache configCacheTag = new ConfigCache();
        String tagContent = "content_from_specific_tag_directreadÄãºÃ" + System.currentTimeMillis();
        configCacheTag.setMd5Gbk(MD5Utils.md5Hex(tagContent, "GBK"));
        configCacheTag.setMd5Utf8(MD5Utils.md5Hex(tagContent, "UTF-8"));
        configCacheTag.setEncryptedDataKey("key_testGetTag_AutoTag_NotDirectRead");
        cacheItem.initConfigTagsIfEmpty();
        String autoTag = "auto_tag";
        cacheItem.getConfigCacheTags().put(autoTag, configCacheTag);
        when(ConfigCacheService.getContentCache(eq(groupKey))).thenReturn(cacheItem);
        
        ConfigQueryRequest configQueryRequest = new ConfigQueryRequest();
        configQueryRequest.setDataId(dataId);
        configQueryRequest.setGroup(group);
        configQueryRequest.putHeader(VIPSERVER_TAG, autoTag);
        RequestMeta requestMeta = new RequestMeta();
        requestMeta.setClientIp("127.0.0.1");
        
        //mock disk read.
        when(configRocksDbDiskService.getTagContent(eq(dataId), eq(group), eq(null), eq(autoTag))).thenReturn(
                tagContent);
        ConfigQueryResponse response = configQueryRequestHandler.handle(configQueryRequest, requestMeta);
        
        //check content&md5
        Assert.assertEquals(tagContent, response.getContent());
        Assert.assertEquals(MD5Utils.md5Hex(tagContent, "UTF-8"), response.getMd5());
        Assert.assertEquals("key_testGetTag_AutoTag_NotDirectRead", response.getEncryptedDataKey());
        
        //check flags.
        Assert.assertFalse(response.isBeta());
        Assert.assertEquals(response.getTag(), autoTag);
        
    }
    
    
    /**
     * get tag config of auto tag from direct read.
     *
     * @throws Exception Exception.
     */
    @Test
    public void testGetTagAutoTagDirectRead() throws Exception {
        
        final String groupKey = GroupKey2.getKey(dataId, group, "");
        String content = "content_from_tag_directreadÄãºÃ" + System.currentTimeMillis();
        ConfigRocksDbDiskService configRocksDbDiskService = Mockito.mock(ConfigRocksDbDiskService.class);
        when(ConfigDiskServiceFactory.getInstance()).thenReturn(configRocksDbDiskService);
        when(PropertyUtil.isDirectRead()).thenReturn(true);
        
        CacheItem cacheItem = new CacheItem(groupKey);
        cacheItem.getConfigCache().setMd5Gbk(MD5Utils.md5Hex(content, "GBK"));
        cacheItem.getConfigCache().setMd5Utf8(MD5Utils.md5Hex(content, "UTF-8"));
        ConfigCache configCacheTag = new ConfigCache();
        String tagContent = "content_from_specific_tag_directreadÄãºÃ" + System.currentTimeMillis();
        configCacheTag.setMd5Gbk(MD5Utils.md5Hex(tagContent, "GBK"));
        configCacheTag.setMd5Utf8(MD5Utils.md5Hex(tagContent, "UTF-8"));
        configCacheTag.setEncryptedDataKey("key_testGetTag_AutoTag_DirectRead");
        cacheItem.initConfigTagsIfEmpty();
        //just for compare.
        String autoTag = "auto_tag";
        cacheItem.getConfigCacheTags().put(autoTag, configCacheTag);
        when(ConfigCacheService.getContentCache(eq(groupKey))).thenReturn(cacheItem);
        
        ConfigQueryRequest configQueryRequest = new ConfigQueryRequest();
        configQueryRequest.setDataId(dataId);
        configQueryRequest.setGroup(group);
        configQueryRequest.setTag(autoTag);
        configQueryRequest.putHeader(VIPSERVER_TAG, autoTag);
        RequestMeta requestMeta = new RequestMeta();
        requestMeta.setClientIp("127.0.0.1");
        
        //mock direct read.
        ConfigInfoTagWrapper configInfoBase = new ConfigInfoTagWrapper();
        configInfoBase.setDataId(dataId);
        configInfoBase.setGroup(group);
        configInfoBase.setContent(tagContent);
        when(configInfoTagPersistService.findConfigInfo4Tag(eq(dataId), eq(group), eq(null), eq(autoTag))).thenReturn(
                configInfoBase);
        ConfigQueryResponse response = configQueryRequestHandler.handle(configQueryRequest, requestMeta);
        
        //check content&md5
        Assert.assertEquals(tagContent, response.getContent());
        Assert.assertEquals(MD5Utils.md5Hex(tagContent, "UTF-8"), response.getMd5());
        Assert.assertEquals("key_testGetTag_AutoTag_DirectRead", response.getEncryptedDataKey());
        //check flags.
        Assert.assertFalse(response.isBeta());
        Assert.assertEquals(response.getTag(), autoTag);
        
    }
    
}
