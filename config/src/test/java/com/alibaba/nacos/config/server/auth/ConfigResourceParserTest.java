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

package com.alibaba.nacos.config.server.auth;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import javax.servlet.http.HttpServletRequest;

import static org.mockito.ArgumentMatchers.eq;

public class ConfigResourceParserTest {
    
    private ConfigResourceParser configResourceParser;
    
    @Before
    public void init() {
        configResourceParser = new ConfigResourceParser();
    }
    
    @Test
    public void testSuccess1x() {
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        Mockito.when(request.getParameter(eq("tenant"))).thenReturn("test");
        Mockito.when(request.getParameter(eq("group"))).thenReturn("test");
        Mockito.when(request.getParameter(eq("dataId"))).thenReturn("test");
        Assert.assertEquals("test:test:config/test", configResourceParser.parseName(request));
        
        Mockito.when(request.getParameter(eq("tenant"))).thenReturn("test");
        Mockito.when(request.getParameter(eq("group"))).thenReturn("APP");
        Mockito.when(request.getParameter(eq("dataId"))).thenReturn("dataId*");
        Assert.assertEquals("test:APP:config/dataId*", configResourceParser.parseName(request));
        
        Mockito.when(request.getParameter(eq("tenant"))).thenReturn("");
        Mockito.when(request.getParameter(eq("group"))).thenReturn("DEFAULT_GROUP");
        Mockito.when(request.getParameter(eq("dataId"))).thenReturn("dataId*");
        Assert.assertEquals(":DEFAULT_GROUP:config/dataId*", configResourceParser.parseName(request));
    }
    
    @Test
    public void testSuccess1xNoGroup() {
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        Mockito.when(request.getParameter(eq("tenant"))).thenReturn("test");
        Mockito.when(request.getParameter(eq("dataId"))).thenReturn("test");
        Assert.assertEquals("test:*:config/test", configResourceParser.parseName(request));
    }
    
    @Test
    public void testSuccess1xNoDataId() {
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        Mockito.when(request.getParameter(eq("tenant"))).thenReturn("test");
        Mockito.when(request.getParameter(eq("group"))).thenReturn("test");
        Assert.assertEquals("test:test:config/*", configResourceParser.parseName(request));
    }
    
    @Test
    public void testSuccess1xNoTenant() {
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        Mockito.when(request.getParameter(eq("group"))).thenReturn("test");
        Mockito.when(request.getParameter(eq("dataId"))).thenReturn("test");
        Assert.assertEquals(":test:config/test", configResourceParser.parseName(request));
    }
    
}
