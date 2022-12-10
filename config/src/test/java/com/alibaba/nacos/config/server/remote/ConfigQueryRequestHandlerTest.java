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
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.config.server.model.CacheItem;
import com.alibaba.nacos.config.server.service.ConfigCacheService;
import com.alibaba.nacos.config.server.utils.DiskUtil;
import com.alibaba.nacos.config.server.utils.GroupKey2;
import com.alibaba.nacos.config.server.utils.PropertyUtil;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.core.env.StandardEnvironment;

import java.io.File;
import java.io.IOException;

import static com.alibaba.nacos.api.common.Constants.ENCODE;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ConfigQueryRequestHandlerTest {
    
    @InjectMocks
    private ConfigQueryRequestHandler configQueryRequestHandler;
    
    @Mock
    private File file;
    
    @Before
    public void setUp() throws IOException {
        EnvUtil.setEnvironment(new StandardEnvironment());
    }
    
    @Test
    public void testHandle() throws NacosException {
        final MockedStatic<ConfigCacheService> configCacheServiceMockedStatic = Mockito
                .mockStatic(ConfigCacheService.class);
        final MockedStatic<FileUtils> fileUtilsMockedStatic = Mockito.mockStatic(FileUtils.class);
        final MockedStatic<DiskUtil> diskUtilMockedStatic = Mockito.mockStatic(DiskUtil.class);
        MockedStatic<PropertyUtil> propertyUtilMockedStatic = Mockito.mockStatic(PropertyUtil.class);
        
        propertyUtilMockedStatic.when(PropertyUtil::isDirectRead).thenReturn(false);
        
        final String groupKey = GroupKey2.getKey("dataId", "group", "");
        configCacheServiceMockedStatic.when(() -> ConfigCacheService.tryReadLock(groupKey)).thenReturn(1);
        diskUtilMockedStatic.when(() -> DiskUtil.targetFile(Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(file);
        fileUtilsMockedStatic.when(() -> FileUtils.readFileToString(file, ENCODE)).thenReturn("content");
        when(file.exists()).thenReturn(true);
        CacheItem cacheItem = new CacheItem(groupKey);
        cacheItem.setMd5("1");
        cacheItem.setLastModifiedTs(1L);
        configCacheServiceMockedStatic.when(() -> ConfigCacheService.getContentCache(Mockito.any()))
                .thenReturn(cacheItem);
        
        ConfigQueryRequest configQueryRequest = new ConfigQueryRequest();
        configQueryRequest.setDataId("dataId");
        configQueryRequest.setGroup("group");
        RequestMeta requestMeta = new RequestMeta();
        requestMeta.setClientIp("127.0.0.1");
        ConfigQueryResponse response = configQueryRequestHandler.handle(configQueryRequest, requestMeta);
        Assert.assertEquals(response.getContent(), "content");
        
        configCacheServiceMockedStatic.close();
        fileUtilsMockedStatic.close();
        diskUtilMockedStatic.close();
        propertyUtilMockedStatic.close();
    }
}
