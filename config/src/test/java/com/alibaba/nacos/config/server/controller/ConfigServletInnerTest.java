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

import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.model.CacheItem;
import com.alibaba.nacos.config.server.model.ConfigInfoBetaWrapper;
import com.alibaba.nacos.config.server.model.ConfigInfoTagWrapper;
import com.alibaba.nacos.config.server.model.ConfigInfoWrapper;
import com.alibaba.nacos.config.server.service.ConfigCacheService;
import com.alibaba.nacos.config.server.service.LongPollingService;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoBetaPersistService;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoPersistService;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoTagPersistService;
import com.alibaba.nacos.config.server.utils.DiskUtil;
import com.alibaba.nacos.config.server.utils.MD5Util;
import com.alibaba.nacos.config.server.utils.PropertyUtil;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.util.ReflectionTestUtils;

import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.alibaba.nacos.config.server.utils.RequestUtil.CLIENT_APPNAME_HEADER;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@RunWith(SpringJUnit4ClassRunner.class)
@WebAppConfiguration
public class ConfigServletInnerTest {
    
    @InjectMocks
    ConfigServletInner configServletInner;
    
    @Mock
    private LongPollingService longPollingService;
    
    @Mock
    private ConfigInfoPersistService configInfoPersistService;
    
    @Mock
    private ConfigInfoBetaPersistService configInfoBetaPersistService;
    
    @Mock
    private ConfigInfoTagPersistService configInfoTagPersistService;
    
    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();
    
    @Before
    public void setUp() {
        EnvUtil.setEnvironment(new StandardEnvironment());
        ReflectionTestUtils.setField(configServletInner, "longPollingService", longPollingService);
    }
    
    @Test
    public void testDoPollingConfig() throws Exception {
        MockedStatic<MD5Util> md5UtilMockedStatic = Mockito.mockStatic(MD5Util.class);
        
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
        
        Assert.assertEquals(HttpServletResponse.SC_OK + "", actualValue);
        Assert.assertEquals("test-old", response.getHeader(Constants.PROBE_MODIFY_RESPONSE));
        Assert.assertEquals("test-new", response.getHeader(Constants.PROBE_MODIFY_RESPONSE_NEW));
        Assert.assertEquals("no-cache,no-store", response.getHeader("Cache-Control"));
        
        md5UtilMockedStatic.close();
    }
    
    @Test
    public void testDoGetConfigV1() throws Exception {
        final MockedStatic<ConfigCacheService> configCacheServiceMockedStatic = Mockito
                .mockStatic(ConfigCacheService.class);
        final MockedStatic<DiskUtil> diskUtilMockedStatic = Mockito.mockStatic(DiskUtil.class);
        final MockedStatic<PropertyUtil> propertyUtilMockedStatic = Mockito.mockStatic(PropertyUtil.class);
        
        configCacheServiceMockedStatic.when(() -> ConfigCacheService.tryReadLock(anyString())).thenReturn(1);
        
        // isBeta: true
        CacheItem cacheItem = new CacheItem("test");
        cacheItem.setBeta(true);
        List<String> ips4Beta = new ArrayList<>();
        ips4Beta.add("localhost");
        cacheItem.setIps4Beta(ips4Beta);
        configCacheServiceMockedStatic.when(() -> ConfigCacheService.getContentCache(anyString()))
                .thenReturn(cacheItem);
        
        // if direct read is true
        propertyUtilMockedStatic.when(PropertyUtil::isDirectRead).thenReturn(true);
        ConfigInfoBetaWrapper configInfoBetaWrapper = new ConfigInfoBetaWrapper();
        configInfoBetaWrapper.setDataId("test");
        configInfoBetaWrapper.setGroup("test");
        configInfoBetaWrapper.setContent("isBeta:true, direct read: true");
        when(configInfoBetaPersistService.findConfigInfo4Beta(anyString(), anyString(), anyString()))
                .thenReturn(configInfoBetaWrapper);
        
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("localhost:8080");
        request.addHeader(CLIENT_APPNAME_HEADER, "test");
        MockHttpServletResponse response = new MockHttpServletResponse();
        String actualValue = configServletInner
                .doGetConfig(request, response, "test", "test", "test", "", "true", "localhost");
        Assert.assertEquals(HttpServletResponse.SC_OK + "", actualValue);
        Assert.assertEquals("true", response.getHeader("isBeta"));
        Assert.assertEquals("isBeta:true, direct read: true", response.getContentAsString());
        
        // if direct read is false
        propertyUtilMockedStatic.when(PropertyUtil::isDirectRead).thenReturn(false);
        File file = tempFolder.newFile("test.txt");
        diskUtilMockedStatic.when(() -> DiskUtil.targetBetaFile(anyString(), anyString(), anyString()))
                .thenReturn(file);
        response = new MockHttpServletResponse();
        actualValue = configServletInner
                .doGetConfig(request, response, "test", "test", "test", "", "true", "localhost");
        Assert.assertEquals(HttpServletResponse.SC_OK + "", actualValue);
        Assert.assertEquals("true", response.getHeader("isBeta"));
        Assert.assertEquals("", response.getContentAsString());
        
        configCacheServiceMockedStatic.close();
        diskUtilMockedStatic.close();
        propertyUtilMockedStatic.close();
    }
    
    @Test
    public void testDoGetConfigV2() throws Exception {
        
        final MockedStatic<ConfigCacheService> configCacheServiceMockedStatic = Mockito
                .mockStatic(ConfigCacheService.class);
        final MockedStatic<DiskUtil> diskUtilMockedStatic = Mockito.mockStatic(DiskUtil.class);
        final MockedStatic<PropertyUtil> propertyUtilMockedStatic = Mockito.mockStatic(PropertyUtil.class);
        
        configCacheServiceMockedStatic.when(() -> ConfigCacheService.tryReadLock(anyString())).thenReturn(1);
        
        // isBeta: false
        CacheItem cacheItem = new CacheItem("test");
        cacheItem.setBeta(false);
        List<String> ips4Beta = new ArrayList<>();
        ips4Beta.add("localhost");
        cacheItem.setIps4Beta(ips4Beta);
        configCacheServiceMockedStatic.when(() -> ConfigCacheService.getContentCache(anyString()))
                .thenReturn(cacheItem);
        
        // if tag is blank and direct read is true
        propertyUtilMockedStatic.when(PropertyUtil::isDirectRead).thenReturn(true);
        ConfigInfoWrapper configInfoWrapper = new ConfigInfoWrapper();
        configInfoWrapper.setDataId("test");
        configInfoWrapper.setGroup("test");
        configInfoWrapper.setContent("tag is blank and direct read is true");
        when(configInfoPersistService.findConfigInfo(anyString(), anyString(), anyString()))
                .thenReturn(configInfoWrapper);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("localhost:8080");
        request.addHeader(CLIENT_APPNAME_HEADER, "test");
        MockHttpServletResponse response = new MockHttpServletResponse();
        String actualValue = configServletInner
                .doGetConfig(request, response, "test", "test", "test", "", "true", "localhost");
        Assert.assertEquals(HttpServletResponse.SC_OK + "", actualValue);
        Assert.assertEquals("tag is blank and direct read is true", response.getContentAsString());
        
        // if tag is blank and direct read is false
        propertyUtilMockedStatic.when(PropertyUtil::isDirectRead).thenReturn(false);
        response = new MockHttpServletResponse();
        File file = tempFolder.newFile("test.txt");
        diskUtilMockedStatic.when(() -> DiskUtil.targetFile(anyString(), anyString(), anyString())).thenReturn(file);
        actualValue = configServletInner
                .doGetConfig(request, response, "test", "test", "test", "", "true", "localhost");
        Assert.assertEquals(HttpServletResponse.SC_OK + "", actualValue);
        Assert.assertEquals("", response.getContentAsString());
        
        // if tag is not blank and direct read is true
        propertyUtilMockedStatic.when(PropertyUtil::isDirectRead).thenReturn(true);
        ConfigInfoTagWrapper configInfoTagWrapper = new ConfigInfoTagWrapper();
        configInfoTagWrapper.setDataId("test");
        configInfoTagWrapper.setGroup("test");
        configInfoTagWrapper.setContent("tag is not blank and direct read is true");
        when(configInfoTagPersistService.findConfigInfo4Tag(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(configInfoTagWrapper);
        response = new MockHttpServletResponse();
        actualValue = configServletInner
                .doGetConfig(request, response, "test", "test", "test", "test", "true", "localhost");
        Assert.assertEquals(HttpServletResponse.SC_OK + "", actualValue);
        Assert.assertEquals("tag is not blank and direct read is true", response.getContentAsString());
        
        // if tag is not blank and direct read is false
        propertyUtilMockedStatic.when(PropertyUtil::isDirectRead).thenReturn(false);
        response = new MockHttpServletResponse();
        diskUtilMockedStatic.when(() -> DiskUtil.targetTagFile(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(file);
        actualValue = configServletInner
                .doGetConfig(request, response, "test", "test", "test", "test", "true", "localhost");
        Assert.assertEquals(HttpServletResponse.SC_OK + "", actualValue);
        Assert.assertEquals("", response.getContentAsString());
        
        // if use auto tag and direct read is true
        propertyUtilMockedStatic.when(PropertyUtil::isDirectRead).thenReturn(true);
        Map<String, String> tagMd5 = new HashMap<>();
        tagMd5.put("auto-tag-test", "auto-tag-test");
        cacheItem.setTagMd5(tagMd5);
        request.addHeader("Vipserver-Tag", "auto-tag-test");
        configInfoTagWrapper.setContent("auto tag mode and direct read is true");
        when(configInfoTagPersistService.findConfigInfo4Tag(anyString(), anyString(), anyString(), eq("auto-tag-test")))
                .thenReturn(configInfoTagWrapper);
        response = new MockHttpServletResponse();
        actualValue = configServletInner
                .doGetConfig(request, response, "test", "test", "test", "", "true", "localhost");
        Assert.assertEquals(HttpServletResponse.SC_OK + "", actualValue);
        Assert.assertEquals("auto tag mode and direct read is true", response.getContentAsString());
        
        // if use auto tag and direct read is false
        propertyUtilMockedStatic.when(PropertyUtil::isDirectRead).thenReturn(false);
        response = new MockHttpServletResponse();
        actualValue = configServletInner
                .doGetConfig(request, response, "test", "test", "test", "", "true", "localhost");
        Assert.assertEquals(HttpServletResponse.SC_OK + "", actualValue);
        Assert.assertEquals("", response.getContentAsString());
        
        configCacheServiceMockedStatic.close();
        diskUtilMockedStatic.close();
        propertyUtilMockedStatic.close();
    }
    
    @Test
    public void testDoGetConfigV3() throws Exception {
        
        final MockedStatic<ConfigCacheService> configCacheServiceMockedStatic = Mockito
                .mockStatic(ConfigCacheService.class);
        
        // if lockResult equals 0
        configCacheServiceMockedStatic.when(() -> ConfigCacheService.tryReadLock(anyString())).thenReturn(0);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        String actualValue = configServletInner
                .doGetConfig(request, response, "test", "test", "test", "test", "true", "localhost");
        Assert.assertEquals(HttpServletResponse.SC_NOT_FOUND + "", actualValue);
        
        // if lockResult less than 0
        configCacheServiceMockedStatic.when(() -> ConfigCacheService.tryReadLock(anyString())).thenReturn(-1);
        actualValue = configServletInner
                .doGetConfig(request, response, "test", "test", "test", "test", "true", "localhost");
        Assert.assertEquals(HttpServletResponse.SC_CONFLICT + "", actualValue);
        
        configCacheServiceMockedStatic.close();
    }
}
