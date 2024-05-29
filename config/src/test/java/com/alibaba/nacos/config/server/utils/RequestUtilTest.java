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

package com.alibaba.nacos.config.server.utils;

import com.alibaba.nacos.api.common.Constants;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;

class RequestUtilTest {
    
    private static final String X_REAL_IP = "X-Real-IP";
    
    private static final String X_FORWARDED_FOR = "X-Forwarded-For";
    
    @Test
    void testGetRemoteIp() {
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        
        Mockito.when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        assertEquals("127.0.0.1", RequestUtil.getRemoteIp(request));
        
        Mockito.when(request.getHeader(eq(X_REAL_IP))).thenReturn("127.0.0.2");
        assertEquals("127.0.0.2", RequestUtil.getRemoteIp(request));
        
        Mockito.when(request.getHeader(eq(X_FORWARDED_FOR))).thenReturn("127.0.0.3");
        assertEquals("127.0.0.3", RequestUtil.getRemoteIp(request));
        
        Mockito.when(request.getHeader(eq(X_FORWARDED_FOR))).thenReturn("127.0.0.3, 127.0.0.4");
        assertEquals("127.0.0.3", RequestUtil.getRemoteIp(request));
        
        Mockito.when(request.getHeader(eq(X_FORWARDED_FOR))).thenReturn("");
        assertEquals("127.0.0.2", RequestUtil.getRemoteIp(request));
        
        Mockito.when(request.getHeader(eq(X_REAL_IP))).thenReturn("");
        assertEquals("127.0.0.1", RequestUtil.getRemoteIp(request));
    }
    
    @Test
    void testGetAppName() {
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        Mockito.when(request.getHeader(eq(RequestUtil.CLIENT_APPNAME_HEADER))).thenReturn("test");
        assertEquals("test", RequestUtil.getAppName(request));
    }
    
    @Test
    void testGetSrcUserNameV1() {
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        HttpSession session = Mockito.mock(HttpSession.class);
        Mockito.when(request.getSession()).thenReturn(session);
        Mockito.when(session.getAttribute(eq(com.alibaba.nacos.plugin.auth.constant.Constants.Identity.IDENTITY_ID))).thenReturn("test");
        assertEquals("test", RequestUtil.getSrcUserName(request));
    }
    
    @Test
    void testGetSrcUserNameV2() {
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        HttpSession session = Mockito.mock(HttpSession.class);
        Mockito.when(request.getSession()).thenReturn(session);
        Mockito.when(session.getAttribute(eq(com.alibaba.nacos.plugin.auth.constant.Constants.Identity.IDENTITY_ID))).thenReturn(null);
        Mockito.when(request.getParameter(eq(Constants.USERNAME))).thenReturn("parameterName");
        assertEquals("parameterName", RequestUtil.getSrcUserName(request));
    }
    
}
