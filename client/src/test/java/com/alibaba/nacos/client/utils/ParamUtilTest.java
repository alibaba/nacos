/*
 *
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
 *
 */

package com.alibaba.nacos.client.utils;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.common.utils.VersionUtils;
import org.junit.Assert;
import org.junit.Test;

import java.util.Properties;

public class ParamUtilTest {
    
    @Test
    public void testGetAppKey() {
        String defaultVal = ParamUtil.getAppKey();
        Assert.assertEquals("", defaultVal);
        
        String expect = "test";
        ParamUtil.setAppKey(expect);
        Assert.assertEquals(expect, ParamUtil.getAppKey());
        
        ParamUtil.setAppKey(defaultVal);
    }
    
    @Test
    public void testGetAppName() {
        String defaultVal = ParamUtil.getAppName();
        Assert.assertEquals("unknown", defaultVal);
        
        String expect = "test";
        ParamUtil.setAppName(expect);
        Assert.assertEquals(expect, ParamUtil.getAppName());
        
        ParamUtil.setAppName(defaultVal);
    }
    
    @Test
    public void testGetDefaultContextPath() {
        String defaultVal = ParamUtil.getDefaultContextPath();
        Assert.assertEquals("nacos", defaultVal);
        
        String expect = "test";
        ParamUtil.setDefaultContextPath(expect);
        Assert.assertEquals(expect, ParamUtil.getDefaultContextPath());
        
        ParamUtil.setDefaultContextPath(defaultVal);
    }
    
    @Test
    public void testGetClientVersion() {
        String defaultVal = ParamUtil.getClientVersion();
        Assert.assertEquals(VersionUtils.version, defaultVal);
        
        String expect = "test";
        ParamUtil.setClientVersion(expect);
        Assert.assertEquals(expect, ParamUtil.getClientVersion());
        
        ParamUtil.setClientVersion(defaultVal);
    }
    
    @Test
    public void testSetConnectTimeout() {
        int defaultVal = ParamUtil.getConnectTimeout();
        Assert.assertEquals(1000, defaultVal);
        
        int expect = 50;
        ParamUtil.setConnectTimeout(expect);
        Assert.assertEquals(expect, ParamUtil.getConnectTimeout());
        
        ParamUtil.setConnectTimeout(defaultVal);
    }
    
    @Test
    public void testGetPerTaskConfigSize() {
        double defaultVal = ParamUtil.getPerTaskConfigSize();
        Assert.assertEquals(3000.0, defaultVal, 0.01);
        
        double expect = 50.0;
        ParamUtil.setPerTaskConfigSize(expect);
        Assert.assertEquals(expect, ParamUtil.getPerTaskConfigSize(), 0.01);
        
        ParamUtil.setPerTaskConfigSize(defaultVal);
    }
    
    @Test
    public void testGetDefaultServerPort() {
        String actual = ParamUtil.getDefaultServerPort();
        Assert.assertEquals("8848", actual);
    }
    
    @Test
    public void testGetDefaultNodesPath() {
        String defaultVal = ParamUtil.getDefaultNodesPath();
        Assert.assertEquals("serverlist", defaultVal);
        
        String expect = "test";
        ParamUtil.setDefaultNodesPath(expect);
        Assert.assertEquals(expect, ParamUtil.getDefaultNodesPath());
        
        ParamUtil.setDefaultNodesPath(defaultVal);
    }
    
    @Test
    public void testParseNamespace() {
        String expect = "test";
        Properties properties = new Properties();
        properties.setProperty(PropertyKeyConst.NAMESPACE, expect);
        String actual = ParamUtil.parseNamespace(properties);
        Assert.assertEquals(expect, actual);
    }
    
    @Test
    public void testParsingEndpointRule() {
        String url = "${test:www.example.com}";
        String actual = ParamUtil.parsingEndpointRule(url);
        Assert.assertEquals("www.example.com", actual);
    }
}