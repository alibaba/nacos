/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.client.auth.impl.process;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.client.auth.impl.NacosAuthLoginConstant;
import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.http.client.NacosRestTemplate;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.plugin.auth.api.LoginIdentityContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HttpLoginProcessorTest {
    
    @Mock
    NacosRestTemplate restTemplate;
    
    @Mock
    HttpRestResult result;
    
    Properties properties;
    
    HttpLoginProcessor loginProcessor;
    
    @BeforeEach
    void setUp() {
        loginProcessor = new HttpLoginProcessor(restTemplate);
        properties = new Properties();
    }
    
    @Test
    void testGetResponseSuccess() throws Exception {
        properties.setProperty(NacosAuthLoginConstant.SERVER, "http://localhost:8848");
        when(restTemplate.postForm(eq("http://localhost:8848/nacos/v1/auth/users/login"), eq(Header.EMPTY),
                any(Query.class), anyMap(), eq(String.class))).thenReturn(result);
        when(result.ok()).thenReturn(true);
        Map<String, String> mockMap = new HashMap<>();
        mockMap.put(Constants.ACCESS_TOKEN, "mock_access_token");
        mockMap.put(Constants.TOKEN_TTL, "100L");
        when(result.getData()).thenReturn(JacksonUtils.toJson(mockMap));
        LoginIdentityContext actual = loginProcessor.getResponse(properties);
        assertEquals("mock_access_token", actual.getParameter(NacosAuthLoginConstant.ACCESSTOKEN));
        assertEquals("100L", actual.getParameter(NacosAuthLoginConstant.TOKENTTL));
    }
    
    @Test
    void testGetResponseFailed() throws Exception {
        properties.setProperty(NacosAuthLoginConstant.SERVER, "localhost");
        when(restTemplate.postForm(eq("http://localhost:8848/nacos/v1/auth/users/login"), eq(Header.EMPTY),
                any(Query.class), anyMap(), eq(String.class))).thenReturn(result);
        assertNull(loginProcessor.getResponse(properties));
    }
    
    @Test
    void testGetResponseException() throws Exception {
        properties.setProperty(NacosAuthLoginConstant.SERVER, "localhost");
        when(restTemplate.postForm(eq("http://localhost:8848/nacos/v1/auth/users/login"), eq(Header.EMPTY),
                any(Query.class), anyMap(), eq(String.class))).thenThrow(new RuntimeException("test"));
        assertNull(loginProcessor.getResponse(properties));
    }
}