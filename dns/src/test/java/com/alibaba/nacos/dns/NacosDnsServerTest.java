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
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
     * Queries with no question section (qdcount=0) must return FORMERR,
     * not fall through to the forwarding path.
     */
    @Test
    void testQueryWithoutQuestionReturnsFormerr() throws Exception {
        mockInstances(1);
        server.start();
        int port = getListeningPort();
        Thread.sleep(200);
        
        // Build a message with no question section
        Message query = new Message();
        query.getHeader().setOpcode(0);
        byte[] queryData = query.toWire();
        
        DatagramSocket sock = new DatagramSocket();
        sock.setSoTimeout(3000);
        sock.send(new java.net.DatagramPacket(queryData, queryData.length,
            java.net.InetAddress.getByName("127.0.0.1"), port));
        byte[] buf = new byte[512];
        java.net.DatagramPacket resp = new java.net.DatagramPacket(buf, buf.length);
        sock.receive(resp);
        sock.close();
        
        Message response = new Message(java.util.Arrays.copyOf(resp.getData(), resp.getLength()));
        assertEquals(org.xbill.DNS.Rcode.FORMERR, response.getHeader().getRcode(),
            "Query without question must return FORMERR");
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
    
    /**
     * Forwarding success must relay the upstream Message back to the client,
     * preserving transaction ID and question section.
     */
    @Test
    void testForwardSuccessRelaysUpstreamResponse() throws Exception {
        NacosDnsProperties props = new NacosDnsProperties();
        props.setEnabled(true);
        props.setPort(0);
        props.setDomainSuffix("nacos");
        props.setForwardEnabled(true);
        props.setForwardServers(List.of("127.0.0.1:9999"));
        
        MeterRegistry registry = new SimpleMeterRegistry();
        NacosDnsMetrics metrics = new NacosDnsMetrics(registry);
        
        // Stub forwarder that returns a crafted NOERROR response with an A record
        NacosDnsForwarder successForwarder = new NacosDnsForwarder(props, metrics) {
            
            @Override
            public NacosDnsForwarder.ForwardResult forward(Message query) {
                try {
                    Message upstream = new Message(query.getHeader().getID());
                    upstream.getHeader().setFlag(Flags.QR);
                    Record q = query.getQuestion();
                    upstream.addRecord(q, Section.QUESTION);
                    Record answer = new ARecord(q.getName(), DClass.IN, 300,
                        java.net.InetAddress.getByName("93.184.216.34"));
                    upstream.addRecord(answer, Section.ANSWER);
                    return new NacosDnsForwarder.ForwardResult(
                        NacosDnsForwarder.ForwardStatus.SUCCESS, upstream, "127.0.0.1:9999");
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };
        
        NacosDnsQueryHandler handler = new NacosDnsQueryHandler(instanceOperator, props, metrics);
        NacosDnsServer srv = new NacosDnsServer(props, handler, successForwarder, metrics);
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
            
            Name queryName = Name.fromConstantString("www.example.com.");
            Record question = Record.newRecord(queryName, Type.A, DClass.IN);
            Message query = Message.newQuery(question);
            int queryId = query.getHeader().getID();
            Message response = resolver.send(query);
            
            assertEquals(org.xbill.DNS.Rcode.NOERROR, response.getHeader().getRcode(),
                "Forwarded success must return NOERROR");
            assertEquals(queryId, response.getHeader().getID(),
                "Transaction ID must be preserved");
            List<Record> answers = response.getSection(Section.ANSWER);
            assertEquals(1, answers.size(), "Upstream A record must be relayed");
            assertEquals("93.184.216.34",
                ((ARecord) answers.get(0)).getAddress().getHostAddress());
            assertFalse(response.getHeader().getFlag(Flags.RA),
                "Conditional forwarder must not set RA");
        } finally {
            srv.stop();
        }
    }
    
    /**
     * End-to-end forwarding test with a real stub UDP upstream.
     * The stub upstream replies with a canned NOERROR+A response; the Nacos DNS
     * server must relay it back to the client with the correct transaction ID,
     * question section, and answer record. This exercises the full path:
     * client UDP -> NacosDnsServer -> forwardWorkerPool -> SimpleResolver ->
     * stub UDP upstream -> SimpleResolver -> NacosDnsServer -> client.
     */
    @Test
    void testForwardSuccessWithRealUdpUpstream() throws Exception {
        // Start a stub DNS upstream on an ephemeral port
        DatagramSocket upstreamSocket = new DatagramSocket(0, InetAddress.getByName("127.0.0.1"));
        int upstreamPort = upstreamSocket.getLocalPort();
        AtomicInteger upstreamQueries = new AtomicInteger(0);
        Thread upstreamThread = new Thread(() -> {
            byte[] buf = new byte[512];
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    upstreamSocket.receive(packet);
                    upstreamQueries.incrementAndGet();
                    Message query = new Message(
                        java.util.Arrays.copyOf(packet.getData(), packet.getLength()));
                    // Build canned response: same ID, NOERROR, A record 203.0.113.10
                    Message response = new Message(query.getHeader().getID());
                    response.getHeader().setFlag(Flags.QR);
                    response.getHeader().setFlag(Flags.RA);
                    Record q = query.getQuestion();
                    if (q != null) {
                        response.addRecord(q, Section.QUESTION);
                        Record answer = new ARecord(q.getName(), DClass.IN, 300,
                            InetAddress.getByName("203.0.113.10"));
                        response.addRecord(answer, Section.ANSWER);
                    }
                    byte[] respData = response.toWire();
                    DatagramPacket resp = new DatagramPacket(respData, respData.length,
                        packet.getAddress(), packet.getPort());
                    upstreamSocket.send(resp);
                } catch (Exception e) {
                    if (!Thread.currentThread().isInterrupted()) {
                        break;
                    }
                }
            }
        }, "stub-dns-upstream");
        upstreamThread.setDaemon(true);
        upstreamThread.start();
        
        // Configure Nacos DNS server to forward to the stub upstream
        NacosDnsProperties props = new NacosDnsProperties();
        props.setEnabled(true);
        props.setPort(0);
        props.setDomainSuffix("nacos");
        props.setForwardEnabled(true);
        props.setForwardServers(List.of("127.0.0.1:" + upstreamPort));
        props.setForwardTimeoutMs(2000);
        
        MeterRegistry registry = new SimpleMeterRegistry();
        NacosDnsMetrics metrics = new NacosDnsMetrics(registry);
        NacosDnsForwarder forwarder = new NacosDnsForwarder(props, metrics);
        NacosDnsQueryHandler handler = new NacosDnsQueryHandler(instanceOperator, props, metrics);
        NacosDnsServer srv = new NacosDnsServer(props, handler, forwarder, metrics);
        
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
            
            // Query the Nacos DNS server for a non-Nacos domain
            SimpleResolver resolver = new SimpleResolver("127.0.0.1");
            resolver.setPort(port);
            resolver.setTCP(false);
            resolver.setTimeout(5);
            
            Name queryName = Name.fromConstantString("www.example.com.");
            Record question = Record.newRecord(queryName, Type.A, DClass.IN);
            Message query = Message.newQuery(question);
            int queryId = query.getHeader().getID();
            Message response = resolver.send(query);
            
            // Verify the response was relayed from the stub upstream
            assertEquals(org.xbill.DNS.Rcode.NOERROR, response.getHeader().getRcode(),
                "Forwarded success must return NOERROR");
            assertEquals(queryId, response.getHeader().getID(),
                "Transaction ID must be preserved end-to-end");
            List<Record> answers = response.getSection(Section.ANSWER);
            assertEquals(1, answers.size(), "Stub upstream A record must be relayed");
            assertEquals("203.0.113.10",
                ((ARecord) answers.get(0)).getAddress().getHostAddress(),
                "Relayed answer must match stub upstream response");
            assertEquals(1, upstreamQueries.get(),
                "Stub upstream should have received exactly one query");
        } finally {
            srv.stop();
            upstreamThread.interrupt();
            upstreamSocket.close();
        }
    }
    
    @Test
    void testLocalQueryNotBlockedByPendingForwarding() throws Exception {
        // When forwarding queries are pending (slow upstream), local Nacos-suffix
        // queries must still complete promptly on the UDP worker pool. The UDP
        // worker must not block on forwarder.future.get().
        NacosDnsProperties props = new NacosDnsProperties();
        props.setEnabled(true);
        props.setPort(0);
        props.setDomainSuffix("nacos");
        props.setForwardEnabled(true);
        props.setForwardServers(List.of("127.0.0.1:9999"));
        props.setForwardTimeoutMs(5000);
        
        MeterRegistry registry = new SimpleMeterRegistry();
        NacosDnsMetrics metrics = new NacosDnsMetrics(registry);
        
        // Blocking forwarder: holds the forward pool threads until latch released
        java.util.concurrent.CountDownLatch blockLatch =
            new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch enteredLatch =
            new java.util.concurrent.CountDownLatch(4);
        NacosDnsForwarder blockingForwarder = new NacosDnsForwarder(props, metrics) {
            
            @Override
            public ForwardResult forward(Message query) {
                enteredLatch.countDown();
                try {
                    blockLatch.await(10, java.util.concurrent.TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return new ForwardResult(ForwardStatus.UPSTREAM_FAILURE, null, null);
            }
        };
        
        Instance inst = new Instance();
        inst.setIp("10.0.0.99");
        inst.setPort(8080);
        inst.setHealthy(true);
        inst.setEnabled(true);
        ServiceInfo si = new ServiceInfo();
        si.setName("localsvc");
        si.setGroupName("DEFAULT_GROUP");
        si.setHosts(List.of(inst));
        when(instanceOperator.listInstance(anyString(), anyString(), eq("localsvc"),
            any(), any(), anyBoolean())).thenReturn(si);
        
        NacosDnsQueryHandler handler = new NacosDnsQueryHandler(instanceOperator, props, metrics);
        NacosDnsServer srv = new NacosDnsServer(props, handler, blockingForwarder, metrics);
        
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
            final int serverPort = port;
            
            // Saturate the forward pool with 4 blocking forwarding queries
            for (int i = 0; i < 4; i++) {
                final int idx = i;
                new Thread(() -> {
                    try (DatagramSocket sock = new DatagramSocket()) {
                        sock.setSoTimeout(500);
                        Name fwdName = Name.fromConstantString("external" + idx + ".com.");
                        Message fwdQuery = Message.newQuery(
                            Record.newRecord(fwdName, Type.A, DClass.IN));
                        byte[] data = fwdQuery.toWire();
                        DatagramPacket pkt = new DatagramPacket(data, data.length,
                            InetAddress.getByName("127.0.0.1"), serverPort);
                        sock.send(pkt);
                        // Wait for response (will time out since forwarder is blocked)
                        byte[] buf = new byte[512];
                        DatagramPacket resp = new DatagramPacket(buf, buf.length);
                        try {
                            sock.receive(resp);
                        } catch (Exception ignored) {
                            // expected timeout
                        }
                    } catch (Exception ignored) {
                        // ignore
                    }
                }, "fwd-client-" + i).start();
            }
            // Wait until all 4 forward pool threads are occupied
            assertTrue(enteredLatch.await(10, java.util.concurrent.TimeUnit.SECONDS),
                "All 4 forward pool threads should be occupied");
            
            // Local query must complete promptly despite forward pool being saturated
            SimpleResolver localResolver = new SimpleResolver("127.0.0.1");
            localResolver.setPort(port);
            localResolver.setTCP(false);
            localResolver.setTimeout(3);
            long start = System.currentTimeMillis();
            Name localName = Name.fromConstantString("localsvc.nacos.");
            Message localQuery = Message.newQuery(
                Record.newRecord(localName, Type.A, DClass.IN));
            Message localResponse = localResolver.send(localQuery);
            long elapsed = System.currentTimeMillis() - start;
            
            assertEquals(org.xbill.DNS.Rcode.NOERROR, localResponse.getHeader().getRcode(),
                "Local query must succeed while forward pool is saturated");
            List<Record> answers = localResponse.getSection(Section.ANSWER);
            assertEquals(1, answers.size());
            assertEquals("10.0.0.99",
                ((ARecord) answers.get(0)).getAddress().getHostAddress());
            assertTrue(elapsed < 3000,
                "Local query must complete promptly (<3s), took " + elapsed + "ms");
        } finally {
            blockLatch.countDown();
            srv.stop();
        }
    }
    
    /**
     * When upstreams are configured but temporarily unresolved (empty resolved
     * snapshot after a startup or refresh failure), the server must return
     * SERVFAIL on both UDP and TCP — not NXDOMAIN, which would poison client
     * negative caches for domains that may well exist.
     */
    @Test
    void testUnresolvedUpstreamsReturnServfailOnUdpAndTcp() throws Exception {
        NacosDnsProperties props = new NacosDnsProperties();
        props.setEnabled(true);
        props.setPort(0);
        props.setDomainSuffix("nacos");
        props.setForwardEnabled(true);
        props.setForwardServers(List.of("8.8.8.8"));
        props.setForwardTimeoutMs(500);
        
        MeterRegistry registry = new SimpleMeterRegistry();
        NacosDnsMetrics metrics = new NacosDnsMetrics(registry);
        NacosDnsForwarder forwarder = new NacosDnsForwarder(props, metrics);
        // Simulate a transient resolution failure: snapshot is empty while
        // originalSpecs still contains the configured upstream.
        Field serversField = NacosDnsForwarder.class.getDeclaredField("servers");
        serversField.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.concurrent.atomic.AtomicReference<List<Object>> snapshot =
            (java.util.concurrent.atomic.AtomicReference<List<Object>>) serversField.get(forwarder);
        snapshot.set(List.of());
        
        NacosDnsQueryHandler handler = new NacosDnsQueryHandler(instanceOperator, props, metrics);
        NacosDnsServer srv = new NacosDnsServer(props, handler, forwarder, metrics);
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
            
            Name queryName = Name.fromConstantString("www.example.com.");
            Record question = Record.newRecord(queryName, Type.A, DClass.IN);
            Message query = Message.newQuery(question);
            
            // UDP path
            SimpleResolver udpResolver = new SimpleResolver("127.0.0.1");
            udpResolver.setPort(port);
            udpResolver.setTCP(false);
            udpResolver.setTimeout(5);
            Message udpResponse = udpResolver.send(query);
            assertEquals(org.xbill.DNS.Rcode.SERVFAIL, udpResponse.getHeader().getRcode(),
                "UDP: unresolved upstreams must return SERVFAIL, not NXDOMAIN");
            
            // TCP path
            SimpleResolver tcpResolver = new SimpleResolver("127.0.0.1");
            tcpResolver.setPort(port);
            tcpResolver.setTCP(true);
            tcpResolver.setTimeout(5);
            Message tcpResponse = tcpResolver.send(query);
            assertEquals(org.xbill.DNS.Rcode.SERVFAIL, tcpResponse.getHeader().getRcode(),
                "TCP: unresolved upstreams must return SERVFAIL, not NXDOMAIN");
        } finally {
            srv.stop();
            forwarder.close();
        }
    }
}
