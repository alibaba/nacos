/*
 *   Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package com.alibaba.nacos.client.naming.utils;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.SystemPropertyKeyConst;
import com.alibaba.nacos.client.env.NacosClientProperties;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

public class InitUtilsTest {
    
    @After
    public void tearDown() {
        System.clearProperty(SystemPropertyKeyConst.IS_USE_CLOUD_NAMESPACE_PARSING);
        System.clearProperty(SystemPropertyKeyConst.ANS_NAMESPACE);
        System.clearProperty(PropertyKeyConst.NAMESPACE);
        System.clearProperty(SystemPropertyKeyConst.IS_USE_ENDPOINT_PARSING_RULE);
        System.clearProperty(PropertyKeyConst.SystemEnv.ALIBABA_ALIWARE_ENDPOINT_URL);
        System.clearProperty(PropertyKeyConst.SystemEnv.ALIBABA_ALIWARE_ENDPOINT_PORT);
        UtilAndComs.webContext = "/nacos";
        UtilAndComs.nacosUrlBase = "/nacos/v1/ns";
        UtilAndComs.nacosUrlInstance = "/nacos/v1/ns/instance";
    }
    
    /**
     * current namespace priority 1. system.Properties 2. user.Properties 3. default value
     */
    @Test
    public void testInitNamespaceForDefault() {
        //DEFAULT
        final NacosClientProperties properties = NacosClientProperties.PROTOTYPE.derive();
        String actual = InitUtils.initNamespaceForNaming(properties);
        Assert.assertEquals(UtilAndComs.DEFAULT_NAMESPACE_ID, actual);
    }
    
    @Test
    public void testInitNamespaceFromAnsWithCloudParsing() {
        String expect = "ans";
        System.setProperty(SystemPropertyKeyConst.ANS_NAMESPACE, expect);
        final NacosClientProperties properties = NacosClientProperties.PROTOTYPE.derive();
        properties.setProperty(PropertyKeyConst.IS_USE_CLOUD_NAMESPACE_PARSING, "true");
        String actual = InitUtils.initNamespaceForNaming(properties);
        Assert.assertEquals(expect, actual);
    }
    
    @Test
    public void testInitNamespaceFromAliwareWithCloudParsing() {
        String expect = "aliware";
        System.setProperty(SystemPropertyKeyConst.IS_USE_CLOUD_NAMESPACE_PARSING, "true");
        final NacosClientProperties properties = NacosClientProperties.PROTOTYPE.derive();
        properties.setProperty(PropertyKeyConst.SystemEnv.ALIBABA_ALIWARE_NAMESPACE, expect);
        String actual = InitUtils.initNamespaceForNaming(properties);
        Assert.assertEquals(expect, actual);
    }
    
    @Test
    public void testInitNamespaceFromJvmNamespaceWithCloudParsing() {
        String expect = "jvm_namespace";
        System.setProperty(PropertyKeyConst.NAMESPACE, expect);
        final NacosClientProperties properties = NacosClientProperties.PROTOTYPE.derive();
        String ns = InitUtils.initNamespaceForNaming(properties);
        Assert.assertEquals(expect, ns);
    }
    
    @Test
    public void testInitNamespaceFromPropNamespaceWithCloudParsing() {
        final NacosClientProperties properties = NacosClientProperties.PROTOTYPE.derive();
        String expect = "ns1";
        properties.setProperty(PropertyKeyConst.NAMESPACE, expect);
        String ns = InitUtils.initNamespaceForNaming(properties);
        Assert.assertEquals(expect, ns);
    }
    
    @Test
    public void testInitNamespaceFromDefaultNamespaceWithCloudParsing() {
        final NacosClientProperties properties = NacosClientProperties.PROTOTYPE.derive();
        properties.setProperty(PropertyKeyConst.IS_USE_CLOUD_NAMESPACE_PARSING, "true");
        String actual = InitUtils.initNamespaceForNaming(properties);
        Assert.assertEquals(UtilAndComs.DEFAULT_NAMESPACE_ID, actual);
    }
    
    @Test
    public void testInitNamespaceFromJvmNamespaceWithoutCloudParsing() {
        System.setProperty(SystemPropertyKeyConst.ANS_NAMESPACE, "ans");
        String expect = "jvm_namespace";
        System.setProperty(PropertyKeyConst.NAMESPACE, expect);
        final NacosClientProperties properties = NacosClientProperties.PROTOTYPE.derive();
        properties.setProperty(PropertyKeyConst.IS_USE_CLOUD_NAMESPACE_PARSING, "false");
        String ns = InitUtils.initNamespaceForNaming(properties);
        Assert.assertEquals(expect, ns);
    }
    
    @Test
    public void testInitNamespaceFromPropNamespaceWithoutCloudParsing() {
        System.setProperty(SystemPropertyKeyConst.ANS_NAMESPACE, "ans");
        System.setProperty(SystemPropertyKeyConst.IS_USE_CLOUD_NAMESPACE_PARSING, "false");
        final NacosClientProperties properties = NacosClientProperties.PROTOTYPE.derive();
        String expect = "ns1";
        properties.setProperty(PropertyKeyConst.NAMESPACE, expect);
        String ns = InitUtils.initNamespaceForNaming(properties);
        Assert.assertEquals(expect, ns);
    }
    
    @Test
    public void testInitNamespaceFromDefaultNamespaceWithoutCloudParsing() {
        System.setProperty(SystemPropertyKeyConst.ANS_NAMESPACE, "ans");
        final NacosClientProperties properties = NacosClientProperties.PROTOTYPE.derive();
        properties.setProperty(PropertyKeyConst.IS_USE_CLOUD_NAMESPACE_PARSING, "false");
        String actual = InitUtils.initNamespaceForNaming(properties);
        Assert.assertEquals(UtilAndComs.DEFAULT_NAMESPACE_ID, actual);
    }
    
    @Test
    public void testInitWebRootContext() {
        String ctx = "/aaa";
        final NacosClientProperties properties = NacosClientProperties.PROTOTYPE.derive();
        properties.setProperty(PropertyKeyConst.CONTEXT_PATH, ctx);
        InitUtils.initWebRootContext(properties);
        Assert.assertEquals(ctx, UtilAndComs.webContext);
        Assert.assertEquals(ctx + "/v1/ns", UtilAndComs.nacosUrlBase);
        Assert.assertEquals(ctx + "/v1/ns/instance", UtilAndComs.nacosUrlInstance);
    }
    
    @Test
    public void testInitWebRootContextWithoutValue() {
        final NacosClientProperties properties = NacosClientProperties.PROTOTYPE.derive();
        InitUtils.initWebRootContext(properties);
        Assert.assertEquals("/nacos", UtilAndComs.webContext);
        Assert.assertEquals("/nacos/v1/ns", UtilAndComs.nacosUrlBase);
        Assert.assertEquals("/nacos/v1/ns/instance", UtilAndComs.nacosUrlInstance);
    }
    
    @Test
    public void testInitEndpointForNullProperties() {
        Assert.assertEquals("", InitUtils.initEndpoint(null));
    }
    
    @Test
    public void testInitEndpointFromDefaultWithoutCloudParsing() {
        System.setProperty(SystemPropertyKeyConst.IS_USE_ENDPOINT_PARSING_RULE, "false");
        final NacosClientProperties properties = NacosClientProperties.PROTOTYPE.derive();
        String actual = InitUtils.initEndpoint(properties);
        Assert.assertEquals("", actual);
    }
    
    @Test
    public void testInitEndpointFromPropertiesWithoutCloudParsing() {
        System.setProperty(SystemPropertyKeyConst.IS_USE_ENDPOINT_PARSING_RULE, "false");
        final NacosClientProperties properties = NacosClientProperties.PROTOTYPE.derive();
        String endpoint = "1.1.1.1";
        String endpointPort = "1234";
        properties.setProperty(PropertyKeyConst.ENDPOINT, endpoint);
        properties.setProperty(PropertyKeyConst.ENDPOINT_PORT, endpointPort);
        String actual = InitUtils.initEndpoint(properties);
        Assert.assertEquals(endpoint + ":" + endpointPort, actual);
    }
    
    @Test
    public void testInitEndpointFromAliwareWithoutCloudParsing() {
        String endpoint = "aliware_endpoint";
        String endpointPort = "1234";
        System.setProperty(SystemPropertyKeyConst.IS_USE_ENDPOINT_PARSING_RULE, "false");
        System.setProperty(PropertyKeyConst.SystemEnv.ALIBABA_ALIWARE_ENDPOINT_URL, endpoint);
        System.setProperty(PropertyKeyConst.SystemEnv.ALIBABA_ALIWARE_ENDPOINT_PORT, endpointPort);
        final NacosClientProperties properties = NacosClientProperties.PROTOTYPE.derive();
        properties.setProperty(PropertyKeyConst.ENDPOINT_PORT, endpointPort + "1");
        String actual = InitUtils.initEndpoint(properties);
        Assert.assertEquals("", actual);
    }
    
    @Test
    public void testInitEndpointFromDefaultWithCloudParsing() {
        System.setProperty(SystemPropertyKeyConst.IS_USE_ENDPOINT_PARSING_RULE, "true");
        final NacosClientProperties properties = NacosClientProperties.PROTOTYPE.derive();
        String actual = InitUtils.initEndpoint(properties);
        Assert.assertEquals("", actual);
    }
    
    @Test
    public void testInitEndpointFromPropertiesWithCloudParsing() {
        System.setProperty(SystemPropertyKeyConst.IS_USE_ENDPOINT_PARSING_RULE, "true");
        final NacosClientProperties properties = NacosClientProperties.PROTOTYPE.derive();
        String endpoint = "1.1.1.1";
        String endpointPort = "1234";
        properties.setProperty(PropertyKeyConst.ENDPOINT, endpoint);
        properties.setProperty(PropertyKeyConst.ENDPOINT_PORT, endpointPort);
        String actual = InitUtils.initEndpoint(properties);
        Assert.assertEquals(endpoint + ":" + endpointPort, actual);
    }
    
    @Test
    public void testInitEndpointFromAliwareWithCloudParsing() {
        String endpoint = "aliware_endpoint";
        String endpointPort = "1234";
        System.setProperty(SystemPropertyKeyConst.IS_USE_ENDPOINT_PARSING_RULE, "true");
        System.setProperty(PropertyKeyConst.SystemEnv.ALIBABA_ALIWARE_ENDPOINT_URL, endpoint);
        System.setProperty(PropertyKeyConst.SystemEnv.ALIBABA_ALIWARE_ENDPOINT_PORT, endpointPort);
        final NacosClientProperties properties = NacosClientProperties.PROTOTYPE.derive();
        properties.setProperty(PropertyKeyConst.ENDPOINT_PORT, endpointPort + "1");
        String actual = InitUtils.initEndpoint(properties);
        Assert.assertEquals(endpoint + ":" + endpointPort, actual);
    }
    
    @Test
    public void testInitEndpointAns() {
        try {
            System.setProperty(PropertyKeyConst.IS_USE_ENDPOINT_PARSING_RULE, "true");
            final NacosClientProperties properties = NacosClientProperties.PROTOTYPE.derive();
            String endpoint = "${key:test.com}";
            properties.setProperty(PropertyKeyConst.ENDPOINT, endpoint);
            String actual = InitUtils.initEndpoint(properties);
            //defaultEndpointPort is  "8080";
            Assert.assertEquals("test.com:8080", actual);
        } finally {
            System.clearProperty(PropertyKeyConst.IS_USE_ENDPOINT_PARSING_RULE);
        }
    }
    
}