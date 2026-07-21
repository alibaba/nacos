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

package com.alibaba.nacos.api.ai.utils;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentValidationUtilsTest {
    
    @Test
    void testIdentityValidatorsPreserveCaseAndSpaces() {
        assertDoesNotThrow(() -> AgentValidationUtils.validateNamespaceId("Name_space-1"));
        assertDoesNotThrow(() -> AgentValidationUtils.validateAgentName("Nacos Agent"));
        assertDoesNotThrow(() -> AgentValidationUtils.validateProtocol("A2A-v1"));
        assertDoesNotThrow(() -> AgentValidationUtils.validateProtocolVersion("V1.0"));
        assertDoesNotThrow(() -> AgentValidationUtils.validateLabel("Release_RC1"));
        assertDoesNotThrow(() -> AgentValidationUtils.validateTransport("JSON-RPC"));
        
        assertThrows(IllegalArgumentException.class,
            () -> AgentValidationUtils.validateNamespaceId("bad space"));
        assertThrows(IllegalArgumentException.class,
            () -> AgentValidationUtils.validateAgentName("   "));
        assertThrows(IllegalArgumentException.class,
            () -> AgentValidationUtils.validateAgentName("Agent\nName"));
        assertThrows(IllegalArgumentException.class,
            () -> AgentValidationUtils.validateProtocol("a2a_rpc"));
        assertThrows(IllegalArgumentException.class,
            () -> AgentValidationUtils.validateProtocolVersion("v 1"));
        assertThrows(IllegalArgumentException.class,
            () -> AgentValidationUtils.validateLabel("-label"));
        assertThrows(IllegalArgumentException.class,
            () -> AgentValidationUtils.validateTransport("json_rpc"));
        assertThrows(IllegalArgumentException.class,
            () -> AgentValidationUtils.validateNonLatestLabel("latest"));
        assertThrows(IllegalArgumentException.class,
            () -> AgentValidationUtils.validateAgentName(null));
        assertThrows(IllegalArgumentException.class,
            () -> AgentValidationUtils.validateProtocolVersion(null));
    }
    
    @Test
    void testVersionValidators() {
        assertDoesNotThrow(() -> AgentValidationUtils.validateVersion("1.0.0-RC1"));
        assertThrows(IllegalArgumentException.class,
            () -> AgentValidationUtils.validateVersion("1.0"));
        assertDoesNotThrow(() -> AgentValidationUtils.validateVersionRange("[1.0.0,2.0.0)"));
        assertThrows(IllegalArgumentException.class,
            () -> AgentValidationUtils.validateVersionRange("[2.0.0,1.0.0]"));
    }
    
    @Test
    void testDigestAndMediaTypeValidators() {
        assertDoesNotThrow(() -> AgentValidationUtils.validateContentDigest("sha256:"
            + "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"));
        assertDoesNotThrow(
            () -> AgentValidationUtils.validateMediaType("application/json;charset=utf-8"));
        assertThrows(IllegalArgumentException.class,
            () -> AgentValidationUtils.validateContentDigest("sha256:ABC"));
        assertThrows(IllegalArgumentException.class,
            () -> AgentValidationUtils.validateMediaType("application json"));
    }
    
    @Test
    void testEndpointMetadataConstraints() {
        Map<String, String> metadata = new LinkedHashMap<String, String>();
        metadata.put("zone", "cn-hangzhou-a");
        metadata.put("环境", "生产");
        assertDoesNotThrow(() -> AgentValidationUtils.validateEndpointMetadata(metadata));
        assertDoesNotThrow(() -> AgentValidationUtils.validateEndpointMetadata(null));
        assertDoesNotThrow(() -> AgentValidationUtils
            .validateEndpointMetadata(Collections.<String, String>emptyMap()));
        
        assertInvalidMetadata("preserved.heart.beat.interval", "1000");
        assertInvalidMetadata("preserved.heart.beat.timeout", "3000");
        assertInvalidMetadata("preserved.ip.delete.timeout", "5000");
        assertInvalidMetadata("__nacos.agent.endpoint.versionRange__", "[1.0.0]");
        assertInvalidMetadata("", "value");
        assertInvalidMetadata("key", null);
        
        Map<String, String> tooMany = new LinkedHashMap<String, String>();
        for (int i = 0; i < 33; i++) {
            tooMany.put("key-" + i, "value");
        }
        assertThrows(IllegalArgumentException.class,
            () -> AgentValidationUtils.validateEndpointMetadata(tooMany));
    }
    
    private void assertInvalidMetadata(String key, String value) {
        assertThrows(IllegalArgumentException.class,
            () -> AgentValidationUtils
                .validateEndpointMetadata(Collections.singletonMap(key, value)));
    }
}
