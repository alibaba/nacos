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

package com.alibaba.nacos.test.nativeimage;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.xbill.DNS.ARecord;
import org.xbill.DNS.DClass;
import org.xbill.DNS.Message;
import org.xbill.DNS.Name;
import org.xbill.DNS.Rcode;
import org.xbill.DNS.Record;
import org.xbill.DNS.SRVRecord;
import org.xbill.DNS.Section;
import org.xbill.DNS.SimpleResolver;
import org.xbill.DNS.Type;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The DNS adapter: it answers A and SRV queries for a service of the registry over UDP and over TCP,
 * and NXDOMAIN for a name that it does not know.
 *
 * @author Nacos
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NativeDnsITCase {

    private static final int DNS_PORT = 15353;

    private static final String SERVICE = "dns-svc";

    private final NativeHttp http = new NativeHttp();

    private NativeEnvironment environment;

    @BeforeAll
    void startEnvironment() {
        environment = NativeEnvironment.builder("dns").property("nacos.naming.dns.enabled", "true")
                .property("nacos.naming.dns.port", String.valueOf(DNS_PORT))
                .property("nacos.naming.dns.bind-address", "127.0.0.1")
                .property("nacos.naming.dns.namespace", "public").build().start();
        String token = environment.identities().adminToken();
        NativeHttp.Response registered = http.form("POST", environment.server().apiUrl()
                + "/nacos/v3/client/ns/instance", token,
                Map.of("serviceName", SERVICE, "ip", "10.1.2.3", "port", "8080"));
        assertTrue(registered.ok(), registered.describe());
    }

    @AfterAll
    void stopEnvironment() {
        if (environment != null) {
            environment.close();
        }
    }

    @Test
    void answersAForAServiceOverUdp() throws IOException {
        assertAddress(false);
    }

    @Test
    void answersAForAServiceOverTcp() throws IOException {
        assertAddress(true);
    }

    @Test
    void answersSrvWithThePortOfTheInstance() throws IOException {
        Message answer = ask(SERVICE + ".nacos.", Type.SRV, true);
        assertEquals(Rcode.NOERROR, answer.getRcode());
        Record[] records = answer.getSectionArray(Section.ANSWER);
        assertTrue(records.length > 0, answer.toString());
        assertEquals(8080, ((SRVRecord) records[0]).getPort());
    }

    @Test
    void answersNxDomainForAnUnknownService() throws IOException {
        assertEquals(Rcode.NXDOMAIN, ask("no-such-service.nacos.", Type.A, false).getRcode());
    }

    private void assertAddress(boolean tcp) throws IOException {
        Message answer = ask(SERVICE + ".nacos.", Type.A, tcp);
        assertEquals(Rcode.NOERROR, answer.getRcode(), answer.toString());
        Record[] records = answer.getSectionArray(Section.ANSWER);
        assertTrue(records.length > 0, answer.toString());
        assertEquals("10.1.2.3", ((ARecord) records[0]).getAddress().getHostAddress());
    }

    private Message ask(String name, int type, boolean tcp) throws IOException {
        SimpleResolver resolver = new SimpleResolver(new InetSocketAddress("127.0.0.1", DNS_PORT));
        resolver.setTCP(tcp);
        resolver.setTimeout(Duration.ofSeconds(5));
        Message query = Message.newQuery(Record.newRecord(Name.fromString(name), type, DClass.IN));
        // The registry fills the service of the adapter with the first naming event.
        Message answer = resolver.send(query);
        for (int attempt = 0; attempt < 20 && answer.getRcode() == Rcode.NXDOMAIN
                && name.startsWith(SERVICE); attempt++) {
            sleep();
            answer = resolver.send(query);
        }
        return answer;
    }

    private static void sleep() {
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
