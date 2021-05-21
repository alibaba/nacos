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
import org.junit.Assert;
import org.junit.Test;

import java.util.Properties;

public class InitUtilsTest {
    
    @Test
    public void testInitWebRootContext() {
        String ctx = "/aaa";
        Properties properties = new Properties();
        properties.put(PropertyKeyConst.CONTEXT_PATH, ctx);
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
        Properties prop = new Properties();
        String ns = InitUtils.initNamespaceForNaming(prop);
        Assert.assertEquals("public", ns);
    }
    
    @Test
    public void testInitNamespaceForNamingFromProp() {
        Properties prop = new Properties();
        String expect = "ns1";
        prop.put(PropertyKeyConst.NAMESPACE, expect);
        String ns = InitUtils.initNamespaceForNaming(prop);
        Assert.assertEquals(expect, ns);
    }
    
    @Test
    public void testInitNamespaceForNamingFromSystem() {
        try {
            String expect1 = "ns1";
            System.setProperty(PropertyKeyConst.NAMESPACE, expect1);
            Properties prop = new Properties();
            prop.put(PropertyKeyConst.NAMESPACE, "cccccc");
            String ns = InitUtils.initNamespaceForNaming(prop);
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
            System.setProperty(PropertyKeyConst.IS_USE_CLOUD_NAMESPACE_PARSING, " true");
            System.setProperty(SystemPropertyKeyConst.ANS_NAMESPACE, expect1);
            Properties prop = new Properties();
            prop.put(PropertyKeyConst.NAMESPACE, "cccccc");
            String ns = InitUtils.initNamespaceForNaming(prop);
            Assert.assertEquals(expect1, ns);
        } finally {
            System.clearProperty(PropertyKeyConst.IS_USE_CLOUD_NAMESPACE_PARSING);
            System.clearProperty(SystemPropertyKeyConst.ANS_NAMESPACE);
            
        }
    }
    
    @Test
    public void testInitEndpoint() {
        Properties prop = new Properties();
        String endpoint = "1.1.1.1";
        String endpointPort = "1234";
        prop.put(PropertyKeyConst.ENDPOINT, endpoint);
        prop.put(PropertyKeyConst.ENDPOINT_PORT, endpointPort);
        String actual = InitUtils.initEndpoint(prop);
        Assert.assertEquals(endpoint + ":" + endpointPort, actual);
    }
    
    @Test
    public void testInitEndpointAns() {
        try {
            System.setProperty(PropertyKeyConst.IS_USE_ENDPOINT_PARSING_RULE, "true");
            Properties prop = new Properties();
            String endpoint = "${key:test.com}";
            prop.put(PropertyKeyConst.ENDPOINT, endpoint);
            String actual = InitUtils.initEndpoint(prop);
            //defaultEndpointPort is  "8080";
            Assert.assertEquals("test.com:8080", actual);
        } finally {
            System.clearProperty(PropertyKeyConst.IS_USE_ENDPOINT_PARSING_RULE);
        }
    }
    
}