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

package com.alibaba.nacos.config.server.controller;

import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.common.constant.HttpHeaderConsts;
import com.alibaba.nacos.common.http.param.MediaType;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.model.CacheItem;
import com.alibaba.nacos.config.server.service.ConfigCacheService;
import com.alibaba.nacos.config.server.service.LongPollingService;
import com.alibaba.nacos.config.server.service.dump.disk.ConfigDiskServiceFactory;
import com.alibaba.nacos.config.server.service.dump.disk.ConfigRocksDbDiskService;
import com.alibaba.nacos.config.server.utils.GroupKey;
import com.alibaba.nacos.config.server.utils.GroupKey2;
import com.alibaba.nacos.config.server.utils.MD5Util;
import com.alibaba.nacos.config.server.utils.PropertyUtil;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.util.ReflectionTestUtils;

import javax.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.alibaba.nacos.api.common.Constants.VIPSERVER_TAG;
import static com.alibaba.nacos.config.server.constant.Constants.CONTENT_MD5;
import static com.alibaba.nacos.config.server.utils.RequestUtil.CLIENT_APPNAME_HEADER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@WebAppConfiguration
class ConfigServletInnerTest {
    
    static MockedStatic<ConfigDiskServiceFactory> configDiskServiceFactoryMockedStatic;
    
    @InjectMocks
    ConfigServletInner configServletInner;
    
    MockedStatic<ConfigCacheService> configCacheServiceMockedStatic;
    
    MockedStatic<PropertyUtil> propertyUtilMockedStatic;
    
    MockedStatic<MD5Util> md5UtilMockedStatic;
    
    @Mock
    private LongPollingService longPollingService;
    
    @Mock
    private ConfigRocksDbDiskService configRocksDbDiskService;
    
    @BeforeEach
    void setUp() {
        EnvUtil.setEnvironment(new StandardEnvironment());
        ReflectionTestUtils.setField(configServletInner, "longPollingService", longPollingService);
        configCacheServiceMockedStatic = Mockito.mockStatic(ConfigCacheService.class);
        propertyUtilMockedStatic = Mockito.mockStatic(PropertyUtil.class);
        propertyUtilMockedStatic.when(PropertyUtil::getMaxContent).thenReturn(1024 * 1000);
        md5UtilMockedStatic = Mockito.mockStatic(MD5Util.class);
        configDiskServiceFactoryMockedStatic = Mockito.mockStatic(ConfigDiskServiceFactory.class);
        when(ConfigDiskServiceFactory.getInstance()).thenReturn(configRocksDbDiskService);
        
    }
    
    @AfterEach
    void after() {
        
        if (configCacheServiceMockedStatic != null) {
            configCacheServiceMockedStatic.close();
        }
        if (propertyUtilMockedStatic != null) {
            propertyUtilMockedStatic.close();
        }
        if (md5UtilMockedStatic != null) {
            md5UtilMockedStatic.close();
        }
        if (configDiskServiceFactoryMockedStatic != null) {
            configDiskServiceFactoryMockedStatic.close();
            
        }
        
    }
    
    @Test
    void testDoPollingConfig() throws Exception {
        
        Map<String, String> clientMd5Map = new HashMap<>();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        List<String> changedGroups = new ArrayList<>();
        changedGroups.add("1");
        changedGroups.add("2");
        
        md5UtilMockedStatic.when(() -> MD5Util.compareMd5(request, response, clientMd5Map)).thenReturn(changedGroups);
        md5UtilMockedStatic.when(() -> MD5Util.compareMd5OldResult(changedGroups)).thenReturn("test-old");
        md5UtilMockedStatic.when(() -> MD5Util.compareMd5ResultString(changedGroups)).thenReturn("test-new");
        
        String actualValue = configServletInner.doPollingConfig(request, response, clientMd5Map, 1);
        
        assertEquals(HttpServletResponse.SC_OK + "", actualValue);
        assertEquals("test-old", response.getHeader(Constants.PROBE_MODIFY_RESPONSE));
        assertEquals("test-new", response.getHeader(Constants.PROBE_MODIFY_RESPONSE_NEW));
        assertEquals("no-cache,no-store", response.getHeader("Cache-Control"));
        
    }
    
    @Test
    void testDoGetConfigV1Beta() throws Exception {
        
        configCacheServiceMockedStatic.when(() -> ConfigCacheService.tryConfigReadLock(anyString())).thenReturn(1);
        
        //mock cache item  isBeta
        CacheItem cacheItem = new CacheItem("test");
        cacheItem.setBeta(true);
        List<String> ips4Beta = new ArrayList<>();
        ips4Beta.add("localhost");
        cacheItem.setIps4Beta(ips4Beta);
        cacheItem.initBetaCacheIfEmpty();
        cacheItem.getConfigCacheBeta().setEncryptedDataKey("betaKey1234567");
        cacheItem.getConfigCacheBeta().setMd5Utf8("md52345Beta");
        String dataId = "testDataId135";
        String group = "group23";
        String tenant = "tenant234";
        configCacheServiceMockedStatic.when(() -> ConfigCacheService.getContentCache(GroupKey.getKeyTenant(dataId, group, tenant)))
                .thenReturn(cacheItem);
        
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("localhost:8080");
        request.addHeader(CLIENT_APPNAME_HEADER, "test");
        MockHttpServletResponse response = new MockHttpServletResponse();
        String mockBetaContent = "content3456543";
        when(configRocksDbDiskService.getBetaContent(dataId, group, tenant)).thenReturn(mockBetaContent);
        String actualValue = configServletInner.doGetConfig(request, response, dataId, group, tenant, "", "true", "localhost");
        assertEquals(HttpServletResponse.SC_OK + "", actualValue);
        assertEquals("true", response.getHeader("isBeta"));
        assertEquals("md52345Beta", response.getHeader(CONTENT_MD5));
        assertEquals("betaKey1234567", response.getHeader("Encrypted-Data-Key"));
        assertEquals(mockBetaContent, response.getContentAsString());
    }
    
    /**
     * test get config of tag.
     *
     * @throws Exception exception.
     */
    @Test
    void testDoGetConfigV1Tag() throws Exception {
        
        String dataId = "dataId123455";
        String group = "group";
        String tenant = "tenant";
        configCacheServiceMockedStatic.when(() -> ConfigCacheService.tryConfigReadLock(GroupKey2.getKey(dataId, group, tenant)))
                .thenReturn(1);
        
        //mock cache item with tag.
        CacheItem cacheItem = new CacheItem("test");
        cacheItem.setBeta(false);
        List<String> ips4Beta = new ArrayList<>();
        ips4Beta.add("localhost");
        cacheItem.setIps4Beta(ips4Beta);
        String autoTag = "auto-tag-test";
        cacheItem.initConfigTagsIfEmpty(autoTag);
        cacheItem.getConfigCacheTags().get(autoTag).setEncryptedDataKey("autoTagkey");
        cacheItem.getConfigCacheTags().get(autoTag).setMd5Utf8("md5autotag11");
        long autoTagTs = System.currentTimeMillis();
        cacheItem.getConfigCacheTags().get(autoTag).setLastModifiedTs(autoTagTs);
        String specificTag = "specificTag";
        cacheItem.initConfigTagsIfEmpty(specificTag);
        cacheItem.getConfigCacheTags().get(specificTag).setEncryptedDataKey("specificTagkey");
        cacheItem.getConfigCacheTags().get(specificTag).setMd5Utf8("md5specificTag11");
        long specificTs = System.currentTimeMillis();
        cacheItem.getConfigCacheTags().get(specificTag).setLastModifiedTs(specificTs);
        
        configCacheServiceMockedStatic.when(() -> ConfigCacheService.getContentCache(GroupKey2.getKey(dataId, group, tenant)))
                .thenReturn(cacheItem);
        
        //test auto tag.
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("localhost:8080");
        request.addHeader(CLIENT_APPNAME_HEADER, "test");
        request.addHeader(VIPSERVER_TAG, autoTag);
        MockHttpServletResponse response = new MockHttpServletResponse();
        String autoTagContent = "1234566autotag";
        Mockito.when(configRocksDbDiskService.getTagContent(dataId, group, tenant, autoTag)).thenReturn(autoTagContent);
        String actualValue = configServletInner.doGetConfig(request, response, dataId, group, tenant, null, "true", "localhost");
        assertEquals(HttpServletResponse.SC_OK + "", actualValue);
        assertEquals(autoTagContent, response.getContentAsString());
        assertEquals("md5autotag11", response.getHeader(CONTENT_MD5));
        assertEquals("autoTagkey", response.getHeader("Encrypted-Data-Key"));
        
        //test for specific tag. has higher propority than auto tag.
        response = new MockHttpServletResponse();
        String specificTagContent = "1234566autotag";
        when(configRocksDbDiskService.getTagContent(dataId, group, tenant, specificTag)).thenReturn(specificTagContent);
        actualValue = configServletInner.doGetConfig(request, response, dataId, group, tenant, specificTag, "true", "localhost");
        assertEquals(HttpServletResponse.SC_OK + "", actualValue);
        assertEquals(specificTagContent, response.getContentAsString());
        assertEquals("md5specificTag11", response.getHeader(CONTENT_MD5));
        assertEquals("specificTagkey", response.getHeader("Encrypted-Data-Key"));
        
        // test for specific tag ,not exist
        when(configRocksDbDiskService.getTagContent(dataId, group, tenant, "auto-tag-test-not-exist")).thenReturn(null);
        response = new MockHttpServletResponse();
        actualValue = configServletInner.doGetConfig(request, response, dataId, group, tenant, "auto-tag-test-not-exist", "true",
                "localhost");
        assertEquals(HttpServletResponse.SC_NOT_FOUND + "", actualValue);
        String expectedContent = "config data not exist";
        String actualContent = response.getContentAsString();
        
        assertTrue(actualContent.contains(expectedContent));
        
    }
    
    @Test
    void testDoGetConfigFormal() throws Exception {
        String dataId = "dataId1234552333";
        String group = "group";
        String tenant = "tenant";
        configCacheServiceMockedStatic.when(() -> ConfigCacheService.tryConfigReadLock(GroupKey2.getKey(dataId, group, tenant)))
                .thenReturn(1);
        
        //mock cache item .
        CacheItem cacheItem = new CacheItem("test");
        cacheItem.setBeta(false);
        String md5 = "md5wertyui";
        String content = "content345678";
        cacheItem.getConfigCache().setMd5Utf8(md5);
        long ts = System.currentTimeMillis();
        cacheItem.getConfigCache().setLastModifiedTs(ts);
        cacheItem.getConfigCache().setEncryptedDataKey("key2345678");
        configCacheServiceMockedStatic.when(() -> ConfigCacheService.getContentCache(GroupKey.getKeyTenant(dataId, group, tenant)))
                .thenReturn(cacheItem);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        
        when(configRocksDbDiskService.getContent(dataId, group, tenant)).thenReturn(content);
        String actualValue = configServletInner.doGetConfig(request, response, dataId, group, tenant, null, "true", "localhost");
        assertEquals(content, response.getContentAsString());
        assertEquals(HttpServletResponse.SC_OK + "", actualValue);
        assertEquals(md5, response.getHeader(CONTENT_MD5));
        assertEquals("key2345678", response.getHeader("Encrypted-Data-Key"));
        
    }
    
    @Test
    void testDoGetConfigFormalV2() throws Exception {
        String dataId = "dataId1234552333V2";
        String group = "group";
        String tenant = "tenant";
        configCacheServiceMockedStatic.when(() -> ConfigCacheService.tryConfigReadLock(GroupKey2.getKey(dataId, group, tenant)))
                .thenReturn(1);
        
        //mock cache item .
        CacheItem cacheItem = new CacheItem("test");
        cacheItem.setBeta(false);
        String md5 = "md5wertyui";
        String content = "content345678";
        cacheItem.getConfigCache().setMd5Utf8(md5);
        long ts = System.currentTimeMillis();
        cacheItem.getConfigCache().setLastModifiedTs(ts);
        cacheItem.getConfigCache().setEncryptedDataKey("key2345678");
        configCacheServiceMockedStatic.when(() -> ConfigCacheService.getContentCache(GroupKey.getKeyTenant(dataId, group, tenant)))
                .thenReturn(cacheItem);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        
        when(configRocksDbDiskService.getContent(dataId, group, tenant)).thenReturn(content);
        String actualValue = configServletInner.doGetConfig(request, response, dataId, group, tenant, null, "true", "localhost", true);
        assertEquals(JacksonUtils.toJson(Result.success(content)), response.getContentAsString());
        assertEquals(HttpServletResponse.SC_OK + "", actualValue);
        assertEquals(md5, response.getHeader(CONTENT_MD5));
        assertEquals("key2345678", response.getHeader("Encrypted-Data-Key"));
        assertEquals(MediaType.APPLICATION_JSON, response.getHeader(HttpHeaderConsts.CONTENT_TYPE));
    }
    
    @Test
    void testDoGetConfigNotExist() throws Exception {
        
        // if lockResult equals 0,cache item not exist.
        configCacheServiceMockedStatic.when(() -> ConfigCacheService.tryConfigReadLock(anyString())).thenReturn(0);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        String actualValue = configServletInner.doGetConfig(request, response, "test", "test", "test", "test", "true", "localhost");
        assertEquals(HttpServletResponse.SC_NOT_FOUND + "", actualValue);
        
        configCacheServiceMockedStatic.when(() -> ConfigCacheService.getContentCache(GroupKey2.getKey("test", "test", "test")))
                .thenReturn(new CacheItem(GroupKey2.getKey("test", "test", "test")));
        // if lockResult less than 0
        configCacheServiceMockedStatic.when(() -> ConfigCacheService.tryConfigReadLock(anyString())).thenReturn(-1);
        actualValue = configServletInner.doGetConfig(request, response, "test", "test", "test", "test", "true", "localhost");
        assertEquals(HttpServletResponse.SC_CONFLICT + "", actualValue);
        
    }
}
