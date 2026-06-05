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
