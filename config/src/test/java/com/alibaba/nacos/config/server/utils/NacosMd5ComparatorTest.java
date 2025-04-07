/*
 * Copyright 1999-2024 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.config.server.utils;

import com.alibaba.nacos.config.server.service.ConfigCacheService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.List;

import static com.alibaba.nacos.api.common.Constants.VIPSERVER_TAG;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NacosMd5ComparatorTest {
    
    MockedStatic<RequestUtil> mockRequestUtil;
    
    MockedStatic<ConfigCacheService> configCacheServiceMockedStatic;
    
    @Mock
    HttpServletRequest request;
    
    @Mock
    HttpServletResponse response;
    
    @BeforeEach
    void setUp() {
        mockRequestUtil = mockStatic(RequestUtil.class);
        configCacheServiceMockedStatic = mockStatic(ConfigCacheService.class);
    }
    
    @AfterEach
    void tearDown() {
        mockRequestUtil.close();
        configCacheServiceMockedStatic.close();
    }
    
    @Test
    void getName() {
        NacosMd5Comparator nacosMd5Comparator = new NacosMd5Comparator();
        assertEquals("nacos", nacosMd5Comparator.getName());
    }
    
    @Test
    void compareMd5NoChange() {
        String ip = "127.0.0.1";
        String tag = "tag";
        when(request.getHeader(VIPSERVER_TAG)).thenReturn(tag);
        mockRequestUtil.when(() -> RequestUtil.getRemoteIp(request)).thenReturn(ip);
        
        String groupKey1 = "groupKey1";
        String groupKey2 = "groupKey2";
        String clientMd5 = "clientMd5";
        HashMap<String, String> clientMd5Map = new HashMap<>();
        clientMd5Map.put(groupKey1, clientMd5);
        clientMd5Map.put(groupKey2, clientMd5);
        
        NacosMd5Comparator nacosMd5Comparator = new NacosMd5Comparator();
        configCacheServiceMockedStatic.when(
                () -> ConfigCacheService.isUptodate(anyString(), eq(clientMd5), eq(ip), eq(tag))).thenReturn(true);
        
        List<String> changedGroupKeys = nacosMd5Comparator.compareMd5(request, response, clientMd5Map);
        assertEquals(0, changedGroupKeys.size());
    }
    
    @Test
    void compareMd5Change() {
        String ip = "127.0.0.1";
        String tag = "tag";
        when(request.getHeader(VIPSERVER_TAG)).thenReturn(tag);
        mockRequestUtil.when(() -> RequestUtil.getRemoteIp(request)).thenReturn(ip);
        
        String groupKey1 = "groupKey1";
        String groupKey2 = "groupKey2";
        String clientMd5 = "clientMd5";
        HashMap<String, String> clientMd5Map = new HashMap<>();
        clientMd5Map.put(groupKey1, clientMd5);
        clientMd5Map.put(groupKey2, clientMd5);
        
        NacosMd5Comparator nacosMd5Comparator = new NacosMd5Comparator();
        configCacheServiceMockedStatic.when(
                () -> ConfigCacheService.isUptodate(anyString(), eq(clientMd5), eq(ip), eq(tag))).thenReturn(false);
        
        List<String> changedGroupKeys = nacosMd5Comparator.compareMd5(request, response, clientMd5Map);
        assertEquals(2, changedGroupKeys.size());
    }
}