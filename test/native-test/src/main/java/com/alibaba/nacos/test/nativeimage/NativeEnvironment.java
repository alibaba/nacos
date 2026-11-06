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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A database in a container and a Server in front of it, with the credentials that the
 * integration suites need. It replaces what {@code native-ci.sh} did with {@code docker run}, the
 * service container of the workflow and the shell scripts around them, so that every developer
 * can start the same environment from a test or from an IDE.
 *
 * @author Nacos
 */
public final class NativeEnvironment implements AutoCloseable {

    /**
     * The values of {@code .github/workflows/it-new.yml}, which the AI integration tests are
     * written for.
     */
    private static final int RAD_CAPACITY = 3;

    private final String name;

    private final NativeDatabase database;

    private final NativeCredentials credentials = NativeCredentials.create();

    private final Map<String, String> extraProperties;

    private final List<Path> plugins;

    private NativeServer server;

    private NativeEnvironment(String name, NativeDatabase database,
            Map<String, String> extraProperties, List<Path> plugins) {
        this.name = name;
        this.database = database;
        this.extraProperties = extraProperties;
        this.plugins = plugins;
    }

    /**
     * Starts the description of an environment.
     *
     * @param name a directory name for the homes and the logs
     * @return a builder, with the database that the settings name
     */
    public static Builder builder(String name) {
        return new Builder(name);
    }

    /**
     * The description of an environment.
     */
    public static final class Builder {

        private final String name;

        private String platform = NativeTestSettings.database();

        private final Map<String, String> properties = new LinkedHashMap<>();

        private final List<Path> plugins = new ArrayList<>();

        private Builder(String name) {
            this.name = name;
        }

        /**
         * Selects the database.
         *
         * @param value {@code mysql} or {@code postgresql}
         * @return this builder
         */
        public Builder platform(String value) {
            this.platform = value;
            return this;
        }

        /**
         * Adds a property of {@code conf/application.properties}.
         *
         * @param key   the key
         * @param value the value
         * @return this builder
         */
        public Builder property(String key, String value) {
            properties.put(key, value);
            return this;
        }

        /**
         * Adds properties of {@code conf/application.properties}.
         *
         * @param more the properties
         * @return this builder
         */
        public Builder properties(Map<String, String> more) {
            properties.putAll(more);
            return this;
        }

        /**
         * Adds a plugin jar, see {@link NativeServer.Builder#plugin(Path)}.
         *
         * @param jar the jar
         * @return this builder
         */
        public Builder plugin(Path jar) {
            plugins.add(jar);
            return this;
        }

        /**
         * Creates the environment, which is not started yet.
         *
         * @return the environment
         */
        public NativeEnvironment build() {
            return new NativeEnvironment(name, NativeDatabase.of(platform), properties, plugins);
        }
    }

    /**
     * Prepares an environment with the database that the settings name.
     *
     * @param name a directory name for the homes and the logs
     * @return an environment that is not started yet
     */
    public static NativeEnvironment create(String name) {
        return create(name, NativeTestSettings.database(), Map.of());
    }

    /**
     * Prepares an environment.
     *
     * @param name            a directory name for the homes and the logs
     * @param platform        {@code mysql} or {@code postgresql}
     * @param extraProperties more properties of {@code conf/application.properties}
     * @return an environment that is not started yet
     */
    public static NativeEnvironment create(String name, String platform,
            Map<String, String> extraProperties) {
        return builder(name).platform(platform).properties(extraProperties).build();
    }

    /**
     * Starts the database and the Server, sets the password of the administrator and prepares the
     * identities of the suites.
     *
     * @return this environment
     */
    public NativeEnvironment start() {
        try {
            database.start();
            NativeServer.Builder description = NativeServer.builder(name)
                    .properties(serverProperties());
            plugins.forEach(description::plugin);
            server = description.prepare();
            server.start();
            // MCP lifecycle APIs are unavailable until the background cutover is persisted, as in
            // it-new.yml; the log line tells that it happened.
            Duration markerTimeout = NativeTestSettings.scaled(Duration.ofSeconds(120));
            if (!server.awaitLog("Persisted permanent MCP LIFECYCLE_MANAGED marker",
                    markerTimeout)) {
                System.err.println("WARNING: the MCP LIFECYCLE_MANAGED marker was not logged "
                        + "within " + markerTimeout.toSeconds() + " s");
            }
            NativeIdentities identities = identities();
            identities.bootstrapAdmin();
            identities.prepare();
        } catch (RuntimeException | Error e) {
            close();
            throw e;
        }
        return this;
    }

    /**
     * The Server.
     *
     * @return the Server, after {@link #start()}
     */
    public NativeServer server() {
        return server;
    }

    /**
     * The credentials of the Server.
     *
     * @return the credentials
     */
    public NativeCredentials credentials() {
        return credentials;
    }

    /**
     * The helper that logs in as the users of the credentials.
     *
     * @return a helper for the Server
     */
    public NativeIdentities identities() {
        return new NativeIdentities(server.apiUrl(), credentials);
    }

    /**
     * Publishes what the integration suites read: the credentials and the capacities that the
     * Server was configured with.
     */
    public void exportSystemProperties() {
        credentials.exportSystemProperties();
        System.setProperty("nacos.test.auth.anonymous-ai.enabled", "true");
        System.setProperty("nacos.test.auth.anonymous-ai.source", "STATIC");
        for (String side : new String[] {"server", "client"}) {
            System.setProperty("nacos.agent.it." + side + ".publication.capacity",
                    String.valueOf(RAD_CAPACITY));
        }
        System.setProperty("nacos.agent.it.server.watch.capacity", String.valueOf(RAD_CAPACITY));
        System.setProperty("nacos.agent.it.server.http.watch.waiter.capacity",
                String.valueOf(RAD_CAPACITY));
        System.setProperty("nacos.agent.it.client.subscription.capacity",
                String.valueOf(RAD_CAPACITY));
    }

    @Override
    public void close() {
        if (server != null) {
            server.close();
        }
        try {
            Path directory = NativeTestSettings.workDirectory().resolve(name);
            Files.createDirectories(directory);
            Files.writeString(directory.resolve("database.log"), database.logs(),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            database.close();
        }
    }

    private Map<String, String> serverProperties() {
        Map<String, String> properties = new LinkedHashMap<>(database.serverProperties());
        properties.putAll(testProfile(credentials));
        properties.putAll(NativeTestSettings.serverProperties());
        properties.putAll(extraProperties);
        return properties;
    }

    /**
     * The runtime configuration that the integration tests are written for, together with the
     * secrets of the credentials.
     *
     * @param credentials the credentials of the Server
     * @return properties of {@code conf/application.properties}
     */
    static Map<String, String> testProfile(NativeCredentials credentials) {
        Map<String, String> properties = new LinkedHashMap<>(credentials.serverProperties());
        properties.put("nacos.plugin.auth.nacos.anonymous.ai.enabled", "true");
        properties.put("nacos.ai.rad.capacity.publication.max-publications-per-client",
                String.valueOf(RAD_CAPACITY));
        properties.put("nacos.ai.rad.capacity.watch.max-per-client", String.valueOf(RAD_CAPACITY));
        properties.put("nacos.ai.rad.capacity.watch.http.max-active-requests-per-node",
                String.valueOf(RAD_CAPACITY));
        // The two reconciliation intervals make the background MCP cutover finish within seconds.
        properties.put("nacos.ai.mcp.resource.reconciliation.interval-seconds", "1");
        properties.put("nacos.ai.resource.search.index.reconcile.interval-seconds", "1");
        // JRaft takes the first snapshot of each group at a random time within 30 minutes after the
        // start, which a test reaches only sometimes. Every 30 seconds makes the code that saves a
        // snapshot, for example the Hessian serialization of the persistent naming data, run in
        // every test, and gives a restart a snapshot to load.
        properties.put("nacos.core.protocol.raft.data.snapshot_interval_secs", "30");
        return properties;
    }
}
