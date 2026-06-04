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
import com.alibaba.nacos.api.naming.listener.Event;
import com.alibaba.nacos.api.naming.listener.EventListener;
import com.alibaba.nacos.api.naming.listener.NamingEvent;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ListView;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.test.sdk.JavaSdkBaseITCase;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

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
 *     instances are filtered from healthy selection, list pagination boundaries return stable
 *     result shapes, blank service name is rejected by the grouped-name utility, and null
 *     instance, invalid port/cluster, invalid heartbeat metadata, persistent batch instance,
 *     empty batch deregister, plus mismatched group-prefix inputs throw controlled SDK
 *     exceptions.</li>
 *     <li>Listener/error handling: subscribe receives an instance change event, subscribe state
 *     is visible, null listener subscribe is a no-op, unsubscribe stops later callbacks, and
 *     deregister cleanup plus SDK shutdown are safe.</li>
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
        assertTrue(namingService.selectInstances(disabledServiceName, groupName, true, false)
                .isEmpty());
        assertTrue(namingService.selectInstances(zeroWeightServiceName, groupName, true, false)
                .isEmpty());
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
