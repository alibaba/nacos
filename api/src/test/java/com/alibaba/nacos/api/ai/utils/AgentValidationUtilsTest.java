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
    void testValidateNamespaceId() {
        assertDoesNotThrow(() -> AgentValidationUtils.validateNamespaceId("a"));
        assertDoesNotThrow(() -> AgentValidationUtils.validateNamespaceId("Name_space-1"));
        assertDoesNotThrow(() -> AgentValidationUtils.validateNamespaceId(repeat('a', 128)));
        
        assertThrows(IllegalArgumentException.class,
            () -> AgentValidationUtils.validateNamespaceId(null));
        assertThrows(IllegalArgumentException.class,
            () -> AgentValidationUtils.validateNamespaceId(""));
        assertThrows(IllegalArgumentException.class,
            () -> AgentValidationUtils.validateNamespaceId(repeat('a', 129)));
        assertThrows(IllegalArgumentException.class,
            () -> AgentValidationUtils.validateNamespaceId("bad space"));
        assertThrows(IllegalArgumentException.class,
            () -> AgentValidationUtils.validateNamespaceId("bad.namespace"));
        assertThrows(IllegalArgumentException.class,
            () -> AgentValidationUtils.validateNamespaceId("命名空间"));
    }
    
    @Test
    void testValidateAgentName() {
        assertDoesNotThrow(() -> AgentValidationUtils.validateAgentName("A"));
        assertDoesNotThrow(() -> AgentValidationUtils.validateAgentName("Nacos Agent"));
        assertDoesNotThrow(() -> AgentValidationUtils.validateAgentName(" !\"#$%&'()*+,-./"));
        assertDoesNotThrow(() -> AgentValidationUtils.validateAgentName(
            repeat(' ', 63) + "~"));
        
        assertThrows(IllegalArgumentException.class,
            () -> AgentValidationUtils.validateAgentName(null));
        assertThrows(IllegalArgumentException.class,
            () -> AgentValidationUtils.validateAgentName(""));
        assertThrows(IllegalArgumentException.class,
            () -> AgentValidationUtils.validateAgentName(repeat('A', 65)));
        assertThrows(IllegalArgumentException.class,
            () -> AgentValidationUtils.validateAgentName(repeat(' ', 64)));
        assertThrows(IllegalArgumentException.class,
            () -> AgentValidationUtils.validateAgentName("Agent\u001fName"));
        assertThrows(IllegalArgumentException.class,
            () -> AgentValidationUtils.validateAgentName("Agent\u007fName"));
        assertThrows(IllegalArgumentException.class,
            () -> AgentValidationUtils.validateAgentName("Nacos代理"));
    }
    
    @Test
    void testValidateProtocol() {
        assertDoesNotThrow(() -> AgentValidationUtils.validateProtocol("A"));
        assertDoesNotThrow(() -> AgentValidationUtils.validateProtocol("A2A-v1"));
        assertDoesNotThrow(() -> AgentValidationUtils.validateProtocol(repeat('a', 32)));
        
        assertThrows(IllegalArgumentException.class,
            () -> AgentValidationUtils.validateProtocol(null));
        assertThrows(IllegalArgumentException.class,
            () -> AgentValidationUtils.validateProtocol(""));
        assertThrows(IllegalArgumentException.class,
            () -> AgentValidationUtils.validateProtocol(repeat('a', 33)));
        assertThrows(IllegalArgumentException.class,
            () -> AgentValidationUtils.validateProtocol("a2a_rpc"));
        assertThrows(IllegalArgumentException.class,
            () -> AgentValidationUtils.validateProtocol("-a2a"));
        assertThrows(IllegalArgumentException.class,
            () -> AgentValidationUtils.validateProtocol("a2a.v1"));
        assertThrows(IllegalArgumentException.class,
            () -> AgentValidationUtils.validateProtocol("a2a v1"));
        assertThrows(IllegalArgumentException.class,
            () -> AgentValidationUtils.validateProtocol("协议"));
    }
    
    @Test
    void testValidateProtocolVersion() {
        assertDoesNotThrow(() -> AgentValidationUtils.validateProtocolVersion("!"));
        assertDoesNotThrow(() -> AgentValidationUtils.validateProtocolVersion("V1.0"));
        assertDoesNotThrow(() -> AgentValidationUtils.validateProtocolVersion(repeat('~', 64)));
        
        assertThrows(IllegalArgumentException.class,
            () -> AgentValidationUtils.validateProtocolVersion(null));
        assertThrows(IllegalArgumentException.class,
            () -> AgentValidationUtils.validateProtocolVersion(""));
        assertThrows(IllegalArgumentException.class,
            () -> AgentValidationUtils.validateProtocolVersion(repeat('a', 65)));
        assertThrows(IllegalArgumentException.class,
            () -> AgentValidationUtils.validateProtocolVersion("v 1"));
        assertThrows(IllegalArgumentException.class,
            () -> AgentValidationUtils.validateProtocolVersion("v\u001f1"));
        assertThrows(IllegalArgumentException.class,
            () -> AgentValidationUtils.validateProtocolVersion("v\u007f1"));
        assertThrows(IllegalArgumentException.class,
            () -> AgentValidationUtils.validateProtocolVersion("版本1"));
    }
    
    @Test
    void testValidateLabel() {
        assertDoesNotThrow(() -> AgentValidationUtils.validateLabel("A"));
        assertDoesNotThrow(() -> AgentValidationUtils.validateLabel("Release_RC1.0"));
        assertDoesNotThrow(() -> AgentValidationUtils.validateLabel("A" + repeat('.', 63)));
        assertDoesNotThrow(() -> AgentValidationUtils.validateLabel("latest"));
        
        assertThrows(IllegalArgumentException.class,
            () -> AgentValidationUtils.validateLabel(null));
        assertThrows(IllegalArgumentException.class,
            () -> AgentValidationUtils.validateLabel(""));
        assertThrows(IllegalArgumentException.class,
            () -> AgentValidationUtils.validateLabel("A" + repeat('.', 64)));
        assertThrows(IllegalArgumentException.class,
            () -> AgentValidationUtils.validateLabel("-label"));
        assertThrows(IllegalArgumentException.class,
            () -> AgentValidationUtils.validateLabel("_label"));
        assertThrows(IllegalArgumentException.class,
            () -> AgentValidationUtils.validateLabel(".label"));
        assertThrows(IllegalArgumentException.class,
            () -> AgentValidationUtils.validateLabel("release label"));
        assertThrows(IllegalArgumentException.class,
            () -> AgentValidationUtils.validateLabel("发布"));
    }
    
    @Test
    void testValidateNonLatestLabel() {
        assertDoesNotThrow(() -> AgentValidationUtils.validateNonLatestLabel("Latest"));
        assertDoesNotThrow(() -> AgentValidationUtils.validateNonLatestLabel("stable"));
        
        assertThrows(IllegalArgumentException.class,
            () -> AgentValidationUtils.validateNonLatestLabel("latest"));
        assertThrows(IllegalArgumentException.class,
            () -> AgentValidationUtils.validateNonLatestLabel("-invalid"));
    }
    
    @Test
    void testValidateTransport() {
        assertDoesNotThrow(() -> AgentValidationUtils.validateTransport("-"));
        assertDoesNotThrow(() -> AgentValidationUtils.validateTransport("JSON-RPC"));
        assertDoesNotThrow(() -> AgentValidationUtils.validateTransport("HTTP+JSON"));
        assertDoesNotThrow(() -> AgentValidationUtils.validateTransport(repeat('A', 64)));
        
        assertThrows(IllegalArgumentException.class,
            () -> AgentValidationUtils.validateTransport(null));
        assertThrows(IllegalArgumentException.class,
            () -> AgentValidationUtils.validateTransport(""));
        assertThrows(IllegalArgumentException.class,
            () -> AgentValidationUtils.validateTransport(repeat('A', 65)));
        assertThrows(IllegalArgumentException.class,
            () -> AgentValidationUtils.validateTransport("json_rpc"));
        assertThrows(IllegalArgumentException.class,
            () -> AgentValidationUtils.validateTransport("json.rpc"));
        assertThrows(IllegalArgumentException.class,
            () -> AgentValidationUtils.validateTransport("json rpc"));
        assertThrows(IllegalArgumentException.class,
            () -> AgentValidationUtils.validateTransport("传输"));
    }
    
    @Test
    void testVersionValidators() {
        assertDoesNotThrow(() -> AgentValidationUtils.validateVersion("1.0.0-RC1"));
        assertDoesNotThrow(() -> AgentValidationUtils.validateVersion(
            "1.0.0-" + repeat('A', 58)));
        assertThrows(IllegalArgumentException.class,
            () -> AgentValidationUtils.validateVersion(null));
        assertThrows(IllegalArgumentException.class,
            () -> AgentValidationUtils.validateVersion("1.0"));
        assertThrows(IllegalArgumentException.class,
            () -> AgentValidationUtils.validateVersion("1.0.0-" + repeat('A', 59)));
        assertDoesNotThrow(() -> AgentValidationUtils.validateVersionRange("[1.0.0,2.0.0)"));
        assertThrows(IllegalArgumentException.class,
            () -> AgentValidationUtils.validateVersionRange(null));
        assertThrows(IllegalArgumentException.class,
            () -> AgentValidationUtils.validateVersionRange("[2.0.0,1.0.0]"));
    }
    
    @Test
    void testValidateContentDigest() {
        assertDoesNotThrow(() -> AgentValidationUtils.validateContentDigest("sha256:"
            + "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"));
        
        assertThrows(IllegalArgumentException.class,
            () -> AgentValidationUtils.validateContentDigest(null));
        assertThrows(IllegalArgumentException.class,
            () -> AgentValidationUtils.validateContentDigest("sha256:ABC"));
        assertThrows(IllegalArgumentException.class,
            () -> AgentValidationUtils.validateContentDigest("SHA256:" + repeat('a', 64)));
        assertThrows(IllegalArgumentException.class,
            () -> AgentValidationUtils.validateContentDigest("sha256:" + repeat('a', 63)));
        assertThrows(IllegalArgumentException.class,
            () -> AgentValidationUtils.validateContentDigest("sha256:" + repeat('a', 65)));
        assertThrows(IllegalArgumentException.class,
            () -> AgentValidationUtils.validateContentDigest("sha256:" + repeat('g', 64)));
    }
    
    @Test
    void testValidateMediaType() {
        assertDoesNotThrow(() -> AgentValidationUtils.validateMediaType("a/b"));
        assertDoesNotThrow(
            () -> AgentValidationUtils.validateMediaType("application/json;charset=utf-8"));
        assertDoesNotThrow(() -> AgentValidationUtils.validateMediaType(
            repeat('a', 63) + "/" + repeat('b', 64)));
        
        assertThrows(IllegalArgumentException.class,
            () -> AgentValidationUtils.validateMediaType(null));
        assertThrows(IllegalArgumentException.class,
            () -> AgentValidationUtils.validateMediaType(""));
        assertThrows(IllegalArgumentException.class,
            () -> AgentValidationUtils.validateMediaType("application"));
        assertThrows(IllegalArgumentException.class,
            () -> AgentValidationUtils.validateMediaType("application/"));
        assertThrows(IllegalArgumentException.class,
            () -> AgentValidationUtils.validateMediaType("/json"));
        assertThrows(IllegalArgumentException.class,
            () -> AgentValidationUtils.validateMediaType("application json"));
        assertThrows(IllegalArgumentException.class,
            () -> AgentValidationUtils.validateMediaType("application/\njson"));
        assertThrows(IllegalArgumentException.class,
            () -> AgentValidationUtils.validateMediaType("应用/json"));
        assertThrows(IllegalArgumentException.class,
            () -> AgentValidationUtils.validateMediaType(
                repeat('a', 64) + "/" + repeat('b', 64)));
    }
    
    @Test
    void testValidateNonNullJsonValue() {
        assertDoesNotThrow(() -> AgentValidationUtils.validateNonNullJsonValue("", "field"));
        assertDoesNotThrow(() -> AgentValidationUtils.validateNonNullJsonValue(0, "field"));
        assertDoesNotThrow(
            () -> AgentValidationUtils.validateNonNullJsonValue(Boolean.FALSE, "field"));
        assertDoesNotThrow(() -> AgentValidationUtils
            .validateNonNullJsonValue(Collections.emptyMap(), "field"));
        
        assertThrows(IllegalArgumentException.class,
            () -> AgentValidationUtils.validateNonNullJsonValue(null, "nativeDescriptor"));
    }
    
    @Test
    void testValidateEndpointMetadata() {
        Map<String, String> metadata = new LinkedHashMap<String, String>();
        metadata.put("zone", "cn-hangzhou-a");
        metadata.put("环境", "生产");
        metadata.put(repeat("😀", 64), repeat("😀", 256));
        assertDoesNotThrow(() -> AgentValidationUtils.validateEndpointMetadata(metadata));
        assertDoesNotThrow(() -> AgentValidationUtils.validateEndpointMetadata(null));
        assertDoesNotThrow(() -> AgentValidationUtils
            .validateEndpointMetadata(Collections.<String, String>emptyMap()));
        
        Map<String, String> maximumEntries = new LinkedHashMap<String, String>();
        for (int i = 0; i < 32; i++) {
            maximumEntries.put("key-" + i, "");
        }
        assertDoesNotThrow(
            () -> AgentValidationUtils.validateEndpointMetadata(maximumEntries));
        
        assertInvalidMetadata("preserved.heart.beat.interval", "1000");
        assertInvalidMetadata("preserved.heart.beat.timeout", "3000");
        assertInvalidMetadata("preserved.ip.delete.timeout", "5000");
        assertInvalidMetadata("__nacos.agent.endpoint.", "value");
        assertInvalidMetadata("__nacos.agent.endpoint.versionRange__", "[1.0.0]");
        assertInvalidMetadata(null, "value");
        assertInvalidMetadata("", "value");
        assertInvalidMetadata(repeat("😀", 65), "value");
        assertInvalidMetadata("key", null);
        assertInvalidMetadata("key", repeat("😀", 257));
        
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
    
    private String repeat(char value, int count) {
        StringBuilder result = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            result.append(value);
        }
        return result.toString();
    }
    
    private String repeat(String value, int count) {
        StringBuilder result = new StringBuilder(value.length() * count);
        for (int i = 0; i < count; i++) {
            result.append(value);
        }
        return result.toString();
    }
}
