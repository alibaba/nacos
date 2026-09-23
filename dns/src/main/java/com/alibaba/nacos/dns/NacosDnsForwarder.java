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
import org.xbill.DNS.Record;
import org.xbill.DNS.SimpleResolver;

import java.time.Duration;
import java.util.List;

/**
 * Forwards DNS queries that don't match the Nacos domain suffix to upstream DNS servers.
 *
 * @author Nacos
 */
@Component
public class NacosDnsForwarder {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(NacosDnsForwarder.class);
    
    /** Minimum forward timeout in milliseconds. Prevents zero/infinite waits from misconfiguration. */
    private static final int MIN_FORWARD_TIMEOUT_MS = 100;
    
    private final NacosDnsProperties properties;
    
    public NacosDnsForwarder(NacosDnsProperties properties) {
        this.properties = properties;
    }
    
    /**
     * Forward a DNS query to upstream DNS servers.
     *
     * @param query the DNS query message
     * @return the response from upstream, or null if forwarding is disabled or fails
     */
    public Message forward(Message query) {
        if (!properties.isForwardEnabled()) {
            return null;
        }
        
        List<String> servers = properties.getForwardServers();
        if (servers == null || servers.isEmpty()) {
            LOGGER.debug("Forwarding enabled but no upstream servers configured");
            return null;
        }
        
        for (String server : servers) {
            try {
                SimpleResolver resolver = new SimpleResolver(server);
                // Clamp to minimum: Duration.ZERO means infinite wait in dnsjava
                int timeoutMs = Math.max(properties.getForwardTimeoutMs(), MIN_FORWARD_TIMEOUT_MS);
                resolver.setTimeout(Duration.ofMillis(timeoutMs));
                Message response = resolver.send(query);
                if (response != null) {
                    LOGGER.debug("Forwarded query to {} successfully", server);
                    return response;
                }
            } catch (Exception e) {
                LOGGER.debug("Failed to forward query to {}: {}", server, e.getMessage());
            }
        }
        
        Record question = query.getQuestion();
        LOGGER.warn("All upstream DNS servers failed for query {}",
            question != null ? question.getName() : "unknown");
        return null;
    }
}
