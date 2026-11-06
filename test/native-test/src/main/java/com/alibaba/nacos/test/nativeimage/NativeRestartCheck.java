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
import java.util.List;
import java.util.Map;

/**
 * Data that has to survive a restart of the Server. The integration suites never restart the
 * Server and create only a few kinds of data, so this check covers what they cannot reach:
 *
 * <ul>
 *   <li>a persistent service with a label selector and a persistent instance in a cluster with the
 *   MYSQL health checker. The Raft groups of the naming service write them into their log and into
 *   their snapshots and read both back when the Server starts. The Server answers the service
 *   detail with the selector and the checker, which Jackson writes.</li>
 *   <li>a naming switch that the Raft group replicates. The suites set switches in memory only.</li>
 *   <li>a configuration, which the datasource keeps.</li>
 * </ul>
 *
 * <p>A snapshot of the data has to exist before the restart, so that the Raft groups load a
 * snapshot and replay the log behind it. The environment asks JRaft for one every 30 seconds.
 * The check does not throw for a failed step: it returns one line per step, {@code PASS} or
 * {@code FAIL}, as {@code native-ci.sh} reported them.
 *
 * @author Nacos
 */
public final class NativeRestartCheck {

    private static final String GROUP = "DEFAULT_GROUP";

    private static final String SERVICE = "native-restart-service";

    private static final String CLUSTER = "DB";

    private static final String INSTANCE_PORT = "19306";

    private static final String DATA_ID = "native-restart-config";

    private static final String SWITCH_ENTRY = "defaultCacheMillis";

    private static final String SWITCH_VALUE = "12345";

    private static final String SWITCH_DEFAULT = "3000";

    private static final String SERVICE_QUERY = "serviceName=" + SERVICE + "&groupName=" + GROUP
            + "&namespaceId=public";

    private static final String LABEL_SELECTOR = "\"labels\"\\s*:\\s*\\[\\s*\"zone\"\\s*\\]";

    private static final String MYSQL_CHECKER = "\"type\"\\s*:\\s*\"MYSQL\"";

    private final NativeHttp http = new NativeHttp();

    private final NativeEnvironment environment;

    private final List<String> report = new ArrayList<>();

    private final String api;

    private NativeRestartCheck(NativeEnvironment environment) {
        this.environment = environment;
        this.api = environment.server().apiUrl() + "/nacos/v3";
    }

    /**
     * Seeds the data, restarts the Server of the environment, and looks for the data.
     *
     * @param environment a started environment
     * @return the report, one line per step
     */
    public static List<String> run(NativeEnvironment environment) {
        NativeRestartCheck check = new NativeRestartCheck(environment);
        check.execute();
        return check.report;
    }

    private void execute() {
        NativeServer server = environment.server();
        String token = environment.identities().adminToken();
        cleanup(token);
        List<String> failures = seed(token);
        if (!failures.isEmpty()) {
            record(false, "seed: " + String.join("; ", failures));
            return;
        }
        record(true, "seed");

        // JRaft saves a snapshot of each group at an interval between 30 and 60 seconds here. Wait a
        // little longer, so that the data is in a snapshot that the restart loads.
        long savedBefore = server.snapshotsSaved();
        sleep(70_000);
        System.out.println("Snapshots saved while waiting: " + (server.snapshotsSaved() - savedBefore));

        if (server.stop()) {
            record(true, "stop");
        } else {
            record(false, "stop: the Server ignored SIGTERM for 60 seconds");
        }
        try {
            server.restart();
        } catch (RuntimeException e) {
            record(false, "start after the restart: " + e.getMessage());
            return;
        }
        record(true, "start after the restart");

        String restarted = environment.identities().adminToken();
        failures = verify(restarted);
        cleanup(restarted);
        if (failures.isEmpty()) {
            record(true, "data after the restart");
        } else {
            record(false, "data after the restart: " + String.join("; ", failures));
        }
    }

    private List<String> seed(String token) {
        List<String> failures = new ArrayList<>();
        require(failures, "persistent service with a label selector", form("POST",
                "/admin/ns/service", token, Map.of("serviceName", SERVICE, "groupName", GROUP,
                        "namespaceId", "public", "ephemeral", "false", "protectThreshold", "0.5",
                        "metadata", "{\"owner\":\"team-x\"}", "selector",
                        "{\"type\":\"label\",\"expression\":\"CONSUMER.label.zone = "
                                + "PROVIDER.label.zone\"}")));
        require(failures, "persistent instance", form("POST", "/admin/ns/instance", token,
                Map.of("serviceName", SERVICE, "groupName", GROUP, "namespaceId", "public", "ip",
                        "127.0.0.1", "port", INSTANCE_PORT, "clusterName", CLUSTER, "weight", "2.5",
                        "ephemeral", "false", "metadata", "{\"zone\":\"z1\"}")));
        // The cluster exists once the instance is visible.
        for (int attempt = 0; attempt < 40; attempt++) {
            if (get("/admin/ns/instance/list?" + SERVICE_QUERY + "&clusterName=" + CLUSTER, token)
                    .body().contains(INSTANCE_PORT)) {
                break;
            }
            sleep(500);
        }
        require(failures, "cluster with the MYSQL health checker", form("PUT", "/admin/ns/cluster",
                token, Map.of("serviceName", SERVICE, "groupName", GROUP, "namespaceId", "public",
                        "clusterName", CLUSTER, "checkPort", INSTANCE_PORT,
                        "useInstancePort4Check", "true", "healthChecker",
                        "{\"type\":\"MYSQL\",\"user\":\"probe\",\"pwd\":\"secret\","
                                + "\"cmd\":\"select 1\"}", "metadata", "{\"owner\":\"team-x\"}")));
        // debug=false replicates the switch through the Raft group instead of keeping it in memory.
        require(failures, "naming switch through the Raft group", form("PUT",
                "/admin/ns/ops/switches", token,
                Map.of("entry", SWITCH_ENTRY, "value", SWITCH_VALUE, "debug", "false")));
        require(failures, "config publish", form("POST", "/admin/cs/config", token,
                Map.of("dataId", DATA_ID, "groupName", GROUP, "content", "restart=true", "type",
                        "properties")));

        // What the Server answers now is what it has to answer after the restart.
        NativeHttp.Response detail = get("/admin/ns/service?" + SERVICE_QUERY, token);
        require(failures, "service detail before the restart", detail);
        expect(failures, "label selector before the restart", detail, LABEL_SELECTOR);
        expect(failures, "MYSQL health checker before the restart", detail, MYSQL_CHECKER);
        return failures;
    }

    private List<String> verify(String token) {
        List<String> failures = new ArrayList<>();
        NativeHttp.Response detail = get("/admin/ns/service?" + SERVICE_QUERY, token);
        require(failures, "service detail after the restart", detail);
        expect(failures, "label selector after the restart", detail, LABEL_SELECTOR);
        expect(failures, "MYSQL health checker after the restart", detail, MYSQL_CHECKER);

        NativeHttp.Response instances = get("/admin/ns/instance/list?" + SERVICE_QUERY
                + "&clusterName=" + CLUSTER, token);
        require(failures, "instance list after the restart", instances);
        expect(failures, "persistent instance after the restart", instances, INSTANCE_PORT);
        expect(failures, "instance metadata after the restart", instances,
                "\"zone\"\\s*:\\s*\"z1\"");

        NativeHttp.Response switches = get("/admin/ns/ops/switches", token);
        require(failures, "switches after the restart", switches);
        expect(failures, "replicated switch after the restart", switches,
                "\"" + SWITCH_ENTRY + "\"\\s*:\\s*" + SWITCH_VALUE);

        NativeHttp.Response config = get("/admin/cs/config?dataId=" + DATA_ID + "&groupName="
                + GROUP, token);
        require(failures, "config get after the restart", config);
        expect(failures, "config content after the restart", config, "restart=true");
        return failures;
    }

    /**
     * Best effort: it removes what an earlier run left and what this run created.
     */
    private void cleanup(String token) {
        form("DELETE", "/admin/ns/instance?" + SERVICE_QUERY + "&ip=127.0.0.1&port=" + INSTANCE_PORT
                + "&clusterName=" + CLUSTER + "&ephemeral=false", token, Map.of());
        form("DELETE", "/admin/ns/service?" + SERVICE_QUERY, token, Map.of());
        form("DELETE", "/admin/cs/config?dataId=" + DATA_ID + "&groupName=" + GROUP, token,
                Map.of());
        form("PUT", "/admin/ns/ops/switches", token,
                Map.of("entry", SWITCH_ENTRY, "value", SWITCH_DEFAULT, "debug", "false"));
    }

    private void record(boolean passed, String step) {
        String line = (passed ? "PASS  " : "FAIL  ") + step;
        report.add(line);
        System.out.println(line);
    }

    private NativeHttp.Response get(String path, String token) {
        return http.get(api + path, token);
    }

    private NativeHttp.Response form(String method, String path, String token,
            Map<String, String> form) {
        return http.form(method, api + path, token, form);
    }

    private static void require(List<String> failures, String name, NativeHttp.Response response) {
        if (!response.ok()) {
            failures.add(name + ": " + response.describe());
        }
    }

    private static void expect(List<String> failures, String name, NativeHttp.Response response,
            String regex) {
        if (!response.matches(regex)) {
            failures.add(name + ": the response does not match " + regex);
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
