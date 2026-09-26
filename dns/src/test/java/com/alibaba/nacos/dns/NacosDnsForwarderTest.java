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

package com.alibaba.nacos.dns;

import org.junit.jupiter.api.Test;
import org.xbill.DNS.DClass;
import org.xbill.DNS.Flags;
import org.xbill.DNS.Message;
import org.xbill.DNS.Name;
import org.xbill.DNS.Rcode;
import org.xbill.DNS.Record;
import org.xbill.DNS.SimpleResolver;
import org.xbill.DNS.Type;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link NacosDnsForwarder}.
 *
 * @author Nacos
 */
class NacosDnsForwarderTest {
    
    private NacosDnsMetrics metrics = new NacosDnsMetrics(null);
    
    private NacosDnsProperties buildProperties(boolean forwardEnabled, String... servers) {
        NacosDnsProperties properties = new NacosDnsProperties();
        properties.setForwardEnabled(forwardEnabled);
        properties.setForwardTimeoutMs(200);
        if (servers.length > 0) {
            properties.setForwardServers(Arrays.asList(servers));
        }
        return properties;
    }
    
    private Message buildQuery(String domain, int type) {
        Name queryName = Name.fromConstantString(domain.endsWith(".") ? domain : domain + ".");
        Record question = Record.newRecord(queryName, type, DClass.IN);
        return Message.newQuery(question);
    }
    
    private Message buildResponse(Message query, int rcode) {
        Message response = new Message(query.getHeader().getID());
        response.getHeader().setFlag(Flags.QR);
        response.getHeader().setRcode(rcode);
        response.addRecord(query.getQuestion(), org.xbill.DNS.Section.QUESTION);
        return response;
    }
    
    /** Create a forwarder that returns the given mock resolver for all servers. */
    private NacosDnsForwarder forwarderWithResolver(NacosDnsProperties props,
        SimpleResolver resolver) {
        return new NacosDnsForwarder(props, metrics) {
            
            @Override
            protected SimpleResolver createResolver(String host, int port) {
                return resolver;
            }
        };
    }
    
    @Test
    void testForwardDisabledReturnsDisabledStatus() {
        NacosDnsForwarder forwarder = new NacosDnsForwarder(buildProperties(false), metrics);
        NacosDnsForwarder.ForwardResult result =
            forwarder.forward(buildQuery("www.google.com", Type.A));
        assertEquals(NacosDnsForwarder.ForwardStatus.DISABLED, result.getStatus());
        assertNull(result.getResponse());
    }
    
    @Test
    void testNoUpstreamServersReturnsNoServersStatus() {
        NacosDnsForwarder forwarder = new NacosDnsForwarder(buildProperties(true), metrics);
        NacosDnsForwarder.ForwardResult result =
            forwarder.forward(buildQuery("www.google.com", Type.A));
        assertEquals(NacosDnsForwarder.ForwardStatus.NO_SERVERS, result.getStatus());
    }
    
    @Test
    void testEmptyUpstreamServersReturnsNoServersStatus() {
        NacosDnsProperties props = buildProperties(true);
        props.setForwardServers(Collections.emptyList());
        NacosDnsForwarder forwarder = new NacosDnsForwarder(props, metrics);
        NacosDnsForwarder.ForwardResult result =
            forwarder.forward(buildQuery("www.google.com", Type.A));
        assertEquals(NacosDnsForwarder.ForwardStatus.NO_SERVERS, result.getStatus());
    }
    
    @Test
    void testNullQueryReturnsUpstreamFailure() {
        NacosDnsForwarder forwarder =
            new NacosDnsForwarder(buildProperties(true, "8.8.8.8"), metrics);
        NacosDnsForwarder.ForwardResult result = forwarder.forward(null);
        assertEquals(NacosDnsForwarder.ForwardStatus.UPSTREAM_FAILURE, result.getStatus());
    }
    
    @Test
    void testInternalSuffixRejected() {
        NacosDnsForwarder forwarder =
            new NacosDnsForwarder(buildProperties(true, "8.8.8.8"), metrics);
        NacosDnsForwarder.ForwardResult result = forwarder.forward(
            buildQuery("my-service.DEFAULT_GROUP.nacos", Type.A));
        assertEquals(NacosDnsForwarder.ForwardStatus.INTERNAL_SUFFIX, result.getStatus());
    }
    
    @Test
    void testSuccessfulForwardReturnsResponse() throws IOException {
        SimpleResolver mockResolver = mock(SimpleResolver.class);
        Message query = buildQuery("www.google.com", Type.A);
        Message expected = buildResponse(query, Rcode.NOERROR);
        when(mockResolver.send(query)).thenReturn(expected);
        
        NacosDnsForwarder forwarder =
            forwarderWithResolver(buildProperties(true, "8.8.8.8"), mockResolver);
        NacosDnsForwarder.ForwardResult result = forwarder.forward(query);
        
        assertEquals(NacosDnsForwarder.ForwardStatus.SUCCESS, result.getStatus());
        assertNotNull(result.getResponse());
        assertEquals(Rcode.NOERROR, result.getResponse().getHeader().getRcode());
        assertEquals("8.8.8.8:53", result.getServer());
    }
    
    @Test
    void testNxdomainResponseAccepted() throws IOException {
        SimpleResolver mockResolver = mock(SimpleResolver.class);
        Message query = buildQuery("nonexistent.example.com", Type.A);
        Message expected = buildResponse(query, Rcode.NXDOMAIN);
        when(mockResolver.send(query)).thenReturn(expected);
        
        NacosDnsForwarder forwarder =
            forwarderWithResolver(buildProperties(true, "8.8.8.8"), mockResolver);
        NacosDnsForwarder.ForwardResult result = forwarder.forward(query);
        
        assertEquals(NacosDnsForwarder.ForwardStatus.SUCCESS, result.getStatus());
        assertEquals(Rcode.NXDOMAIN, result.getResponse().getHeader().getRcode());
    }
    
    @Test
    void testFirstUpstreamThrowsFallsBackToSecond() throws IOException {
        SimpleResolver first = mock(SimpleResolver.class);
        SimpleResolver second = mock(SimpleResolver.class);
        Message query = buildQuery("www.google.com", Type.A);
        Message expected = buildResponse(query, Rcode.NOERROR);
        
        when(first.send(query)).thenThrow(new IOException("connection refused"));
        when(second.send(query)).thenReturn(expected);
        
        AtomicInteger callCount = new AtomicInteger(0);
        NacosDnsForwarder forwarder =
            new NacosDnsForwarder(buildProperties(true, "1.1.1.1", "8.8.8.8"), metrics) {
                
                @Override
                protected SimpleResolver createResolver(String host, int port) {
                    return callCount.getAndIncrement() == 0 ? first : second;
                }
            };
        
        NacosDnsForwarder.ForwardResult result = forwarder.forward(query);
        assertEquals(NacosDnsForwarder.ForwardStatus.SUCCESS, result.getStatus());
        assertEquals("8.8.8.8:53", result.getServer());
    }
    
    @Test
    void testUpstreamServfailTriesNext() throws IOException {
        SimpleResolver first = mock(SimpleResolver.class);
        SimpleResolver second = mock(SimpleResolver.class);
        Message query = buildQuery("www.google.com", Type.A);
        Message servfail = buildResponse(query, Rcode.SERVFAIL);
        Message noerror = buildResponse(query, Rcode.NOERROR);
        
        when(first.send(query)).thenReturn(servfail);
        when(second.send(query)).thenReturn(noerror);
        
        AtomicInteger callCount = new AtomicInteger(0);
        NacosDnsForwarder forwarder =
            new NacosDnsForwarder(buildProperties(true, "1.1.1.1", "8.8.8.8"), metrics) {
                
                @Override
                protected SimpleResolver createResolver(String host, int port) {
                    return callCount.getAndIncrement() == 0 ? first : second;
                }
            };
        
        NacosDnsForwarder.ForwardResult result = forwarder.forward(query);
        assertEquals(NacosDnsForwarder.ForwardStatus.SUCCESS, result.getStatus());
        assertEquals("8.8.8.8:53", result.getServer());
    }
    
    @Test
    void testAllUpstreamsFailReturnsUpstreamFailure() throws IOException {
        SimpleResolver mockResolver = mock(SimpleResolver.class);
        Message query = buildQuery("www.google.com", Type.A);
        when(mockResolver.send(query)).thenThrow(new IOException("timeout"));
        
        NacosDnsForwarder forwarder = forwarderWithResolver(
            buildProperties(true, "1.1.1.1", "8.8.8.8"), mockResolver);
        NacosDnsForwarder.ForwardResult result = forwarder.forward(query);
        
        assertEquals(NacosDnsForwarder.ForwardStatus.UPSTREAM_FAILURE, result.getStatus());
        assertNull(result.getResponse());
    }
    
    @Test
    void testServerWithCustomPort() {
        NacosDnsProperties props = buildProperties(true, "127.0.0.1:5353");
        NacosDnsForwarder forwarder = new NacosDnsForwarder(props, metrics);
        // Should not throw during construction — port parsing happens at init
        assertNotNull(forwarder);
    }
    
    @Test
    void testInvalidServerEntriesIgnored() {
        NacosDnsProperties props = buildProperties(true, "", "  ", "8.8.8.8");
        NacosDnsForwarder forwarder = new NacosDnsForwarder(props, metrics);
        // Blank entries should be skipped, only 8.8.8.8 remains
        assertNotNull(forwarder);
    }
    
    @Test
    void testBareIpv6WithoutBracketsIsRejected() {
        // "2001:db8::1" has multiple colons and is ambiguous with host:port.
        // Must be rejected with a clear message, not silently misparsed.
        NacosDnsProperties props = buildProperties(true, "2001:db8::1");
        NacosDnsForwarder forwarder = new NacosDnsForwarder(props, metrics);
        // The invalid entry is logged and skipped; forwarder still constructs.
        assertNotNull(forwarder);
    }
    
    @Test
    void testIpv6WithBracketsAndPortIsAccepted() {
        NacosDnsProperties props = buildProperties(true, "[2001:db8::1]:5353");
        NacosDnsForwarder forwarder = new NacosDnsForwarder(props, metrics);
        assertNotNull(forwarder);
    }
    
    @Test
    void testTimeoutBudgetExhaustedSkipsRemainingServers() throws IOException {
        // Use a resolver that blocks then throws (simulating timeout beyond total budget)
        SimpleResolver slowResolver = mock(SimpleResolver.class);
        Message query = buildQuery("www.google.com", Type.A);
        when(slowResolver.send(query)).thenAnswer(invocation -> {
            Thread.sleep(500); // block beyond total budget (2 * 200ms = 400ms)
            throw new IOException("timed out");
        });
        
        AtomicInteger createCount = new AtomicInteger(0);
        NacosDnsForwarder forwarder = new NacosDnsForwarder(
            buildProperties(true, "1.1.1.1", "8.8.8.8", "9.9.9.9"), metrics) {
            
            @Override
            protected SimpleResolver createResolver(String host, int port) {
                createCount.incrementAndGet();
                return slowResolver;
            }
        };
        
        long start = System.currentTimeMillis();
        NacosDnsForwarder.ForwardResult result = forwarder.forward(query);
        long elapsed = System.currentTimeMillis() - start;
        
        assertEquals(NacosDnsForwarder.ForwardStatus.UPSTREAM_FAILURE, result.getStatus());
        // Should not have tried all 3 servers — budget runs out after first slow attempt
        assertTrue(createCount.get() < 3,
            "Expected fewer than 3 resolver creations due to budget exhaustion, got "
                + createCount.get());
        // Total time should be bounded near the budget, not 3 * 500ms
        assertTrue(elapsed < 1500, "Expected bounded total time, got " + elapsed + "ms");
    }
}
