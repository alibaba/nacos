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

package com.alibaba.nacos.naming.core.v2.index;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.naming.utils.NamingUtils;
import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.notify.listener.Subscriber;
import com.alibaba.nacos.common.utils.FuzzyGroupKeyPattern;
import com.alibaba.nacos.naming.core.v2.event.client.ClientOperationEvent;
import com.alibaba.nacos.naming.core.v2.event.publisher.NamingEventPublisher;
import com.alibaba.nacos.naming.core.v2.event.service.ServiceEvent;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublisherIndexEventOrderingTest {
    
    @Test
    void queueFullFallbackKeepsFuzzyWatchMatchedAfterInterleavedReregistration()
        throws Exception {
        Service service = Service.newService("issue-16023-order", "DEFAULT_GROUP", "orders");
        String serviceKey = NamingUtils.getServiceKey(service.getNamespace(), service.getGroup(),
            service.getName());
        String pattern = FuzzyGroupKeyPattern.generatePattern("orders*", "DEFAULT_GROUP",
            service.getNamespace());
        ConfigurableEnvironment previousEnvironment = EnvUtil.getEnvironment();
        EnvUtil.setEnvironment(new MockEnvironment());
        ClientServiceIndexesManager indexes = new ClientServiceIndexesManager();
        NamingFuzzyWatchContextService fuzzyWatch = new NamingFuzzyWatchContextService();
        ConcurrentMap<String, Set<String>> matched =
            (ConcurrentMap<String, Set<String>>) ReflectionTestUtils.getField(fuzzyWatch,
                "matchedServiceKeysMap");
        Set<String> matchingServices = ConcurrentHashMap.newKeySet();
        matchingServices.add(serviceKey);
        matched.put(pattern, matchingServices);
        CountDownLatch removed = new CountDownLatch(1);
        CountDownLatch resumeRemoval = new CountDownLatch(1);
        ConcurrentMap<Service, Set<String>> gatedIndexes = new ConcurrentHashMap<>() {
            
            @Override
            public Set<String> computeIfPresent(Service key,
                BiFunction<? super Service, ? super Set<String>, ? extends Set<String>> remapping) {
                Set<String> result = super.computeIfPresent(key, remapping);
                removed.countDown();
                try {
                    if (!resumeRemoval.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Timed out waiting to resume removal");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
                return result;
            }
        };
        Set<String> initialClients = ConcurrentHashMap.newKeySet();
        initialClients.add("old-client");
        gatedIndexes.put(service, initialClients);
        ReflectionTestUtils.setField(indexes, "publisherIndexes", gatedIndexes);
        List<String> changes = new CopyOnWriteArrayList<>();
        CountDownLatch additionObserved = new CountDownLatch(1);
        CountDownLatch changesObserved = new CountDownLatch(2);
        AtomicReference<Throwable> listenerFailure = new AtomicReference<>();
        Subscriber<ServiceEvent.ServiceChangedEvent> listener = new Subscriber<>() {
            
            @Override
            public void onEvent(ServiceEvent.ServiceChangedEvent event) {
                if (!service.equals(event.getService())) {
                    return;
                }
                try {
                    changes.add(event.getChangedType());
                    fuzzyWatch.syncServiceContext(event.getService(), event.getChangedType());
                } catch (Throwable failure) {
                    listenerFailure.set(failure);
                } finally {
                    if (Constants.ServiceChangedType.ADD_SERVICE.equals(event.getChangedType())) {
                        additionObserved.countDown();
                    }
                    changesObserved.countDown();
                }
            }
            
            @Override
            public Class<? extends Event> subscribeType() {
                return ServiceEvent.ServiceChangedEvent.class;
            }
        };
        NotifyCenter.registerSubscriber(listener);
        NamingEventPublisher publisher = new NamingEventPublisher();
        publisher.init(ClientOperationEvent.class, 1);
        CountDownLatch workerBlocked = new CountDownLatch(1);
        CountDownLatch resumeWorker = new CountDownLatch(1);
        Subscriber<BlockerEvent> blocker = new Subscriber<>() {
            
            @Override
            public void onEvent(BlockerEvent event) {
                workerBlocked.countDown();
                try {
                    resumeWorker.await(30, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            
            @Override
            public Class<? extends Event> subscribeType() {
                return BlockerEvent.class;
            }
        };
        publisher.addSubscriber(blocker, BlockerEvent.class);
        publisher.addSubscriber(indexes, ClientOperationEvent.ClientRegisterServiceEvent.class);
        publisher.addSubscriber(indexes, ClientOperationEvent.ClientDeregisterServiceEvent.class);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            publisher.publish(new BlockerEvent());
            assertTrue(workerBlocked.await(10, TimeUnit.SECONDS), "Publisher did not block");
            publisher.publish(new BlockerEvent());
            assertEquals(1, publisher.currentEventSize(), "Publisher queue must be full");
            Future<?> removal = executor.submit(() -> publisher.publish(
                new ClientOperationEvent.ClientDeregisterServiceEvent(service, "old-client")));
            assertTrue(removed.await(10, TimeUnit.SECONDS), "Removal did not reach the gate");
            Future<?> registration = executor.submit(() -> publisher.publish(
                new ClientOperationEvent.ClientRegisterServiceEvent(service, "new-client")));
            registration.get(10, TimeUnit.SECONDS);
            assertTrue(additionObserved.await(10, TimeUnit.SECONDS), "Addition was not observed");
            resumeRemoval.countDown();
            removal.get(10, TimeUnit.SECONDS);
            assertTrue(changesObserved.await(10, TimeUnit.SECONDS), "Events were not observed");
            assertNull(listenerFailure.get());
            assertEquals(1, publisher.currentEventSize(), "Publisher queue must stay full");
            assertEquals(List.of(Constants.ServiceChangedType.DELETE_SERVICE,
                Constants.ServiceChangedType.ADD_SERVICE), changes);
            assertEquals(Set.of("new-client"), indexes.getAllClientsRegisteredService(service));
            assertTrue(matchingServices.contains(serviceKey), "Live service lost from fuzzy watch");
        } finally {
            resumeRemoval.countDown();
            resumeWorker.countDown();
            executor.shutdownNow();
            publisher.shutdown();
            publisher.interrupt();
            NotifyCenter.deregisterSubscriber(listener);
            NotifyCenter.deregisterSubscriber(indexes);
            EnvUtil.setEnvironment(previousEnvironment);
        }
    }
    
    private static class BlockerEvent extends Event {
        
        private static final long serialVersionUID = 1L;
    }
}
