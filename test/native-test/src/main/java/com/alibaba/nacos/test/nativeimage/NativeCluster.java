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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * A cluster of Servers on one host that share a database: node {@code i} listens on API port
 * {@code 8848 + 10 * i} and console port {@code 8080 + 10 * i}, so the gRPC ports ({@code +1000}
 * and {@code +1001}) and the Raft port ({@code -1000}) of the nodes do not meet. The members are
 * listed in {@code conf/cluster.conf}. Embedded storage cannot be used by a Native cluster, so the
 * database is an external one.
 *
 * @author Nacos
 */
public final class NativeCluster implements AutoCloseable {

    private static final int PORT_STEP = 10;

    private final String name;

    private final NativeDatabase database;

    private final NativeCredentials credentials = NativeCredentials.create();

    private final int size;

    private final Map<String, String> extraProperties;

    private final List<NativeServer> servers = new ArrayList<>();

    private NativeCluster(String name, NativeDatabase database, int size,
            Map<String, String> extraProperties) {
        this.name = name;
        this.database = database;
        this.size = size;
        this.extraProperties = extraProperties;
    }

    /**
     * Prepares a cluster.
     *
     * @param name            a directory name for the homes and the logs
     * @param platform        {@code mysql} or {@code postgresql}
     * @param size            the number of nodes
     * @param extraProperties more properties of {@code conf/application.properties} of every node
     * @return a cluster that is not started yet
     */
    public static NativeCluster create(String name, String platform, int size,
            Map<String, String> extraProperties) {
        return new NativeCluster(name, NativeDatabase.of(platform), size, extraProperties);
    }

    /**
     * Starts the database and all nodes at once, and sets the password of the administrator.
     *
     * @return this cluster
     */
    public NativeCluster start() {
        try {
            database.start();
            StringBuilder members = new StringBuilder();
            for (int index = 0; index < size; index++) {
                members.append("127.0.0.1:").append(apiPort(index)).append('\n');
            }
            for (int index = 0; index < size; index++) {
                Map<String, String> properties = new LinkedHashMap<>(database.serverProperties());
                properties.putAll(NativeEnvironment.testProfile(credentials));
                properties.putAll(NativeTestSettings.serverProperties());
                properties.put("nacos.inetutils.ip-address", "127.0.0.1");
                properties.put("nacos.core.member.lookup.type", "file");
                properties.putAll(extraProperties);
                servers.add(NativeServer.builder(name + "-node" + index).properties(properties)
                        .ports(apiPort(index), consolePort(index)).standalone(false)
                        .file("conf/cluster.conf", members.toString()).prepare());
            }
            List<CompletableFuture<Void>> starting = new ArrayList<>();
            for (NativeServer server : servers) {
                starting.add(CompletableFuture.runAsync(server::start));
            }
            try {
                CompletableFuture.allOf(starting.toArray(new CompletableFuture<?>[0])).join();
            } catch (CompletionException e) {
                throw e.getCause() instanceof RuntimeException ? (RuntimeException) e.getCause()
                        : e;
            }
            identities(0).bootstrapAdmin();
        } catch (RuntimeException | Error e) {
            close();
            throw e;
        }
        return this;
    }

    /**
     * A node.
     *
     * @param index the index, from 0
     * @return the Server
     */
    public NativeServer node(int index) {
        return servers.get(index);
    }

    /**
     * The number of nodes.
     *
     * @return the number of nodes
     */
    public int size() {
        return size;
    }

    /**
     * The helper that logs in to a node as the users of the credentials.
     *
     * @param index the index of the node, from 0
     * @return a helper for the node
     */
    public NativeIdentities identities(int index) {
        return new NativeIdentities(servers.get(index).apiUrl(), credentials);
    }

    /**
     * The credentials of the cluster.
     *
     * @return the credentials
     */
    public NativeCredentials credentials() {
        return credentials;
    }

    @Override
    public void close() {
        for (NativeServer server : servers) {
            server.close();
        }
        database.close();
    }

    private static int apiPort(int index) {
        return NativeServer.API_PORT + PORT_STEP * index;
    }

    private static int consolePort(int index) {
        return NativeServer.CONSOLE_PORT + PORT_STEP * index;
    }
}
