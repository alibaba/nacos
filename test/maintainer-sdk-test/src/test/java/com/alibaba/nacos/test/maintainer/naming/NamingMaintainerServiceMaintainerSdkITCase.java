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

package com.alibaba.nacos.test.maintainer.naming;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.Service;
import com.alibaba.nacos.api.naming.pojo.healthcheck.AbstractHealthChecker;
import com.alibaba.nacos.api.naming.pojo.maintainer.ClientPublisherInfo;
import com.alibaba.nacos.api.naming.pojo.maintainer.ClientServiceInfo;
import com.alibaba.nacos.api.naming.pojo.maintainer.ClientSubscriberInfo;
import com.alibaba.nacos.api.naming.pojo.maintainer.ClientSummaryInfo;
import com.alibaba.nacos.api.naming.pojo.maintainer.ClusterInfo;
import com.alibaba.nacos.api.naming.pojo.maintainer.InstanceMetadataBatchResult;
import com.alibaba.nacos.api.naming.pojo.maintainer.MetricsInfo;
import com.alibaba.nacos.api.naming.pojo.maintainer.ServiceDetailInfo;
import com.alibaba.nacos.api.naming.pojo.maintainer.ServiceView;
import com.alibaba.nacos.api.naming.pojo.maintainer.SubscriberInfo;
import com.alibaba.nacos.api.selector.NoneSelector;
import com.alibaba.nacos.maintainer.client.naming.NamingMaintainerService;
import com.alibaba.nacos.test.maintainer.MaintainerSdkBaseITCase;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for {@link NamingMaintainerService}.
 *
 * <p>Scenario coverage:
 * <ul>
 *     <li>Expected capability: maintainer SDK can create, query, list, update,
 *     and remove naming services through the admin API.</li>
 *     <li>Expected capability: maintainer SDK can register, query, update,
 *     partially update, batch update metadata, and deregister persistent
 *     instances.</li>
 *     <li>Expected capability: maintainer SDK can query selector types,
 *     health checkers, service detail pages, cluster metadata, manual
 *     persistent instance health, and naming client diagnostics.</li>
 *     <li>Boundary/validation: missing service and invalid service/instance
 *     required parameters fail with controlled SDK exceptions.</li>
 * </ul>
 *
 * @author xiweng.yy
 */
class NamingMaintainerServiceMaintainerSdkITCase extends MaintainerSdkBaseITCase {
    
    @Test
    void shouldManageServiceLifecycle() throws Exception {
        NamingMaintainerService maintainerService = createNamingMaintainerService();
        String namespaceId = Constants.DEFAULT_NAMESPACE_ID;
        String groupName = randomGroup("naming");
        String serviceName = randomMaintainerName("service");
        
        assertThrows(NacosException.class,
                () -> maintainerService.getServiceDetail(namespaceId, groupName, serviceName));
        assertNotNull(maintainerService.createService(namespaceId, groupName, serviceName, false,
                0.3F));
        addCleanup(() -> maintainerService.removeService(namespaceId, groupName, serviceName));
        
        ServiceDetailInfo detail =
                maintainerService.getServiceDetail(namespaceId, groupName, serviceName);
        assertServiceDetail(detail, namespaceId, groupName, serviceName);
        assertEquals(0.3F, detail.getProtectThreshold());
        assertFalse(detail.isEphemeral());
        
        Map<String, String> metadata = new HashMap<>();
        metadata.put("maintainer", "true");
        metadata.put("scenario", "service-lifecycle");
        assertNotNull(maintainerService.updateService(namespaceId, groupName, serviceName, false,
                metadata, 0.5F, new NoneSelector()));
        
        ServiceDetailInfo updated =
                maintainerService.getServiceDetail(namespaceId, groupName, serviceName);
        assertServiceDetail(updated, namespaceId, groupName, serviceName);
        assertEquals(0.5F, updated.getProtectThreshold());
        assertEquals("true", updated.getMetadata().get("maintainer"));
        assertEquals("service-lifecycle", updated.getMetadata().get("scenario"));
        
        Page<ServiceView> services =
                maintainerService.listServices(namespaceId, groupName, serviceName, false, 1, 10);
        assertContainsService(services, groupName, serviceName);
        
        Page<ServiceDetailInfo> detailedServices =
                maintainerService.listServicesWithDetail(namespaceId, groupName, serviceName, 1, 10);
        assertContainsServiceDetail(detailedServices, groupName, serviceName);
        
        assertNotNull(maintainerService.removeService(namespaceId, groupName, serviceName));
        assertThrows(NacosException.class,
                () -> maintainerService.getServiceDetail(namespaceId, groupName, serviceName));
    }
    
    @Test
    void shouldManagePersistentInstanceLifecycle() throws Exception {
        NamingMaintainerService maintainerService = createNamingMaintainerService();
        String namespaceId = Constants.DEFAULT_NAMESPACE_ID;
        String groupName = randomGroup("naming");
        String serviceName = randomMaintainerName("instance");
        String ip = "127.0.0.1";
        int port = randomPort();
        Service service = service(namespaceId, groupName, serviceName, false);
        Instance instance = instance(ip, port, false);
        
        assertNotNull(maintainerService.createService(service));
        addCleanup(() -> maintainerService.removeService(service));
        assertNotNull(maintainerService.registerInstance(service, instance));
        addCleanup(() -> maintainerService.deregisterInstance(service, instance));
        
        Instance detail = maintainerService.getInstanceDetail(service, instance);
        assertInstance(detail, ip, port, true, true);
        List<Instance> instances = maintainerService.listInstances(service, "", false);
        assertTrue(instances.stream().anyMatch(each -> ip.equals(each.getIp())
                && port == each.getPort()));
        
        Instance fullUpdate = instance(ip, port, false);
        fullUpdate.setMetadata(Collections.singletonMap("mode", "full"));
        assertNotNull(maintainerService.updateInstance(service, fullUpdate));
        
        Instance fullUpdated = maintainerService.getInstanceDetail(service, instance);
        assertInstance(fullUpdated, ip, port, true, true);
        
        Instance partialUpdate = instance(ip, port, false);
        partialUpdate.setWeight(3.0D);
        partialUpdate.setEnabled(true);
        partialUpdate.setMetadata(Collections.singletonMap("partial", "true"));
        assertNotNull(maintainerService.partialUpdateInstance(service, partialUpdate));
        
        Instance partialUpdated = maintainerService.getInstanceDetail(service, instance);
        assertInstance(partialUpdated, ip, port, true, true);
        
        Map<String, String> batchMetadata = Collections.singletonMap("batch", "updated");
        InstanceMetadataBatchResult batchResult =
                maintainerService.batchUpdateInstanceMetadata(service,
                        Collections.singletonList(instance), batchMetadata);
        assertNotNull(batchResult);
        waitUntil("batch metadata should be visible", () -> {
            Instance batchUpdated = maintainerService.getInstanceDetail(service, instance);
            return "updated".equals(batchUpdated.getMetadata().get("batch"));
        });
        
        InstanceMetadataBatchResult deleteResult =
                maintainerService.batchDeleteInstanceMetadata(service,
                        Collections.singletonList(instance), batchMetadata);
        assertNotNull(deleteResult);
        waitUntil("batch metadata should be removed", () -> {
            Instance metadataDeleted = maintainerService.getInstanceDetail(service, instance);
            return !metadataDeleted.getMetadata().containsKey("batch");
        });
        
        assertNotNull(maintainerService.deregisterInstance(service, instance));
        waitUntil("deregistered instance should be absent", () -> {
            try {
                maintainerService.getInstanceDetail(service, instance);
                return false;
            } catch (NacosException ignored) {
                return true;
            }
        });
    }
    
    @Test
    void shouldManageClusterAndInstanceHealth() throws Exception {
        NamingMaintainerService maintainerService = createNamingMaintainerService();
        String namespaceId = Constants.DEFAULT_NAMESPACE_ID;
        String groupName = randomGroup("naming");
        String serviceName = randomMaintainerName("cluster");
        String ip = "127.0.0.1";
        int port = randomPort();
        Service service = service(namespaceId, groupName, serviceName, false);
        Instance instance = instance(ip, port, false);
        
        assertNotNull(maintainerService.createService(service));
        addCleanup(() -> maintainerService.removeService(service));
        assertNotNull(maintainerService.registerInstance(service, instance));
        addCleanup(() -> maintainerService.deregisterInstance(service, instance));
        
        ClusterInfo cluster = new ClusterInfo();
        cluster.setClusterName(Constants.DEFAULT_CLUSTER_NAME);
        cluster.setHealthChecker(new AbstractHealthChecker.None());
        cluster.setHealthyCheckPort(port);
        cluster.setUseInstancePortForCheck(true);
        cluster.setMetadata(Collections.singletonMap("scenario", "cluster-health"));
        assertEquals("ok", maintainerService.updateCluster(service, cluster));
        
        waitUntil("cluster metadata should be visible", () -> {
            ServiceDetailInfo detail = maintainerService.getServiceDetail(service);
            ClusterInfo updatedCluster =
                    detail.getClusterMap().get(Constants.DEFAULT_CLUSTER_NAME);
            return null != updatedCluster
                    && null != updatedCluster.getHealthChecker()
                    && AbstractHealthChecker.None.TYPE.equals(
                            updatedCluster.getHealthChecker().getType())
                    && null != updatedCluster.getMetadata()
                    && "cluster-health".equals(updatedCluster.getMetadata().get("scenario"));
        });
        
        Instance unhealthy = instance(ip, port, false);
        unhealthy.setHealthy(false);
        assertEquals("ok", maintainerService.updateInstanceHealthStatus(service, unhealthy));
        waitUntil("manual unhealthy status should be visible",
                () -> !maintainerService.getInstanceDetail(service, instance).isHealthy());
        
        Instance healthy = instance(ip, port, false);
        healthy.setHealthy(true);
        assertEquals("ok", maintainerService.updateInstanceHealthStatus(service, healthy));
        waitUntil("manual healthy status should be visible",
                () -> maintainerService.getInstanceDetail(service, instance).isHealthy());
    }
    
    @Test
    void shouldRejectInvalidNamingParameters() throws Exception {
        NamingMaintainerService maintainerService = createNamingMaintainerService();
        String groupName = randomGroup("naming");
        String serviceName = randomMaintainerName("invalid-instance");
        
        assertThrows(NacosException.class, () -> maintainerService.createService(""));
        
        Service service = service(Constants.DEFAULT_NAMESPACE_ID, groupName, serviceName, false);
        Instance invalidIp = instance("", randomPort(), false);
        assertThrows(NacosException.class, () -> maintainerService.registerInstance(service,
                invalidIp));
        
        Instance invalidPort = instance("127.0.0.1", 70000, false);
        assertThrows(NacosException.class, () -> maintainerService.registerInstance(service,
                invalidPort));
    }
    
    @Test
    void shouldQueryNamingStaticDiagnostics() throws Exception {
        NamingMaintainerService maintainerService = createNamingMaintainerService();
        
        List<String> selectorTypes = maintainerService.listSelectorTypes();
        assertNotNull(selectorTypes);
        assertTrue(selectorTypes.contains("none"));
        
        Map<String, AbstractHealthChecker> healthCheckers =
                maintainerService.getHealthCheckers();
        assertNotNull(healthCheckers);
        assertTrue(healthCheckers.containsKey(AbstractHealthChecker.None.TYPE));
    }
    
    @Test
    void shouldQueryNamingClientDiagnostics() throws Exception {
        NamingMaintainerService maintainerService = createNamingMaintainerService();
        String namespaceId = Constants.DEFAULT_NAMESPACE_ID;
        String groupName = randomGroup("naming");
        String serviceName = randomMaintainerName("client-diagnostics");
        String ip = "127.0.0.1";
        int port = randomPort();
        Service service = service(namespaceId, groupName, serviceName, false);
        Instance instance = instance(ip, port, false);
        
        assertNotNull(maintainerService.createService(service));
        addCleanup(() -> maintainerService.removeService(service));
        assertNotNull(maintainerService.registerInstance(service, instance));
        addCleanup(() -> maintainerService.deregisterInstance(service, instance));
        waitUntil("published client should be visible", () -> maintainerService
                .getPublishedClientList(namespaceId, groupName, serviceName, ip, port).stream()
                .anyMatch(publisher -> ip.equals(publisher.getIp())
                        && port == publisher.getPort()));
        
        List<ClientPublisherInfo> publishers =
                maintainerService.getPublishedClientList(namespaceId, groupName, serviceName, ip,
                        port);
        assertTrue(publishers.stream().anyMatch(publisher -> ip.equals(publisher.getIp())
                && port == publisher.getPort()));
        
        List<ClientSubscriberInfo> subscribers =
                maintainerService.getSubscribeClientList(namespaceId, groupName, serviceName, ip,
                        port);
        assertNotNull(subscribers);
        
        List<String> clientIds = maintainerService.getClientList();
        assertNotNull(clientIds);
        String clientId = publishers.stream().map(ClientPublisherInfo::getClientId)
                .filter(value -> null != value).findFirst().orElseThrow();
        assertTrue(clientIds.contains(clientId));
        
        ClientSummaryInfo clientDetail = maintainerService.getClientDetail(clientId);
        assertEquals(clientId, clientDetail.getClientId());
        
        List<ClientServiceInfo> publishedServices =
                maintainerService.getPublishedServiceList(clientId);
        assertTrue(publishedServices.stream()
                .anyMatch(each -> namespaceId.equals(each.getNamespaceId())
                        && groupName.equals(each.getGroupName())
                        && serviceName.equals(each.getServiceName())));
        
        List<ClientServiceInfo> subscribeServices =
                maintainerService.getSubscribeServiceList(clientId);
        assertNotNull(subscribeServices);
    }
    
    @Test
    void shouldQueryNamingDiagnostics() throws Exception {
        NamingMaintainerService maintainerService = createNamingMaintainerService();
        String namespaceId = Constants.DEFAULT_NAMESPACE_ID;
        String groupName = randomGroup("naming");
        String serviceName = randomMaintainerName("diagnostics");
        Service service = service(namespaceId, groupName, serviceName, false);

        assertNotNull(maintainerService.createService(service));
        addCleanup(() -> maintainerService.removeService(service));
        Page<SubscriberInfo> subscribers = maintainerService.getSubscribers(service, 1, 10, false);
        assertNotNull(subscribers);
        assertNotNull(subscribers.getPageItems());

        MetricsInfo statusOnlyMetrics = maintainerService.getMetrics(true);
        assertNotNull(statusOnlyMetrics);
        assertNotNull(statusOnlyMetrics.getStatus());

        MetricsInfo fullMetrics = maintainerService.getMetrics(false);
        assertNotNull(fullMetrics);
        assertNotNull(fullMetrics.getStatus());
        assertTrue(fullMetrics.getServiceCount() >= 0);
        assertTrue(fullMetrics.getClientCount() >= 0);

        assertEquals("ok", maintainerService.setLogLevel("naming-main", "INFO"));
    }

    private Service service(String namespaceId, String groupName, String serviceName,
            boolean ephemeral) {
        Service service = new Service();
        service.setNamespaceId(namespaceId);
        service.setGroupName(groupName);
        service.setName(serviceName);
        service.setEphemeral(ephemeral);
        return service;
    }
    
    private Instance instance(String ip, int port, boolean ephemeral) {
        Instance instance = new Instance();
        instance.setIp(ip);
        instance.setPort(port);
        instance.setClusterName(Constants.DEFAULT_CLUSTER_NAME);
        instance.setEphemeral(ephemeral);
        return instance;
    }
    
    private void assertServiceDetail(ServiceDetailInfo detail, String namespaceId, String groupName,
            String serviceName) {
        assertNotNull(detail);
        assertEquals(namespaceId, detail.getNamespaceId());
        assertEquals(groupName, detail.getGroupName());
        assertEquals(serviceName, detail.getServiceName());
    }
    
    private void assertContainsService(Page<ServiceView> page, String groupName,
            String serviceName) {
        assertNotNull(page);
        assertTrue(page.getPageItems().stream()
                .anyMatch(service -> serviceName.equals(service.getName())
                        && groupName.equals(service.getGroupName())));
    }
    
    private void assertContainsServiceDetail(Page<ServiceDetailInfo> page, String groupName,
            String serviceName) {
        assertNotNull(page);
        assertTrue(page.getPageItems().stream()
                .anyMatch(service -> serviceName.equals(service.getServiceName())
                        && groupName.equals(service.getGroupName())));
    }
    
    private void assertInstance(Instance instance, String ip, int port, boolean enabled,
            boolean healthy) {
        assertNotNull(instance);
        assertEquals(ip, instance.getIp());
        assertEquals(port, instance.getPort());
        assertEquals(enabled, instance.isEnabled());
        assertEquals(healthy, instance.isHealthy());
    }
    
    private int randomPort() {
        return 20000 + Math.abs(randomMaintainerName("port").hashCode() % 30000);
    }
}
