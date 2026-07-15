/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.nacos.test.consoleapi.core;

import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.http.param.Query;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for console plugin OpenAPIs under {@code /nacos/v3/console/plugin}.
 *
 * <p>Scenario coverage:
 * <ul>
 *     <li>Expected capability: list exposes discovered plugin inventory, pluginType filtering narrows results, detail
 *     returns identity and mutable-state fields, and availability returns the cluster-node availability map.</li>
 *     <li>Boundary/validation: unknown pluginType list filter returns an empty list; detail/status/config/availability
 *     require plugin identity parameters; config mutation requires a configuration map and rejects non-configurable
 *     plugins.</li>
 *     <li>Exception/error handling: plugin state/config success mutations are intentionally not executed because they
 *     change runtime extension state; missing plugin detail and validation errors are verified as controlled v3
 *     envelopes.</li>
 * </ul>
 *
 * @author xiweng.yy
 */
public class PluginConsoleApiOpenApiITCase extends CoreConsoleApiBaseITCase {

    @Test
    public void testListFilterDetailAndAvailability() throws Exception {
        JsonNode plugins = getJsonOk(CONSOLE_PLUGIN_LIST_PATH, Query.newInstance()).get("data");
        assertTrue(plugins.size() > 0, plugins.toString());

        JsonNode plugin = plugins.get(0);
        String pluginType = plugin.get("pluginType").asText();
        String pluginName = plugin.get("pluginName").asText();
        assertTrue(plugin.get("pluginId").asText().contains(":"), plugin.toString());
        assertTrue(pluginType.length() > 0, plugin.toString());
        assertTrue(pluginName.length() > 0, plugin.toString());

        JsonNode filtered = getJsonOk(CONSOLE_PLUGIN_LIST_PATH,
                Query.newInstance().addParam("pluginType", pluginType)).get("data");
        assertTrue(filtered.size() > 0, filtered.toString());
        for (JsonNode each : filtered) {
            assertEquals(pluginType, each.get("pluginType").asText(), each.toString());
        }

        JsonNode detail = getJsonOk(CONSOLE_PLUGIN_PATH,
                Query.newInstance().addParam("pluginType", pluginType).addParam("pluginName", pluginName))
                .get("data");
        assertEquals(plugin.get("pluginId").asText(), detail.get("pluginId").asText(), detail.toString());
        assertEquals(pluginType, detail.get("pluginType").asText(), detail.toString());
        assertEquals(pluginName, detail.get("pluginName").asText(), detail.toString());
        assertTrue(detail.has("enabled"), detail.toString());
        assertTrue(detail.has("configurable"), detail.toString());
        assertTrue(detail.get("configValueMetas").isObject(), detail.toString());

        JsonNode availability = getJsonOk(CONSOLE_PLUGIN_AVAILABILITY_PATH,
                Query.newInstance().addParam("pluginType", pluginType).addParam("pluginName", pluginName))
                .get("data");
        assertTrue(availability.isObject(), availability.toString());

        JsonNode unknownType = getJsonOk(CONSOLE_PLUGIN_LIST_PATH,
                Query.newInstance().addParam("pluginType", "not-a-plugin-type")).get("data");
        assertEquals(0, unknownType.size(), unknownType.toString());
    }

    @Test
    public void testNacosAuthPluginConfigMetadata() throws Exception {
        JsonNode detail = getJsonOk(CONSOLE_PLUGIN_PATH,
                Query.newInstance().addParam("pluginType", "auth").addParam("pluginName", "nacos"))
                .get("data");
        assertTrue(detail.get("configurable").asBoolean(), detail.toString());

        JsonNode definitions = detail.get("configDefinitions");
        assertEquals(5, definitions.size(), definitions.toString());
        assertDefinition(definitions, "token.secret.key",
                "nacos.core.auth.plugin.nacos.token.secret.key", "STRING", "RESTART", true);
        assertDefinition(definitions, "token.expire.seconds",
                "nacos.core.auth.plugin.nacos.token.expire.seconds", "NUMBER", "RUNTIME", false);
        assertDefinition(definitions, "token.cache.enable",
                "nacos.core.auth.plugin.nacos.token.cache.enable", "BOOLEAN", "RUNTIME", false);
        assertDefinition(definitions, "caching.enabled", "nacos.core.auth.caching.enabled",
                "BOOLEAN", "RUNTIME", false);
        assertDefinition(definitions, "anonymous.ai.enabled",
                "nacos.core.auth.nacos.anonymous.ai.enabled", "BOOLEAN", "RUNTIME", false);

        JsonNode config = detail.get("config");
        assertTrue(config.get("token.secret.key").asText().contains("******"), config.toString());
        assertEquals("18000", config.get("token.expire.seconds").asText(), config.toString());
        assertEquals("false", config.get("token.cache.enable").asText(), config.toString());
        assertEquals("true", config.get("caching.enabled").asText(), config.toString());
        assertEquals("false", config.get("anonymous.ai.enabled").asText(), config.toString());

        JsonNode metas = detail.get("configValueMetas");
        assertEquals("STATIC", metas.get("token.secret.key").get("source").asText(), metas.toString());
        assertEquals("DEFAULT", metas.get("anonymous.ai.enabled").get("source").asText(),
                metas.toString());
    }

    @Test
    public void testPluginValidationAndNotFoundReturnControlledErrors() throws Exception {
        assertError(getRaw(CONSOLE_PLUGIN_PATH,
                Query.newInstance().addParam("pluginType", "auth").addParam("pluginName", "missing-plugin")),
                404, ErrorCode.RESOURCE_NOT_FOUND, "auth:missing-plugin");
        assertError(getRaw(CONSOLE_PLUGIN_PATH,
                Query.newInstance().addParam("pluginType", "auth")), 400,
                ErrorCode.PARAMETER_MISSING, "pluginName");
        assertError(getRaw(CONSOLE_PLUGIN_AVAILABILITY_PATH,
                Query.newInstance().addParam("pluginName", "missing-plugin")), 400,
                ErrorCode.PARAMETER_MISSING, "pluginType");
        assertError(putRaw(CONSOLE_PLUGIN_PATH + "/status",
                Query.newInstance().addParam("pluginType", "auth").addParam("enabled", "true")),
                400, ErrorCode.PARAMETER_MISSING, "pluginName");
        assertError(putRaw(CONSOLE_PLUGIN_PATH + "/config",
                Query.newInstance().addParam("pluginType", "auth").addParam("pluginName", "missing-plugin")),
                400, ErrorCode.PARAMETER_VALIDATE_ERROR, "configuration");
    }

    @Test
    public void testNonConfigurablePluginRejectsConfigUpdate() throws Exception {
        JsonNode plugins = getJsonOk(CONSOLE_PLUGIN_LIST_PATH, Query.newInstance()).get("data");
        JsonNode nonConfigurable = null;
        for (JsonNode plugin : plugins) {
            if (!plugin.get("configurable").asBoolean()) {
                nonConfigurable = plugin;
                break;
            }
        }
        assertNotNull(nonConfigurable, plugins.toString());

        assertError(putRaw(CONSOLE_PLUGIN_PATH + "/config",
                Query.newInstance().addParam("pluginType", nonConfigurable.get("pluginType").asText())
                        .addParam("pluginName", nonConfigurable.get("pluginName").asText())
                        .addParam("config%5Bprobe%5D", "value")),
                400, ErrorCode.PARAMETER_VALIDATE_ERROR, "does not support configuration");
    }

    private void assertDefinition(JsonNode definitions, String key, String alias, String type,
            String effectMode, boolean sensitive) {
        JsonNode definition = findDefinition(definitions, key);
        assertNotNull(definition, definitions.toString());
        assertEquals(type, definition.get("type").asText(), definition.toString());
        assertEquals(effectMode, definition.get("effectMode").asText(), definition.toString());
        assertEquals(sensitive, definition.get("sensitive").asBoolean(), definition.toString());
        assertEquals(alias, definition.get("aliases").get(0).asText(), definition.toString());
    }

    private JsonNode findDefinition(JsonNode definitions, String key) {
        for (JsonNode definition : definitions) {
            if (key.equals(definition.get("key").asText())) {
                return definition;
            }
        }
        return null;
    }
}
