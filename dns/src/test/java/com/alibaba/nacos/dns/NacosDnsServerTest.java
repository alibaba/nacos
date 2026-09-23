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

import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.naming.core.InstanceOperatorClientImpl;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xbill.DNS.ARecord;
import org.xbill.DNS.DClass;
import org.xbill.DNS.Flags;
import org.xbill.DNS.Message;
import org.xbill.DNS.Name;
import org.xbill.DNS.Record;
import org.xbill.DNS.Section;
import org.xbill.DNS.SimpleResolver;
import org.xbill.DNS.Type;

import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.DatagramSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Integration tests for {@link NacosDnsServer} covering UDP truncation and TCP retrieval.
 *
 * @author Nacos
 */
class NacosDnsServerTest {
    
    private NacosDnsServer server;
    private NacosDnsProperties properties;
    private InstanceOperatorClientImpl instanceOperator;
    
    @BeforeEach
    void setUp() {
        properties = new NacosDnsProperties();
        properties.setEnabled(true);
        properties.setPort(0);
        properties.setDomainSuffix("nacos");
        properties.setDefaultGroup("DEFAULT_GROUP");
        properties.setTtl(60);
        
        instanceOperator = mock(InstanceOperatorClientImpl.class);
        MeterRegistry registry = new SimpleMeterRegistry();
        NacosDnsMetrics metrics = new NacosDnsMetrics(registry);
        NacosDnsForwarder forwarder = new NacosDnsForwarder(properties, metrics);
        NacosDnsQueryHandler queryHandler =
            new NacosDnsQueryHandler(instanceOperator, properties, metrics);
        server = new NacosDnsServer(properties, queryHandler, forwarder, metrics);
    }
    
    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
        }
    }
    
    private void mockInstances(int count) {
        List<Instance> hosts = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            Instance inst = new Instance();
            inst.setIp("10." + (i / 256) + "." + ((i % 256) / 16) + "." + (i % 16));
            inst.setPort(8080);
            inst.setHealthy(true);
            inst.setEnabled(true);
            hosts.add(inst);
        }
        ServiceInfo info = new ServiceInfo();
        info.setHosts(hosts);
        when(instanceOperator.listInstance(anyString(), anyString(), anyString(), any(), any(),
            anyBoolean()))
            .thenReturn(info);
    }
    
    private int getListeningPort() throws Exception {
        Field f = NacosDnsServer.class.getDeclaredField("udpSocket");
        f.setAccessible(true);
        DatagramSocket sock = null;
        for (int i = 0; i < 100; i++) {
            sock = (DatagramSocket) f.get(server);
            if (sock != null && sock.getLocalPort() > 0) {
                break;
            }
            Thread.sleep(50);
        }
        assert sock != null;
        return sock.getLocalPort();
    }
    
    private Message sendQuery(int port, String domain, boolean tcp) throws Exception {
        SimpleResolver resolver = new SimpleResolver("127.0.0.1");
        resolver.setPort(port);
        resolver.setTCP(tcp);
        resolver.setTimeout(10);
        Name name = Name.fromConstantString(domain.endsWith(".") ? domain : domain + ".");
        Record question = Record.newRecord(name, Type.A, DClass.IN);
        Message query = Message.newQuery(question);
        return resolver.send(query);
    }
    
    /**
     * Small instance set should fit in UDP without truncation.
     */
    @Test
    void testUdpSmallResponseNotTruncated() throws Exception {
        mockInstances(2);
        server.start();
        int port = getListeningPort();
        Thread.sleep(200);
        
        Message response = sendQuery(port, "svc.nacos.", false);
        assertFalse(response.getHeader().getFlag(Flags.TC),
            "Small UDP response should not be truncated");
        assertEquals(2, response.getSection(Section.ANSWER).size());
    }
    
    /**
     * TCP query should return results successfully.
     */
    @Test
    void testTcpRetrievalWorks() throws Exception {
        mockInstances(3);
        server.start();
        int port = getListeningPort();
        Thread.sleep(500);
        
        Message response = sendQuery(port, "svc.nacos.", true);
        assertFalse(response.getHeader().getFlag(Flags.TC),
            "TCP response should not be truncated");
        assertTrue(response.getSection(Section.ANSWER).size() > 0,
            "TCP response should contain answer records");
        for (Record r : response.getSection(Section.ANSWER)) {
            assertEquals(Type.A, r.getType());
            assertTrue(r instanceof ARecord);
        }
    }
    
    /**
     * Large instance set: verify results are capped at 20 (round-robin).
     */
    @Test
    void testLargeInstanceSetCapped() throws Exception {
        mockInstances(50);
        server.start();
        int port = getListeningPort();
        Thread.sleep(200);
        
        Message response = sendQuery(port, "svc.nacos.", false);
        int answers = response.getSection(Section.ANSWER).size();
        assertTrue(answers <= 20,
            "answer records should be capped at 20, got " + answers);
        assertTrue(answers > 0, "should have some answers");
    }
    
    /**
     * Slow TCP client sending only 1 byte of the 2-byte length prefix must be
     * closed within the absolute deadline (review #6, RFC 7766 §6.2.3).
     */
    @Test
    void testSlowTcpFrameClosedWithinDeadline() throws Exception {
        mockInstances(1);
        server.start();
        int port = getListeningPort();
        Thread.sleep(200);
        
        Socket slowClient = new Socket("127.0.0.1", port);
        slowClient.setSoTimeout(10000);
        OutputStream out = slowClient.getOutputStream();
        InputStream in = slowClient.getInputStream();
        
        // Send only 1 byte (first byte of 2-byte length prefix), never send the second
        out.write(0x00);
        out.flush();
        
        long start = System.currentTimeMillis();
        int b = in.read();
        long elapsed = System.currentTimeMillis() - start;
        
        // Server should close the connection within ~5 seconds (deadline)
        assertEquals(-1, b, "connection should be closed (EOF) after deadline");
        assertTrue(elapsed < 8000,
            "slow frame should be closed within deadline, took " + elapsed + "ms");
        
        slowClient.close();
    }
    
    /**
     * TCP workers occupied by slow connections must not block UDP queries
     * (review #6: separate worker pools).
     */
    @Test
    void testUdpResponsiveWhileTcpWorkersBusy() throws Exception {
        mockInstances(2);
        server.start();
        int port = getListeningPort();
        Thread.sleep(200);
        
        // Open 8 TCP connections and send 1 byte each (they'll wait on deadline)
        List<Socket> slowConns = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            Socket s = new Socket("127.0.0.1", port);
            s.getOutputStream().write(0x00);
            s.getOutputStream().flush();
            slowConns.add(s);
        }
        
        // UDP query should still succeed immediately (separate worker pool)
        Message response = sendQuery(port, "svc.nacos.", false);
        assertFalse(response.getHeader().getFlag(Flags.TC));
        assertEquals(2, response.getSection(Section.ANSWER).size(),
            "UDP should remain responsive despite busy TCP workers");
        
        for (Socket s : slowConns) {
            s.close();
        }
    }
    
    /**
     * Non-Nacos domains with forwarding disabled must return NXDOMAIN.
     */
    @Test
    void testNonNacosDomainReturnsNxDomainWhenForwardDisabled() throws Exception {
        mockInstances(1);
        server.start();
        int port = getListeningPort();
        Thread.sleep(200);
        
        SimpleResolver resolver = new SimpleResolver("127.0.0.1");
        resolver.setPort(port);
        resolver.setTCP(false);
        
        Name queryName = Name.fromConstantString("www.google.com.");
        Record question = Record.newRecord(queryName, Type.A, DClass.IN);
        Message query = Message.newQuery(question);
        Message response = resolver.send(query);
        
        assertEquals(org.xbill.DNS.Rcode.NXDOMAIN, response.getHeader().getRcode(),
            "Non-Nacos domain should return NXDOMAIN when forwarding is disabled");
    }
    
    /**
     * Upstream forward failure must return SERVFAIL, not NXDOMAIN.
     * This prevents transient upstream failures from poisoning client negative caches
     * (P0 review item #2).
     */
    @Test
    void testUpstreamFailureReturnsServfailNotNxdomain() throws Exception {
        // Build a server with a forwarder that always reports UPSTREAM_FAILURE
        NacosDnsProperties props = new NacosDnsProperties();
        props.setEnabled(true);
        props.setPort(0);
        props.setDomainSuffix("nacos");
        props.setForwardEnabled(true);
        props.setForwardServers(List.of("127.0.0.1:9999"));
        
        MeterRegistry registry = new SimpleMeterRegistry();
        NacosDnsMetrics metrics = new NacosDnsMetrics(registry);
        NacosDnsForwarder failingForwarder = new NacosDnsForwarder(props, metrics) {
            
            @Override
            public NacosDnsForwarder.ForwardResult forward(Message query) {
                return new NacosDnsForwarder.ForwardResult(
                    NacosDnsForwarder.ForwardStatus.UPSTREAM_FAILURE, null, null);
            }
        };
        NacosDnsQueryHandler handler = new NacosDnsQueryHandler(instanceOperator, props, metrics);
        NacosDnsServer srv = new NacosDnsServer(props, handler, failingForwarder, metrics);
        try {
            srv.start();
            int port = -1;
            Field f = NacosDnsServer.class.getDeclaredField("udpSocket");
            f.setAccessible(true);
            for (int i = 0; i < 100; i++) {
                DatagramSocket sock = (DatagramSocket) f.get(srv);
                if (sock != null && sock.getLocalPort() > 0) {
                    port = sock.getLocalPort();
                    break;
                }
                Thread.sleep(50);
            }
            assertTrue(port > 0, "server should be listening");
            Thread.sleep(200);
            
            SimpleResolver resolver = new SimpleResolver("127.0.0.1");
            resolver.setPort(port);
            resolver.setTCP(false);
            resolver.setTimeout(5);
            
            Name queryName = Name.fromConstantString("www.google.com.");
            Record question = Record.newRecord(queryName, Type.A, DClass.IN);
            Message query = Message.newQuery(question);
            Message response = resolver.send(query);
            
            assertEquals(org.xbill.DNS.Rcode.SERVFAIL, response.getHeader().getRcode(),
                "Upstream failure must return SERVFAIL, not NXDOMAIN");
        } finally {
            srv.stop();
        }
    }
}
