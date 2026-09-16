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

import java.lang.reflect.Field;
import java.net.DatagramSocket;
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
        NacosDnsQueryHandler queryHandler = new NacosDnsQueryHandler(instanceOperator, properties);
        server = new NacosDnsServer(properties, queryHandler);
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
        when(instanceOperator.listInstance(anyString(), anyString(), anyString(), any(), any(), anyBoolean()))
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
}
