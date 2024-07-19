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

package com.alibaba.nacos.client.naming.event;

import com.alibaba.nacos.api.naming.listener.AbstractEventListener;
import com.alibaba.nacos.api.naming.listener.EventListener;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.api.naming.selector.NamingSelector;
import com.alibaba.nacos.client.naming.selector.DefaultNamingSelector;
import com.alibaba.nacos.client.naming.selector.NamingSelectorFactory;
import com.alibaba.nacos.client.naming.selector.NamingSelectorWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

class InstancesChangeNotifierTest {
    
    private static final String EVENT_SCOPE_CASE = "scope-001";
    
    private static final String GROUP_CASE = "a";
    
    private static final String SERVICE_NAME_CASE = "b";
    
    private static final String CLUSTER_STR_CASE = "c";
    
    InstancesChangeNotifier instancesChangeNotifier;
    
    @BeforeEach
    public void setUp() {
        instancesChangeNotifier = new InstancesChangeNotifier(EVENT_SCOPE_CASE);
    }
    
    @Test
    void testRegisterListener() {
        List<String> clusters = Collections.singletonList(CLUSTER_STR_CASE);
        EventListener listener = Mockito.mock(EventListener.class);
        NamingSelector selector = NamingSelectorFactory.newClusterSelector(clusters);
        NamingSelectorWrapper wrapper = new NamingSelectorWrapper(SERVICE_NAME_CASE, GROUP_CASE, CLUSTER_STR_CASE,
                selector, listener);
        instancesChangeNotifier.registerListener(GROUP_CASE, SERVICE_NAME_CASE, wrapper);
        List<ServiceInfo> subscribeServices = instancesChangeNotifier.getSubscribeServices();
        assertEquals(1, subscribeServices.size());
        assertEquals(GROUP_CASE, subscribeServices.get(0).getGroupName());
        assertEquals(SERVICE_NAME_CASE, subscribeServices.get(0).getName());
        assertNull(subscribeServices.get(0).getClusters());
        
        List<Instance> hosts = new ArrayList<>();
        Instance ins = new Instance();
        hosts.add(ins);
        InstancesDiff diff = new InstancesDiff();
        diff.setAddedInstances(hosts);
        InstancesChangeEvent event = new InstancesChangeEvent(EVENT_SCOPE_CASE, SERVICE_NAME_CASE, GROUP_CASE,
                CLUSTER_STR_CASE, hosts, diff);
        assertTrue(instancesChangeNotifier.scopeMatches(event));
    }
    
    @Test
    void testDeregisterListener() {
        List<String> clusters = Collections.singletonList(CLUSTER_STR_CASE);
        EventListener listener = Mockito.mock(EventListener.class);
        NamingSelector selector = NamingSelectorFactory.newClusterSelector(clusters);
        NamingSelectorWrapper wrapper = new NamingSelectorWrapper(selector, listener);
        instancesChangeNotifier.registerListener(GROUP_CASE, SERVICE_NAME_CASE, wrapper);
        List<ServiceInfo> subscribeServices = instancesChangeNotifier.getSubscribeServices();
        assertEquals(1, subscribeServices.size());
        
        instancesChangeNotifier.deregisterListener(GROUP_CASE, SERVICE_NAME_CASE, wrapper);
        
        List<ServiceInfo> subscribeServices2 = instancesChangeNotifier.getSubscribeServices();
        assertEquals(0, subscribeServices2.size());
    }
    
    @Test
    void testIsSubscribed() {
        List<String> clusters = Collections.singletonList(CLUSTER_STR_CASE);
        EventListener listener = Mockito.mock(EventListener.class);
        NamingSelector selector = NamingSelectorFactory.newClusterSelector(clusters);
        assertFalse(instancesChangeNotifier.isSubscribed(GROUP_CASE, SERVICE_NAME_CASE));
        
        NamingSelectorWrapper wrapper = new NamingSelectorWrapper(SERVICE_NAME_CASE, GROUP_CASE, CLUSTER_STR_CASE,
                selector, listener);
        instancesChangeNotifier.registerListener(GROUP_CASE, SERVICE_NAME_CASE, wrapper);
        assertTrue(instancesChangeNotifier.isSubscribed(GROUP_CASE, SERVICE_NAME_CASE));
    }
    
    @Test
    void testOnEvent() {
        List<String> clusters = Collections.singletonList(CLUSTER_STR_CASE);
        NamingSelector selector = NamingSelectorFactory.newClusterSelector(clusters);
        EventListener listener = Mockito.mock(EventListener.class);
        
        NamingSelectorWrapper wrapper = new NamingSelectorWrapper(SERVICE_NAME_CASE, GROUP_CASE, CLUSTER_STR_CASE,
                selector, listener);
        instancesChangeNotifier.registerListener(GROUP_CASE, SERVICE_NAME_CASE, wrapper);
        Instance instance = new Instance();
        InstancesDiff diff = new InstancesDiff(null, Collections.singletonList(instance), null);
        instance.setClusterName(CLUSTER_STR_CASE);
        InstancesChangeEvent event1 = new InstancesChangeEvent(null, SERVICE_NAME_CASE, GROUP_CASE, CLUSTER_STR_CASE,
                Collections.emptyList(), diff);
        instancesChangeNotifier.onEvent(event1);
        Mockito.verify(listener, times(1)).onEvent(any());
    }
    
    @Test
    void testOnEventWithoutListener() {
        InstancesChangeEvent event1 = Mockito.mock(InstancesChangeEvent.class);
        when(event1.getClusters()).thenReturn(CLUSTER_STR_CASE);
        when(event1.getGroupName()).thenReturn(GROUP_CASE);
        when(event1.getServiceName()).thenReturn(SERVICE_NAME_CASE);
        EventListener listener = Mockito.mock(EventListener.class);
        instancesChangeNotifier.registerListener(GROUP_CASE, SERVICE_NAME_CASE + "c", new NamingSelectorWrapper(
                NamingSelectorFactory.newClusterSelector(Collections.singletonList(CLUSTER_STR_CASE)), listener));
        instancesChangeNotifier.onEvent(event1);
        Mockito.verify(listener, never()).onEvent(any());
    }
    
    @Test
    void testOnEventByExecutor() {
        AbstractEventListener listener = Mockito.mock(AbstractEventListener.class);
        Executor executor = mock(Executor.class);
        when(listener.getExecutor()).thenReturn(executor);
        
        instancesChangeNotifier.registerListener(GROUP_CASE, SERVICE_NAME_CASE,
                new NamingSelectorWrapper(new DefaultNamingSelector(instance -> true), listener));
        InstancesDiff instancesDiff = new InstancesDiff();
        instancesDiff.setRemovedInstances(Collections.singletonList(new Instance()));
        InstancesChangeEvent event = new InstancesChangeEvent(EVENT_SCOPE_CASE, SERVICE_NAME_CASE, GROUP_CASE,
                CLUSTER_STR_CASE, new ArrayList<>(), instancesDiff);
        instancesChangeNotifier.onEvent(event);
        Mockito.verify(executor).execute(any());
    }
    
    @Test
    void testSubscribeType() {
        assertEquals(InstancesChangeEvent.class, instancesChangeNotifier.subscribeType());
    }
}