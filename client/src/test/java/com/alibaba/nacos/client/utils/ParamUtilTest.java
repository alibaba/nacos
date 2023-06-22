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
import com.alibaba.nacos.client.env.NacosClientProperties;
import com.alibaba.nacos.common.utils.VersionUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Properties;

public class ParamUtilTest {
    
    private String defaultAppKey;
    
    private String defaultAppName;
    
    private String defaultContextPath;
    
    private String defaultVersion;
    
    private int defaultConnectTimeout;
    
    private double defaultPerTaskConfigSize;
    
    private String defaultNodesPath;
    
    @Before
    public void before() {
        defaultAppKey = "";
        defaultAppName = "unknown";
        defaultContextPath = "nacos";
        defaultVersion = VersionUtils.version;
        defaultConnectTimeout = 1000;
        defaultPerTaskConfigSize = 3000.0;
        defaultNodesPath = "serverlist";
        
    }
    
    @After
    public void after() {
        ParamUtil.setAppKey(defaultAppKey);
        ParamUtil.setAppName(defaultAppName);
        ParamUtil.setDefaultContextPath(defaultContextPath);
        ParamUtil.setClientVersion(defaultVersion);
        ParamUtil.setConnectTimeout(defaultConnectTimeout);
        ParamUtil.setPerTaskConfigSize(defaultPerTaskConfigSize);
        ParamUtil.setDefaultNodesPath(defaultNodesPath);
    }
    
    @Test
    public void testGetAppKey() {
        String defaultVal = ParamUtil.getAppKey();
        Assert.assertEquals(defaultAppKey, defaultVal);
        
        String expect = "test";
        ParamUtil.setAppKey(expect);
        Assert.assertEquals(expect, ParamUtil.getAppKey());
    }
    
    @Test
    public void testGetAppName() {
        String defaultVal = ParamUtil.getAppName();
        Assert.assertEquals(defaultAppName, defaultVal);
        
        String expect = "test";
        ParamUtil.setAppName(expect);
        Assert.assertEquals(expect, ParamUtil.getAppName());
    }
    
    @Test
    public void testGetDefaultContextPath() {
        String defaultVal = ParamUtil.getDefaultContextPath();
        Assert.assertEquals(defaultContextPath, defaultVal);
        
        String expect = "test";
        ParamUtil.setDefaultContextPath(expect);
        Assert.assertEquals(expect, ParamUtil.getDefaultContextPath());
    }
    
    @Test
    public void testGetClientVersion() {
        String defaultVal = ParamUtil.getClientVersion();
        Assert.assertEquals(defaultVersion, defaultVal);
        
        String expect = "test";
        ParamUtil.setClientVersion(expect);
        Assert.assertEquals(expect, ParamUtil.getClientVersion());
    }
    
    @Test
    public void testSetConnectTimeout() {
        int defaultVal = ParamUtil.getConnectTimeout();
        Assert.assertEquals(defaultConnectTimeout, defaultVal);
        
        int expect = 50;
        ParamUtil.setConnectTimeout(expect);
        Assert.assertEquals(expect, ParamUtil.getConnectTimeout());
    }
    
    @Test
    public void testGetPerTaskConfigSize() {
        double defaultVal = ParamUtil.getPerTaskConfigSize();
        Assert.assertEquals(defaultPerTaskConfigSize, defaultVal, 0.01);
        
        double expect = 50.0;
        ParamUtil.setPerTaskConfigSize(expect);
        Assert.assertEquals(expect, ParamUtil.getPerTaskConfigSize(), 0.01);
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
    }
    
    @Test
    public void testParseNamespace() {
        String expect = "test";
        Properties properties = new Properties();
        properties.setProperty(PropertyKeyConst.NAMESPACE, expect);
    
        final NacosClientProperties nacosClientProperties = NacosClientProperties.PROTOTYPE.derive(properties);
        String actual = ParamUtil.parseNamespace(nacosClientProperties);
        Assert.assertEquals(expect, actual);
    }
    
    @Test
    public void testParsingEndpointRule() {
        String url = "${test:www.example.com}";
        String actual = ParamUtil.parsingEndpointRule(url);
        Assert.assertEquals("www.example.com", actual);
    }
}