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

package com.alibaba.nacos.ai.importer.security;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.plugin.ai.importer.model.AiResourceImportArtifact;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AiResourceImportSecurityGuardTest {
    
    private final AiResourceImportSecurityGuard guard = new AiResourceImportSecurityGuard();
    
    @Test
    void testAcceptsMatchingArtifactWithinLimit() {
        AiResourceImportArtifact artifact = artifact("mcp");
        artifact.setPayload(new byte[] {1, 2});
        artifact.setPayloadJson("{}");
        
        assertDoesNotThrow(() -> guard.checkArtifact(4, "mcp", artifact));
        assertDoesNotThrow(() -> guard.checkArtifact(0, "mcp", artifact));
    }
    
    @Test
    void testRejectsNullMismatchedAndOversizedArtifacts() {
        assertThrows(NacosException.class, () -> guard.checkArtifact(10, "mcp", null));
        assertThrows(NacosException.class,
            () -> guard.checkArtifact(10, "mcp", artifact("skill")));
        
        AiResourceImportArtifact bytes = artifact("mcp");
        bytes.setPayload(new byte[] {1, 2});
        assertThrows(NacosException.class, () -> guard.checkArtifact(1, "mcp", bytes));
        
        AiResourceImportArtifact json = artifact("mcp");
        json.setPayloadJson("{}");
        assertThrows(NacosException.class, () -> guard.checkArtifact(1, "mcp", json));
    }
    
    @Test
    void testUserEndpointRejectsLocalAndPrivateTargets() {
        assertThrows(NacosException.class, () -> guard.checkUserEndpoint("http://127.0.0.1:8848/nacos"));
        assertThrows(NacosException.class, () -> guard.checkUserEndpoint("http://localhost:8848/nacos"));
        assertThrows(NacosException.class, () -> guard.checkUserEndpoint("http://sub.localhost/registry"));
        assertThrows(NacosException.class, () -> guard.checkUserEndpoint("http://10.0.0.5/registry"));
        assertThrows(NacosException.class, () -> guard.checkUserEndpoint("http://192.168.1.10/registry"));
        assertThrows(NacosException.class,
            () -> guard.checkUserEndpoint("http://169.254.169.254/latest/meta-data"));
        assertThrows(NacosException.class, () -> guard.checkUserEndpoint("http://[::1]/registry"));
        assertThrows(NacosException.class, () -> guard.checkUserEndpoint("http://[fc00::5]/registry"));
        assertThrows(NacosException.class, () -> guard.checkUserEndpoint("http://224.0.0.1/registry"));
    }
    
    @Test
    void testUserEndpointAcceptsPublicTargets() {
        assertDoesNotThrow(() -> guard.checkUserEndpoint("https://8.8.8.8/v0/servers"));
        assertDoesNotThrow(() -> guard.checkUserEndpoint("http://100.64.0.1/v0/servers"));
        assertDoesNotThrow(() -> guard.checkUserEndpoint("https://192.0.2.10:8443/v0/servers"));
    }
    
    @Test
    void testUserEndpointRejectsNonHttpSchemeAndBlankHost() {
        assertThrows(NacosException.class, () -> guard.checkUserEndpoint("file:///etc/passwd"));
        assertThrows(NacosException.class, () -> guard.checkUserEndpoint("ftp://192.0.2.10/registry"));
        assertThrows(NacosException.class, () -> guard.checkUserEndpoint("http:///no-host/path"));
    }
    
    @Test
    void testUserEndpointIgnoresBlankValue() {
        assertDoesNotThrow(() -> guard.checkUserEndpoint(""));
        assertDoesNotThrow(() -> guard.checkUserEndpoint(null));
    }
    
    private AiResourceImportArtifact artifact(String resourceType) {
        AiResourceImportArtifact result = new AiResourceImportArtifact();
        result.setResourceType(resourceType);
        return result;
    }
}
