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

package com.alibaba.nacos.naming.web;

import com.alibaba.nacos.api.naming.CommonParams;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import javax.servlet.http.HttpServletRequest;

import static org.mockito.ArgumentMatchers.eq;

public class NamingResourceParserTest {
    
    private NamingResourceParser namingResourceParser;
    
    @Before
    public void init() {
        namingResourceParser = new NamingResourceParser();
    }
    
    @Test
    public void testSuccess1x() {
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        Mockito.when(request.getParameter(eq(CommonParams.NAMESPACE_ID))).thenReturn("test");
        Mockito.when(request.getParameter(eq(CommonParams.GROUP_NAME))).thenReturn("test");
        Mockito.when(request.getParameter(eq(CommonParams.SERVICE_NAME))).thenReturn("test");
        Assert.assertEquals("test:test:naming/test", namingResourceParser.parseName(request));
        
        Mockito.when(request.getParameter(eq(CommonParams.NAMESPACE_ID))).thenReturn("test");
        Mockito.when(request.getParameter(eq(CommonParams.GROUP_NAME))).thenReturn("APP");
        Mockito.when(request.getParameter(eq(CommonParams.SERVICE_NAME))).thenReturn("dataId*");
        Assert.assertEquals("test:APP:naming/dataId*", namingResourceParser.parseName(request));
        
        Mockito.when(request.getParameter(eq(CommonParams.NAMESPACE_ID))).thenReturn("");
        Mockito.when(request.getParameter(eq(CommonParams.GROUP_NAME))).thenReturn("DEFAULT_GROUP");
        Mockito.when(request.getParameter(eq(CommonParams.SERVICE_NAME))).thenReturn("Service*");
        Assert.assertEquals(":DEFAULT_GROUP:naming/Service*", namingResourceParser.parseName(request));
        
        // groupName is blank and serviceName has @@
        Mockito.when(request.getParameter(eq(CommonParams.NAMESPACE_ID))).thenReturn("test");
        Mockito.when(request.getParameter(eq(CommonParams.GROUP_NAME))).thenReturn("");
        Mockito.when(request.getParameter(eq(CommonParams.SERVICE_NAME))).thenReturn("dev@@Ssss");
        Assert.assertEquals("test:dev:naming/Ssss", namingResourceParser.parseName(request));
    }
    
    @Test
    public void testSuccess1xNoGroup() {
        // groupName is blank and serviceName don't have @@
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        Mockito.when(request.getParameter(eq(CommonParams.NAMESPACE_ID))).thenReturn("test");
        Mockito.when(request.getParameter(eq(CommonParams.SERVICE_NAME))).thenReturn("test");
        Assert.assertEquals("test:DEFAULT_GROUP:naming/test", namingResourceParser.parseName(request));
        
        // groupName is blank and serviceName has @@
        Mockito.when(request.getParameter(eq(CommonParams.NAMESPACE_ID))).thenReturn("test");
        Mockito.when(request.getParameter(eq(CommonParams.SERVICE_NAME))).thenReturn("dev@@Ssss");
        Assert.assertEquals("test:dev:naming/Ssss", namingResourceParser.parseName(request));
    }
    
    @Test
    public void testSuccess1xNoServiceName() {
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        Mockito.when(request.getParameter(eq(CommonParams.NAMESPACE_ID))).thenReturn("test");
        Mockito.when(request.getParameter(eq(CommonParams.GROUP_NAME))).thenReturn("test");
        Assert.assertEquals("test:test:naming/*", namingResourceParser.parseName(request));
    }
    
    @Test
    public void testSuccess1xNoNamespace() {
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        Mockito.when(request.getParameter(eq(CommonParams.GROUP_NAME))).thenReturn("test");
        Mockito.when(request.getParameter(eq(CommonParams.SERVICE_NAME))).thenReturn("test");
        Assert.assertEquals(":test:naming/test", namingResourceParser.parseName(request));
    }
    
}
