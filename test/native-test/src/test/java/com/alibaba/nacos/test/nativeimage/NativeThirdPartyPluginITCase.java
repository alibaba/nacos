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

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A plugin from outside the repository, as a third party would ship it: the jar of {@code
 * test/native-test-plugin} is in the plugin directory of the build, brings the services file and
 * the reachability metadata of its own, and has to be found, instantiated and run by the Server.
 * The plugin refuses to publish a configuration whose content contains a marker text.
 *
 * @author Nacos
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NativeThirdPartyPluginITCase {

    private static final String MARKER = "rejected-by-plugin";

    private final NativeHttp http = new NativeHttp();

    private NativeEnvironment environment;

    private String api;

    private String token;

    @BeforeAll
    void startEnvironment() {
        environment = NativeEnvironment.builder("plugin").plugin(NativeTestSettings.samplePlugin())
                .build().start();
        api = environment.server().apiUrl() + "/nacos/v3";
        token = environment.identities().adminToken();
    }

    @AfterAll
    void stopEnvironment() {
        if (environment != null) {
            environment.close();
        }
    }

    @Test
    void theServerListsThePlugin() {
        // The tests share one Server and run in no fixed order: each of them sets what it needs.
        NativeHttp.Response response = http.get(api
                + "/admin/core/plugin/list?pluginType=config-change", token);
        assertTrue(response.ok(), response.describe());
        assertTrue(response.body().contains("native-test-rejecting"), response.describe());
    }

    @Test
    void anEnabledPluginRefusesTheContentItIsWrittenToRefuse() {
        setEnabled(true);
        String accepted = "plugin-accepted-" + UUID.randomUUID();
        NativeHttp.Response published = publish(accepted, "plugin=fine");
        assertTrue(published.ok(), published.describe());
        assertTrue(published.matches("\"data\"\\s*:\\s*true"), published.describe());

        String refused = "plugin-refused-" + UUID.randomUUID();
        NativeHttp.Response rejected = publish(refused, "text=" + MARKER);
        assertTrue(rejected.matches("\"data\"\\s*:\\s*false"), rejected.describe());
        NativeHttp.Response read = http.get(api + "/admin/cs/config?dataId=" + refused
                + "&groupName=DEFAULT_GROUP", token);
        assertFalse(read.ok() && read.body().contains(MARKER), read.describe());
    }

    @Test
    void aDisabledPluginDoesNotRun() {
        setEnabled(false);
        NativeHttp.Response published = publish("plugin-disabled-" + UUID.randomUUID(),
                "text=" + MARKER);
        assertTrue(published.matches("\"data\"\\s*:\\s*true"), published.describe());
    }

    private void setEnabled(boolean enabled) {
        NativeHttp.Response response = http.form("PUT", api + "/admin/core/plugin/status", token,
                Map.of("pluginType", "config-change", "pluginName", "native-test-rejecting",
                        "enabled", String.valueOf(enabled)));
        assertTrue(response.ok(), response.describe());
    }

    private NativeHttp.Response publish(String dataId, String content) {
        return http.form("POST", api + "/admin/cs/config", token, Map.of("dataId", dataId,
                "groupName", "DEFAULT_GROUP", "content", content, "type", "properties"));
    }
}
