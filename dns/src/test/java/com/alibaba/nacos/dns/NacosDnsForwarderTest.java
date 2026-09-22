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
import org.xbill.DNS.Message;
import org.xbill.DNS.Name;
import org.xbill.DNS.Record;
import org.xbill.DNS.Type;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests for {@link NacosDnsForwarder}.
 *
 * @author Nacos
 */
class NacosDnsForwarderTest {
    
    private NacosDnsProperties buildProperties(boolean forwardEnabled, String... servers) {
        NacosDnsProperties properties = new NacosDnsProperties();
        properties.setForwardEnabled(forwardEnabled);
        if (servers.length > 0) {
            properties.setForwardServers(java.util.Arrays.asList(servers));
        }
        return properties;
    }
    
    private Message buildQuery(String domain, int type) {
        Name queryName = Name.fromConstantString(domain.endsWith(".") ? domain : domain + ".");
        Record question = Record.newRecord(queryName, type, DClass.IN);
        return Message.newQuery(question);
    }
    
    @Test
    void testForwardDisabledReturnsNull() {
        NacosDnsForwarder forwarder = new NacosDnsForwarder(buildProperties(false));
        Message result = forwarder.forward(buildQuery("www.google.com", Type.A));
        assertNull(result, "Should return null when forwarding is disabled");
    }
    
    @Test
    void testNoUpstreamServersReturnsNull() {
        NacosDnsForwarder forwarder = new NacosDnsForwarder(buildProperties(true));
        Message result = forwarder.forward(buildQuery("www.google.com", Type.A));
        assertNull(result, "Should return null when no upstream servers configured");
    }
    
    @Test
    void testEmptyUpstreamServersReturnsNull() {
        NacosDnsProperties props = buildProperties(true);
        props.setForwardServers(Collections.emptyList());
        NacosDnsForwarder forwarder = new NacosDnsForwarder(props);
        Message result = forwarder.forward(buildQuery("www.google.com", Type.A));
        assertNull(result, "Should return null when upstream server list is empty");
    }
    
    @Test
    void testUnreachableUpstreamReturnsNull() {
        // Use an unroutable address that will fail quickly
        NacosDnsForwarder forwarder = new NacosDnsForwarder(
            buildProperties(true, "10.255.255.1"));
        Message result = forwarder.forward(buildQuery("www.google.com", Type.A));
        assertNull(result, "Should return null when upstream is unreachable");
    }
}
