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
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.naming.core.v2.event.client.ClientOperationEvent;
import com.alibaba.nacos.naming.core.v2.index.ClientServiceIndexesManager;
import com.alibaba.nacos.naming.core.v2.index.ServiceStorage;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ServiceIndexChangeWatcherTest {
    
    @Test
    void registrationRefreshesViewWhenWatcherRunsBeforeIndexUpdate() throws Exception {
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
            watcher.onEvent(event);
            assertTrue(!watcher.snapshot().containsKey("orders.DEFAULT-GROUP.issue-16023")
                || watcher.snapshot().get("orders.DEFAULT-GROUP.issue-16023").getHosts().isEmpty());
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
            indexes.onEvent(
                new ClientOperationEvent.ClientDeregisterServiceEvent(service, "client-3"));
            waitFor(() -> !watcher.snapshot().containsKey("orders.DEFAULT-GROUP.issue-16023"));
        } finally {
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
