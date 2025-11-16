/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.api.ai.model.mcp.registry;

import com.alibaba.nacos.api.remote.request.BasicRequestTest;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackageTransportTest extends BasicRequestTest {
    
    @Test
    void testPackageWithStdioTransport() throws JsonProcessingException {
        Package pkg = new Package();
        pkg.setIdentifier("test-package");
        pkg.setVersion("1.0.0");
        
        StdioTransport transport = new StdioTransport();
        pkg.setTransport(transport);
        
        String json = mapper.writeValueAsString(pkg);
        assertTrue(json.contains("\"identifier\":\"test-package\""));
        assertTrue(json.contains("\"version\":\"1.0.0\""));
        assertTrue(json.contains("\"transport\":{"));
        assertTrue(json.contains("\"type\":\"stdio\""));
    }
    
    @Test
    void testPackageWithStreamableHttpTransport() throws JsonProcessingException {
        Package pkg = new Package();
        pkg.setIdentifier("test-package");
        pkg.setVersion("1.0.0");
        
        StreamableHttpTransport transport = new StreamableHttpTransport();
        transport.setUrl("http://localhost:8080/api");
        
        KeyValueInput header = new KeyValueInput();
        header.setName("Authorization");
        header.setValue("Bearer token");
        transport.setHeaders(Arrays.asList(header));
        
        pkg.setTransport(transport);
        
        String json = mapper.writeValueAsString(pkg);
        assertTrue(json.contains("\"type\":\"streamable-http\""));
        assertTrue(json.contains("\"url\":\"http://localhost:8080/api\""));
    }
    
    @Test
    void testPackageWithSseTransport() throws JsonProcessingException {
        Package pkg = new Package();
        pkg.setIdentifier("test-package");
        pkg.setVersion("1.0.0");
        
        SseTransport transport = new SseTransport();
        transport.setUrl("https://example.com/sse");
        pkg.setTransport(transport);
        
        String json = mapper.writeValueAsString(pkg);
        assertTrue(json.contains("\"type\":\"sse\""));
        assertTrue(json.contains("\"url\":\"https://example.com/sse\""));
    }
    
    @Test
    void testDeserializePackageWithStdioTransport() throws JsonProcessingException {
        String json = "{\"identifier\":\"test-package\",\"version\":\"1.0.0\","
                + "\"transport\":{\"type\":\"stdio\"}}";
        
        Package pkg = mapper.readValue(json, Package.class);
        
        assertEquals("test-package", pkg.getIdentifier());
        assertEquals("1.0.0", pkg.getVersion());
        assertNotNull(pkg.getTransport());
        assertTrue(pkg.getTransport() instanceof StdioTransport);
    }
    
    @Test
    void testDeserializePackageWithStreamableHttpTransport() throws JsonProcessingException {
        String json = "{\"identifier\":\"test-package\",\"version\":\"1.0.0\","
                + "\"transport\":{\"type\":\"streamable-http\",\"url\":\"http://localhost:8080/api\","
                + "\"headers\":[{\"name\":\"Authorization\",\"value\":\"Bearer token\"}]}}";
        
        Package pkg = mapper.readValue(json, Package.class);
        
        assertEquals("test-package", pkg.getIdentifier());
        assertNotNull(pkg.getTransport());
        assertTrue(pkg.getTransport() instanceof StreamableHttpTransport);
    }
    
    @Test
    void testDeserializePackageWithSseTransport() throws JsonProcessingException {
        String json = "{\"identifier\":\"test-package\",\"version\":\"1.0.0\","
                + "\"transport\":{\"type\":\"sse\",\"url\":\"https://example.com/sse\"}}";
        
        Package pkg = mapper.readValue(json, Package.class);
        
        assertEquals("test-package", pkg.getIdentifier());
        assertNotNull(pkg.getTransport());
        assertTrue(pkg.getTransport() instanceof SseTransport);
    }
    
    @Test
    void testPackageWithCompleteFields() throws JsonProcessingException {
        Package pkg = new Package();
        pkg.setRegistryType("npm");
        pkg.setRegistryBaseUrl("https://registry.npmjs.org");
        pkg.setIdentifier("test-package");
        pkg.setVersion("1.0.0");
        pkg.setFileSha256("abc123def456");
        pkg.setRuntimeHint("node runtime");
        
        StdioTransport transport = new StdioTransport();
        pkg.setTransport(transport);
        
        String json = mapper.writeValueAsString(pkg);
        assertNotNull(json);
        assertTrue(json.contains("\"registryType\":\"npm\""));
        assertTrue(json.contains("\"identifier\":\"test-package\""));
        assertTrue(json.contains("\"transport\":{"));
    }
}
