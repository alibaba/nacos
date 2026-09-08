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

package com.alibaba.nacos.naming.healthcheck;

import com.alibaba.nacos.api.naming.pojo.healthcheck.impl.Http;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URI;
import java.net.URISyntaxException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HealthCheckTargetUtilTest {
    
    @ParameterizedTest
    @ValueSource(strings = {"localhost", "127.0.0.1", "mysql-service.internal",
        "mysql_service", "[::1]", "::1", "fe80::1%eth0"})
    void testValidAddress(String address) {
        assertTrue(HealthCheckTargetUtil.isValidAddress(address));
    }
    
    @ParameterizedTest
    @ValueSource(strings = {"", "host:3306", "host/path", "host?query=true",
        "host#fragment", "user@host", "host\\path", "host%3Fquery=true",
        "[::1]:3306", "[::1]suffix", "::1]", "[]",
        "rogue-mysql:3306?allowLoadLocalInfile=true#", "host\nquery=true"})
    void testInvalidAddress(String address) {
        assertFalse(HealthCheckTargetUtil.isValidAddress(address));
    }
    
    @Test
    void testNullAddress() {
        assertFalse(HealthCheckTargetUtil.isValidAddress(null));
    }
    
    @Test
    void testAddressWithIsoControlCharacter() {
        assertFalse(HealthCheckTargetUtil.isValidAddress("host" + (char) 0 + "name"));
    }
    
    @ParameterizedTest
    @ValueSource(strings = {"", "/", "/actuator/health", "actuator/health",
        "/health?group=readiness", "?ready=true", "/health?next=http://example.com",
        "/a:b", "/a@b", "/health%2Fready", "/健康"})
    void testValidHttpRequestTarget(String requestTarget) {
        assertTrue(HealthCheckTargetUtil.isValidHttpRequestTarget(requestTarget));
    }
    
    @ParameterizedTest
    @ValueSource(strings = {"http://example.com/health", "https://example.com/health",
        "//example.com/health", "///example.com/health", "http:health",
        "/health#fragment", "/health%2", "/health path", "/health\\path",
        "/health\nheader"})
    void testInvalidHttpRequestTarget(String requestTarget) {
        assertFalse(HealthCheckTargetUtil.isValidHttpRequestTarget(requestTarget));
    }
    
    @Test
    void testNullHttpRequestTarget() {
        assertFalse(HealthCheckTargetUtil.isValidHttpRequestTarget(null));
    }
    
    @ParameterizedTest
    @ValueSource(strings = {"", ":value", "0:value", "Host:example.com",
        "Authorization:Bearer token",
        "X-Trace-Id:abc-123|X-Health-Mode:ready", "X-Tab:value\twith-tab"})
    void testValidHttpHealthCheckerHeaders(String headers) {
        Http checker = new Http();
        checker.setPath("/health?ready=true");
        checker.setHeaders(headers);
        
        assertTrue(HealthCheckTargetUtil.isValidHttpHealthChecker(checker));
    }
    
    @ParameterizedTest
    @ValueSource(strings = {"Bad Name:value", "X-Test:value\nInjected",
        "X\r\nInjected:value", "[:value", "{:value", "X-Test:value\u0000suffix",
        "Content-Length:5", "Transfer-Encoding:chunked"})
    void testInvalidHttpHealthCheckerHeaders(String headers) {
        Http checker = new Http();
        checker.setPath("/health");
        checker.setHeaders(headers);
        
        assertFalse(HealthCheckTargetUtil.isValidHttpHealthChecker(checker));
    }
    
    @Test
    void testInvalidHttpHealthCheckerHeaderWithDeleteCharacter() {
        Http checker = new Http();
        checker.setPath("/health");
        checker.setHeaders("X-Test:value" + (char) 0x7F + "suffix");
        
        assertFalse(HealthCheckTargetUtil.isValidHttpHealthChecker(checker));
    }
    
    @Test
    void testNullHttpHealthChecker() {
        assertFalse(HealthCheckTargetUtil.isValidHttpHealthChecker(null));
    }
    
    @Test
    void testBuildHttpTargetKeepsConfiguredOriginAndQuery() throws URISyntaxException {
        URI target = HealthCheckTargetUtil.buildHttpTarget("127.0.0.1", 8848,
            "/health/ready?group=readiness&next=http://example.com");
        
        assertEquals("http", target.getScheme());
        assertEquals("127.0.0.1:8848", target.getRawAuthority());
        assertEquals("/health/ready", target.getRawPath());
        assertEquals("group=readiness&next=http://example.com", target.getRawQuery());
    }
    
    @Test
    void testBuildHttpTargetSupportsRelativePathAndQueryOnly() throws URISyntaxException {
        assertEquals("http://localhost:8080/health?ready=true",
            HealthCheckTargetUtil.buildHttpTarget("localhost", 8080,
                "health?ready=true").toString());
        assertEquals("http://localhost:8080/?ready=true",
            HealthCheckTargetUtil.buildHttpTarget("localhost", 8080,
                "?ready=true").toString());
    }
    
    @Test
    void testBuildHttpTargetSupportsIpv6AndCompatibleHostNames() throws URISyntaxException {
        assertEquals("http://[::1]:8080/health",
            HealthCheckTargetUtil.buildHttpTarget("::1", 8080, "/health").toString());
        assertEquals("http://[::1]:8080/health",
            HealthCheckTargetUtil.buildHttpTarget("[::1]", 8080, "/health").toString());
        assertEquals("http://mysql_service:8080/health",
            HealthCheckTargetUtil.buildHttpTarget("mysql_service", 8080,
                "/health").toString());
    }
    
    @Test
    void testBuildHttpTargetRejectsInvalidOriginOrRequestTarget() {
        assertThrows(URISyntaxException.class,
            () -> HealthCheckTargetUtil.buildHttpTarget("host:8080", 8080, "/health"));
        assertThrows(URISyntaxException.class,
            () -> HealthCheckTargetUtil.buildHttpTarget("127.0.0.1", 8080,
                "http://example.com/health"));
    }
}
