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

package com.alibaba.nacos.airegistry.service.ard;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for {@link ArdTrustIdentityValidator}.
 *
 * @author nacos
 */
class ArdTrustIdentityValidatorTest {
    
    @Test
    void shouldNormalizeOnlyExplicitFqdnSyntaxWithoutResolvingDns() {
        assertEquals("registry.example.com",
            ArdTrustIdentityValidator.normalizePublisher("Registry.Example.COM."));
        for (String publisher : List.of("xn--bcher-kva.example", "a.b", "registry.invalid",
            "a".repeat(63) + ".example")) {
            assertEquals(publisher, ArdTrustIdentityValidator.normalizePublisher(publisher));
        }
        for (String publisher : List.of("", "nacos", "localhost", "127.0.0.1", "[::1]",
            "https://example.com", "example.com:443", " example.com", "example.com ",
            "example.com..", "example..com", "-example.com", "example-.com", "a_b.com",
            "bücher.example", "example.123", "a".repeat(64) + ".example",
            ("a".repeat(63) + ".").repeat(4) + "com")) {
            assertThrows(IllegalStateException.class,
                () -> ArdTrustIdentityValidator.normalizePublisher(publisher), publisher);
        }
    }
    
    @Test
    void shouldCompareKnownAuthoritiesWithoutResolvingThem() {
        for (String identity : List.of("https://example.com", "HTTPS://EXAMPLE.COM:443/id",
            "https://example.com./id?version=1#key", "https://example.com:8443/id",
            "spiffe://example.com/workload", "SPIFFE://example.com/workload",
            "did:web:example.com", "did:web:EXAMPLE.COM%3A8443:users:alice",
            "did:web:example.com%3a443:users:a%2Fb")) {
            assertDoesNotThrow(() -> ArdTrustIdentityValidator.validate("Example.COM", identity),
                identity);
        }
        assertDoesNotThrow(() -> ArdTrustIdentityValidator.validate("example.com.",
            "https://example.com/id"));
        assertDoesNotThrow(() -> ArdTrustIdentityValidator.validate("example.com.",
            "spiffe://example.com./workload"));
    }
    
    @Test
    void shouldRejectAuthorityMismatchWithoutGuessingDelegation() {
        for (String identity : List.of("https://other.example/id", "https://sub.example.com/id",
            "https://example.com.evil.test/id", "spiffe://other.example/workload",
            "did:web:other.example:users:alice", "spiffe://example.com./workload")) {
            assertThrows(IllegalStateException.class,
                () -> ArdTrustIdentityValidator.validate("example.com", identity), identity);
        }
        assertThrows(IllegalStateException.class,
            () -> ArdTrustIdentityValidator.validate("nacos", "https://example.com/id"));
        assertThrows(IllegalStateException.class,
            () -> ArdTrustIdentityValidator.validate("n1-ZXhhbXBsZTpjb20", "https://example.com"));
    }
    
    @Test
    void shouldRejectMalformedRecognizedIdentities() {
        for (String identity : List.of(" https://example.com", "https://example.com ", "https:",
            "https:example.com", "https:///example.com",
            "https://user@example.com/id", "https://example.com:bad/id",
            "https://example.com:/id", "https://example.com:65536/id", "https://exa mple.com",
            "spiffe:example.com", "spiffe://EXAMPLE.COM/workload",
            "spiffe://example.com:443/workload", "spiffe://user@example.com/workload",
            "spiffe://example.com/workload?query=1", "spiffe://example.com/workload#key",
            "spiffe://example%2ecom/workload", "did:web", "did:web:", "did:web:example.com:",
            "did:web:example.com%3Aabc", "did:web:example.com%3A65536",
            "did:web:example.com%2Fother", "did:web:example.com:bad%xx",
            "did:web:example.com?query=1", "did:WEB:example.com", "did:web:127.0.0.1")) {
            assertThrows(IllegalStateException.class,
                () -> ArdTrustIdentityValidator.validate("example.com", identity), identity);
        }
        assertThrows(IllegalStateException.class,
            () -> ArdTrustIdentityValidator.validate("127.0.0.1", "did:web:127.0.0.1"));
    }
    
    @Test
    void shouldLeaveAbsentAndUnknownIdentitiesUnverified() {
        assertDoesNotThrow(() -> ArdTrustIdentityValidator.validate("nacos", null));
        for (String identity : List.of("", " ", "did:example:subject", "did:key:z123",
            "urn:example:identity", "custom://other.example/id")) {
            assertDoesNotThrow(() -> ArdTrustIdentityValidator.validate("nacos", identity),
                identity);
        }
    }
}
