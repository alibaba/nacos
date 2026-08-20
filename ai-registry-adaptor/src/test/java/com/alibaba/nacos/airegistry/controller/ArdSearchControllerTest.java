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

package com.alibaba.nacos.airegistry.controller;

import com.alibaba.nacos.airegistry.annotation.ArdApi;
import com.alibaba.nacos.airegistry.constant.ArdProtocolConstants;
import com.alibaba.nacos.airegistry.model.ard.ArdCatalog;
import com.alibaba.nacos.airegistry.model.ard.ArdExploreRequest;
import com.alibaba.nacos.airegistry.model.ard.ArdExploreResponse;
import com.alibaba.nacos.airegistry.model.ard.ArdListResponse;
import com.alibaba.nacos.airegistry.model.ard.ArdSearchRequest;
import com.alibaba.nacos.airegistry.model.ard.ArdSearchResponse;
import com.alibaba.nacos.airegistry.service.ard.ArdArtifact;
import com.alibaba.nacos.airegistry.service.ard.ArdArtifactService;
import com.alibaba.nacos.airegistry.service.ard.ArdSearchService;
import com.alibaba.nacos.api.common.ApiType;
import com.alibaba.nacos.api.annotation.NacosApi;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.SignType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import java.lang.reflect.Method;

import static com.alibaba.nacos.plugin.auth.constant.Constants.Tag.ALLOW_ANONYMOUS;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
        when(ardArtifactService.get("public", "skill", "demo", "1.0.0", null, null, null))
            .thenReturn(artifact);
        
        ResponseEntity<Object> response = controller.artifact("public", "skill", "demo",
            "1.0.0", null, null, null);
        
        assertEquals("# Demo", response.getBody());
        assertEquals("application/ai-skill+md", response.getHeaders().getContentType().toString());
    }
    
    @Test
    void artifactShouldForwardAgentIntegrityParameters() throws NacosException {
        ArdSearchController controller = controller();
        ArdArtifact artifact = new ArdArtifact(ArdProtocolConstants.MEDIA_TYPE_NACOS_AGENT,
            "agent");
        when(ardArtifactService.get("public", "agent", "demo", "1.0.0", null,
            "sha256:digest", "nacos-agent")).thenReturn(artifact);
        
        ResponseEntity<Object> response = controller.artifact("public", "agent", "demo",
            "1.0.0", null, "sha256:digest", "nacos-agent");
        
        assertEquals("agent", response.getBody());
        assertEquals(ArdProtocolConstants.MEDIA_TYPE_NACOS_AGENT,
            response.getHeaders().getContentType().toString());
    }
    
    @Test
    void ardPathShouldUseProtocolEndpoint() {
        assertEquals("/v3/ai/ard", ArdProtocolConstants.CLIENT_PATH);
    }
    
    @Test
    void ardWellKnownPathShouldUseStandardEndpoint() {
        assertEquals("/.well-known", ArdProtocolConstants.WELL_KNOWN_PATH);
    }
    
    @Test
    void controllerShouldUseArdProtocolAdvice() {
        assertNotNull(ArdSearchController.class.getAnnotation(ArdApi.class));
        assertFalse(ArdSearchController.class.isAnnotationPresent(NacosApi.class));
    }
    
    @Test
    void endpointsShouldUseConditionalAnonymousAiAuthentication() {
        int endpointCount = 0;
        for (Method method : ArdSearchController.class.getDeclaredMethods()) {
            if (!method.isAnnotationPresent(GetMapping.class)
                && !method.isAnnotationPresent(PostMapping.class)) {
                continue;
            }
            endpointCount++;
            Secured secured = method.getAnnotation(Secured.class);
            assertNotNull(secured, method.getName());
            assertEquals(ActionTypes.READ, secured.action(), method.getName());
            assertEquals(SignType.AI, secured.signType(), method.getName());
            assertEquals(ApiType.OPEN_API, secured.apiType(), method.getName());
            assertArrayEquals(new String[] {ALLOW_ANONYMOUS}, secured.tags(), method.getName());
        }
        assertEquals(5, endpointCount);
    }
    
    private ArdSearchController controller() {
        return new ArdSearchController(ardSearchService, ardArtifactService);
    }
}
