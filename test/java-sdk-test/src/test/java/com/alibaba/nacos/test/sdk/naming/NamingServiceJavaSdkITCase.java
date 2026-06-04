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

package com.alibaba.nacos.test.sdk.naming;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.PreservedMetadataKeys;
import com.alibaba.nacos.api.naming.listener.AbstractFuzzyWatchEventWatcher;
import com.alibaba.nacos.api.naming.listener.Event;
import com.alibaba.nacos.api.naming.listener.EventListener;
import com.alibaba.nacos.api.naming.listener.FuzzyWatchChangeEvent;
import com.alibaba.nacos.api.naming.listener.NamingEvent;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ListView;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.api.naming.selector.NamingSelector;
import com.alibaba.nacos.api.selector.NoneSelector;
import com.alibaba.nacos.test.sdk.JavaSdkBaseITCase;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for Java SDK {@link NamingService}.
 *
 * <p>The full scenario matrix and remaining gaps are recorded in
 * {@code test/java-sdk-test/JAVA_SDK_IT_SCENARIOS.md}.
 *
 * <p>Scenario coverage:
 * <ul>
 *     <li>Expected capability: register, query, select, list, and deregister instances
 *     through the public Java SDK factory; default-group overloads and batch registration are
 *     exercised.</li>
 *     <li>Boundary/validation: explicit/default group and cluster are honored, missing service
 *     is empty, duplicate register and missing deregister are idempotent, disabled or zero-weight
 *     instances are filtered from healthy selection, explicit unhealthy selection returns only
 *     unhealthy instances, empty batch register leaves no instance, list pagination boundaries
 *     return stable result shapes, blank service name is rejected by the grouped-name utility, and
 *     null instance, invalid port/cluster, invalid heartbeat metadata, persistent batch instance,
 *     empty batch deregister, plus mismatched group-prefix inputs throw controlled SDK
 *     exceptions where the SDK defines them; null batch lists fail before a remote call.</li>
 *     <li>Listener/error handling: subscribe receives an instance change event, subscribe=true
 *     queries refresh cached service info through later push, subscribe state is visible, cluster
 *     and selector subscribe overloads filter listener events, null listener subscribe is a no-op,
 *     unsubscribe stops later callbacks, fuzzy watch returns matched service keys, receives add
 *     events, stops callbacks after cancel, and deregister cleanup plus SDK shutdown are safe.</li>
 * </ul>
 *
 * @author xiweng.yy
 */
public class NamingServiceJavaSdkITCase extends JavaSdkBaseITCase {

    private static final String TEST_IP = "127.0.0.1";

    @Test
    public void testRegisterQuerySelectListAndDeregisterInstance() throws Exception {
        NamingService namingService = createNamingService();
        String serviceName = randomServiceName("lifecycle");
        String groupName = randomGroup("naming");
        String clusterName = "sdk-cluster";
        int port = randomPort();
        Instance instance = buildInstance(port, clusterName);
        addCleanup(() -> namingService.deregisterInstance(serviceName, groupName, instance));

        namingService.registerInstance(serviceName, groupName, instance);
        waitUntil("registered instance should be queryable",
                () -> containsInstance(namingService.getAllInstances(serviceName, groupName, false), port));

        List<Instance> allInstances = namingService.getAllInstances(serviceName, groupName,
                Collections.singletonList(clusterName), false);
        Instance queried = findInstance(allInstances, port);
        assertEquals(clusterName, queried.getClusterName());
        assertEquals("java-sdk-test", queried.getMetadata().get("source"));
        assertTrue(queried.isHealthy(), queried.toString());
        assertTrue(queried.isEnabled(), queried.toString());

        assertTrue(containsInstance(namingService.selectInstances(serviceName, groupName, true, false), port));
        assertEquals(port, namingService.selectOneHealthyInstance(serviceName, groupName, false).getPort());
        ListView<String> services = namingService.getServicesOfServer(1, 20, groupName);
        assertTrue(services.getData().contains(serviceName), services.toString());

        namingService.deregisterInstance(serviceName, groupName, instance);
        waitUntil("deregistered instance should be absent",
                () -> !containsInstance(namingService.getAllInstances(serviceName, groupName, false), port));
        assertFalse(containsInstance(namingService.getAllInstances(serviceName, groupName, false), port));
    }

    @Test
    public void testDefaultGroupStringOverloadsRegisterAndDeregisterInstance() throws Exception {
        NamingService namingService = createNamingService();
        String serviceName = randomServiceName("default-group");
        int port = randomPort();
        addCleanup(() -> namingService.deregisterInstance(serviceName, TEST_IP, port));

        namingService.registerInstance(serviceName, TEST_IP, port);
        waitUntil("default group instance should be queryable",
                () -> containsInstance(namingService.getAllInstances(serviceName, false), port));

        assertTrue(containsInstance(namingService.getAllInstances(serviceName, false), port));
        assertEquals(port, namingService.selectOneHealthyInstance(serviceName, false).getPort());
        assertTrue(namingService.getServicesOfServer(1, 100).getData().contains(serviceName));

        namingService.deregisterInstance(serviceName, TEST_IP, port);
        waitUntil("default group instance should be absent",
                () -> !containsInstance(namingService.getAllInstances(serviceName, false), port));
    }

    @Test
    public void testClusterStringOverloadsRegisterAndDeregisterInstance() throws Exception {
        NamingService namingService = createNamingService();
        String serviceName = randomServiceName("cluster-overload");
        String groupName = randomGroup("naming");
        String clusterName = "sdk-string-cluster";
        int port = randomPort();
        addCleanup(() -> namingService.deregisterInstance(serviceName, groupName, TEST_IP, port,
                clusterName));

        namingService.registerInstance(serviceName, groupName, TEST_IP, port, clusterName);
        waitUntil("cluster string overload instance should be queryable",
                () -> containsInstance(namingService.getAllInstances(serviceName, groupName,
                        Collections.singletonList(clusterName), false), port));

        List<Instance> clusterInstances = namingService.getAllInstances(serviceName, groupName,
                Collections.singletonList(clusterName), false);
        Instance queried = findInstance(clusterInstances, port);
        assertEquals(clusterName, queried.getClusterName(), queried.toString());

        namingService.deregisterInstance(serviceName, groupName, TEST_IP, port, clusterName);
        waitUntil("cluster string overload instance should be absent",
                () -> !containsInstance(namingService.getAllInstances(serviceName, groupName,
                        Collections.singletonList(clusterName), false), port));
    }

    @Test
    public void testSinglePersistentInstanceRegisterAndDeregister() throws Exception {
        NamingService namingService = createNamingService();
        String serviceName = randomServiceName("persistent");
        String groupName = randomGroup("naming");
        Instance instance = buildInstance(randomPort(), Constants.DEFAULT_CLUSTER_NAME);
        instance.setEphemeral(false);
        addCleanup(() -> namingService.deregisterInstance(serviceName, groupName, instance));

        namingService.registerInstance(serviceName, groupName, instance);
        waitUntil("single persistent instance should be queryable",
                () -> containsInstance(namingService.getAllInstances(serviceName, groupName,
                        false), instance.getPort()));

        Instance queried = findInstance(namingService.getAllInstances(serviceName, groupName,
                false), instance.getPort());
        assertFalse(queried.isEphemeral(), queried.toString());

        namingService.deregisterInstance(serviceName, groupName, instance);
        waitUntil("single persistent instance should be absent after deregister",
                () -> !containsInstance(namingService.getAllInstances(serviceName, groupName,
                        false), instance.getPort()));
    }

    @Test
    public void testDuplicateRegisterAndMissingDeregisterAreIdempotent() throws Exception {
        NamingService namingService = createNamingService();
        String serviceName = randomServiceName("idempotent");
        String groupName = randomGroup("naming");
        Instance instance = buildInstance(randomPort(), Constants.DEFAULT_CLUSTER_NAME);
        Instance missingInstance = buildInstance(randomPort(), Constants.DEFAULT_CLUSTER_NAME);
        addCleanup(() -> namingService.deregisterInstance(serviceName, groupName, instance));

        namingService.registerInstance(serviceName, groupName, instance);
        namingService.registerInstance(serviceName, groupName, instance);
        waitUntil("duplicate registered instance should be queryable once", () -> {
            List<Instance> current = namingService.getAllInstances(serviceName, groupName, false);
            return 1 == countInstances(current, instance.getPort());
        });
        assertEquals(1, countInstances(namingService.getAllInstances(serviceName, groupName,
                false), instance.getPort()));

        assertDoesNotThrow(() -> namingService.deregisterInstance(serviceName, groupName,
                missingInstance));
        assertTrue(containsInstance(namingService.getAllInstances(serviceName, groupName, false),
                instance.getPort()));

        namingService.deregisterInstance(serviceName, groupName, instance);
        waitUntil("deregistered idempotent instance should be absent",
                () -> !containsInstance(namingService.getAllInstances(serviceName, groupName,
                        false), instance.getPort()));
        assertDoesNotThrow(() -> namingService.deregisterInstance(serviceName, groupName,
                instance));
    }

    @Test
    public void testBatchRegisterAndPartialBatchDeregisterInstance() throws Exception {
        NamingService namingService = createNamingService();
        String serviceName = randomServiceName("batch");
        String groupName = randomGroup("naming");
        Instance first = buildInstance(randomPort(), Constants.DEFAULT_CLUSTER_NAME);
        Instance second = buildInstance(randomPort(), Constants.DEFAULT_CLUSTER_NAME);
        List<Instance> instances = Arrays.asList(first, second);
        addCleanup(() -> cleanupBatchInstances(namingService, serviceName, groupName, instances));

        namingService.batchRegisterInstance(serviceName, groupName, instances);
        waitUntil("batch registered instances should be queryable",
                () -> containsInstance(namingService.getAllInstances(serviceName, groupName, false),
                        first.getPort()) && containsInstance(namingService.getAllInstances(serviceName,
                        groupName, false), second.getPort()));

        assertTrue(containsInstance(namingService.getAllInstances(serviceName, groupName, false),
                first.getPort()));
        assertTrue(containsInstance(namingService.getAllInstances(serviceName, groupName, false),
                second.getPort()));

        namingService.batchDeregisterInstance(serviceName, groupName, Collections.singletonList(first));
        waitUntil("partial batch deregister should remove only the selected instance", () -> {
            List<Instance> current = namingService.getAllInstances(serviceName, groupName, false);
            return !containsInstance(current, first.getPort())
                    && containsInstance(current, second.getPort());
        });
    }

    @Test
    public void testEmptyBatchRegisterLeavesNoInstances() throws Exception {
        NamingService namingService = createNamingService();
        String serviceName = randomServiceName("empty-batch");
        String groupName = randomGroup("naming");

        namingService.batchRegisterInstance(serviceName, groupName, Collections.emptyList());

        assertTrue(namingService.getAllInstances(serviceName, groupName, false).isEmpty());
    }

    @Test
    public void testHealthySelectionFiltersDisabledAndZeroWeightInstances() throws Exception {
        NamingService namingService = createNamingService();
        String healthyServiceName = randomServiceName("selection-healthy");
        String disabledServiceName = randomServiceName("selection-disabled");
        String zeroWeightServiceName = randomServiceName("selection-zero-weight");
        String groupName = randomGroup("naming");
        Instance healthy = buildInstance(randomPort(), Constants.DEFAULT_CLUSTER_NAME);
        Instance disabled = buildInstance(randomPort(), Constants.DEFAULT_CLUSTER_NAME);
        disabled.setEnabled(false);
        Instance zeroWeight = buildInstance(randomPort(), Constants.DEFAULT_CLUSTER_NAME);
        zeroWeight.setWeight(0D);
        addCleanup(() -> namingService.deregisterInstance(healthyServiceName, groupName, healthy));
        addCleanup(() -> namingService.deregisterInstance(disabledServiceName, groupName, disabled));
        addCleanup(() -> namingService.deregisterInstance(zeroWeightServiceName, groupName,
                zeroWeight));

        namingService.registerInstance(healthyServiceName, groupName, healthy);
        waitUntil("healthy instance should be queryable",
                () -> containsInstance(namingService.getAllInstances(healthyServiceName, groupName,
                        false), healthy.getPort()));
        namingService.registerInstance(disabledServiceName, groupName, disabled);
        namingService.registerInstance(zeroWeightServiceName, groupName, zeroWeight);

        List<Instance> selected = namingService.selectInstances(healthyServiceName, groupName, true,
                false);
        assertTrue(containsInstance(selected, healthy.getPort()), selected.toString());
        assertTrue(containsInstance(namingService.selectInstances(healthyServiceName, groupName,
                true), healthy.getPort()));
        assertTrue(namingService.selectInstances(disabledServiceName, groupName, true, false)
                .isEmpty());
        assertTrue(namingService.selectInstances(zeroWeightServiceName, groupName, true, false)
                .isEmpty());
    }

    @Test
    public void testUnhealthySelectionReturnsExplicitUnhealthyInstances() throws Exception {
        NamingService namingService = createNamingService();
        String serviceName = randomServiceName("selection-unhealthy");
        String groupName = randomGroup("naming");
        Instance unhealthy = buildInstance(randomPort(), Constants.DEFAULT_CLUSTER_NAME);
        unhealthy.setHealthy(false);
        addCleanup(() -> namingService.deregisterInstance(serviceName, groupName, unhealthy));

        namingService.registerInstance(serviceName, groupName, unhealthy);
        waitUntil("unhealthy instance should be queryable",
                () -> containsInstance(namingService.selectInstances(serviceName, groupName,
                        false, false), unhealthy.getPort()));

        Instance queried = findInstance(namingService.getAllInstances(serviceName, groupName,
                false), unhealthy.getPort());
        assertFalse(queried.isHealthy(), queried.toString());
        assertTrue(namingService.selectInstances(serviceName, groupName, true, false).isEmpty());
        assertTrue(containsInstance(namingService.selectInstances(serviceName, groupName, false,
                false), unhealthy.getPort()));
    }

    @Test
    public void testServiceListPaginationBoundaries() throws Exception {
        NamingService namingService = createNamingService();
        String serviceName = randomServiceName("service-list-page");
        String groupName = randomGroup("naming");
        Instance instance = buildInstance(randomPort(), Constants.DEFAULT_CLUSTER_NAME);
        addCleanup(() -> namingService.deregisterInstance(serviceName, groupName, instance));

        namingService.registerInstance(serviceName, groupName, instance);
        waitUntil("registered service should appear in first service-list page",
                () -> namingService.getServicesOfServer(1, 1, groupName).getData()
                        .contains(serviceName));

        ListView<String> firstPage = namingService.getServicesOfServer(1, 1, groupName);
        assertEquals(1, firstPage.getCount(), firstPage.toString());
        assertEquals(Collections.singletonList(serviceName), firstPage.getData(),
                firstPage.toString());

        ListView<String> secondPage = namingService.getServicesOfServer(2, 1, groupName);
        assertEquals(1, secondPage.getCount(), secondPage.toString());
        assertTrue(secondPage.getData().isEmpty(), secondPage.toString());

        ListView<String> zeroPage = namingService.getServicesOfServer(0, 1, groupName);
        assertEquals(firstPage.getData(), zeroPage.getData(), zeroPage.toString());
        assertEquals(firstPage.getCount(), zeroPage.getCount(), zeroPage.toString());
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testServiceListSelectorOverloadsReturnStableShape() throws Exception {
        NamingService namingService = createNamingService();
        String serviceName = randomServiceName("service-list-selector");
        String defaultGroupServiceName = randomServiceName("service-list-selector-default");
        String groupName = randomGroup("naming");
        Instance instance = buildInstance(randomPort(), Constants.DEFAULT_CLUSTER_NAME);
        Instance defaultGroupInstance = buildInstance(randomPort(), Constants.DEFAULT_CLUSTER_NAME);
        addCleanup(() -> namingService.deregisterInstance(serviceName, groupName, instance));
        addCleanup(() -> namingService.deregisterInstance(defaultGroupServiceName,
                defaultGroupInstance));

        namingService.registerInstance(serviceName, groupName, instance);
        namingService.registerInstance(defaultGroupServiceName, defaultGroupInstance);
        waitUntil("registered service should appear in selector overload service list",
                () -> namingService.getServicesOfServer(1, 1000, groupName, new NoneSelector())
                        .getData().contains(serviceName));
        waitUntil("default group service should appear in selector overload service list",
                () -> namingService.getServicesOfServer(1, 1000, new NoneSelector()).getData()
                        .contains(defaultGroupServiceName));

        ListView<String> selectorPage = namingService.getServicesOfServer(1, 1000, groupName,
                new NoneSelector());
        assertTrue(selectorPage.getData().contains(serviceName), selectorPage.toString());
        ListView<String> defaultSelectorPage = namingService.getServicesOfServer(1, 1000,
                new NoneSelector());
        assertTrue(defaultSelectorPage.getData().contains(defaultGroupServiceName),
                defaultSelectorPage.toString());
    }

    @Test
    public void testSubscribeReceivesInstanceChangeEvent() throws Exception {
        NamingService namingService = createNamingService();
        String serviceName = randomServiceName("subscribe");
        String groupName = randomGroup("naming");
        int port = randomPort();
        Instance instance = buildInstance(port, Constants.DEFAULT_CLUSTER_NAME);
        CountDownLatch latch = new CountDownLatch(1);
        EventListener listener = event -> {
            if (eventContainsInstance(event, port)) {
                latch.countDown();
            }
        };
        addCleanup(() -> namingService.unsubscribe(serviceName, groupName, listener));
        addCleanup(() -> namingService.deregisterInstance(serviceName, groupName, instance));

        namingService.subscribe(serviceName, groupName, listener);
        namingService.registerInstance(serviceName, groupName, instance);

        assertTrue(latch.await(10, TimeUnit.SECONDS), "listener should receive registered instance event");
        assertTrue(containsSubscribedService(namingService.getSubscribeServices(), serviceName,
                groupName), namingService.getSubscribeServices().toString());
    }

    @Test
    public void testGetAllInstancesSubscribeTrueUsesPushedCache() throws Exception {
        NamingService namingService = createNamingService();
        String serviceName = randomServiceName("subscribe-cache");
        String groupName = randomGroup("naming");
        Instance instance = buildInstance(randomPort(), Constants.DEFAULT_CLUSTER_NAME);
        addCleanup(() -> namingService.deregisterInstance(serviceName, groupName, instance));

        namingService.registerInstance(serviceName, groupName, instance);
        waitUntil("subscribe=true query should return registered instance",
                () -> containsInstance(namingService.getAllInstances(serviceName, groupName,
                        true), instance.getPort()));
        assertTrue(containsInstance(namingService.getAllInstances(serviceName, groupName, true),
                instance.getPort()));

        namingService.deregisterInstance(serviceName, groupName, instance);
        waitUntil("subscribe=true cached service info should be refreshed after server push",
                () -> !containsInstance(namingService.getAllInstances(serviceName, groupName,
                        true), instance.getPort()));
    }

    @Test
    public void testClusterSubscribeFiltersInstanceChangeEvents() throws Exception {
        NamingService namingService = createNamingService();
        String serviceName = randomServiceName("cluster-subscribe");
        String groupName = randomGroup("naming");
        String targetCluster = "sdk-target-cluster";
        String ignoredCluster = "sdk-ignored-cluster";
        Instance target = buildInstance(randomPort(), targetCluster);
        Instance ignored = buildInstance(randomPort(), ignoredCluster);
        List<String> targetClusters = Collections.singletonList(targetCluster);
        CountDownLatch selectedLatch = new CountDownLatch(1);
        AtomicBoolean sawIgnoredInstance = new AtomicBoolean(false);
        EventListener listener = event -> {
            if (eventContainsInstance(event, ignored.getPort())) {
                sawIgnoredInstance.set(true);
            }
            if (eventContainsInstance(event, target.getPort())) {
                selectedLatch.countDown();
            }
        };
        addCleanup(() -> namingService.unsubscribe(serviceName, groupName, targetClusters, listener));
        addCleanup(() -> namingService.deregisterInstance(serviceName, groupName, ignored));
        addCleanup(() -> namingService.deregisterInstance(serviceName, groupName, target));

        namingService.subscribe(serviceName, groupName, targetClusters, listener);
        namingService.registerInstance(serviceName, groupName, ignored);
        namingService.registerInstance(serviceName, groupName, target);

        assertTrue(selectedLatch.await(10, TimeUnit.SECONDS),
                "cluster listener should receive matching cluster instance");
        assertFalse(sawIgnoredInstance.get(),
                "cluster listener should not expose other cluster instances");
    }

    @Test
    public void testSelectorSubscribeFiltersInstanceChangeEvents() throws Exception {
        NamingService namingService = createNamingService();
        String serviceName = randomServiceName("selector-subscribe");
        String groupName = randomGroup("naming");
        Instance target = buildInstance(randomPort(), Constants.DEFAULT_CLUSTER_NAME);
        target.addMetadata("selector", "target");
        Instance ignored = buildInstance(randomPort(), Constants.DEFAULT_CLUSTER_NAME);
        ignored.addMetadata("selector", "ignored");
        NamingSelector selector = metadataSelector("selector", "target");
        CountDownLatch selectedLatch = new CountDownLatch(1);
        AtomicBoolean sawIgnoredInstance = new AtomicBoolean(false);
        EventListener listener = event -> {
            if (eventContainsInstance(event, ignored.getPort())) {
                sawIgnoredInstance.set(true);
            }
            if (eventContainsInstance(event, target.getPort())) {
                selectedLatch.countDown();
            }
        };
        addCleanup(() -> namingService.unsubscribe(serviceName, groupName, selector, listener));
        addCleanup(() -> namingService.deregisterInstance(serviceName, groupName, ignored));
        addCleanup(() -> namingService.deregisterInstance(serviceName, groupName, target));

        namingService.subscribe(serviceName, groupName, selector, listener);
        namingService.registerInstance(serviceName, groupName, ignored);
        namingService.registerInstance(serviceName, groupName, target);

        assertTrue(selectedLatch.await(10, TimeUnit.SECONDS),
                "selector listener should receive matching metadata instance");
        assertFalse(sawIgnoredInstance.get(),
                "selector listener should not expose non-matching instances");
    }

    @Test
    public void testFuzzyWatchReturnsServiceKeysAndStopsAfterCancel() throws Exception {
        NamingService namingService = createNamingService();
        String groupName = randomGroup("fuzzy");
        String servicePrefix = randomServiceName("fuzzy");
        String existingServiceName = servicePrefix + "-existing";
        String addedServiceName = servicePrefix + "-added";
        String afterCancelServiceName = servicePrefix + "-after-cancel";
        String ignoredServiceName = randomServiceName("ignored-fuzzy");
        Instance existingInstance = buildInstance(randomPort(), Constants.DEFAULT_CLUSTER_NAME);
        Instance ignoredInstance = buildInstance(randomPort(), Constants.DEFAULT_CLUSTER_NAME);
        Instance addedInstance = buildInstance(randomPort(), Constants.DEFAULT_CLUSTER_NAME);
        Instance afterCancelInstance = buildInstance(randomPort(), Constants.DEFAULT_CLUSTER_NAME);
        CountDownLatch addLatch = new CountDownLatch(1);
        CountDownLatch afterCancelLatch = new CountDownLatch(1);
        AtomicReference<FuzzyWatchChangeEvent> addEvent = new AtomicReference<>();
        AbstractFuzzyWatchEventWatcher watcher = new AbstractFuzzyWatchEventWatcher() {
            @Override
            public void onEvent(FuzzyWatchChangeEvent event) {
                if (addedServiceName.equals(event.getServiceName())
                        && Constants.ServiceChangedType.ADD_SERVICE.equals(event.getChangeType())) {
                    addEvent.set(event);
                    addLatch.countDown();
                }
                if (afterCancelServiceName.equals(event.getServiceName())) {
                    afterCancelLatch.countDown();
                }
            }
        };
        addCleanup(() -> namingService.cancelFuzzyWatch(servicePrefix + "*", groupName, watcher));
        addCleanup(() -> namingService.deregisterInstance(afterCancelServiceName, groupName,
                afterCancelInstance));
        addCleanup(() -> namingService.deregisterInstance(addedServiceName, groupName,
                addedInstance));
        addCleanup(() -> namingService.deregisterInstance(ignoredServiceName, groupName,
                ignoredInstance));
        addCleanup(() -> namingService.deregisterInstance(existingServiceName, groupName,
                existingInstance));

        namingService.registerInstance(existingServiceName, groupName, existingInstance);
        namingService.registerInstance(ignoredServiceName, groupName, ignoredInstance);
        waitUntil("existing fuzzy service should be queryable",
                () -> containsInstance(namingService.getAllInstances(existingServiceName,
                        groupName, false), existingInstance.getPort()));
        Future<ListView<String>> serviceKeysFuture = namingService.fuzzyWatchWithServiceKeys(
                servicePrefix + "*", groupName, watcher);

        ListView<String> serviceKeys = serviceKeysFuture.get(10, TimeUnit.SECONDS);
        assertTrue(serviceKeys.getData().stream().anyMatch(each -> each.contains(
                existingServiceName)), serviceKeys.toString());
        assertFalse(serviceKeys.getData().stream().anyMatch(each -> each.contains(
                ignoredServiceName)), serviceKeys.toString());

        namingService.registerInstance(addedServiceName, groupName, addedInstance);
        assertTrue(addLatch.await(10, TimeUnit.SECONDS),
                "fuzzy watcher should receive add event for matched service");
        assertEquals(groupName, addEvent.get().getGroupName(), addEvent.get().toString());

        namingService.cancelFuzzyWatch(servicePrefix + "*", groupName, watcher);
        namingService.registerInstance(afterCancelServiceName, groupName, afterCancelInstance);
        assertFalse(afterCancelLatch.await(2, TimeUnit.SECONDS),
                "canceled fuzzy watcher should not receive later service events");
    }

    @Test
    public void testUnsubscribeStopsLaterInstanceChangeEvents() throws Exception {
        NamingService namingService = createNamingService();
        String serviceName = randomServiceName("unsubscribe");
        String groupName = randomGroup("naming");
        int port = randomPort();
        Instance instance = buildInstance(port, Constants.DEFAULT_CLUSTER_NAME);
        CountDownLatch latch = new CountDownLatch(1);
        EventListener listener = event -> {
            if (eventContainsInstance(event, port)) {
                latch.countDown();
            }
        };
        addCleanup(() -> namingService.unsubscribe(serviceName, groupName, listener));
        addCleanup(() -> namingService.deregisterInstance(serviceName, groupName, instance));

        namingService.subscribe(serviceName, groupName, listener);
        namingService.unsubscribe(serviceName, groupName, listener);
        namingService.registerInstance(serviceName, groupName, instance);

        assertFalse(latch.await(2, TimeUnit.SECONDS),
                "unsubscribed listener should not receive later instance event");
    }

    @Test
    public void testInvalidNamingParametersThrowNacosException() throws Exception {
        NamingService namingService = createNamingService();
        String serviceName = randomServiceName("invalid");
        Instance invalidPort = buildInstance(randomPort(), Constants.DEFAULT_CLUSTER_NAME);
        invalidPort.setPort(65536);
        Instance invalidCluster = buildInstance(randomPort(), "bad_cluster");
        Instance mismatchedGroup = buildInstance(randomPort(), Constants.DEFAULT_CLUSTER_NAME);
        mismatchedGroup.setServiceName("OTHER_GROUP@@" + serviceName);
        Instance invalidHeartbeat = buildInstance(randomPort(), Constants.DEFAULT_CLUSTER_NAME);
        invalidHeartbeat.addMetadata(PreservedMetadataKeys.HEART_BEAT_INTERVAL, "2000");
        invalidHeartbeat.addMetadata(PreservedMetadataKeys.HEART_BEAT_TIMEOUT, "1000");
        Instance persistentBatchInstance = buildInstance(randomPort(), Constants.DEFAULT_CLUSTER_NAME);
        persistentBatchInstance.setEphemeral(false);

        assertThrows(IllegalArgumentException.class,
                () -> namingService.registerInstance("", Constants.DEFAULT_GROUP, buildInstance(randomPort(),
                        Constants.DEFAULT_CLUSTER_NAME)));

        NacosException nullInstance = assertThrows(NacosException.class,
                () -> namingService.registerInstance(serviceName, Constants.DEFAULT_GROUP, null));
        assertEquals(NacosException.INVALID_PARAM, nullInstance.getErrCode(), nullInstance.toString());

        NacosException badPort = assertThrows(NacosException.class,
                () -> namingService.registerInstance(serviceName, Constants.DEFAULT_GROUP,
                        invalidPort));
        assertControlledParameterError(badPort);

        NacosException badCluster = assertThrows(NacosException.class,
                () -> namingService.registerInstance(serviceName, Constants.DEFAULT_GROUP, invalidCluster));
        assertEquals(NacosException.INVALID_PARAM, badCluster.getErrCode(), badCluster.toString());

        NacosException wrongGroup = assertThrows(NacosException.class,
                () -> namingService.registerInstance(serviceName, Constants.DEFAULT_GROUP, mismatchedGroup));
        assertEquals(NacosException.CLIENT_INVALID_PARAM, wrongGroup.getErrCode(), wrongGroup.toString());

        NacosException badHeartbeat = assertThrows(NacosException.class,
                () -> namingService.registerInstance(serviceName, Constants.DEFAULT_GROUP, invalidHeartbeat));
        assertEquals(NacosException.INVALID_PARAM, badHeartbeat.getErrCode(), badHeartbeat.toString());

        NacosException badBatchInstance = assertThrows(NacosException.class,
                () -> namingService.batchRegisterInstance(serviceName, Constants.DEFAULT_GROUP,
                        Collections.singletonList(persistentBatchInstance)));
        assertEquals(NacosException.INVALID_PARAM, badBatchInstance.getErrCode(), badBatchInstance.toString());

        NacosException emptyBatchDeregister = assertThrows(NacosException.class,
                () -> namingService.batchDeregisterInstance(serviceName, Constants.DEFAULT_GROUP,
                        Collections.emptyList()));
        assertEquals(NacosException.INVALID_PARAM, emptyBatchDeregister.getErrCode(),
                emptyBatchDeregister.toString());

        assertThrows(NullPointerException.class,
                () -> namingService.batchRegisterInstance(serviceName, Constants.DEFAULT_GROUP,
                        null));
        assertThrows(NullPointerException.class,
                () -> namingService.batchDeregisterInstance(serviceName, Constants.DEFAULT_GROUP,
                        null));

        assertTrue(namingService.getAllInstances(randomServiceName("missing"), Constants.DEFAULT_GROUP, false)
                .isEmpty());
        assertTrue(namingService.selectInstances(randomServiceName("missing"), Constants.DEFAULT_GROUP,
                true, false).isEmpty());
        assertThrows(IllegalStateException.class,
                () -> namingService.selectOneHealthyInstance(randomServiceName("missing"),
                        Constants.DEFAULT_GROUP, false));
        namingService.subscribe(serviceName, Constants.DEFAULT_GROUP, (EventListener) null);
        namingService.unsubscribe(serviceName, Constants.DEFAULT_GROUP, (EventListener) null);
    }

    private Instance buildInstance(int port, String clusterName) {
        Instance instance = new Instance();
        instance.setIp(TEST_IP);
        instance.setPort(port);
        instance.setClusterName(clusterName);
        instance.setWeight(2.0D);
        instance.setHealthy(true);
        instance.setEnabled(true);
        instance.addMetadata("source", "java-sdk-test");
        return instance;
    }

    private NamingSelector metadataSelector(String key, String expectedValue) {
        return context -> () -> {
            if (null == context.getInstances()) {
                return Collections.emptyList();
            }
            return context.getInstances().stream()
                    .filter(instance -> expectedValue.equals(instance.getMetadata().get(key)))
                    .collect(Collectors.toList());
        };
    }

    private boolean eventContainsInstance(Event event, int port) {
        if (!(event instanceof NamingEvent)) {
            return false;
        }
        return containsInstance(((NamingEvent) event).getInstances(), port);
    }

    private boolean containsInstance(List<Instance> instances, int port) {
        return instances.stream().anyMatch(instance -> TEST_IP.equals(instance.getIp()) && port == instance.getPort());
    }

    private long countInstances(List<Instance> instances, int port) {
        return instances.stream().filter(instance -> TEST_IP.equals(instance.getIp()) && port == instance.getPort())
                .count();
    }

    private Instance findInstance(List<Instance> instances, int port) {
        return instances.stream().filter(instance -> TEST_IP.equals(instance.getIp()) && port == instance.getPort())
                .findFirst().orElseThrow(() -> new AssertionError("instance not found, port=" + port + ", data="
                        + instances));
    }

    private void assertControlledParameterError(NacosException exception) {
        assertTrue(NacosException.SERVER_ERROR != exception.getErrCode(), exception.toString());
        assertTrue(NacosException.INVALID_PARAM == exception.getErrCode()
                || NacosException.CLIENT_INVALID_PARAM == exception.getErrCode(),
                exception.toString());
    }

    private boolean containsSubscribedService(List<ServiceInfo> services, String serviceName,
            String groupName) {
        return services.stream().anyMatch(service -> serviceName.equals(service.getName())
                && groupName.equals(service.getGroupName()));
    }

    private void cleanupBatchInstances(NamingService namingService, String serviceName,
            String groupName, List<Instance> instances) throws NacosException {
        try {
            namingService.batchDeregisterInstance(serviceName, groupName, instances);
        } catch (NacosException ignored) {
            for (Instance instance : instances) {
                try {
                    namingService.deregisterInstance(serviceName, groupName, instance);
                } catch (NacosException ignoredAgain) {
                    // cleanup should tolerate resources that were not registered or were already removed
                }
            }
        }
    }
}
