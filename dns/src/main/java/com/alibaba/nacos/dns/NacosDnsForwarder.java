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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.xbill.DNS.Message;
import org.xbill.DNS.Rcode;
import org.xbill.DNS.Record;
import org.xbill.DNS.SimpleResolver;

import java.net.UnknownHostException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Forwards DNS queries that don't match the Nacos domain suffix to upstream DNS servers.
 *
 * <p><b>Security:</b> This is a conditional forwarder, not an open recursive resolver.
 * Queries matching the Nacos domain suffix are rejected defensively. The server socket
 * should be bound to localhost or an internal interface via {@code nacos.naming.dns.bind-address}.
 *
 * <p><b>Failure semantics:</b> upstream timeouts or SERVFAIL are reported as
 * {@link ForwardStatus#UPSTREAM_FAILURE} so the caller can return SERVFAIL, not NXDOMAIN.
 * This prevents transient upstream failures from poisoning client negative caches.
 *
 * <p><b>Time budget:</b> a single absolute deadline covers all upstream attempts. Each
 * upstream uses {@code min(per-server timeout, remaining budget)}, so 3 servers × 3s
 * cannot block a worker for 9s.
 *
 * @author Nacos
 */
@Component
public class NacosDnsForwarder {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(NacosDnsForwarder.class);
    
    /** Minimum per-server forward timeout in milliseconds. */
    private static final int MIN_FORWARD_TIMEOUT_MS = 100;
    
    /** Default DNS port. */
    private static final int DEFAULT_DNS_PORT = 53;
    
    private final NacosDnsProperties properties;
    private final NacosDnsMetrics metrics;
    private final List<ServerAddress> servers;
    
    public NacosDnsForwarder(NacosDnsProperties properties, NacosDnsMetrics metrics) {
        this.properties = properties;
        this.metrics = metrics;
        this.servers = parseAndValidateServers(properties.getForwardServers());
    }
    
    /**
     * Outcome of a forward attempt.
     */
    public enum ForwardStatus {
        /** Forwarding is disabled in configuration. */
        DISABLED,
        /** No valid upstream servers configured. */
        NO_SERVERS,
        /** Query domain matches the internal suffix — must not be forwarded. */
        INTERNAL_SUFFIX,
        /** Got a valid response from an upstream (NOERROR or NXDOMAIN). */
        SUCCESS,
        /** All upstreams failed (timeout, network error, or SERVFAIL/REFUSED). */
        UPSTREAM_FAILURE
    }
    
    /**
     * Result of a forward attempt, carrying status and optional response.
     */
    public static class ForwardResult {
        
        private final ForwardStatus status;
        private final Message response;
        private final String server;
        
        ForwardResult(ForwardStatus status, Message response, String server) {
            this.status = status;
            this.response = response;
            this.server = server;
        }
        
        public ForwardStatus getStatus() {
            return status;
        }
        
        public Message getResponse() {
            return response;
        }
        
        public String getServer() {
            return server;
        }
    }
    
    /**
     * Forward a DNS query to upstream DNS servers.
     *
     * @param query the DNS query message (must not be null)
     * @return a {@link ForwardResult} never null; check {@link ForwardResult#getStatus()}
     */
    public ForwardResult forward(Message query) {
        if (query == null) {
            return new ForwardResult(ForwardStatus.UPSTREAM_FAILURE, null, null);
        }
        if (!properties.isForwardEnabled()) {
            return new ForwardResult(ForwardStatus.DISABLED, null, null);
        }
        if (servers.isEmpty()) {
            return new ForwardResult(ForwardStatus.NO_SERVERS, null, null);
        }
        
        // Defensive: never forward internal suffix domains to upstream
        Record question = query.getQuestion();
        if (question != null && question.getName() != null) {
            String domain = question.getName().toString(true);
            if (matchesInternalSuffix(domain)) {
                LOGGER.warn("Refusing to forward internal suffix query: {}", domain);
                return new ForwardResult(ForwardStatus.INTERNAL_SUFFIX, null, null);
            }
        }
        
        metrics.recordForwardQuery();
        io.micrometer.core.instrument.Timer.Sample sample = metrics.startForwardSample();
        
        // Absolute deadline for all upstream attempts combined
        long deadline = System.currentTimeMillis() + totalBudgetMs();
        List<String> failedServers = new ArrayList<>();
        
        for (ServerAddress addr : servers) {
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0) {
                LOGGER.warn("Forward time budget exhausted after trying servers: {}",
                    failedServers);
                break;
            }
            
            int timeoutMs = (int) Math.min(perServerTimeoutMs(), remaining);
            try {
                // Create a fresh SimpleResolver per request: setTimeout mutates shared
                // state, so a cached resolver would race between concurrent queries with
                // different remaining-time budgets. Upstream count is small; cost is fine.
                SimpleResolver resolver = createResolver(addr.host(), addr.port());
                resolver.setTimeout(Duration.ofMillis(timeoutMs));
                Message response = resolver.send(query);
                
                if (response == null) {
                    failedServers.add(addr + "(null response)");
                    continue;
                }
                
                int rcode = response.getHeader().getRcode();
                if (rcode == Rcode.NOERROR || rcode == Rcode.NXDOMAIN) {
                    metrics.recordForwardSuccess();
                    metrics.stopForwardSample(sample, addr.toString(), "success");
                    return new ForwardResult(ForwardStatus.SUCCESS, response, addr.toString());
                }
                
                // SERVFAIL / REFUSED / FORMERR from upstream — try next server
                LOGGER.warn("Upstream {} returned rcode={}, trying next", addr,
                    Rcode.string(rcode));
                failedServers.add(addr + "(" + Rcode.string(rcode) + ")");
            } catch (Exception e) {
                LOGGER.warn("Forward to {} failed: {}", addr, e.toString());
                failedServers.add(addr + "(" + e.getClass().getSimpleName() + ")");
            }
        }
        
        metrics.recordForwardFail();
        metrics.stopForwardSample(sample, "all", "failure");
        LOGGER.warn("All upstream DNS servers failed for query {}: {}",
            question != null ? question.getName() : "unknown", failedServers);
        return new ForwardResult(ForwardStatus.UPSTREAM_FAILURE, null, null);
    }
    
    // ---- Internal helpers ----
    
    private boolean matchesInternalSuffix(String domain) {
        if (domain == null) {
            return false;
        }
        if (domain.endsWith(".")) {
            domain = domain.substring(0, domain.length() - 1);
        }
        return domain.endsWith("." + properties.getDomainSuffix());
    }
    
    private int perServerTimeoutMs() {
        return Math.max(properties.getForwardTimeoutMs(), MIN_FORWARD_TIMEOUT_MS);
    }
    
    /**
     * Total time budget for all upstream attempts: 2× per-server timeout.
     * This allows one full attempt plus a fallback without blocking workers too long.
     * Remaining-time slicing in the loop ensures each upstream uses at most
     * min(per-server timeout, remaining budget).
     */
    private long totalBudgetMs() {
        return perServerTimeoutMs() * 2L;
    }
    
    /**
     * Create a SimpleResolver for the given host and port. Extracted for testability —
     * subclasses can override to return mocks.
     */
    protected SimpleResolver createResolver(String host, int port) throws UnknownHostException {
        SimpleResolver r = new SimpleResolver(host);
        r.setPort(port);
        return r;
    }
    
    /**
     * Parse and validate upstream server specifications at construction time (fail-fast).
     * Supports: {@code host}, {@code host:port}, {@code [ipv6]:port}.
     */
    private static List<ServerAddress> parseAndValidateServers(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return Collections.emptyList();
        }
        List<ServerAddress> result = new ArrayList<>();
        for (String spec : raw) {
            if (spec == null || spec.trim().isEmpty()) {
                LOGGER.warn("Ignoring empty forward server entry");
                continue;
            }
            try {
                result.add(parseServer(spec.trim()));
            } catch (IllegalArgumentException e) {
                LOGGER.error("Invalid forward server configuration '{}': {}", spec, e.getMessage());
            }
        }
        return Collections.unmodifiableList(result);
    }
    
    private static ServerAddress parseServer(String spec) {
        if (spec.startsWith("[")) {
            // [ipv6]:port
            int close = spec.indexOf(']');
            if (close < 0) {
                throw new IllegalArgumentException("Unclosed IPv6 bracket");
            }
            String host = spec.substring(1, close);
            int port = DEFAULT_DNS_PORT;
            if (close + 1 < spec.length() && spec.charAt(close + 1) == ':') {
                port = parsePort(spec.substring(close + 2));
            }
            return new ServerAddress(host, port);
        }
        // Bare IPv6 (e.g. "2001:db8::1") has multiple colons and is ambiguous with
        // host:port. Require bracket notation for a clear error.
        long colonCount = spec.chars().filter(c -> c == ':').count();
        if (colonCount > 1) {
            throw new IllegalArgumentException(
                "Bare IPv6 address must use bracket notation [ipv6]:port, e.g. [2001:db8::1]:53");
        }
        int colon = spec.lastIndexOf(':');
        if (colon > 0) {
            String host = spec.substring(0, colon);
            int port = parsePort(spec.substring(colon + 1));
            return new ServerAddress(host, port);
        }
        return new ServerAddress(spec, DEFAULT_DNS_PORT);
    }
    
    private static int parsePort(String s) {
        int port = Integer.parseInt(s.trim());
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Port out of range: " + port);
        }
        return port;
    }
    
    /**
     * Parsed upstream server address.
     */
    private record ServerAddress(String host, int port) {
        
        @Override
        public String toString() {
            return host + ":" + port;
        }
    }
}
