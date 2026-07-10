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

package com.alibaba.nacos.ai.controller;

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.service.ard.ArdArtifact;
import com.alibaba.nacos.ai.service.ard.ArdArtifactService;
import com.alibaba.nacos.ai.service.ard.ArdSearchService;
import com.alibaba.nacos.api.ai.model.ard.ArdCatalog;
import com.alibaba.nacos.api.ai.model.ard.ArdExploreRequest;
import com.alibaba.nacos.api.ai.model.ard.ArdExploreResponse;
import com.alibaba.nacos.api.ai.model.ard.ArdListResponse;
import com.alibaba.nacos.api.ai.model.ard.ArdSearchRequest;
import com.alibaba.nacos.api.ai.model.ard.ArdSearchResponse;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import org.springframework.http.ResponseEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link ArdSearchController}.
 *
 * @author nacos
 */
@ExtendWith(MockitoExtension.class)
class ArdSearchControllerTest {
    
    @Mock
    private ArdSearchService ardSearchService;
    
    @Mock
    private ArdArtifactService ardArtifactService;
    
    @Test
    void searchShouldReturnRawArdResponse() throws NacosException {
        ArdSearchController controller = controller();
        ArdSearchRequest request = new ArdSearchRequest();
        ArdSearchResponse response = new ArdSearchResponse();
        when(ardSearchService.search(request)).thenReturn(response);
        
        assertSame(response, controller.search("tenant-a", request));
        assertEquals("tenant-a", request.getNamespaceId());
    }
    
    @Test
    void exploreShouldReturnRawExploreResponse() throws NacosException {
        ArdSearchController controller = controller();
        ArdExploreRequest request = new ArdExploreRequest();
        ArdExploreResponse response = new ArdExploreResponse();
        when(ardSearchService.explore(request)).thenReturn(response);
        
        assertSame(response, controller.explore("tenant-a", request));
        assertEquals("tenant-a", request.getNamespaceId());
    }
    
    @Test
    void searchShouldRejectBodyOnlyCustomNamespace() {
        ArdSearchController controller = controller();
        ArdSearchRequest request = new ArdSearchRequest();
        request.setNamespaceId("tenant-a");
        
        assertThrows(NacosApiException.class, () -> controller.search(null, request));
    }
    
    @Test
    void exploreShouldRejectNamespaceMismatch() {
        ArdSearchController controller = controller();
        ArdExploreRequest request = new ArdExploreRequest();
        request.setNamespaceId("tenant-a");
        
        assertThrows(NacosApiException.class, () -> controller.explore("tenant-b", request));
    }
    
    @Test
    void agentsShouldReturnArdListResponse() throws NacosException {
        ArdSearchController controller = controller();
        ArdListResponse response = new ArdListResponse();
        when(ardSearchService.list("public", "type=application/zip", "name ASC", 10,
            null)).thenReturn(response);
        
        assertSame(response, controller.agents("public", "type=application/zip", "name ASC", 10,
            null));
    }
    
    @Test
    void catalogShouldReturnManifest() throws NacosException {
        ArdSearchController controller = controller();
        ArdCatalog catalog = new ArdCatalog();
        when(ardSearchService.catalog("public")).thenReturn(catalog);
        
        assertSame(catalog, controller.catalog("public"));
    }
    
    @Test
    void artifactShouldReturnTypedBody() throws NacosException {
        ArdSearchController controller = controller();
        ArdArtifact artifact = new ArdArtifact("application/ai-skill+md", "# Demo");
        when(ardArtifactService.get("public", "skill", "demo", "1.0.0", null))
            .thenReturn(artifact);
        
        ResponseEntity<Object> response = controller.artifact("public", "skill", "demo",
            "1.0.0", null);
        
        assertEquals("# Demo", response.getBody());
        assertEquals("application/ai-skill+md", response.getHeaders().getContentType().toString());
    }
    
    @Test
    void ardPathShouldUseProtocolEndpoint() {
        assertEquals("/v3/ai/ard", Constants.ARD_CLIENT_PATH);
    }
    
    @Test
    void ardWellKnownPathShouldUseStandardEndpoint() {
        assertEquals("/.well-known", Constants.ARD_WELL_KNOWN_PATH);
    }
    
    private ArdSearchController controller() {
        return new ArdSearchController(ardSearchService, ardArtifactService);
    }
}
