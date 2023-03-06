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
import org.junit.Assert;
import org.junit.Test;

public class InitUtilsTest {
    
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
    
    /**
     * current namespace priority 1. system.Properties 2. user.Properties 3. default value
     */
    @Test
    public void testInitNamespaceForNamingDefault() {
        //DEFAULT
        final NacosClientProperties properties = NacosClientProperties.PROTOTYPE.derive();
        String ns = InitUtils.initNamespaceForNaming(properties);
        Assert.assertEquals("public", ns);
    }
    
    @Test
    public void testInitNamespaceForNamingFromProp() {
        final NacosClientProperties properties = NacosClientProperties.PROTOTYPE.derive();
        String expect = "ns1";
        properties.setProperty(PropertyKeyConst.NAMESPACE, expect);
        String ns = InitUtils.initNamespaceForNaming(properties);
        Assert.assertEquals(expect, ns);
    }
    
    @Test
    public void testInitNamespaceForNamingFromSystem() {
        try {
            String expect1 = "ns1";
            System.setProperty(PropertyKeyConst.NAMESPACE, expect1);
            final NacosClientProperties properties = NacosClientProperties.PROTOTYPE.derive();
            String ns = InitUtils.initNamespaceForNaming(properties);
            Assert.assertEquals(expect1, ns);
        } finally {
            System.clearProperty(PropertyKeyConst.NAMESPACE);
        }
    }
    
    /**
     * 1. System.property  tenant.id 2. System.property  ans.namespace 2. System.env  ALIBABA_ALIWARE_NAMESPACE
     */
    @Test
    public void testInitNamespaceForNamingFromCloud() {
        try {
            String expect1 = "ns1";
            System.setProperty(PropertyKeyConst.IS_USE_CLOUD_NAMESPACE_PARSING, "true");
            System.setProperty(SystemPropertyKeyConst.ANS_NAMESPACE, expect1);
            final NacosClientProperties properties = NacosClientProperties.PROTOTYPE.derive();
            properties.setProperty(PropertyKeyConst.NAMESPACE, "cccccc");
            String ns = InitUtils.initNamespaceForNaming(properties);
            Assert.assertEquals(expect1, ns);
        } finally {
            System.clearProperty(PropertyKeyConst.IS_USE_CLOUD_NAMESPACE_PARSING);
            System.clearProperty(SystemPropertyKeyConst.ANS_NAMESPACE);
            
        }
    }
    
    @Test
    public void testInitEndpoint() {
        final NacosClientProperties properties = NacosClientProperties.PROTOTYPE.derive();
        String endpoint = "1.1.1.1";
        String endpointPort = "1234";
        properties.setProperty(PropertyKeyConst.ENDPOINT, endpoint);
        properties.setProperty(PropertyKeyConst.ENDPOINT_PORT, endpointPort);
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