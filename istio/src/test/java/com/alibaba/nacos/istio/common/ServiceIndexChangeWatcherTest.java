/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.istio.common;

import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.naming.core.v2.client.Client;
import com.alibaba.nacos.naming.core.v2.event.client.ClientOperationEvent;
import com.alibaba.nacos.naming.core.v2.event.metadata.InfoChangeEvent;
import com.alibaba.nacos.naming.core.v2.event.service.ServiceEvent;
import com.alibaba.nacos.naming.core.v2.index.ClientServiceIndexesManager;
import com.alibaba.nacos.naming.core.v2.index.ServiceStorage;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.core.v2.pojo.InstancePublishInfo;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ServiceIndexChangeWatcherTest {
    
    @Test
    void registrationAndReleaseRefreshViewAfterIndexUpdate() throws Exception {
        Service service = Service.newService("issue-16023", "DEFAULT_GROUP", "orders");
        ServiceStorage storage = mock(ServiceStorage.class);
        ClientServiceIndexesManager indexes = new ClientServiceIndexesManager();
        NacosServiceInfoResourceWatcher watcher = new NacosServiceInfoResourceWatcher();
        ReflectionTestUtils.setField(watcher, "serviceStorage", storage);
        ReflectionTestUtils.setField(watcher, "isInitial", false);
        Instance instance = new Instance();
        instance.setIp("127.0.0.2");
        instance.setPort(8080);
        try {
            when(storage.getPushData(service)).thenAnswer(invocation -> {
                List<Instance> hosts = new ArrayList<>();
                indexes.getAllClientsRegisteredService(service)
                    .forEach(clientId -> hosts.add(instance));
                ServiceInfo result = new ServiceInfo();
                result.setHosts(hosts);
                return result;
            });
            ClientOperationEvent.ClientRegisterServiceEvent event =
                new ClientOperationEvent.ClientRegisterServiceEvent(service, "client");
            assertTrue(watcher.snapshot().isEmpty());
            indexes.onEvent(event);
            waitFor(() -> watcher.snapshot().containsKey("orders.DEFAULT-GROUP.issue-16023")
                && watcher.snapshot().get("orders.DEFAULT-GROUP.issue-16023").getHosts()
                    .size() == 1);
            assertEquals(1,
                watcher.snapshot().get("orders.DEFAULT-GROUP.issue-16023").getHosts().size());
            
            indexes
                .onEvent(new ClientOperationEvent.ClientRegisterServiceEvent(service, "client-2"));
            waitFor(() -> watcher.snapshot().get("orders.DEFAULT-GROUP.issue-16023").getHosts()
                .size() == 2);
            indexes
                .onEvent(new ClientOperationEvent.ClientDeregisterServiceEvent(service, "client"));
            waitFor(() -> watcher.snapshot().get("orders.DEFAULT-GROUP.issue-16023").getHosts()
                .size() == 1);
            
            indexes.onEvent(
                new ClientOperationEvent.ClientDeregisterServiceEvent(service, "client-2"));
            waitFor(() -> !watcher.snapshot().containsKey("orders.DEFAULT-GROUP.issue-16023"));
            
            indexes
                .onEvent(new ClientOperationEvent.ClientRegisterServiceEvent(service, "client-3"));
            waitFor(() -> watcher.snapshot().containsKey("orders.DEFAULT-GROUP.issue-16023"));
            Client released = mock(Client.class);
            when(released.getClientId()).thenReturn("client-3");
            when(released.getAllSubscribeService()).thenReturn(Collections.emptySet());
            when(released.getAllPublishedService()).thenReturn(Collections.singleton(service));
            when(released.getInstancePublishInfo(service))
                .thenReturn(new InstancePublishInfo("127.0.0.2", 8080));
            indexes.onEvent(new ClientOperationEvent.ClientReleaseEvent(released, true));
            waitFor(() -> !watcher.snapshot().containsKey("orders.DEFAULT-GROUP.issue-16023"));
        } finally {
            NotifyCenter.deregisterSubscriber(watcher);
            NotifyCenter.deregisterSubscriber(indexes);
        }
    }
    
    @Test
    void serviceAndMetadataEventsRefreshTheView() {
        Service service = Service.newService("issue-16023", "DEFAULT_GROUP", "orders");
        String key = "orders.DEFAULT-GROUP.issue-16023";
        ServiceStorage storage = mock(ServiceStorage.class);
        NacosServiceInfoResourceWatcher watcher = new NacosServiceInfoResourceWatcher();
        ReflectionTestUtils.setField(watcher, "serviceStorage", storage);
        ReflectionTestUtils.setField(watcher, "isInitial", false);
        Instance first = new Instance();
        first.setIp("127.0.0.2");
        first.setPort(8080);
        first.setHealthy(true);
        Instance second = new Instance();
        second.setIp("127.0.0.3");
        second.setPort(8080);
        second.setHealthy(true);
        ServiceInfo info = new ServiceInfo();
        info.setHosts(List.of(first, second));
        when(storage.getPushData(service)).thenReturn(info);
        try {
            watcher.onEvent(new ServiceEvent.ServiceChangedEvent(service,
                Constants.ServiceChangedType.INSTANCE_CHANGED));
            assertEquals(2, watcher.snapshot().get(key).getHosts().size());
            
            first.setHealthy(false);
            watcher.onEvent(new ServiceEvent.ServiceChangedEvent(service,
                Constants.ServiceChangedType.HEART_BEAT));
            assertEquals(1, watcher.snapshot().get(key).getHosts().size());
            assertEquals("127.0.0.3", watcher.snapshot().get(key).getHosts().get(0).getAdder());
            
            second.getMetadata().put("region", "region-test");
            watcher.onEvent(new ServiceEvent.ServiceChangedEvent(service,
                Constants.ServiceChangedType.INSTANCE_CHANGED));
            assertEquals("region-test", watcher.snapshot().get(key).getHosts().get(0)
                .getLocality().getRegion());
            
            second.getMetadata().put("region", "metadata-event");
            watcher.onEvent(new InfoChangeEvent.ServiceInfoChangeEvent(service));
            assertEquals("metadata-event", watcher.snapshot().get(key).getHosts().get(0)
                .getLocality().getRegion());
        } finally {
            NotifyCenter.deregisterSubscriber(watcher);
        }
    }
    
    @Test
    void concurrentRegistrationsAndDeregistrationsConverge() throws Exception {
        Service service = Service.newService("issue-16023-burst", "DEFAULT_GROUP", "orders");
        String key = "orders.DEFAULT-GROUP.issue-16023-burst";
        ServiceStorage storage = mock(ServiceStorage.class);
        ClientServiceIndexesManager indexes = new ClientServiceIndexesManager();
        NacosServiceInfoResourceWatcher watcher = new NacosServiceInfoResourceWatcher();
        ReflectionTestUtils.setField(watcher, "serviceStorage", storage);
        ReflectionTestUtils.setField(watcher, "isInitial", false);
        Instance instance = new Instance();
        instance.setIp("127.0.0.2");
        instance.setPort(8080);
        when(storage.getPushData(service)).thenAnswer(invocation -> {
            List<Instance> hosts = new ArrayList<>();
            indexes.getAllClientsRegisteredService(service)
                .forEach(clientId -> hosts.add(instance));
            ServiceInfo result = new ServiceInfo();
            result.setHosts(hosts);
            return result;
        });
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Void>> registrations = new ArrayList<>();
            List<Callable<Void>> deregistrations = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                String clientId = "burst-" + i;
                registrations.add(() -> {
                    indexes.onEvent(
                        new ClientOperationEvent.ClientRegisterServiceEvent(service, clientId));
                    return null;
                });
                deregistrations.add(() -> {
                    indexes.onEvent(
                        new ClientOperationEvent.ClientDeregisterServiceEvent(service, clientId));
                    return null;
                });
            }
            for (var future : executor.invokeAll(registrations)) {
                future.get();
            }
            waitFor(() -> watcher.snapshot().containsKey(key)
                && watcher.snapshot().get(key).getHosts().size() == 20);
            for (var future : executor.invokeAll(deregistrations)) {
                future.get();
            }
            waitFor(() -> !watcher.snapshot().containsKey(key));
        } finally {
            executor.shutdownNow();
            NotifyCenter.deregisterSubscriber(watcher);
            NotifyCenter.deregisterSubscriber(indexes);
        }
    }
    
    private static void waitFor(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertTrue(condition.getAsBoolean(), "The watcher did not observe the indexed instance");
    }
}
