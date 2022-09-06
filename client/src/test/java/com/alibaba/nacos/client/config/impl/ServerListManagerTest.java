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

package com.alibaba.nacos.client.config.impl;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.exception.NacosException;
import org.junit.Assert;
import org.junit.Test;

import java.util.Properties;

public class ServerListManagerTest {
    
    @Test
    public void testStart() throws NacosException {
        Properties properties = new Properties();
        properties.setProperty("endpoint", "localhost");
        properties.setProperty("endpointPort", "0");
        final ServerListManager mgr = new ServerListManager(properties);
        try {
            mgr.start();
            Assert.fail();
        } catch (NacosException e) {
            Assert.assertEquals(
                    "fail to get NACOS-server serverlist! env:custom-localhost_0_nacos_serverlist, not connnect url:http://localhost:0/nacos/serverlist",
                    e.getErrMsg());
        }
        mgr.shutdown();
    }
    
    @Test
    public void testGetter() throws NacosException {
        {
            Properties properties = new Properties();
            properties.put(PropertyKeyConst.CONTEXT_PATH, "aaa");
            properties.put(PropertyKeyConst.ENDPOINT, "endpoint");
            final ServerListManager mgr2 = new ServerListManager(properties);
            Assert.assertEquals("aaa", mgr2.getContentPath());
        }

        // Test https
        {
            Properties properties = new Properties();
            properties.put(PropertyKeyConst.CONTEXT_PATH, "aaa");
            properties.put(PropertyKeyConst.SERVER_ADDR, "https://1.1.1.1:8848");
            final ServerListManager mgr2 = new ServerListManager(properties);
            Assert.assertEquals("aaa", mgr2.getContentPath());
            Assert.assertEquals("[https://1.1.1.1:8848]", mgr2.getUrlString());
        }
        
        {
            Properties properties2 = new Properties();
            properties2.put(PropertyKeyConst.CONTEXT_PATH, "aaa");
            properties2.put(PropertyKeyConst.SERVER_ADDR, "1.1.1.1:8848");
            
            final ServerListManager mgr3 = new ServerListManager(properties2);
            Assert.assertEquals(1, mgr3.getServerList().size());
            Assert.assertEquals("http://1.1.1.1:8848", mgr3.getServerList().get(0));
            Assert.assertEquals("[http://1.1.1.1:8848]", mgr3.getUrlString());
            Assert.assertEquals("ServerManager-fixed-1.1.1.1_8848-[http://1.1.1.1:8848]", mgr3.toString());
        }

        {
            Properties properties3 = new Properties();
            properties3.put(PropertyKeyConst.CONTEXT_PATH, "aaa");
            properties3.put(PropertyKeyConst.SERVER_ADDR, "1.1.1.1:8848,2.2.2.2:8848");

            final ServerListManager mgr4 = new ServerListManager(properties3);
            Assert.assertEquals(2, mgr4.getServerList().size());
            Assert.assertEquals("http://1.1.1.1:8848", mgr4.getServerList().get(0));
            Assert.assertEquals("http://2.2.2.2:8848", mgr4.getServerList().get(1));
            Assert.assertEquals("ServerManager-fixed-1.1.1.1_8848-2.2.2.2_8848-[http://1.1.1.1:8848, http://2.2.2.2:8848]", mgr4.toString());
        }

        {
            Properties properties4 = new Properties();
            properties4.put(PropertyKeyConst.CONTEXT_PATH, "aaa");
            properties4.put(PropertyKeyConst.SERVER_ADDR, "1.1.1.1:8848;2.2.2.2:8848");

            final ServerListManager mgr5 = new ServerListManager(properties4);
            Assert.assertEquals(2, mgr5.getServerList().size());
            Assert.assertEquals("http://1.1.1.1:8848", mgr5.getServerList().get(0));
            Assert.assertEquals("http://2.2.2.2:8848", mgr5.getServerList().get(1));
            Assert.assertEquals("ServerManager-fixed-1.1.1.1_8848-2.2.2.2_8848-[http://1.1.1.1:8848, http://2.2.2.2:8848]", mgr5.toString());
        }
        
    }
    
    @Test
    public void testGetCurrentServer() throws NacosException {
        Properties properties = new Properties();
        properties.put(PropertyKeyConst.SERVER_ADDR, "127.0.0.1:8848");
        final ServerListManager serverListManager = new ServerListManager(properties);
        Assert.assertEquals("127.0.0.1:8848", serverListManager.getCurrentServer());
        Assert.assertEquals("127.0.0.1:8848", serverListManager.getNextServer());
    }
    
}