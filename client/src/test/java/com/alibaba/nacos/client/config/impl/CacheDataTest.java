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

import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.client.config.filter.impl.ConfigFilterChainManager;
import com.alibaba.nacos.common.utils.MD5Utils;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Executor;

public class CacheDataTest {
    
    @Test
    public void testConstructorAndEquals() {
        ConfigFilterChainManager filter = new ConfigFilterChainManager(new Properties());
        final CacheData cacheData1 = new CacheData(filter, "name1", "key", "group", "tenant");
        Assert.assertEquals("CacheData [key, group]", cacheData1.toString());
        
        final CacheData cacheData2 = new CacheData(filter, "name2", "key", "group");
        Assert.assertEquals(cacheData1, cacheData2);
        Assert.assertEquals(cacheData1.hashCode(), cacheData2.hashCode());
        
        final CacheData cacheData3 = new CacheData(filter, "name2", "key3", "group", "tenant");
        Assert.assertNotEquals(cacheData1, cacheData3);
    }
    
    @Test
    public void testGetter() {
        ConfigFilterChainManager filter = new ConfigFilterChainManager(new Properties());
        final CacheData cacheData1 = new CacheData(filter, "name1", "key", "group", "tenant");
        
        Assert.assertTrue(cacheData1.isInitializing());
        Assert.assertNull(cacheData1.getContent());
        Assert.assertEquals(0, cacheData1.getTaskId());
        Assert.assertFalse(cacheData1.isSyncWithServer());
        Assert.assertFalse(cacheData1.isUseLocalConfigInfo());
        Assert.assertEquals(0, cacheData1.getLastModifiedTs());
        Assert.assertEquals(0, cacheData1.getLocalConfigInfoVersion());
        
        cacheData1.setInitializing(false);
        cacheData1.setContent("123");
        cacheData1.setTaskId(123);
        cacheData1.setSyncWithServer(true);
        cacheData1.setType("123");
        long timeStamp = new Date().getTime();
        cacheData1.setLastModifiedTs(timeStamp);
        cacheData1.setUseLocalConfigInfo(true);
        cacheData1.setLocalConfigInfoVersion(timeStamp);
        
        Assert.assertFalse(cacheData1.isInitializing());
        Assert.assertEquals("123", cacheData1.getContent());
        Assert.assertEquals(MD5Utils.md5Hex("123", "UTF-8"), cacheData1.getMd5());
        
        Assert.assertEquals(123, cacheData1.getTaskId());
        Assert.assertTrue(cacheData1.isSyncWithServer());
        //TODO FIX getType
        // Assert.assertFalse("123",cacheData1.getType());
        Assert.assertTrue(cacheData1.isUseLocalConfigInfo());
        Assert.assertEquals(timeStamp, cacheData1.getLastModifiedTs());
        Assert.assertEquals(timeStamp, cacheData1.getLocalConfigInfoVersion());
    }
    
    @Test
    public void testListener() {
        ConfigFilterChainManager filter = new ConfigFilterChainManager(new Properties());
        final CacheData cacheData1 = new CacheData(filter, "name1", "key", "group", "tenant");
        
        Listener listener = new Listener() {
            @Override
            public Executor getExecutor() {
                return null;
            }
            
            @Override
            public void receiveConfigInfo(String configInfo) {
            }
        };
        cacheData1.addListener(listener);
        Assert.assertEquals(1, cacheData1.getListeners().size());
        Assert.assertEquals(listener, cacheData1.getListeners().get(0));
        
        cacheData1.removeListener(listener);
        Assert.assertEquals(0, cacheData1.getListeners().size());
        
    }
    
    @Test
    public void testCheckListenerMd5() {
        ConfigFilterChainManager filter = new ConfigFilterChainManager(new Properties());
        final CacheData data = new CacheData(filter, "name1", "key", "group", "tenant");
        final List<String> list = new ArrayList<>();
        Listener listener = new Listener() {
            @Override
            public Executor getExecutor() {
                return Runnable::run;
            }
            
            @Override
            public void receiveConfigInfo(String configInfo) {
                list.add(configInfo);
            }
        };
        data.addListener(listener);
        data.checkListenerMd5();
        Assert.assertTrue(data.checkListenersMd5Consistent());
        Assert.assertEquals(0, list.size());
        
        data.setContent("new");
        Assert.assertFalse(data.checkListenersMd5Consistent());
        data.checkListenerMd5();
        Assert.assertEquals(1, list.size());
        Assert.assertEquals("new", list.get(0));
        
    }
    
}
