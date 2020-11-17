/*
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
 */

package com.alibaba.nacos.client.naming.core;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.listener.AbstractEventListener;
import com.alibaba.nacos.api.naming.listener.Event;
import com.alibaba.nacos.api.naming.listener.EventListener;
import com.alibaba.nacos.api.naming.listener.NamingEvent;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.client.naming.beat.BeatInfo;
import com.alibaba.nacos.client.naming.beat.BeatReactor;
import com.alibaba.nacos.client.naming.event.InstancesChangeEvent;
import com.alibaba.nacos.client.naming.net.NamingProxy;
import com.alibaba.nacos.common.utils.ClassUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class HostReactorTest {
    
    private static final String CACHE_DIR = HostReactorTest.class.getResource("/").getPath() + "cache/";
    
    @Mock
    private NamingProxy namingProxy;
    
    private HostReactor hostReactor;
    
    private BeatReactor beatReactor;
    
    @Before
    public void setUp() throws Exception {
        beatReactor = new BeatReactor(namingProxy);
        BeatInfo beatInfo = new BeatInfo();
        beatInfo.setServiceName("testName");
        beatInfo.setIp("1.1.1.1");
        beatInfo.setPort(1234);
        beatInfo.setCluster("clusterName");
        beatInfo.setWeight(1);
        beatInfo.setMetadata(new HashMap<String, String>());
        beatInfo.setScheduled(false);
        beatInfo.setPeriod(1000L);
        beatReactor.addBeatInfo("testName", beatInfo);
        hostReactor = new HostReactor(namingProxy, beatReactor, CACHE_DIR);
    }
    
    @Test
    public void testProcessServiceJson() {
        ServiceInfo actual = hostReactor.processServiceJson(EXAMPLE);
        assertServiceInfo(actual);
        hostReactor.processServiceJson(CHANGE_DATA_EXAMPLE);
        BeatInfo actualBeatInfo = beatReactor.dom2Beat.get(beatReactor.buildKey("testName", "1.1.1.1", 1234));
        assertEquals(2.0, actualBeatInfo.getWeight(), 0.0);
    }
    
    @Test
    public void testGetServiceInfoDirectlyFromServer() throws NacosException {
        when(namingProxy.queryList("testName", "testClusters", 0, false)).thenReturn(EXAMPLE);
        ServiceInfo actual = hostReactor.getServiceInfoDirectlyFromServer("testName", "testClusters");
        assertServiceInfo(actual);
    }
    
    @Test
    public void testSubscribe() throws InterruptedException {
        final AtomicInteger count = new AtomicInteger(1);
        EventListener eventListener = new EventListener() {
            @Override
            public void onEvent(Event event) {
                if (event instanceof NamingEvent) {
                    List<Instance> instances = ((NamingEvent) event).getInstances();
                    assertInstance(instances.get(0));
                    Assert.assertEquals("nacos.publisher-" + ClassUtils.getCanonicalName(InstancesChangeEvent.class),
                            Thread.currentThread().getName());
                    count.decrementAndGet();
                }
            }
        };
        hostReactor.subscribe("testName", "testClusters", eventListener);
        hostReactor.processServiceJson(EXAMPLE);
        Thread.sleep(1000);
        Assert.assertEquals(0, count.intValue());
        hostReactor.unSubscribe("testName", "testClusters", eventListener);
    }
    
    @Test
    public void testAsyncSubscribe() throws InterruptedException {
        final AtomicInteger count = new AtomicInteger(1);
        
        ThreadFactory threadFactory = new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r);
                thread.setName("test-thread");
                return thread;
            }
        };
        
        final Executor executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<Runnable>(), threadFactory);
        EventListener eventListener = new AbstractEventListener() {
            
            @Override
            public Executor getExecutor() {
                return executor;
            }
            
            @Override
            public void onEvent(Event event) {
                if (event instanceof NamingEvent) {
                    List<Instance> instances = ((NamingEvent) event).getInstances();
                    assertInstance(instances.get(0));
                    Assert.assertEquals("test-thread", Thread.currentThread().getName());
                    count.decrementAndGet();
                }
            }
        };
        hostReactor.subscribe("testName", "testClusters", eventListener);
        hostReactor.processServiceJson(EXAMPLE);
        Thread.sleep(1000);
        Assert.assertEquals(0, count.intValue());
        hostReactor.unSubscribe("testName", "testClusters", eventListener);
    }
    
    @Test
    public void testUnsubscribe() throws InterruptedException {
        Thread.sleep(1000);
        final AtomicInteger count = new AtomicInteger(1);
        EventListener eventListener = new EventListener() {
            @Override
            public void onEvent(Event event) {
                count.decrementAndGet();
            }
        };
        hostReactor.subscribe("testName", "testClusters", eventListener);
        hostReactor.processServiceJson(EXAMPLE);
        
        Thread.sleep(1000);
        
        hostReactor.unSubscribe("testName", "testClusters", eventListener);
        hostReactor.processServiceJson(CHANGE_DATA_EXAMPLE);
        
        Assert.assertEquals(0, count.intValue());
    }
    
    @Test
    public void testGetSubscribeServices() {
        EventListener listener = new EventListener() {
            @Override
            public void onEvent(Event event) {
            }
        };
        hostReactor.subscribe("testGroup@@testName", "testClusters", listener);
        
        Assert.assertEquals(1, hostReactor.getSubscribeServices().size());
        Assert.assertEquals("testName", hostReactor.getSubscribeServices().get(0).getName());
        Assert.assertEquals("testGroup", hostReactor.getSubscribeServices().get(0).getGroupName());
        Assert.assertEquals("testClusters", hostReactor.getSubscribeServices().get(0).getClusters());
        
        hostReactor.unSubscribe("testGroup@@testName", "testClusters", listener);
        Assert.assertEquals(0, hostReactor.getSubscribeServices().size());
    }
    
    private void assertServiceInfo(ServiceInfo actual) {
        assertEquals("testName", actual.getName());
        assertEquals("testClusters", actual.getClusters());
        assertEquals("", actual.getChecksum());
        assertEquals(1000, actual.getCacheMillis());
        assertEquals(0, actual.getLastRefTime());
        assertNull(actual.getGroupName());
        assertTrue(actual.isValid());
        assertFalse(actual.isAllIPs());
        assertEquals(1, actual.getHosts().size());
        assertInstance(actual.getHosts().get(0));
    }
    
    private void assertInstance(Instance actual) {
        assertEquals("1.1.1.1", actual.getIp());
        assertEquals("testClusters", actual.getClusterName());
        assertEquals("testName", actual.getServiceName());
        assertEquals(1234, actual.getPort());
    }
    
    private static final String EXAMPLE =
            "{\n" + "\t\"name\": \"testName\",\n" + "\t\"clusters\": \"testClusters\",\n" + "\t\"cacheMillis\": 1000,\n"
                    + "\t\"hosts\": [{\n" + "\t\t\"ip\": \"1.1.1.1\",\n" + "\t\t\"port\": 1234,\n"
                    + "\t\t\"weight\": 1.0,\n" + "\t\t\"healthy\": true,\n" + "\t\t\"enabled\": true,\n"
                    + "\t\t\"ephemeral\": true,\n" + "\t\t\"clusterName\": \"testClusters\",\n"
                    + "\t\t\"serviceName\": \"testName\",\n" + "\t\t\"metadata\": {},\n"
                    + "\t\t\"instanceHeartBeatInterval\": 5000,\n" + "\t\t\"instanceHeartBeatTimeOut\": 15000,\n"
                    + "\t\t\"ipDeleteTimeout\": 30000,\n" + "\t\t\"instanceIdGenerator\": \"simple\"\n" + "\t}],\n"
                    + "\t\"lastRefTime\": 0,\n" + "\t\"checksum\": \"\",\n" + "\t\"allIPs\": false,\n"
                    + "\t\"valid\": true\n" + "}";
    
    //the weight changed from 1.0 to 2.0
    private static final String CHANGE_DATA_EXAMPLE =
            "{\n" + "\t\"name\": \"testName\",\n" + "\t\"clusters\": \"testClusters\",\n" + "\t\"cacheMillis\": 1000,\n"
                    + "\t\"hosts\": [{\n" + "\t\t\"ip\": \"1.1.1.1\",\n" + "\t\t\"port\": 1234,\n"
                    + "\t\t\"weight\": 2.0,\n" + "\t\t\"healthy\": true,\n" + "\t\t\"enabled\": true,\n"
                    + "\t\t\"ephemeral\": true,\n" + "\t\t\"clusterName\": \"testClusters\",\n"
                    + "\t\t\"serviceName\": \"testName\",\n" + "\t\t\"metadata\": {},\n"
                    + "\t\t\"instanceHeartBeatInterval\": 5000,\n" + "\t\t\"instanceHeartBeatTimeOut\": 15000,\n"
                    + "\t\t\"ipDeleteTimeout\": 30000,\n" + "\t\t\"instanceIdGenerator\": \"simple\"\n" + "\t}],\n"
                    + "\t\"lastRefTime\": 0,\n" + "\t\"checksum\": \"\",\n" + "\t\"allIPs\": false,\n"
                    + "\t\"valid\": true\n" + "}";
}
