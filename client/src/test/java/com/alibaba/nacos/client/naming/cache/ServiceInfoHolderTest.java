/*
 *
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
 *
 */

package com.alibaba.nacos.client.naming.cache;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.client.env.NacosClientProperties;
import com.alibaba.nacos.client.monitor.MetricsMonitor;
import com.alibaba.nacos.client.naming.backups.FailoverReactor;
import com.alibaba.nacos.client.naming.event.InstancesChangeEvent;
import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.notify.listener.Subscriber;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

class ServiceInfoHolderTest {
    
    NacosClientProperties nacosClientProperties;
    
    ServiceInfoHolder holder;
    
    @BeforeEach
    void setUp() throws Exception {
        nacosClientProperties = NacosClientProperties.PROTOTYPE.derive();
        holder = new ServiceInfoHolder("aa", "scope-001", nacosClientProperties);
    }
    
    @AfterEach
    void tearDown() throws Exception {
        if (holder != null) {
            holder.shutdown();
        }
    }
    
    @Test
    void testGetServiceInfoMap() throws NoSuchFieldException, IllegalAccessException {
        assertEquals(0, holder.getServiceInfoMap().size());
        Field fieldNotifierEventScope =
            ServiceInfoHolder.class.getDeclaredField("notifierEventScope");
        fieldNotifierEventScope.setAccessible(true);
        assertEquals("scope-001", fieldNotifierEventScope.get(holder));
    }
    
    @Test
    void testProcessServiceInfo() {
        ServiceInfo info = new ServiceInfo("a@@b@@c");
        Instance instance1 = createInstance("1.1.1.1", 1);
        Instance instance2 = createInstance("1.1.1.2", 2);
        List<Instance> hosts = new ArrayList<>();
        hosts.add(instance1);
        hosts.add(instance2);
        info.setHosts(hosts);
        
        ServiceInfo actual1 = holder.processServiceInfo(info);
        assertEquals(info, actual1);
        
        Instance newInstance1 = createInstance("1.1.1.1", 1);
        newInstance1.setWeight(2.0);
        Instance instance3 = createInstance("1.1.1.3", 3);
        List<Instance> hosts2 = new ArrayList<>();
        hosts2.add(newInstance1);
        hosts2.add(instance3);
        ServiceInfo info2 = new ServiceInfo("a@@b@@c");
        info2.setHosts(hosts2);
        
        ServiceInfo actual2 = holder.processServiceInfo(info2);
        assertEquals(info2, actual2);
    }
    
    @Test
    void testProcessServiceInfoEnableClientMetricsTrue() {
        ServiceInfoHolder holder = createServiceInfoHolder(true);
        ServiceInfo info = new ServiceInfo("a@@b@@c");
        Instance instance1 = createInstance("1.1.1.1", 1);
        Instance instance2 = createInstance("1.1.1.2", 2);
        List<Instance> hosts = new ArrayList<>();
        hosts.add(instance1);
        hosts.add(instance2);
        info.setHosts(hosts);
        
        try (MockedStatic<MetricsMonitor> mockedMetricsMonitor =
            Mockito.mockStatic(MetricsMonitor.class)) {
            
            holder.processServiceInfo(info);
            
            mockedMetricsMonitor.verify(() -> MetricsMonitor.recordServiceInfoMapSize(1), times(1));
        }
    }
    
    @Test
    void testProcessServiceInfoEnableClientMetricsFalse() {
        ServiceInfoHolder holder = createServiceInfoHolder(false);
        ServiceInfo info = new ServiceInfo("a@@b@@c");
        Instance instance1 = createInstance("1.1.1.1", 1);
        Instance instance2 = createInstance("1.1.1.2", 2);
        List<Instance> hosts = new ArrayList<>();
        hosts.add(instance1);
        hosts.add(instance2);
        info.setHosts(hosts);
        
        try (MockedStatic<MetricsMonitor> mockedMetricsMonitor =
            Mockito.mockStatic(MetricsMonitor.class)) {
            holder.processServiceInfo(info);
            
            mockedMetricsMonitor.verify(() -> MetricsMonitor.recordServiceInfoMapSize(anyInt()),
                never());
        }
    }
    
    @Test
    void testProcessServiceInfoEnableClientMetricsNotSet() {
        ServiceInfoHolder holder = createServiceInfoHolder(null);
        ServiceInfo info = new ServiceInfo("a@@b@@c");
        Instance instance1 = createInstance("1.1.1.1", 1);
        Instance instance2 = createInstance("1.1.1.2", 2);
        List<Instance> hosts = new ArrayList<>();
        hosts.add(instance1);
        hosts.add(instance2);
        
        info.setHosts(hosts);
        
        try (MockedStatic<MetricsMonitor> mockedMetricsMonitor =
            Mockito.mockStatic(MetricsMonitor.class)) {
            
            holder.processServiceInfo(info);
            
            mockedMetricsMonitor.verify(() -> MetricsMonitor.recordServiceInfoMapSize(1), times(1));
        }
    }
    
    @Test
    void testProcessServiceInfoSetThrowsException() {
        ServiceInfoHolder holder = createServiceInfoHolder(true);
        ServiceInfo info = new ServiceInfo("a@@b@@c");
        Instance instance1 = createInstance("1.1.1.1", 1);
        Instance instance2 = createInstance("1.1.1.2", 2);
        List<Instance> hosts = new ArrayList<>();
        hosts.add(instance1);
        hosts.add(instance2);
        info.setHosts(hosts);
        
        RuntimeException exception = new RuntimeException("Mocked exception");
        
        try (MockedStatic<MetricsMonitor> mockedMetricsMonitor =
            Mockito.mockStatic(MetricsMonitor.class)) {
            mockedMetricsMonitor.when(() -> MetricsMonitor.recordServiceInfoMapSize(anyInt()))
                .thenThrow(exception);
            
            ServiceInfo actual2 = holder.processServiceInfo(info);
            
            assertEquals(info, actual2);
        }
    }
    
    @Test
    void testProcessServiceInfoPublishDiskCacheRefreshEvent()
        throws NacosException, NoSuchFieldException, IllegalAccessException {
        holder.shutdown();
        holder = new ServiceInfoHolder("aa", "scope-001", nacosClientProperties);
        ServiceInfo info = new ServiceInfo("a@@b@@c");
        info.setHosts(Collections.singletonList(createInstance("1.1.1.1", 1)));
        try (MockedStatic<DiskCache> mockedDiskCache = Mockito.mockStatic(DiskCache.class)) {
            mockedDiskCache.when(() -> DiskCache.writeWithResult(Mockito.any(ServiceInfo.class),
                Mockito.anyString())).thenReturn(false);
            
            holder.processServiceInfo(info);
            
            ServiceInfoDiskCacheRefresher refresher = getServiceInfoDiskCacheRefresher(holder);
            assertEquals(1, refresher.pendingEventSize());
        }
    }
    
    private ServiceInfoHolder createServiceInfoHolder(Boolean enableClientMetrics) {
        Properties properties = new Properties();
        if (enableClientMetrics != null) {
            properties.put(PropertyKeyConst.ENABLE_CLIENT_METRICS,
                String.valueOf(enableClientMetrics));
        }
        NacosClientProperties clientProperties = NacosClientProperties.PROTOTYPE.derive(properties);
        String namespace = "test-namespace";
        String notifierEventScope = "scope-001";
        return new ServiceInfoHolder(namespace, notifierEventScope, clientProperties);
    }
    
    private Instance createInstance(String ip, int port) {
        Instance instance = new Instance();
        instance.setIp(ip);
        instance.setPort(port);
        return instance;
    }
    
    @Test
    void testProcessServiceInfo2() {
        String json = "{\"groupName\":\"a\",\"name\":\"b\",\"clusters\":\"c\"}";
        
        ServiceInfo actual = holder.processServiceInfo(json);
        ServiceInfo expect = new ServiceInfo("a@@b@@c");
        expect.setJsonFromServer(json);
        assertEquals(expect.getKey(), actual.getKey());
    }
    
    @Test
    void testProcessServiceInfoWithPushEmpty() throws NacosException {
        ServiceInfo oldInfo = new ServiceInfo("a@@b@@c");
        Instance instance1 = createInstance("1.1.1.1", 1);
        Instance instance2 = createInstance("1.1.1.2", 2);
        List<Instance> hosts = new ArrayList<>();
        hosts.add(instance1);
        hosts.add(instance2);
        oldInfo.setHosts(hosts);
        
        nacosClientProperties.setProperty(PropertyKeyConst.NAMING_PUSH_EMPTY_PROTECTION, "true");
        holder.shutdown();
        holder = new ServiceInfoHolder("aa", "scope-001", nacosClientProperties);
        holder.processServiceInfo(oldInfo);
        
        ServiceInfo newInfo = new ServiceInfo("a@@b@@c");
        
        final ServiceInfo actual = holder.processServiceInfo(newInfo);
        
        assertEquals(oldInfo.getKey(), actual.getKey());
        assertEquals(2, actual.getHosts().size());
    }
    
    @Test
    void testProcessNullServiceInfo() {
        assertNull(holder.processServiceInfo(new ServiceInfo()));
    }
    
    @Test
    void testProcessServiceInfoForOlder() {
        ServiceInfo info = new ServiceInfo("a@@b@@c");
        Instance instance1 = createInstance("1.1.1.1", 1);
        Instance instance2 = createInstance("1.1.1.2", 2);
        List<Instance> hosts = new ArrayList<>();
        hosts.add(instance1);
        hosts.add(instance2);
        info.setHosts(hosts);
        info.setLastRefTime(System.currentTimeMillis());
        holder.processServiceInfo(info);
        ServiceInfo olderInfo = new ServiceInfo("a@@b@@c");
        olderInfo.setLastRefTime(0L);
        final ServiceInfo actual = holder.processServiceInfo(olderInfo);
        assertEquals(olderInfo, actual);
    }
    
    @Test
    void testGetServiceInfo() {
        ServiceInfo info = new ServiceInfo("a@@b@@c");
        Instance instance1 = createInstance("1.1.1.1", 1);
        List<Instance> hosts = new ArrayList<>();
        hosts.add(instance1);
        info.setHosts(hosts);
        
        ServiceInfo expect = holder.processServiceInfo(info);
        String serviceName = "b";
        String groupName = "a";
        ServiceInfo actual = holder.getServiceInfo(serviceName, groupName);
        
        // Verify it's a clone (different object)
        assertNotSame(expect, actual);
        
        // Verify content is the same
        assertEquals(expect.getKey(), actual.getKey());
        assertEquals(expect.getHosts().size(), actual.getHosts().size());
        assertEquals(expect.getHosts().get(0), actual.getHosts().get(0));
        
        // Verify hosts list is different
        assertNotSame(expect.getHosts(), actual.getHosts());
    }
    
    @Test
    void testGetServiceInfoReturnsNull() {
        String serviceName = "nonExistent";
        String groupName = "group";
        ServiceInfo actual = holder.getServiceInfo(serviceName, groupName);
        assertNull(actual);
    }
    
    @Test
    void testShutdown() throws NacosException, NoSuchFieldException, IllegalAccessException {
        Field field = ServiceInfoHolder.class.getDeclaredField("failoverReactor");
        field.setAccessible(true);
        FailoverReactor reactor = (FailoverReactor) field.get(holder);
        Field executorService = FailoverReactor.class.getDeclaredField("executorService");
        executorService.setAccessible(true);
        ScheduledExecutorService pool = (ScheduledExecutorService) executorService.get(reactor);
        assertFalse(pool.isShutdown());
        holder.shutdown();
        assertTrue(pool.isShutdown());
    }
    
    @Test
    void testShutdownShutdownDiskCacheRefresher()
        throws NacosException, NoSuchFieldException, IllegalAccessException {
        ServiceInfoDiskCacheRefresher refresher = getServiceInfoDiskCacheRefresher(holder);
        assertFalse(refresher.isShutdown());
        
        holder.shutdown();
        
        assertTrue(refresher.isShutdown());
    }
    
    @Test
    void testConstructWithCacheLoad() throws NacosException {
        nacosClientProperties.setProperty(PropertyKeyConst.NAMING_LOAD_CACHE_AT_START, "true");
        nacosClientProperties.setProperty(PropertyKeyConst.NAMING_CACHE_REGISTRY_DIR, "non-exist");
        holder.shutdown();
        holder = new ServiceInfoHolder("aa", "scope-001", nacosClientProperties);
        assertEquals(System.getProperty("user.home") + File.separator + "nacos" + File.separator
            + "non-exist" + File.separator + "naming" + File.separator + "aa",
            holder.getCacheDir());
        assertTrue(holder.getServiceInfoMap().isEmpty());
    }
    
    @Test
    void testIsFailoverSwitch()
        throws IllegalAccessException, NoSuchFieldException, NacosException {
        FailoverReactor mock = injectMockFailoverReactor();
        when(mock.isFailoverSwitch()).thenReturn(true);
        assertTrue(holder.isFailoverSwitch());
    }
    
    @Test
    void testGetFailoverServiceInfo()
        throws IllegalAccessException, NoSuchFieldException, NacosException {
        FailoverReactor mock = injectMockFailoverReactor();
        ServiceInfo serviceInfo = new ServiceInfo("a@@b@@c");
        when(mock.getService("a@@b")).thenReturn(serviceInfo);
        assertEquals(serviceInfo, holder.getFailoverServiceInfo("b", "a"));
    }
    
    private FailoverReactor injectMockFailoverReactor()
        throws NoSuchFieldException, IllegalAccessException, NacosException {
        Field field = ServiceInfoHolder.class.getDeclaredField("failoverReactor");
        field.setAccessible(true);
        FailoverReactor old = (FailoverReactor) field.get(holder);
        old.shutdown();
        FailoverReactor mock = mock(FailoverReactor.class);
        field.set(holder, mock);
        return mock;
    }
    
    private ServiceInfoDiskCacheRefresher getServiceInfoDiskCacheRefresher(ServiceInfoHolder holder)
        throws NoSuchFieldException, IllegalAccessException {
        Field field = ServiceInfoHolder.class.getDeclaredField("serviceInfoDiskCacheRefresher");
        field.setAccessible(true);
        return (ServiceInfoDiskCacheRefresher) field.get(holder);
    }
    
    @Test
    void testConcurrentUpdateComputesDiffAgainstTheEntryItReplaced() throws Exception {
        List<Instance> v0Hosts = new ArrayList<>();
        v0Hosts.add(createInstance("1.1.1.1", 1));
        ServiceInfo v0 = buildServiceInfo(v0Hosts, 1L);
        
        List<Instance> v1Hosts = new ArrayList<>();
        v1Hosts.add(createInstance("1.1.1.1", 1));
        v1Hosts.add(createInstance("1.1.1.2", 2));
        ServiceInfo v1 = buildServiceInfo(v1Hosts, 2L);
        
        List<Instance> v2Hosts = new ArrayList<>();
        v2Hosts.add(createInstance("1.1.1.2", 2));
        BlockingServiceInfo v2 = new BlockingServiceInfo(v2Hosts, 3L);
        
        InstancesEventSubscriber subscriber = new InstancesEventSubscriber(3);
        NotifyCenter.registerSubscriber(subscriber);
        try {
            holder.processServiceInfo(v0);
            
            // v2 stalls inside the empty-push validation, which the current code runs after it has
            // already read the cache and before it writes, so the update below lands in that window.
            Thread concurrent = new Thread(() -> holder.processServiceInfo(v2));
            concurrent.start();
            assertTrue(v2.awaitEntered(5, TimeUnit.SECONDS), "v2 should stall before writing the cache");
            
            holder.processServiceInfo(v1);
            
            v2.release();
            concurrent.join(5000);
            assertFalse(concurrent.isAlive());
            
            assertTrue(subscriber.latch.await(5, TimeUnit.SECONDS), "expected three instances events");
            assertEquals(1, subscriber.countAdded("1.1.1.2:2"),
                "1.1.1.2 must be reported as added only by the update that introduced it");
            assertEquals(1, subscriber.countRemoved("1.1.1.1:1"),
                "1.1.1.1 must be reported as removed exactly once");
        } finally {
            NotifyCenter.deregisterSubscriber(subscriber);
        }
    }
    
    private ServiceInfo buildServiceInfo(List<Instance> hosts, long lastRefTime) {
        ServiceInfo serviceInfo = new ServiceInfo("a@@b@@c");
        serviceInfo.setHosts(hosts);
        serviceInfo.setLastRefTime(lastRefTime);
        return serviceInfo;
    }
    
    /**
     * Service info that stalls the first {@link ServiceInfo#getHosts()} call, which is the read the
     * empty-push validation performs between reading and writing the cache.
     */
    private static class BlockingServiceInfo extends ServiceInfo {
        
        private final CountDownLatch entered = new CountDownLatch(1);
        
        private final CountDownLatch release = new CountDownLatch(1);
        
        private final AtomicBoolean blocking = new AtomicBoolean(true);
        
        BlockingServiceInfo(List<Instance> hosts, long lastRefTime) {
            super("a@@b@@c");
            setHosts(hosts);
            setLastRefTime(lastRefTime);
        }
        
        @Override
        public List<Instance> getHosts() {
            if (blocking.compareAndSet(true, false)) {
                entered.countDown();
                try {
                    release.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return super.getHosts();
        }
        
        boolean awaitEntered(long timeout, TimeUnit unit) throws InterruptedException {
            return entered.await(timeout, unit);
        }
        
        void release() {
            release.countDown();
        }
    }
    
    private static class InstancesEventSubscriber extends Subscriber<InstancesChangeEvent> {
        
        private final CountDownLatch latch;
        
        private final List<InstancesChangeEvent> events = new CopyOnWriteArrayList<>();
        
        InstancesEventSubscriber(int expectedEvents) {
            this.latch = new CountDownLatch(expectedEvents);
        }
        
        @Override
        public void onEvent(InstancesChangeEvent event) {
            events.add(event);
            latch.countDown();
        }
        
        @Override
        public Class<? extends Event> subscribeType() {
            return InstancesChangeEvent.class;
        }
        
        long countAdded(String inetAddr) {
            return events.stream().filter(event -> contains(event.getInstancesDiff().getAddedInstances(), inetAddr)).count();
        }
        
        long countRemoved(String inetAddr) {
            return events.stream().filter(event -> contains(event.getInstancesDiff().getRemovedInstances(), inetAddr)).count();
        }
        
        private boolean contains(List<Instance> instances, String inetAddr) {
            return instances.stream().anyMatch(instance -> inetAddr.equals(instance.toInetAddr()));
        }
    }
}
