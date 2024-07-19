/*
 * Copyright 1999-2021 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.core.utils;

import com.alibaba.nacos.common.constant.HttpHeaderConsts;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;

import javax.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;

/**
 * {@link WebUtils} unit tests.
 *
 * @author chenglu
 * @date 2021-06-10 13:33
 */
class WebUtilsTest {
    
    private static final String X_REAL_IP = "X-Real-IP";
    
    private static final String X_FORWARDED_FOR = "X-Forwarded-For";
    
    @Test
    void testRequired() {
        final String key = "key";
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        try {
            WebUtils.required(servletRequest, key);
        } catch (Exception e) {
            assertTrue(e instanceof IllegalArgumentException);
        }
        
        servletRequest.addParameter(key, "value");
        String val = WebUtils.required(servletRequest, key);
        assertEquals("value", val);
    }
    
    @Test
    void testOptional() {
        final String key = "key";
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        String val1 = WebUtils.optional(servletRequest, key, "value");
        assertEquals("value", val1);
        
        servletRequest.addParameter(key, "value1");
        assertEquals("value1", WebUtils.optional(servletRequest, key, "value"));
    }
    
    @Test
    void testGetUserAgent() {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        String userAgent = WebUtils.getUserAgent(servletRequest);
        assertEquals("", userAgent);
        
        servletRequest.addHeader(HttpHeaderConsts.CLIENT_VERSION_HEADER, "0");
        assertEquals("0", WebUtils.getUserAgent(servletRequest));
        
        servletRequest.addHeader(HttpHeaderConsts.USER_AGENT_HEADER, "1");
        assertEquals("1", WebUtils.getUserAgent(servletRequest));
    }
    
    @Test
    void testGetAcceptEncoding() {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        assertEquals(StandardCharsets.UTF_8.name(), WebUtils.getAcceptEncoding(servletRequest));
        
        servletRequest.addHeader(HttpHeaderConsts.ACCEPT_ENCODING, "gzip, deflate, br");
        assertEquals("gzip", WebUtils.getAcceptEncoding(servletRequest));
    }
    
    @Test
    void testGetRemoteIp() {
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        
        Mockito.when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        assertEquals("127.0.0.1", WebUtils.getRemoteIp(request));
        
        Mockito.when(request.getHeader(eq(X_REAL_IP))).thenReturn("127.0.0.2");
        assertEquals("127.0.0.2", WebUtils.getRemoteIp(request));
        
        Mockito.when(request.getHeader(eq(X_FORWARDED_FOR))).thenReturn("127.0.0.3");
        assertEquals("127.0.0.3", WebUtils.getRemoteIp(request));
        
        Mockito.when(request.getHeader(eq(X_FORWARDED_FOR))).thenReturn("127.0.0.3, 127.0.0.4");
        assertEquals("127.0.0.3", WebUtils.getRemoteIp(request));
        
        Mockito.when(request.getHeader(eq(X_FORWARDED_FOR))).thenReturn("");
        assertEquals("127.0.0.2", WebUtils.getRemoteIp(request));
        
        Mockito.when(request.getHeader(eq(X_REAL_IP))).thenReturn("");
        assertEquals("127.0.0.1", WebUtils.getRemoteIp(request));
    }
}
