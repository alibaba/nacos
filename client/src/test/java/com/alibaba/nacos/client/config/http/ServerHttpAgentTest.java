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

package com.alibaba.nacos.client.config.http;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.client.config.impl.ServerListManager;
import com.alibaba.nacos.common.http.HttpRestResult;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.Properties;

/**
 * ServerHttpAgentTest.
 *
 * @author shalk
 * @since 2021
 */
public class ServerHttpAgentTest {
    
    @Test
    public void testConstruct() throws NacosException {
        ServerListManager server = new ServerListManager();
        final ServerHttpAgent serverHttpAgent1 = new ServerHttpAgent(server);
        Assert.assertNotNull(serverHttpAgent1);
        
        final ServerHttpAgent serverHttpAgent2 = new ServerHttpAgent(server, new Properties());
        Assert.assertNotNull(serverHttpAgent2);
        
        final Properties properties = new Properties();
        properties.put(PropertyKeyConst.SERVER_ADDR, "aaa");
        final ServerHttpAgent serverHttpAgent3 = new ServerHttpAgent(properties);
        Assert.assertNotNull(serverHttpAgent3);
        
    }
    
    @Test
    public void testGetterAndSetter() throws NacosException {
        ServerListManager server = new ServerListManager("aaa", "namespace1");
        final ServerHttpAgent serverHttpAgent = new ServerHttpAgent(server, new Properties());
        
        final String appname = ServerHttpAgent.getAppname();
        //set by AppNameUtils, init in ParamUtils static block
        Assert.assertEquals("unknown", appname);
        
        final String encode = serverHttpAgent.getEncode();
        final String namespace = serverHttpAgent.getNamespace();
        final String tenant = serverHttpAgent.getTenant();
        final String name = serverHttpAgent.getName();
        Assert.assertEquals(Constants.ENCODE, encode);
        Assert.assertEquals("namespace1", namespace);
        Assert.assertEquals("namespace1", tenant);
        Assert.assertEquals("aaa-namespace1", name);
        
    }
    
    @Test
    public void testHttpGet() throws Exception {
        Properties properties = new Properties();
        properties.put(PropertyKeyConst.SERVER_ADDR, "https://httpbin.org");
        properties.put(PropertyKeyConst.CONTEXT_PATH, "/");
        final ServerHttpAgent serverHttpAgent = new ServerHttpAgent(properties);
        final HttpRestResult<String> result = serverHttpAgent
                .httpGet("/get", new HashMap<String, String>(), new HashMap<String, String>(), "UTF-8", 5000);
        
        Assert.assertEquals(200, result.getCode());
        Assert.assertEquals("application/json", result.getHeader().getHeader().get("content-type"));
    }
    
    @Test
    public void testHttpPost() throws Exception {
        Properties properties = new Properties();
        properties.put(PropertyKeyConst.SERVER_ADDR, "https://httpbin.org");
        properties.put(PropertyKeyConst.CONTEXT_PATH, "/");
        final ServerHttpAgent serverHttpAgent = new ServerHttpAgent(properties);
        final HttpRestResult<String> result = serverHttpAgent
                .httpPost("/post", new HashMap<String, String>(), new HashMap<String, String>(), "UTF-8", 5000);
        
        Assert.assertEquals(200, result.getCode());
        Assert.assertEquals("application/json", result.getHeader().getHeader().get("content-type"));
    }
    
    @Test
    public void testHttpDelete() throws Exception {
        Properties properties = new Properties();
        properties.put(PropertyKeyConst.SERVER_ADDR, "https://httpbin.org");
        properties.put(PropertyKeyConst.CONTEXT_PATH, "/");
        final ServerHttpAgent serverHttpAgent = new ServerHttpAgent(properties);
        final HttpRestResult<String> result = serverHttpAgent
                .httpDelete("/delete", new HashMap<String, String>(), new HashMap<String, String>(), "UTF-8", 5000);
        
        Assert.assertEquals(200, result.getCode());
        Assert.assertEquals("application/json", result.getHeader().getHeader().get("content-type"));
    }
    
    @Test
    public void testLifCycle() throws NacosException, NoSuchFieldException, IllegalAccessException {
        Properties properties = new Properties();
        properties.put(PropertyKeyConst.SERVER_ADDR, "aaa");
        ServerListManager server = Mockito.mock(ServerListManager.class);
        final ServerHttpAgent serverHttpAgent = new ServerHttpAgent(server, properties);
        
        serverHttpAgent.start();
        Mockito.verify(server).start();
        
        try {
            serverHttpAgent.shutdown();
            Assert.fail();
        } catch (NullPointerException e) {
            //
        }
    }
    
}