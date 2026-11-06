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
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What only a cluster does: three Servers that share a database find each other, the Distro
 * protocol hands ephemeral instances to the other nodes, the Raft groups replicate persistent
 * instances, and a node that was stopped rejoins and reads the log and the snapshot of its group.
 * The tests run in order, because the last one restarts a node and looks for the data of the
 * others. The Native executable has to be built for cluster mode ({@code
 * -Dnacos.native.standalone=false}).
 *
 * @author Nacos
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class NativeClusterITCase {

    private static final int NODES = 3;

    private static final Pattern UP = Pattern.compile("\"state\"\\s*:\\s*\"UP\"");

    private final NativeHttp http = new NativeHttp();

    private final String suffix = UUID.randomUUID().toString().substring(0, 8);

    private final Map<Integer, String> tokens = new ConcurrentHashMap<>();

    private NativeCluster cluster;

    @BeforeAll
    void startCluster() {
        cluster = NativeCluster.create("cluster", NativeTestSettings.database(), NODES, Map.of())
                .start();
    }

    @AfterAll
    void stopCluster() {
        if (cluster != null) {
            cluster.close();
        }
    }

    @Test
    @Order(1)
    void everyNodeSeesAllMembersUp() {
        for (int node = 0; node < NODES; node++) {
            int index = node;
            eventually("node " + index + " sees " + NODES + " members up", Duration.ofSeconds(120),
                    () -> {
                        NativeHttp.Response response = get(index,
                                "/nacos/v3/admin/core/cluster/node/list");
                        Matcher matcher = UP.matcher(response.body());
                        int up = 0;
                        while (matcher.find()) {
                            up++;
                        }
                        return response.ok() && up == NODES ? "" : response.describe();
                    });
        }
    }

    @Test
    @Order(2)
    void ephemeralInstanceReachesTheOtherNodes() {
        String service = "cluster-ephemeral-" + suffix;
        NativeHttp.Response registered = form(0, "POST", "/nacos/v3/client/ns/instance",
                Map.of("serviceName", service, "ip", "10.0.0.1", "port", "18081"));
        assertTrue(registered.ok(), registered.describe());
        for (int node = 1; node < NODES; node++) {
            int index = node;
            eventually("node " + index + " lists the ephemeral instance", Duration.ofSeconds(30),
                    () -> {
                        NativeHttp.Response response = get(index,
                                "/nacos/v3/client/ns/instance/list?serviceName=" + service);
                        return response.ok() && response.body().contains("18081") ? ""
                                : response.describe();
                    });
        }
    }

    @Test
    @Order(3)
    void persistentInstanceIsReplicatedThroughRaft() {
        String service = "cluster-persistent-" + suffix;
        createPersistentService(service);
        writeUntilAccepted("a persistent instance is registered", "POST",
                "/nacos/v3/admin/ns/instance", Map.of("serviceName", service, "groupName",
                        "DEFAULT_GROUP", "namespaceId", "public", "ip", "10.0.0.2", "port",
                        "18082", "ephemeral", "false", "metadata", "{\"zone\":\"z1\"}"));
        for (int node = 1; node < NODES; node++) {
            int index = node;
            eventually("node " + index + " lists the persistent instance", Duration.ofSeconds(60),
                    () -> persistentInstance(index, service));
        }
    }

    @Test
    @Order(4)
    void configIsSharedThroughTheDatabase() {
        String dataId = "cluster-config-" + suffix;
        NativeHttp.Response published = form(0, "POST", "/nacos/v3/admin/cs/config",
                Map.of("dataId", dataId, "groupName", "DEFAULT_GROUP", "content", "cluster=true",
                        "type", "properties"));
        assertTrue(published.ok(), published.describe());
        for (int node = 1; node < NODES; node++) {
            int index = node;
            eventually("node " + index + " reads the config", Duration.ofSeconds(30), () -> {
                NativeHttp.Response response = get(index, "/nacos/v3/admin/cs/config?dataId="
                        + dataId + "&groupName=DEFAULT_GROUP");
                return response.ok() && response.body().contains("cluster=true") ? ""
                        : response.describe();
            });
        }
    }

    @Test
    @Order(5)
    void aStoppedNodeRejoinsAndReadsTheDataOfTheOthers() {
        String service = "cluster-rejoin-" + suffix;
        createPersistentService(service);
        assertTrue(cluster.node(1).stop(), "Node 1 ignored SIGTERM for 60 seconds");

        // Written while node 1 is away: it has to catch up through the log or a snapshot. The
        // stopped node may have been the leader of the Raft group, so the write fails until the
        // other nodes have elected a new one.
        writeUntilAccepted("a persistent instance is registered while node 1 is away", "POST",
                "/nacos/v3/admin/ns/instance", Map.of("serviceName", service, "groupName",
                        "DEFAULT_GROUP", "namespaceId", "public", "ip", "10.0.0.3", "port",
                        "18083", "ephemeral", "false"));

        cluster.node(1).restart();
        eventually("node 1 sees all members up again", Duration.ofSeconds(120), () -> {
            NativeHttp.Response response = get(1, "/nacos/v3/admin/core/cluster/node/list");
            Matcher matcher = UP.matcher(response.body());
            int up = 0;
            while (matcher.find()) {
                up++;
            }
            return response.ok() && up == NODES ? "" : response.describe();
        });
        eventually("node 1 lists the instance written while it was away", Duration.ofSeconds(90),
                () -> persistentInstance(1, service, "18083"));
    }

    private void createPersistentService(String service) {
        writeUntilAccepted("a persistent service is created", "POST", "/nacos/v3/admin/ns/service",
                Map.of("serviceName", service, "groupName", "DEFAULT_GROUP", "namespaceId",
                        "public", "ephemeral", "false"));
    }

    /**
     * Sends a write to node 0 until it is accepted. A write to persistent data goes to the leader
     * of a Raft group, which the nodes may not have elected yet shortly after the start, or after
     * the leader was stopped.
     */
    private void writeUntilAccepted(String what, String method, String path,
            Map<String, String> form) {
        eventually(what, Duration.ofSeconds(90), () -> {
            NativeHttp.Response response = form(0, method, path, form);
            return response.ok() ? "" : response.describe();
        });
    }

    private String persistentInstance(int node, String service) {
        return persistentInstance(node, service, "18082");
    }

    private String persistentInstance(int node, String service, String port) {
        NativeHttp.Response response = get(node, "/nacos/v3/admin/ns/instance/list?serviceName="
                + service + "&groupName=DEFAULT_GROUP&namespaceId=public");
        return response.ok() && response.body().contains(port) ? "" : response.describe();
    }

    private NativeHttp.Response get(int node, String path) {
        return http.get(cluster.node(node).apiUrl() + path, token(node));
    }

    private NativeHttp.Response form(int node, String method, String path,
            Map<String, String> form) {
        return http.form(method, cluster.node(node).apiUrl() + path, token(node), form);
    }

    private String token(int node) {
        return tokens.computeIfAbsent(node, index -> cluster.identities(index).adminToken());
    }

    /**
     * Calls the check until it returns an empty string, which means it holds, or the time is up.
     */
    private static void eventually(String what, Duration usual, Supplier<String> check) {
        Duration timeout = NativeTestSettings.scaled(usual);
        long deadline = System.nanoTime() + timeout.toNanos();
        String last;
        do {
            last = check.get();
            if (last.isEmpty()) {
                return;
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        } while (System.nanoTime() < deadline);
        assertEquals("", last, what + " within " + timeout.toSeconds() + " seconds");
    }
}
