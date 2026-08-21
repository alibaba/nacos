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

package com.alibaba.nacos.console.config;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpEndpointAccessValidatorTest {
    
    @Test
    void testRejectWhenImportIsDisabledWithoutResolvingHost() {
        AtomicBoolean resolverCalled = new AtomicBoolean();
        McpEndpointAccessValidator validator = new McpEndpointAccessValidator(() -> false,
            () -> "",
            host -> {
                resolverCalled.set(true);
                return addresses("127.0.0.1");
            });
        
        SecurityException exception = assertThrows(SecurityException.class,
            () -> validator.validate("http://127.0.0.1:8080", "/mcp"));
        
        assertTrue(exception.getMessage().contains(
            McpEndpointAccessValidator.IMPORT_ENABLED_PROPERTY));
        assertTrue(exception.getMessage().contains("disabled"));
        assertFalse(resolverCalled.get());
    }
    
    @Test
    void testAllowPublicAddressesWithoutPrivateAllowlist() throws Exception {
        McpEndpointAccessValidator validator = validator("", "93.184.216.34",
            "2001:4860:4860::8888");
        
        assertDoesNotThrow(() -> validator.validate("https://mcp.example.com", "/mcp"));
    }
    
    @Test
    void testAllowPrivateIpv4CidrWithHostBitsAndExactAddress() throws Exception {
        McpEndpointAccessValidator validator = validator(
            "10.20.30.40/16, 192.168.2.8", "10.20.99.7", "192.168.2.8");
        
        assertDoesNotThrow(() -> validator.validate("http://mcp.example.com:8080", "/mcp"));
    }
    
    @Test
    void testMatchNonByteAlignedCidrPrefix() throws Exception {
        McpEndpointAccessValidator allowed = validator("10.20.128.1/17", "10.20.200.8");
        McpEndpointAccessValidator denied = validator("10.20.128.1/17", "10.20.127.8");
        
        assertDoesNotThrow(() -> allowed.validate("https://mcp.example.com", "/mcp"));
        assertThrows(SecurityException.class,
            () -> denied.validate("https://mcp.example.com", "/mcp"));
    }
    
    @Test
    void testAllowIpv6PrivateCidr() throws Exception {
        McpEndpointAccessValidator validator = validator("fd12:3456::/32", "fd12:3456:1::10");
        
        assertDoesNotThrow(() -> validator.validate("https://[fd12:3456:1::10]", "/mcp"));
    }
    
    @Test
    void testRejectWhenAnyResolvedAddressIsPrivateAndNotAllowed() throws Exception {
        McpEndpointAccessValidator validator = new McpEndpointAccessValidator(() -> true,
            () -> "", host -> addresses("93.184.216.34", "10.21.1.8"));
        
        SecurityException exception = assertThrows(SecurityException.class,
            () -> validator.validate("https://mcp.example.com", "/mcp"));
        
        assertTrue(exception.getMessage().contains("10.21.1.8"));
        assertTrue(exception.getMessage().contains("private or local"));
        assertTrue(exception.getMessage().contains(
            McpEndpointAccessValidator.ALLOWED_PRIVATE_ADDRESSES_PROPERTY));
    }
    
    @Test
    void testRejectInvalidAllowlistEntry() throws Exception {
        AtomicBoolean resolverCalled = new AtomicBoolean();
        McpEndpointAccessValidator validator = new McpEndpointAccessValidator(() -> true,
            () -> "10.20.0.0/33", host -> {
                resolverCalled.set(true);
                return addresses("10.20.1.8");
            });
        
        SecurityException exception = assertThrows(SecurityException.class,
            () -> validator.validate("https://mcp.example.com", "/mcp"));
        
        assertTrue(exception.getMessage().contains("10.20.0.0/33"));
        assertTrue(exception.getMessage().contains("invalid entry"));
        assertFalse(resolverCalled.get());
    }
    
    @Test
    void testRejectNonHttpBaseUrlAndUserInfo() throws Exception {
        McpEndpointAccessValidator validator = validator("", "93.184.216.34");
        
        IllegalArgumentException schemeException = assertThrows(IllegalArgumentException.class,
            () -> validator.validate("file:///etc/passwd", "/mcp"));
        IllegalArgumentException userInfoException = assertThrows(IllegalArgumentException.class,
            () -> validator.validate("http://user@127.0.0.1:8080", "/mcp"));
        
        assertTrue(schemeException.getMessage().contains("HTTP or HTTPS"));
        assertTrue(userInfoException.getMessage().contains("user information"));
    }
    
    @Test
    void testRejectEndpointThatOverridesBaseUrl() throws Exception {
        McpEndpointAccessValidator validator = validator("", "93.184.216.34");
        
        IllegalArgumentException absoluteException = assertThrows(IllegalArgumentException.class,
            () -> validator.validate("http://127.0.0.1:8080", "http://192.0.2.1/mcp"));
        IllegalArgumentException authorityException = assertThrows(IllegalArgumentException.class,
            () -> validator.validate("http://127.0.0.1:8080", "//192.0.2.1/mcp"));
        
        assertTrue(absoluteException.getMessage().contains("relative URI path"));
        assertTrue(authorityException.getMessage().contains("relative URI path"));
    }
    
    @Test
    void testRejectUnresolvableHost() {
        McpEndpointAccessValidator validator = new McpEndpointAccessValidator(() -> true,
            () -> "", host -> {
                throw new UnknownHostException(host);
            });
        
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> validator.validate("https://missing.example.com", "/mcp"));
        
        assertTrue(exception.getMessage().contains("could not be resolved"));
        assertTrue(exception.getMessage().contains("No connection was attempted"));
    }
    
    private McpEndpointAccessValidator validator(String allowlist, String... resolvedAddresses)
        throws Exception {
        InetAddress[] addresses = addresses(resolvedAddresses);
        return new McpEndpointAccessValidator(() -> true, () -> allowlist, host -> addresses);
    }
    
    private static InetAddress[] addresses(String... values) throws UnknownHostException {
        InetAddress[] result = new InetAddress[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = InetAddress.getByName(values[i]);
        }
        return result;
    }
}
