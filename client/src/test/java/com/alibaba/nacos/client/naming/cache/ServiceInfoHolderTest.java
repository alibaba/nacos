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
import io.prometheus.client.Gauge;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ScheduledExecutorService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
    
    }
    
    @Test
    void testGetServiceInfoMap() throws NoSuchFieldException, IllegalAccessException {
        assertEquals(0, holder.getServiceInfoMap().size());
        Field fieldNotifierEventScope = ServiceInfoHolder.class.getDeclaredField("notifierEventScope");
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
        
        Gauge.Child mockGaugeChild = mock(Gauge.Child.class);
        try (MockedStatic<MetricsMonitor> mockedMetricsMonitor = Mockito.mockStatic(MetricsMonitor.class)) {
            mockedMetricsMonitor.when(MetricsMonitor::getServiceInfoMapSizeMonitor).thenReturn(mockGaugeChild);
            
            holder.processServiceInfo(info);
            
            verify(mockGaugeChild, times(1)).set(1);
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
        
        try (MockedStatic<MetricsMonitor> mockedMetricsMonitor = Mockito.mockStatic(MetricsMonitor.class)) {
            holder.processServiceInfo(info);
            
            mockedMetricsMonitor.verify(MetricsMonitor::getServiceInfoMapSizeMonitor, never());
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
        
        Gauge.Child mockGaugeChild = mock(Gauge.Child.class);
        try (MockedStatic<MetricsMonitor> mockedMetricsMonitor = Mockito.mockStatic(MetricsMonitor.class)) {
            mockedMetricsMonitor.when(MetricsMonitor::getServiceInfoMapSizeMonitor).thenReturn(mockGaugeChild);
            
            holder.processServiceInfo(info);
            
            verify(mockGaugeChild, times(1)).set(1);
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
        
        Gauge.Child mockGaugeChild = mock(Gauge.Child.class);
        RuntimeException exception = new RuntimeException("Mocked exception");
        
        try (MockedStatic<MetricsMonitor> mockedMetricsMonitor = Mockito.mockStatic(MetricsMonitor.class)) {
            mockedMetricsMonitor.when(MetricsMonitor::getServiceInfoMapSizeMonitor).thenReturn(mockGaugeChild);
            doThrow(exception).when(mockGaugeChild).set(anyInt());
            
            ServiceInfo actual2 = holder.processServiceInfo(info);
            
            assertEquals(info, actual2);
        }
    }
    
    private ServiceInfoHolder createServiceInfoHolder(Boolean enableClientMetrics) {
        Properties properties = new Properties();
        if (enableClientMetrics != null) {
            properties.put(PropertyKeyConst.ENABLE_CLIENT_METRICS, String.valueOf(enableClientMetrics));
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
        assertEquals(expect.getKey(), actual.getKey());
        assertEquals(expect.getHosts().size(), actual.getHosts().size());
        assertEquals(expect.getHosts().get(0), actual.getHosts().get(0));
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
    void testConstructWithCacheLoad() throws NacosException {
        nacosClientProperties.setProperty(PropertyKeyConst.NAMING_LOAD_CACHE_AT_START, "true");
        nacosClientProperties.setProperty(PropertyKeyConst.NAMING_CACHE_REGISTRY_DIR, "non-exist");
        holder.shutdown();
        holder = new ServiceInfoHolder("aa", "scope-001", nacosClientProperties);
        assertEquals(System.getProperty("user.home") + "/nacos/non-exist/naming/aa", holder.getCacheDir());
        assertTrue(holder.getServiceInfoMap().isEmpty());
    }
    
    @Test
    void testIsFailoverSwitch() throws IllegalAccessException, NoSuchFieldException, NacosException {
        FailoverReactor mock = injectMockFailoverReactor();
        when(mock.isFailoverSwitch()).thenReturn(true);
        assertTrue(holder.isFailoverSwitch());
    }
    
    @Test
    void testGetFailoverServiceInfo() throws IllegalAccessException, NoSuchFieldException, NacosException {
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

    @Test
    void testProcessServiceWithOutOfOrderTimestamp() {
        // Scenario: Out-of-order timestamps
        // InstancesDiffer will reject updates if newService.lastRefTime < oldService.lastRefTime

        // T1: Initial push with timestamp 100
        ServiceInfo info1 = new ServiceInfo("a@@b@@c");
        Instance instance1 = createInstance("192.168.1.1", 8080);
        instance1.setHealthy(true);
        List<Instance> hosts1 = new ArrayList<>();
        hosts1.add(instance1);
        info1.setHosts(hosts1);
        info1.setLastRefTime(100L);

        holder.processServiceInfo(info1);
        assertEquals(100L, holder.getServiceInfoMap().get("a@@b").getLastRefTime());
        assertEquals(1, holder.getServiceInfoMap().get("a@@b").getHosts().size());

        // T2: Out-of-order push with older timestamp 50 but different data
        // This will be REJECTED because timestamp is older (50 < 100)
        ServiceInfo info2 = new ServiceInfo("a@@b@@c");
        Instance instance1T2 = createInstance("192.168.1.1", 8080);
        instance1T2.setHealthy(true);
        Instance instance2T2 = createInstance("192.168.1.2", 8080);
        instance2T2.setHealthy(true);
        List<Instance> hosts2 = new ArrayList<>();
        hosts2.add(instance1T2);
        hosts2.add(instance2T2);
        info2.setHosts(hosts2);
        info2.setLastRefTime(50L);

        holder.processServiceInfo(info2);
        // Cache will NOT be updated because timestamp is older
        // InstancesDiffer returns empty diff for out-of-date data
        assertEquals(100L, holder.getServiceInfoMap().get("a@@b").getLastRefTime());
        assertEquals(1, holder.getServiceInfoMap().get("a@@b").getHosts().size());
        assertEquals("192.168.1.1", holder.getServiceInfoMap().get("a@@b").getHosts().get(0).getIp());

        // T3: Push with newer timestamp 150 and different data
        ServiceInfo info3 = new ServiceInfo("a@@b@@c");
        Instance instance1T3 = createInstance("192.168.1.1", 8080);
        instance1T3.setHealthy(true);
        Instance instance2T3 = createInstance("192.168.1.2", 8080);
        instance2T3.setHealthy(true);
        List<Instance> hosts3 = new ArrayList<>();
        hosts3.add(instance1T3);
        hosts3.add(instance2T3);
        info3.setHosts(hosts3);
        info3.setLastRefTime(150L);

        holder.processServiceInfo(info3);
        // Cache should be updated because timestamp is newer and data is different
        assertEquals(150L, holder.getServiceInfoMap().get("a@@b").getLastRefTime());
        assertEquals(2, holder.getServiceInfoMap().get("a@@b").getHosts().size());
    }

    @Test
    void testProcessServiceWithSameTimestampDifferentData() {
        // Scenario: Same timestamp but different data

        // T1: Initial push
        ServiceInfo info1 = new ServiceInfo("a@@b@@c");
        Instance instance1 = createInstance("192.168.1.1", 8080);
        List<Instance> hosts1 = new ArrayList<>();
        hosts1.add(instance1);
        info1.setHosts(hosts1);
        info1.setLastRefTime(100L);

        holder.processServiceInfo(info1);
        assertEquals(100L, holder.getServiceInfoMap().get("a@@b").getLastRefTime());
        assertEquals("192.168.1.1", holder.getServiceInfoMap().get("a@@b").getHosts().get(0).getIp());

        // T2: Push with same timestamp but different data
        ServiceInfo info2 = new ServiceInfo("a@@b@@c");
        Instance instance2 = createInstance("192.168.1.2", 8080);
        List<Instance> hosts2 = new ArrayList<>();
        hosts2.add(instance2);
        info2.setHosts(hosts2);
        info2.setLastRefTime(100L); // Same timestamp

        holder.processServiceInfo(info2);
        // Should be updated because data is different
        assertEquals(100L, holder.getServiceInfoMap().get("a@@b").getLastRefTime());
        assertEquals("192.168.1.2", holder.getServiceInfoMap().get("a@@b").getHosts().get(0).getIp());
    }

    @Test
    void testProcessServiceWithNoRealChange() {
        // Test that identical data with newer timestamp doesn't trigger update

        ServiceInfo info1 = new ServiceInfo("a@@b@@c");
        Instance instance1 = createInstance("192.168.1.1", 8080);
        instance1.setHealthy(true);
        instance1.setWeight(1.0);
        instance1.setClusterName("DEFAULT");
        List<Instance> hosts1 = new ArrayList<>();
        hosts1.add(instance1);
        info1.setHosts(hosts1);
        info1.setLastRefTime(100L);

        holder.processServiceInfo(info1);
        long firstRefTime = holder.getServiceInfoMap().get("a@@b").getLastRefTime();

        // Push identical data multiple times with different timestamps
        for (int i = 0; i < 5; i++) {
            ServiceInfo infoN = new ServiceInfo("a@@b@@c");
            Instance instanceN = createInstance("192.168.1.1", 8080);
            instanceN.setHealthy(true);
            instanceN.setWeight(1.0);
            instanceN.setClusterName("DEFAULT");
            List<Instance> hostsN = new ArrayList<>();
            hostsN.add(instanceN);
            infoN.setHosts(hostsN);
            infoN.setLastRefTime(200L + i * 10);

            holder.processServiceInfo(infoN);
        }

        // LastRefTime should remain the same as no real data change occurred
        assertEquals(firstRefTime, holder.getServiceInfoMap().get("a@@b").getLastRefTime());
    }

    @Test
    void testProcessServiceInfoWithClockSkew() {
        // T1: Receive s1 data from nacos1 (timestamp=10ns)
        ServiceInfo s1FromNacos1 = new ServiceInfo("a@@b@@c");
        Instance instance1 = createInstance("192.168.1.1", 8080);
        instance1.setHealthy(true);
        instance1.setWeight(1.0);
        List<Instance> hostsS1 = new ArrayList<>();
        hostsS1.add(instance1);
        s1FromNacos1.setHosts(hostsS1);
        s1FromNacos1.setLastRefTime(10L);

        ServiceInfo result1 = holder.processServiceInfo(s1FromNacos1);
        assertEquals(s1FromNacos1.getKey(), result1.getKey());
        assertEquals(1, holder.getServiceInfoMap().size());
        assertEquals(10L, holder.getServiceInfoMap().get("a@@b").getLastRefTime());

        // T2: Receive s1 data from nacos2 (timestamp=12ns, same data but newer timestamp)
        ServiceInfo s1FromNacos2 = new ServiceInfo("a@@b@@c");
        Instance instance1Copy = createInstance("192.168.1.1", 8080);
        instance1Copy.setHealthy(true);
        instance1Copy.setWeight(1.0);
        List<Instance> hostsS1Copy = new ArrayList<>();
        hostsS1Copy.add(instance1Copy);
        s1FromNacos2.setHosts(hostsS1Copy);
        s1FromNacos2.setLastRefTime(12L);

        ServiceInfo result2 = holder.processServiceInfo(s1FromNacos2);
        assertEquals(s1FromNacos2.getKey(), result2.getKey());
        // Cache should NOT be updated because data is identical (no diff)
        // The lastRefTime should remain 10, not 12
        assertEquals(10L, holder.getServiceInfoMap().get("a@@b").getLastRefTime());

        // T3: Receive s2 data from nacos1 (timestamp=11ns, older than nacos2's push but with new data)
        ServiceInfo s2FromNacos1 = new ServiceInfo("a@@b@@c");
        Instance instance2 = createInstance("192.168.1.2", 8080);
        instance2.setHealthy(true);
        instance2.setWeight(1.0);
        List<Instance> hostsS2 = new ArrayList<>();
        hostsS2.add(instance2);
        s2FromNacos1.setHosts(hostsS2);
        s2FromNacos1.setLastRefTime(11L);

        ServiceInfo result3 = holder.processServiceInfo(s2FromNacos1);
        assertEquals(s2FromNacos1.getKey(), result3.getKey());
        // Cache should be updated because oldService (s1 with 10ns) differs from newService (s2 with 11ns)
        assertEquals(11L, holder.getServiceInfoMap().get("a@@b").getLastRefTime());
        assertEquals("192.168.1.2", holder.getServiceInfoMap().get("a@@b").getHosts().get(0).getIp());

        // T4: Receive s2 data from nacos2 (timestamp=14ns, same s2 data)
        ServiceInfo s2FromNacos2 = new ServiceInfo("a@@b@@c");
        Instance instance2Copy = createInstance("192.168.1.2", 8080);
        instance2Copy.setHealthy(true);
        instance2Copy.setWeight(1.0);
        List<Instance> hostsS2Copy = new ArrayList<>();
        hostsS2Copy.add(instance2Copy);
        s2FromNacos2.setHosts(hostsS2Copy);
        s2FromNacos2.setLastRefTime(14L);

        ServiceInfo result4 = holder.processServiceInfo(s2FromNacos2);
        assertEquals(s2FromNacos2.getKey(), result4.getKey());
        // Cache should NOT be updated because data is identical (no diff)
        // The lastRefTime should remain 11, not 14
        assertEquals(11L, holder.getServiceInfoMap().get("a@@b").getLastRefTime());
        assertEquals("192.168.1.2", holder.getServiceInfoMap().get("a@@b").getHosts().get(0).getIp());
    }
}