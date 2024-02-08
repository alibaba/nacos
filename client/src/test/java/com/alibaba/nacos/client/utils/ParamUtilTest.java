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
import com.alibaba.nacos.common.utils.MD5Utils;
import com.alibaba.nacos.common.utils.VersionUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Properties;

import static org.junit.Assert.assertEquals;

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
        System.clearProperty("NACOS.CONNECT.TIMEOUT");
        System.clearProperty("PER_TASK_CONFIG_SIZE");
        System.clearProperty(PropertyKeyConst.SystemEnv.ALIBABA_ALIWARE_ENDPOINT_URL);
    }
    
    @Test
    public void testGetAppKey() {
        String defaultVal = ParamUtil.getAppKey();
        assertEquals(defaultAppKey, defaultVal);
        
        String expect = "test";
        ParamUtil.setAppKey(expect);
        assertEquals(expect, ParamUtil.getAppKey());
    }
    
    @Test
    public void testGetAppName() {
        String defaultVal = ParamUtil.getAppName();
        assertEquals(defaultAppName, defaultVal);
        
        String expect = "test";
        ParamUtil.setAppName(expect);
        assertEquals(expect, ParamUtil.getAppName());
    }
    
    @Test
    public void testGetDefaultContextPath() {
        String defaultVal = ParamUtil.getDefaultContextPath();
        assertEquals(defaultContextPath, defaultVal);
        
        String expect = "test";
        ParamUtil.setDefaultContextPath(expect);
        assertEquals(expect, ParamUtil.getDefaultContextPath());
    }
    
    @Test
    public void testGetClientVersion() {
        String defaultVal = ParamUtil.getClientVersion();
        assertEquals(defaultVersion, defaultVal);
        
        String expect = "test";
        ParamUtil.setClientVersion(expect);
        assertEquals(expect, ParamUtil.getClientVersion());
    }
    
    @Test
    public void testSetConnectTimeout() {
        int defaultVal = ParamUtil.getConnectTimeout();
        assertEquals(defaultConnectTimeout, defaultVal);
        
        int expect = 50;
        ParamUtil.setConnectTimeout(expect);
        assertEquals(expect, ParamUtil.getConnectTimeout());
    }
    
    @Test
    public void testGetPerTaskConfigSize() {
        double defaultVal = ParamUtil.getPerTaskConfigSize();
        assertEquals(defaultPerTaskConfigSize, defaultVal, 0.01);
        
        double expect = 50.0;
        ParamUtil.setPerTaskConfigSize(expect);
        assertEquals(expect, ParamUtil.getPerTaskConfigSize(), 0.01);
    }
    
    @Test
    public void testGetDefaultServerPort() {
        String actual = ParamUtil.getDefaultServerPort();
        assertEquals("8848", actual);
    }
    
    @Test
    public void testGetDefaultNodesPath() {
        String defaultVal = ParamUtil.getDefaultNodesPath();
        assertEquals("serverlist", defaultVal);
        
        String expect = "test";
        ParamUtil.setDefaultNodesPath(expect);
        assertEquals(expect, ParamUtil.getDefaultNodesPath());
    }
    
    @Test
    public void testParseNamespace() {
        String expect = "test";
        Properties properties = new Properties();
        properties.setProperty(PropertyKeyConst.NAMESPACE, expect);
        
        final NacosClientProperties nacosClientProperties = NacosClientProperties.PROTOTYPE.derive(properties);
        String actual = ParamUtil.parseNamespace(nacosClientProperties);
        assertEquals(expect, actual);
    }
    
    @Test
    public void testParsingEndpointRule() {
        String url = "${test:www.example.com}";
        String actual = ParamUtil.parsingEndpointRule(url);
        assertEquals("www.example.com", actual);
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testInitConnectionTimeoutWithException() throws Throwable {
        Method method = ParamUtil.class.getDeclaredMethod("initConnectionTimeout");
        method.setAccessible(true);
        System.setProperty("NACOS.CONNECT.TIMEOUT", "test");
        try {
            method.invoke(null);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testInitPerTaskConfigSizeWithException() throws Throwable {
        Method method = ParamUtil.class.getDeclaredMethod("initPerTaskConfigSize");
        method.setAccessible(true);
        System.setProperty("PER_TASK_CONFIG_SIZE", "test");
        try {
            method.invoke(null);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }
    
    @Test
    public void testParsingEndpointRuleFromSystem() {
        System.setProperty(PropertyKeyConst.SystemEnv.ALIBABA_ALIWARE_ENDPOINT_URL, "alibaba_aliware_endpoint_url");
        assertEquals("alibaba_aliware_endpoint_url", ParamUtil.parsingEndpointRule(null));
    }
    
    @Test
    public void testParsingEndpointRuleFromSystemWithParam() {
        System.setProperty(PropertyKeyConst.SystemEnv.ALIBABA_ALIWARE_ENDPOINT_URL, "alibaba_aliware_endpoint_url");
        assertEquals("alibaba_aliware_endpoint_url", ParamUtil.parsingEndpointRule("${abc:xxx}"));
    }
    
    @Test
    public void testSimplyEnvNameIfOverLimit() {
        StringBuilder envNameOverLimitBuilder = new StringBuilder("test");
        for (int i = 0; i < 50; i++) {
            envNameOverLimitBuilder.append(i);
        }
        String envName = envNameOverLimitBuilder.toString();
        String actual = ParamUtil.simplyEnvNameIfOverLimit(envName);
        String expect = envName.substring(0, 50) + MD5Utils.md5Hex(envName, "UTF-8");
        assertEquals(expect, actual);
    }
    
    @Test public void testSimplyEnvNameNotOverLimit() {
        String expect = "test";
        assertEquals(expect, ParamUtil.simplyEnvNameIfOverLimit(expect));
    }
}