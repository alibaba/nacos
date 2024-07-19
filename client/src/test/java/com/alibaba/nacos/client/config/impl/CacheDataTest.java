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

import com.alibaba.nacos.api.config.ConfigChangeEvent;
import com.alibaba.nacos.api.config.PropertyChangeType;
import com.alibaba.nacos.api.config.listener.AbstractSharedListener;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.client.config.filter.impl.ConfigFilterChainManager;
import com.alibaba.nacos.client.config.listener.impl.AbstractConfigChangeListener;
import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.notify.listener.Subscriber;
import com.alibaba.nacos.common.utils.MD5Utils;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CacheDataTest {
    
    @Test
    void testConstructorAndEquals() {
        ConfigFilterChainManager filter = new ConfigFilterChainManager(new Properties());
        final CacheData cacheData1 = new CacheData(filter, "name1", "key", "group", "tenant");
        assertEquals("CacheData [key, group]", cacheData1.toString());
        
        final CacheData cacheData2 = new CacheData(filter, "name2", "key", "group");
        assertEquals(cacheData1, cacheData2);
        assertEquals(cacheData1.hashCode(), cacheData2.hashCode());
        
        final CacheData cacheData3 = new CacheData(filter, "name2", "key3", "group", "tenant");
        assertNotEquals(cacheData1, cacheData3);
    }
    
    @Test
    void testGetter() {
        ConfigFilterChainManager filter = new ConfigFilterChainManager(new Properties());
        final CacheData cacheData1 = new CacheData(filter, "name1", "key", "group", "tenant");
        
        assertTrue(cacheData1.isInitializing());
        assertNull(cacheData1.getContent());
        assertEquals(0, cacheData1.getTaskId());
        assertFalse(cacheData1.isConsistentWithServer());
        assertFalse(cacheData1.isUseLocalConfigInfo());
        assertEquals(0, cacheData1.getLastModifiedTs().intValue());
        assertEquals(0, cacheData1.getLocalConfigInfoVersion());
        
        cacheData1.setInitializing(false);
        cacheData1.setContent("123");
        cacheData1.setTaskId(123);
        cacheData1.setConsistentWithServer(true);
        cacheData1.setType("123");
        long timeStamp = new Date().getTime();
        cacheData1.setLastModifiedTs(timeStamp);
        cacheData1.setUseLocalConfigInfo(true);
        cacheData1.setLocalConfigInfoVersion(timeStamp);
        
        assertFalse(cacheData1.isInitializing());
        assertEquals("123", cacheData1.getContent());
        assertEquals(MD5Utils.md5Hex("123", "UTF-8"), cacheData1.getMd5());
        
        assertEquals(123, cacheData1.getTaskId());
        assertTrue(cacheData1.isConsistentWithServer());
        assertEquals("123", cacheData1.getType());
        assertTrue(cacheData1.isUseLocalConfigInfo());
        assertEquals(timeStamp, cacheData1.getLastModifiedTs().longValue());
        assertEquals(timeStamp, cacheData1.getLocalConfigInfoVersion());
    }
    
    @Test
    void testNotifyWarnTimeout() {
        System.setProperty("nacos.listener.notify.warn.timeout", "5000");
        long notifyWarnTimeout = CacheData.initNotifyWarnTimeout();
        assertEquals(5000, notifyWarnTimeout);
        System.setProperty("nacos.listener.notify.warn.timeout", "1bf000abc");
        long notifyWarnTimeout2 = CacheData.initNotifyWarnTimeout();
        assertEquals(60000, notifyWarnTimeout2);
    }
    
    @Test
    void testListener() throws NacosException {
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
        assertEquals(1, cacheData1.getListeners().size());
        assertEquals(listener, cacheData1.getListeners().get(0));
        
        cacheData1.removeListener(listener);
        assertEquals(0, cacheData1.getListeners().size());
        
    }
    
    @Test
    void testCheckListenerMd5() throws NacosException {
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
        assertTrue(data.checkListenersMd5Consistent());
        assertEquals(0, list.size());
        
        data.setContent("new");
        assertFalse(data.checkListenersMd5Consistent());
        data.checkListenerMd5();
        assertEquals(1, list.size());
        assertEquals("new", list.get(0));
        
    }
    
    @Test
    void testCheckListenerMd5NotifyTimeouts() throws NacosException {
        System.setProperty("nacos.listener.notify.warn.timeout", "1000");
        long notifyWarnTimeout = CacheData.initNotifyWarnTimeout();
        assertEquals(1000, notifyWarnTimeout);
        ConfigFilterChainManager filter = new ConfigFilterChainManager(new Properties());
        final CacheData data = new CacheData(filter, "name1", "keytimeouts", "group", "tenant");
        Listener listener = new Listener() {
            @Override
            public Executor getExecutor() {
                return Runnable::run;
            }
            
            @Override
            public void receiveConfigInfo(String configInfo) {
                try {
                    Thread.sleep(11000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        };
        
        AtomicReference<String> dataIdNotifyTimeouts = new AtomicReference();
        NotifyCenter.registerSubscriber(new Subscriber() {
            
            @Override
            public void onEvent(Event event) {
                ChangeNotifyBlockEvent changeNotifyBlockEvent = (ChangeNotifyBlockEvent) event;
                dataIdNotifyTimeouts.set(changeNotifyBlockEvent.getDataId());
                System.out.println("timeout:" + changeNotifyBlockEvent.getDataId());
            }
            
            @Override
            public Class<? extends Event> subscribeType() {
                return ChangeNotifyBlockEvent.class;
            }
        });
        data.addListener(listener);
        data.setContent("new");
        data.checkListenerMd5();
        assertTrue(data.checkListenersMd5Consistent());
        assertEquals("keytimeouts", dataIdNotifyTimeouts.get());
    }
    
    @Test
    void testAbstractSharedListener() throws NacosException {
        ConfigFilterChainManager filter = new ConfigFilterChainManager(new Properties());
        final CacheData data = new CacheData(filter, "name1", "keyshare", "group", "tenant");
        
        final String[] dataIdReceive = new String[1];
        final String[] groupReceive = new String[1];
        final String[] contentReceive = new String[1];
        
        Listener listener = new AbstractSharedListener() {
            @Override
            public Executor getExecutor() {
                return Runnable::run;
            }
            
            @Override
            public void innerReceive(String dataId, String group, String configInfo) {
                dataIdReceive[0] = dataId;
                groupReceive[0] = group;
                contentReceive[0] = configInfo;
            }
            
        };
        data.addListener(listener);
        String content = "content" + System.currentTimeMillis();
        data.setContent(content);
        data.checkListenerMd5();
        assertTrue(data.checkListenersMd5Consistent());
        assertEquals("keyshare", dataIdReceive[0]);
        assertEquals("group", groupReceive[0]);
        assertEquals(contentReceive[0], content);
    }
    
    @Test
    void testAbstractConfigChangeListener() throws NacosException {
        ConfigFilterChainManager filter = new ConfigFilterChainManager(new Properties());
        final CacheData data = new CacheData(filter, "name1", "keyshare", "group", "tenant");
        data.setType("properties");
        data.setContent("a=a\nb=b\nc=c");
        
        AtomicReference<ConfigChangeEvent> changeItemReceived = new AtomicReference<>();
        Listener listener = new AbstractConfigChangeListener() {
            @Override
            public void receiveConfigChange(ConfigChangeEvent event) {
                changeItemReceived.set(event);
            }
            
            @Override
            public Executor getExecutor() {
                return Runnable::run;
            }
            
        };
        data.addListener(listener);
        String content = "b=b\nc=abc\nd=d";
        data.setContent(content);
        data.checkListenerMd5();
        assertTrue(data.checkListenersMd5Consistent());
        assertEquals(PropertyChangeType.DELETED, changeItemReceived.get().getChangeItem("a").getType());
        assertEquals(PropertyChangeType.MODIFIED, changeItemReceived.get().getChangeItem("c").getType());
        assertEquals(PropertyChangeType.ADDED, changeItemReceived.get().getChangeItem("d").getType());
    }
    
}
