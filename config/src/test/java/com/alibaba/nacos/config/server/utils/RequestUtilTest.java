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

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import javax.servlet.http.HttpServletRequest;

import static org.mockito.ArgumentMatchers.eq;

public class RequestUtilTest {
    
    private static final String X_REAL_IP = "X-Real-IP";
    
    private static final String X_FORWARDED_FOR = "X-Forwarded-For";
    
    @Test
    public void getRemoteIp() {
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        
        Mockito.when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        Assert.assertEquals(RequestUtil.getRemoteIp(request), "127.0.0.1");
        
        Mockito.when(request.getHeader(eq(X_REAL_IP))).thenReturn("127.0.0.2");
        Assert.assertEquals(RequestUtil.getRemoteIp(request), "127.0.0.2");
        
        Mockito.when(request.getHeader(eq(X_FORWARDED_FOR))).thenReturn("127.0.0.3");
        Assert.assertEquals(RequestUtil.getRemoteIp(request), "127.0.0.3");
        
        Mockito.when(request.getHeader(eq(X_FORWARDED_FOR))).thenReturn("127.0.0.3, 127.0.0.4");
        Assert.assertEquals(RequestUtil.getRemoteIp(request), "127.0.0.3");
        
        Mockito.when(request.getHeader(eq(X_FORWARDED_FOR))).thenReturn("");
        Assert.assertEquals(RequestUtil.getRemoteIp(request), "127.0.0.2");
        
        Mockito.when(request.getHeader(eq(X_REAL_IP))).thenReturn("");
        Assert.assertEquals(RequestUtil.getRemoteIp(request), "127.0.0.1");
    }
    
    @Test
    public void getAppName() {
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        Mockito.when(request.getHeader(eq(RequestUtil.CLIENT_APPNAME_HEADER))).thenReturn("test");
        Assert.assertEquals(RequestUtil.getAppName(request), "test");
    }
}
