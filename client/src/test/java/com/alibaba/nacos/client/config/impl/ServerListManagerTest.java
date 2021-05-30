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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

public class ServerListManagerTest {
    
    @Test
    public void testStart() throws NacosException {
        final ServerListManager mgr = new ServerListManager("localhost", 0);
        try {
            mgr.start();
            Assert.fail();
        } catch (NacosException e) {
            Assert.assertEquals(
                    "fail to get NACOS-server serverlist! env:custom-localhost-0, not connnect url:http://localhost:0/nacos/serverlist",
                    e.getErrMsg());
        }
        mgr.shutdown();
    }
    
    @Test
    public void testGetter() throws NacosException {
        {
            final ServerListManager mgr = new ServerListManager();
            Assert.assertEquals("nacos", mgr.getContentPath());
            Assert.assertEquals("default", mgr.getName());
            Assert.assertEquals("", mgr.getTenant());
            Assert.assertEquals("", mgr.getNamespace());
            Assert.assertEquals("1.1.1.1-2.2.2.2_8848", mgr.getFixedNameSuffix("http://1.1.1.1", "2.2.2.2:8848"));
        }
        
        {
            Properties properties = new Properties();
            properties.put(PropertyKeyConst.CONTEXT_PATH, "aaa");
            properties.put(PropertyKeyConst.ENDPOINT, "endpoint");
            final ServerListManager mgr2 = new ServerListManager(properties);
            Assert.assertEquals("aaa", mgr2.getContentPath());
        }
        
        {
            Properties properties2 = new Properties();
            properties2.put(PropertyKeyConst.CONTEXT_PATH, "aaa");
            properties2.put(PropertyKeyConst.SERVER_ADDR, "1.1.1.1:8848");
            
            final ServerListManager mgr3 = new ServerListManager(properties2);
            Assert.assertEquals(1, mgr3.getServerUrls().size());
            Assert.assertEquals("http://1.1.1.1:8848", mgr3.getServerUrls().get(0));
            Assert.assertEquals("[http://1.1.1.1:8848]", mgr3.getUrlString());
            Assert.assertTrue(mgr3.contain("http://1.1.1.1:8848"));
            Assert.assertEquals("ServerManager-fixed-1.1.1.1_8848-[http://1.1.1.1:8848]", mgr3.toString());
        }

        {
            Properties properties3 = new Properties();
            properties3.put(PropertyKeyConst.CONTEXT_PATH, "aaa");
            properties3.put(PropertyKeyConst.SERVER_ADDR, "1.1.1.1:8848,2.2.2.2:8848");

            final ServerListManager mgr4 = new ServerListManager(properties3);
            Assert.assertEquals(2, mgr4.getServerUrls().size());
            Assert.assertEquals("http://1.1.1.1:8848", mgr4.getServerUrls().get(0));
            Assert.assertEquals("http://2.2.2.2:8848", mgr4.getServerUrls().get(1));
            Assert.assertTrue(mgr4.contain("http://1.1.1.1:8848"));
            Assert.assertEquals("ServerManager-fixed-1.1.1.1_8848-2.2.2.2_8848-[http://1.1.1.1:8848, http://2.2.2.2:8848]", mgr4.toString());
        }

        {
            Properties properties4 = new Properties();
            properties4.put(PropertyKeyConst.CONTEXT_PATH, "aaa");
            properties4.put(PropertyKeyConst.SERVER_ADDR, "1.1.1.1:8848;2.2.2.2:8848");

            final ServerListManager mgr5 = new ServerListManager(properties4);
            Assert.assertEquals(2, mgr5.getServerUrls().size());
            Assert.assertEquals("http://1.1.1.1:8848", mgr5.getServerUrls().get(0));
            Assert.assertEquals("http://2.2.2.2:8848", mgr5.getServerUrls().get(1));
            Assert.assertTrue(mgr5.contain("http://1.1.1.1:8848"));
            Assert.assertEquals("ServerManager-fixed-1.1.1.1_8848-2.2.2.2_8848-[http://1.1.1.1:8848, http://2.2.2.2:8848]", mgr5.toString());
        }
        
    }
    
    @Test
    public void testIterator() {
        List<String> addrs = new ArrayList<>();
        String addr = "1.1.1.1:8848";
        addrs.add(addr);
        final ServerListManager mgr = new ServerListManager(addrs, "aaa");
        
        // new iterator
        final Iterator<String> it = mgr.iterator();
        Assert.assertTrue(it.hasNext());
        Assert.assertEquals(addr, it.next());
        
        Assert.assertNull(mgr.getIterator());
        mgr.refreshCurrentServerAddr();
        Assert.assertNotNull(mgr.getIterator());
        
        final String currentServerAddr = mgr.getCurrentServerAddr();
        Assert.assertEquals(addr, currentServerAddr);
        
        final String nextServerAddr = mgr.getNextServerAddr();
        Assert.assertEquals(addr, nextServerAddr);
        
        final Iterator<String> iterator1 = mgr.iterator();
        Assert.assertTrue(iterator1.hasNext());
        
    }
    
}