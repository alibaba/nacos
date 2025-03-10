/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.client.utils;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.client.env.NacosClientProperties;
import com.alibaba.nacos.client.env.SourceType;
import com.alibaba.nacos.common.utils.VersionUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientBasicParamUtilTest {
    
    private String defaultAppKey;
    
    private String defaultContextPath;
    
    private String defaultVersion;
    
    private String defaultNodesPath;
    
    @BeforeEach
    void before() {
        defaultAppKey = "";
        defaultContextPath = "nacos";
        defaultVersion = VersionUtils.version;
        defaultNodesPath = "serverlist";
    }
    
    @AfterEach
    void after() {
        ClientBasicParamUtil.setAppKey(defaultAppKey);
        ClientBasicParamUtil.setDefaultContextPath(defaultContextPath);
        ClientBasicParamUtil.setClientVersion(defaultVersion);
        ClientBasicParamUtil.setDefaultNodesPath(defaultNodesPath);
        System.clearProperty(PropertyKeyConst.SystemEnv.ALIBABA_ALIWARE_ENDPOINT_URL);
    }
    
    @Test
    void testGetAppKey() {
        String defaultVal = ClientBasicParamUtil.getAppKey();
        assertEquals(defaultAppKey, defaultVal);
        
        String expect = "test";
        ClientBasicParamUtil.setAppKey(expect);
        assertEquals(expect, ClientBasicParamUtil.getAppKey());
    }
    
    @Test
    void testGetDefaultContextPath() {
        String defaultVal = ClientBasicParamUtil.getDefaultContextPath();
        assertEquals(defaultContextPath, defaultVal);
        
        String expect = "test";
        ClientBasicParamUtil.setDefaultContextPath(expect);
        assertEquals(expect, ClientBasicParamUtil.getDefaultContextPath());
    }
    
    @Test
    void testGetClientVersion() {
        String defaultVal = ClientBasicParamUtil.getClientVersion();
        assertEquals(defaultVersion, defaultVal);
        
        String expect = "test";
        ClientBasicParamUtil.setClientVersion(expect);
        assertEquals(expect, ClientBasicParamUtil.getClientVersion());
    }
    
    @Test
    void testGetDefaultServerPort() {
        String actual = ClientBasicParamUtil.getDefaultServerPort();
        assertEquals("8848", actual);
    }
    
    @Test
    void testGetDefaultNodesPath() {
        String defaultVal = ClientBasicParamUtil.getDefaultNodesPath();
        assertEquals("serverlist", defaultVal);
        
        String expect = "test";
        ClientBasicParamUtil.setDefaultNodesPath(expect);
        assertEquals(expect, ClientBasicParamUtil.getDefaultNodesPath());
    }
    
    @Test
    void testParseNamespace() {
        String expect = "test";
        Properties properties = new Properties();
        properties.setProperty(PropertyKeyConst.NAMESPACE, expect);
        
        final NacosClientProperties nacosClientProperties = NacosClientProperties.PROTOTYPE.derive(properties);
        String actual = ClientBasicParamUtil.parseNamespace(nacosClientProperties);
        assertEquals(expect, actual);
    }
    
    @Test
    void testParsingEndpointRule() {
        String url = "${test:www.example.com}";
        String actual = ClientBasicParamUtil.parsingEndpointRule(url);
        assertEquals("www.example.com", actual);
    }
    
    @Test
    void testParsingEndpointRuleFromSystem() {
        System.setProperty(PropertyKeyConst.SystemEnv.ALIBABA_ALIWARE_ENDPOINT_URL, "alibaba_aliware_endpoint_url");
        assertEquals("alibaba_aliware_endpoint_url", ClientBasicParamUtil.parsingEndpointRule(null));
    }
    
    @Test
    void testParsingEndpointRuleFromSystemWithParam() {
        System.setProperty(PropertyKeyConst.SystemEnv.ALIBABA_ALIWARE_ENDPOINT_URL, "alibaba_aliware_endpoint_url");
        assertEquals("alibaba_aliware_endpoint_url", ClientBasicParamUtil.parsingEndpointRule("${abc:xxx}"));
    }
    
    @Test
    void testGetInputParametersWithFullMode() {
        Properties properties = new Properties();
        properties.setProperty("testKey", "testValue");
        properties.setProperty(PropertyKeyConst.LOG_ALL_PROPERTIES, "true");
        NacosClientProperties clientProperties = NacosClientProperties.PROTOTYPE.derive(properties);
        String actual = ClientBasicParamUtil.getInputParameters(clientProperties.asProperties());
        assertTrue(actual.startsWith(
                "Log nacos client init properties with Full mode, This mode is only used for debugging and troubleshooting."));
        assertTrue(actual.contains("\ttestKey=testValue\n"));
        Properties envProperties = clientProperties.getProperties(SourceType.ENV);
        String envCaseKey = envProperties.stringPropertyNames().iterator().next();
        String envCaseValue = envProperties.getProperty(envCaseKey);
        assertTrue(actual.contains(String.format("\t%s=%s\n", envCaseKey, envCaseValue)));
    }
    
    @Test
    void testGetInputParameters() {
        Properties properties = new Properties();
        properties.setProperty("testKey", "testValue");
        properties.setProperty(PropertyKeyConst.SERVER_ADDR, "localhost:8848");
        properties.setProperty(PropertyKeyConst.PASSWORD, "testPassword");
        NacosClientProperties clientProperties = NacosClientProperties.PROTOTYPE.derive(properties);
        String actual = ClientBasicParamUtil.getInputParameters(clientProperties.asProperties());
        assertEquals("Nacos client key init properties: \n\tserverAddr=localhost:8848\n\tpassword=te********rd\n",
                actual);
    }
    
    @Test
    void testDesensitiseParameter() {
        String shortParameter = "aa";
        assertEquals(shortParameter, ClientBasicParamUtil.desensitiseParameter(shortParameter));
        String middleParameter = "aaa";
        assertEquals("a*a", ClientBasicParamUtil.desensitiseParameter(middleParameter));
        middleParameter = "aaaaaaa";
        assertEquals("a*****a", ClientBasicParamUtil.desensitiseParameter(middleParameter));
        String longParameter = "testPass";
        assertEquals("te****ss", ClientBasicParamUtil.desensitiseParameter(longParameter));
    }
    
    @Test
    void testGetNameSuffixByServerIps() {
        assertEquals("1.1.1.1-2.2.2.2_8848",
                ClientBasicParamUtil.getNameSuffixByServerIps("http://1.1.1.1", "2.2.2.2:8848"));
    }
}