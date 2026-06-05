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

package com.alibaba.nacos.test.maintainer.core;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.response.ConnectionInfo;
import com.alibaba.nacos.api.model.response.IdGeneratorInfo;
import com.alibaba.nacos.api.model.response.NacosMember;
import com.alibaba.nacos.api.model.response.Namespace;
import com.alibaba.nacos.api.model.response.ServerLoaderMetrics;
import com.alibaba.nacos.maintainer.client.config.ConfigMaintainerService;
import com.alibaba.nacos.maintainer.client.core.CoreMaintainerService;
import com.alibaba.nacos.test.maintainer.MaintainerSdkBaseITCase;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for {@link CoreMaintainerService}.
 *
 * <p>Scenario coverage:
 * <ul>
 *     <li>Expected capability: maintainer SDK factory can create a client and
 *     query liveness/readiness/server-state through a running standalone Nacos
 *     server, read-only core operational views, and namespace lifecycle
 *     operations.</li>
 *     <li>Boundary/validation: profile wiring resolves the default
 *     {@code nacos.host}/{@code nacos.port} server address; invalid namespace
 *     ID/name inputs fail with controlled SDK exceptions.</li>
 *     <li>Error handling: unavailable-server mappings are documented as pending
 *     scenarios for later batches.</li>
 * </ul>
 *
 * @author xiweng.yy
 */
class CoreMaintainerServiceMaintainerSdkITCase extends MaintainerSdkBaseITCase {
    
    @Test
    void shouldQueryStandaloneServerState() throws Exception {
        ConfigMaintainerService maintainerService = createConfigMaintainerService();
        
        assertTrue(maintainerService.liveness());
        assertTrue(maintainerService.readiness());
        Map<String, String> serverState = maintainerService.getServerState();
        assertNotNull(serverState);
    }
    
    @Test
    void shouldQueryReadOnlyCoreOperationalViews() throws Exception {
        ConfigMaintainerService maintainerService = createConfigMaintainerService();
        
        List<IdGeneratorInfo> idGenerators = maintainerService.getIdGenerators();
        assertNotNull(idGenerators);
        
        Collection<NacosMember> members = maintainerService.listClusterNodes("", "");
        assertNotNull(members);
        
        Map<String, ConnectionInfo> currentClients = maintainerService.getCurrentClients();
        assertNotNull(currentClients);
        
        ServerLoaderMetrics loaderMetrics = maintainerService.getClusterLoaderMetrics();
        assertNotNull(loaderMetrics);
        
        List<Map<String, Object>> plugins = maintainerService.listPlugins(null);
        assertNotNull(plugins);
    }
    
    @Test
    void shouldManageNamespaceLifecycle() throws Exception {
        ConfigMaintainerService maintainerService = createConfigMaintainerService();
        Namespace publicNamespace = maintainerService.getNamespace(Constants.DEFAULT_NAMESPACE_ID);
        assertEquals(Constants.DEFAULT_NAMESPACE_ID, publicNamespace.getNamespace());
        
        String namespaceId = randomMaintainerName("namespace");
        String namespaceName = namespaceId + "-name";
        String namespaceDesc = "namespace created by maintainer sdk integration test";
        String updatedName = namespaceId + "-updated-name";
        String updatedDesc = "namespace updated by maintainer sdk integration test";
        
        assertFalse(maintainerService.checkNamespaceIdExist(namespaceId));
        assertTrue(maintainerService.createNamespace(namespaceId, namespaceName, namespaceDesc));
        addCleanup(() -> maintainerService.deleteNamespace(namespaceId));
        
        assertTrue(maintainerService.checkNamespaceIdExist(namespaceId));
        Namespace createdNamespace = maintainerService.getNamespace(namespaceId);
        assertEquals(namespaceId, createdNamespace.getNamespace());
        assertEquals(namespaceName, createdNamespace.getNamespaceShowName());
        assertEquals(namespaceDesc, createdNamespace.getNamespaceDesc());
        assertTrue(maintainerService.getNamespaceList().stream()
                .anyMatch(namespace -> namespaceId.equals(namespace.getNamespace())));
        
        assertThrows(NacosException.class,
                () -> maintainerService.createNamespace(namespaceId, namespaceName, namespaceDesc));
        
        assertTrue(maintainerService.updateNamespace(namespaceId, updatedName, updatedDesc));
        Namespace updatedNamespace = maintainerService.getNamespace(namespaceId);
        assertEquals(updatedName, updatedNamespace.getNamespaceShowName());
        assertEquals(updatedDesc, updatedNamespace.getNamespaceDesc());
        
        assertTrue(maintainerService.deleteNamespace(namespaceId));
        assertFalse(maintainerService.checkNamespaceIdExist(namespaceId));
    }
    
    @Test
    void shouldRejectInvalidNamespaceParameters() throws Exception {
        ConfigMaintainerService maintainerService = createConfigMaintainerService();
        
        assertThrows(NacosException.class,
                () -> maintainerService.createNamespace("invalid@namespace", "valid-name", ""));
        assertThrows(NacosException.class,
                () -> maintainerService.createNamespace(randomMaintainerName("namespace"),
                        "invalid@name", ""));
    }
}
