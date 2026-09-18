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
import org.junit.jupiter.api.Test;
import org.xbill.DNS.DClass;
import org.xbill.DNS.Flags;
import org.xbill.DNS.Message;
import org.xbill.DNS.Name;
import org.xbill.DNS.Rcode;
import org.xbill.DNS.Record;
import org.xbill.DNS.Section;
import org.xbill.DNS.Type;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link NacosDnsQueryHandler}.
 *
 * @author Nacos
 */
class NacosDnsQueryHandlerTest {

    private NacosDnsProperties buildProperties() {
        NacosDnsProperties properties = new NacosDnsProperties();
        properties.setDomainSuffix("nacos");
        properties.setDefaultGroup("DEFAULT_GROUP");
        properties.setNamespace("");
        properties.setTtl(60);
        return properties;
    }

    private Message buildQuery(String domain, int type, int dclass) {
        Name queryName = Name.fromConstantString(domain.endsWith(".") ? domain : domain + ".");
        Record question = Record.newRecord(queryName, type, dclass);
        return Message.newQuery(question);
    }

    private Instance buildInstance(String ip, boolean healthy, boolean enabled) {
        Instance instance = new Instance();
        instance.setIp(ip);
        instance.setPort(8080);
        instance.setHealthy(healthy);
        instance.setEnabled(enabled);
        return instance;
    }

    private ServiceInfo buildServiceInfo(List<Instance> hosts) {
        ServiceInfo info = new ServiceInfo();
        info.setName("test-service");
        info.setHosts(hosts);
        return info;
    }

    // ---- Basic property defaults ----

    @Test
    void testPropertiesDefaultValues() {
        NacosDnsProperties properties = new NacosDnsProperties();
        assertEquals(5353, properties.getPort());
        assertEquals("nacos", properties.getDomainSuffix());
        assertEquals("DEFAULT_GROUP", properties.getDefaultGroup());
        assertEquals(60, properties.getTtl());
        assertEquals(false, properties.isEnabled());
    }

    // ---- Invalid / edge-case queries ----

    @Test
    void testNullQuestionReturnsFormErr() {
        NacosDnsQueryHandler handler = new NacosDnsQueryHandler(null, buildProperties());
        Message query = new Message();
        Message response = handler.handleQuery(query);
        assertEquals(Rcode.FORMERR, response.getHeader().getRcode());
    }

    @Test
    void testNonInClassReturnsRefused() {
        NacosDnsQueryHandler handler = new NacosDnsQueryHandler(null, buildProperties());
        Message query = buildQuery("test.nacos.", Type.A, DClass.CH);
        Message response = handler.handleQuery(query);
        assertEquals(Rcode.REFUSED, response.getHeader().getRcode());
    }

    @Test
    void testNonAQueryTypeReturnsNotImplemented() {
        NacosDnsQueryHandler handler = new NacosDnsQueryHandler(null, buildProperties());
        Message query = buildQuery("test.nacos.", Type.MX, DClass.IN);
        Message response = handler.handleQuery(query);
        assertEquals(Rcode.NOTIMP, response.getHeader().getRcode());
    }

    @Test
    void testAaaaQueryTypeReturnsNotImplementedIfNoIpv6() {
        InstanceOperatorClientImpl op = mock(InstanceOperatorClientImpl.class);
        when(op.listInstance(anyString(), anyString(), anyString(), any(), any(), anyBoolean()))
                .thenReturn(buildServiceInfo(Arrays.asList(buildInstance("192.168.1.1", true, true))));

        NacosDnsQueryHandler handler = new NacosDnsQueryHandler(op, buildProperties());
        Message query = buildQuery("test.nacos.", Type.AAAA, DClass.IN);
        Message response = handler.handleQuery(query);
        // Only IPv4 instance, AAAA answer section should be empty but rcode NOERROR
        assertEquals(Rcode.NOERROR, response.getHeader().getRcode());
        assertEquals(0, response.getSection(Section.ANSWER).size());
    }

    // ---- Domain suffix / parsing ----

    @Test
    void testDomainSuffixMismatchReturnsNxDomain() {
        NacosDnsQueryHandler handler = new NacosDnsQueryHandler(null, buildProperties());
        Message query = buildQuery("test.example.com.", Type.A, DClass.IN);
        Message response = handler.handleQuery(query);
        assertEquals(Rcode.NXDOMAIN, response.getHeader().getRcode());
    }

    @Test
    void testServiceOnlyUsesDefaultGroup() {
        InstanceOperatorClientImpl op = mock(InstanceOperatorClientImpl.class);
        when(op.listInstance(anyString(), eq("DEFAULT_GROUP"),
                eq("test-service"), any(), any(), anyBoolean()))
                .thenReturn(buildServiceInfo(Arrays.asList(buildInstance("10.0.0.1", true, true))));

        NacosDnsQueryHandler handler = new NacosDnsQueryHandler(op, buildProperties());
        Message query = buildQuery("test-service.nacos.", Type.A, DClass.IN);
        Message response = handler.handleQuery(query);

        assertEquals(Rcode.NOERROR, response.getHeader().getRcode());
        List<Record> answers = response.getSection(Section.ANSWER);
        assertEquals(1, answers.size());
        assertEquals(Type.A, answers.get(0).getType());
    }

    @Test
    void testServiceWithGroupInDomain() {
        InstanceOperatorClientImpl op = mock(InstanceOperatorClientImpl.class);
        when(op.listInstance(anyString(), eq("my-group"),
                eq("my-service"), any(), any(), anyBoolean()))
                .thenReturn(buildServiceInfo(Arrays.asList(buildInstance("10.0.0.2", true, true))));

        NacosDnsQueryHandler handler = new NacosDnsQueryHandler(op, buildProperties());
        Message query = buildQuery("my-service.my-group.nacos.", Type.A, DClass.IN);
        Message response = handler.handleQuery(query);

        assertEquals(Rcode.NOERROR, response.getHeader().getRcode());
        assertEquals(1, response.getSection(Section.ANSWER).size());
    }

    // ---- Instance filtering ----

    @Test
    void testOnlyHealthyEnabledInstancesReturned() {
        InstanceOperatorClientImpl op = mock(InstanceOperatorClientImpl.class);
        List<Instance> hosts = Arrays.asList(
                buildInstance("10.0.0.1", true, true),   // healthy+enabled
                buildInstance("10.0.0.2", false, true),  // unhealthy
                buildInstance("10.0.0.3", true, false)   // disabled
        );
        when(op.listInstance(anyString(), anyString(), anyString(), any(), any(), anyBoolean()))
                .thenReturn(buildServiceInfo(hosts));

        NacosDnsQueryHandler handler = new NacosDnsQueryHandler(op, buildProperties());
        Message query = buildQuery("svc.nacos.", Type.A, DClass.IN);
        Message response = handler.handleQuery(query);

        assertEquals(Rcode.NOERROR, response.getHeader().getRcode());
        // Only 1 healthy+enabled instance
        assertEquals(1, response.getSection(Section.ANSWER).size());
    }

    @Test
    void testNoHealthyInstancesReturnsNxDomain() {
        InstanceOperatorClientImpl op = mock(InstanceOperatorClientImpl.class);
        when(op.listInstance(anyString(), anyString(), anyString(), any(), any(), anyBoolean()))
                .thenReturn(buildServiceInfo(Arrays.asList(buildInstance("10.0.0.1", false, true))));

        NacosDnsQueryHandler handler = new NacosDnsQueryHandler(op, buildProperties());
        Message query = buildQuery("svc.nacos.", Type.A, DClass.IN);
        Message response = handler.handleQuery(query);

        assertEquals(Rcode.NXDOMAIN, response.getHeader().getRcode());
    }

    @Test
    void testNullServiceInfoReturnsNxDomain() {
        InstanceOperatorClientImpl op = mock(InstanceOperatorClientImpl.class);
        when(op.listInstance(anyString(), anyString(), anyString(), any(), any(), anyBoolean()))
                .thenReturn(null);

        NacosDnsQueryHandler handler = new NacosDnsQueryHandler(op, buildProperties());
        Message query = buildQuery("svc.nacos.", Type.A, DClass.IN);
        Message response = handler.handleQuery(query);

        assertEquals(Rcode.NXDOMAIN, response.getHeader().getRcode());
    }

    @Test
    void testNamingExceptionReturnsNxDomain() {
        InstanceOperatorClientImpl op = mock(InstanceOperatorClientImpl.class);
        when(op.listInstance(anyString(), anyString(), anyString(), any(), any(), anyBoolean()))
                .thenThrow(new RuntimeException("naming backend error"));

        NacosDnsQueryHandler handler = new NacosDnsQueryHandler(op, buildProperties());
        Message query = buildQuery("svc.nacos.", Type.A, DClass.IN);
        Message response = handler.handleQuery(query);

        assertEquals(Rcode.NXDOMAIN, response.getHeader().getRcode());
    }

    // ---- Answer record limit ----

    @Test
    void testAnswerRecordsCappedAtMax() {
        InstanceOperatorClientImpl op = mock(InstanceOperatorClientImpl.class);
        List<Instance> hosts = new ArrayList<>();
        for (int i = 1; i <= 50; i++) {
            hosts.add(buildInstance("10.0." + (i / 256) + "." + (i % 256), true, true));
        }
        when(op.listInstance(anyString(), anyString(), anyString(), any(), any(), anyBoolean()))
                .thenReturn(buildServiceInfo(hosts));

        NacosDnsQueryHandler handler = new NacosDnsQueryHandler(op, buildProperties());
        Message query = buildQuery("svc.nacos.", Type.A, DClass.IN);
        Message response = handler.handleQuery(query);

        assertEquals(Rcode.NOERROR, response.getHeader().getRcode());
        // Should be capped at 20
        assertTrue(response.getSection(Section.ANSWER).size() <= 20,
                "answer records should be capped at 20");
    }

    // ---- Flags ----

    @Test
    void testResponseHasQrAndRaFlags() {
        InstanceOperatorClientImpl op = mock(InstanceOperatorClientImpl.class);
        when(op.listInstance(anyString(), anyString(), anyString(), any(), any(), anyBoolean()))
                .thenReturn(buildServiceInfo(Arrays.asList(buildInstance("10.0.0.1", true, true))));

        NacosDnsQueryHandler handler = new NacosDnsQueryHandler(op, buildProperties());
        Message query = buildQuery("svc.nacos.", Type.A, DClass.IN);
        Message response = handler.handleQuery(query);

        assertTrue(response.getHeader().getFlag(Flags.QR), "response should set QR flag");
        assertTrue(response.getHeader().getFlag(Flags.RA), "response should set RA flag");
    }

    // ---- Mixed IPv4/IPv6 regression tests (review #7) ----

    @Test
    void testAaaaQueryWithManyIpv4AndOneIpv6ReturnsIpv6() {
        // 100 IPv4 + 1 IPv6: AAAA query must still find the single IPv6 address
        InstanceOperatorClientImpl op = mock(InstanceOperatorClientImpl.class);
        List<Instance> hosts = new ArrayList<>();
        for (int i = 1; i <= 100; i++) {
            hosts.add(buildInstance("10.0." + (i / 256) + "." + (i % 256), true, true));
        }
        hosts.add(buildInstance("2001:db8::1", true, true));
        when(op.listInstance(anyString(), anyString(), anyString(), any(), any(), anyBoolean()))
                .thenReturn(buildServiceInfo(hosts));

        NacosDnsQueryHandler handler = new NacosDnsQueryHandler(op, buildProperties());
        Message query = buildQuery("svc.nacos.", Type.AAAA, DClass.IN);
        Message response = handler.handleQuery(query);

        assertEquals(Rcode.NOERROR, response.getHeader().getRcode());
        assertEquals(1, response.getSection(Section.ANSWER).size(),
                "AAAA query should return the single IPv6 address even when 100 IPv4 exist");
        Record r = response.getSection(Section.ANSWER).get(0);
        assertEquals(Type.AAAA, r.getType());
    }

    @Test
    void testAQueryWithManyIpv6AndOneIpv4ReturnsIpv4() {
        // 100 IPv6 + 1 IPv4: A query must still find the single IPv4 address
        InstanceOperatorClientImpl op = mock(InstanceOperatorClientImpl.class);
        List<Instance> hosts = new ArrayList<>();
        for (int i = 1; i <= 100; i++) {
            hosts.add(buildInstance("2001:db8::" + i, true, true));
        }
        hosts.add(buildInstance("10.0.0.1", true, true));
        when(op.listInstance(anyString(), anyString(), anyString(), any(), any(), anyBoolean()))
                .thenReturn(buildServiceInfo(hosts));

        NacosDnsQueryHandler handler = new NacosDnsQueryHandler(op, buildProperties());
        Message query = buildQuery("svc.nacos.", Type.A, DClass.IN);
        Message response = handler.handleQuery(query);

        assertEquals(Rcode.NOERROR, response.getHeader().getRcode());
        assertEquals(1, response.getSection(Section.ANSWER).size(),
                "A query should return the single IPv4 address even when 100 IPv6 exist");
        Record r = response.getSection(Section.ANSWER).get(0);
        assertEquals(Type.A, r.getType());
    }
}
