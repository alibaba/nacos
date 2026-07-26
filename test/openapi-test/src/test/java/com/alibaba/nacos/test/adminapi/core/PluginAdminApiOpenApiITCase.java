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

package com.alibaba.nacos.test.adminapi.core;

import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.http.param.Query;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for core plugin admin OpenAPI {@code /nacos/v3/admin/core/plugin}.
 *
 * <p>Scenario coverage:
 * <ul>
 *     <li>Expected capability: plugin list exposes discovered plugin inventory, pluginType filters narrow results, and
 *     plugin detail returns the same identity plus type capability and mutable-state fields.</li>
 *     <li>Boundary/validation: unknown pluginType filters return an empty list; status update requires
 *     {@code pluginName}; config update requires {@code config} and rejects non-configurable plugins; missing plugin
 *     detail is reported as not found.</li>
 *     <li>Exception/error handling: critical disable and exclusive runtime-switch attempts are rejected without
 *     mutation; required-parameter and detail not-found failures are verified as controlled v3 error envelopes.</li>
 * </ul>
 *
 * @author xiweng.yy
 */
public class PluginAdminApiOpenApiITCase extends CoreAdminApiBaseITCase {

    @Test
    public void testListFilterAndDetailPluginInventory() throws Exception {
        JsonNode plugins = getJsonOk(ADMIN_CORE_PLUGIN_PATH + "/list", Query.newInstance()).get("data");
        assertTrue(plugins.size() > 0, plugins.toString());

        JsonNode plugin = plugins.get(0);
        assertTrue(plugin.get("pluginId").asText().contains(":"), plugin.toString());
        assertTrue(plugin.get("pluginType").asText().length() > 0, plugin.toString());
        assertTrue(plugin.get("pluginName").asText().length() > 0, plugin.toString());
        assertTrue(plugin.has("typeCritical"), plugin.toString());
        assertTrue(plugin.get("executionMode").asText().length() > 0, plugin.toString());
        assertTrue(plugin.has("exclusive"), plugin.toString());

        JsonNode filtered = getJsonOk(ADMIN_CORE_PLUGIN_PATH + "/list",
                Query.newInstance().addParam("pluginType", plugin.get("pluginType").asText())).get("data");
        assertTrue(filtered.size() > 0, filtered.toString());
        for (JsonNode each : filtered) {
            assertEquals(plugin.get("pluginType").asText(), each.get("pluginType").asText(), each.toString());
        }

        JsonNode detail = getJsonOk(ADMIN_CORE_PLUGIN_PATH + "/detail",
                Query.newInstance().addParam("pluginType", plugin.get("pluginType").asText())
                        .addParam("pluginName", plugin.get("pluginName").asText())).get("data");
        assertEquals(plugin.get("pluginId").asText(), detail.get("pluginId").asText(), detail.toString());
        assertEquals(plugin.get("pluginType").asText(), detail.get("pluginType").asText(), detail.toString());
        assertEquals(plugin.get("pluginName").asText(), detail.get("pluginName").asText(), detail.toString());
        assertTrue(detail.has("enabled"), detail.toString());
        assertTrue(detail.has("configurable"), detail.toString());
        assertEquals(plugin.get("typeCritical"), detail.get("typeCritical"), detail.toString());
        assertEquals(plugin.get("executionMode"), detail.get("executionMode"), detail.toString());
        assertEquals(plugin.get("exclusive"), detail.get("exclusive"), detail.toString());
        assertTrue(detail.get("configValueMetas").isObject(), detail.toString());

        JsonNode unknownType = getJsonOk(ADMIN_CORE_PLUGIN_PATH + "/list",
                Query.newInstance().addParam("pluginType", "not-a-plugin-type")).get("data");
        assertEquals(0, unknownType.size(), unknownType.toString());
    }

    @Test
    public void testNacosAuthPluginConfigMetadata() throws Exception {
        JsonNode detail = getJsonOk(ADMIN_CORE_PLUGIN_PATH + "/detail",
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
    public void testLdapAuthPluginConfigMetadata() throws Exception {
        JsonNode detail = getJsonOk(ADMIN_CORE_PLUGIN_PATH + "/detail",
                Query.newInstance().addParam("pluginType", "auth").addParam("pluginName", "ldap"))
                .get("data");
        assertTrue(detail.get("configurable").asBoolean(), detail.toString());

        JsonNode definitions = detail.get("configDefinitions");
        assertEquals(8, definitions.size(), definitions.toString());
        assertDefinition(definitions, "url", "nacos.core.auth.ldap.url", "STRING", "RESTART",
                false);
        assertDefinition(definitions, "base-dn", "nacos.core.auth.ldap.basedc", "STRING",
                "RESTART", false);
        assertDefinition(definitions, "timeout", "nacos.core.auth.ldap.timeout", "NUMBER",
                "RESTART", false);
        assertDefinition(definitions, "user-dn", "nacos.core.auth.ldap.userDn", "STRING",
                "RESTART", false);
        assertDefinition(definitions, "password", "nacos.core.auth.ldap.password", "STRING",
                "RESTART", true);
        assertDefinition(definitions, "filter-prefix", "nacos.core.auth.ldap.filter.prefix",
                "STRING", "RESTART", false);
        assertDefinition(definitions, "case-sensitive", "nacos.core.auth.ldap.case.sensitive",
                "BOOLEAN", "RESTART", false);
        assertDefinition(definitions, "ignore-partial-result-exception",
                "nacos.core.auth.ldap.ignore.partial.result.exception", "BOOLEAN", "RESTART", false);

        JsonNode config = detail.get("config");
        assertEquals("ldap://localhost:389", config.get("url").asText(), config.toString());
        assertEquals("3000", config.get("timeout").asText(), config.toString());
        assertTrue(config.get("password").asText().contains("******"), config.toString());

        JsonNode metas = detail.get("configValueMetas");
        assertEquals("DEFAULT", metas.get("url").get("source").asText(), metas.toString());
        assertEquals("DEFAULT", metas.get("password").get("source").asText(), metas.toString());
    }

    @Test
    public void testOidcAuthPluginConfigMetadata() throws Exception {
        JsonNode detail = getJsonOk(ADMIN_CORE_PLUGIN_PATH + "/detail",
                Query.newInstance().addParam("pluginType", "auth").addParam("pluginName", "oidc"))
                .get("data");
        assertTrue(detail.get("configurable").asBoolean(), detail.toString());

        JsonNode definitions = detail.get("configDefinitions");
        assertEquals(14, definitions.size(), definitions.toString());
        assertDefinition(definitions, "issuer-uri", "nacos.core.auth.plugin.oidc.issuer-uri",
                "STRING", "RESTART", false);
        assertDefinition(definitions, "client-id", "nacos.core.auth.plugin.oidc.client-id",
                "STRING", "RESTART", false);
        assertDefinition(definitions, "client-secret", "nacos.core.auth.plugin.oidc.client-secret",
                "STRING", "RESTART", true);
        assertDefinition(definitions, "scope", "nacos.core.auth.plugin.oidc.scope",
                "STRING", "RESTART", false);
        assertDefinition(definitions, "token-validation-method",
                "nacos.core.auth.plugin.oidc.token-validation-method", "STRING", "RESTART", false);
        assertDefinition(definitions, "jwks-cache-ttl-seconds",
                "nacos.core.auth.plugin.oidc.jwks-cache-ttl-seconds", "NUMBER", "RESTART", false);
        assertDefinition(definitions, "username-claim", "nacos.core.auth.plugin.oidc.username-claim",
                "STRING", "RESTART", false);
        assertDefinition(definitions, "roles-claim", "nacos.core.auth.plugin.oidc.roles-claim",
                "STRING", "RESTART", false);
        assertDefinition(definitions, "admin-role", "nacos.core.auth.plugin.oidc.admin-role",
                "STRING", "RESTART", false);
        assertDefinition(definitions, "auto-create-user",
                "nacos.core.auth.plugin.oidc.auto-create-user", "BOOLEAN", "RESTART", false);
        assertDefinition(definitions, "authorization-endpoint",
                "nacos.core.auth.plugin.oidc.authorization-endpoint", "STRING", "RESTART", false);
        assertDefinition(definitions, "authorization-timeout-ms",
                "nacos.core.auth.plugin.oidc.authorization-timeout-ms", "NUMBER", "RESTART", false);
        assertDefinition(definitions, "strict-nonce-validation",
                "nacos.core.auth.plugin.oidc.strict-nonce-validation", "BOOLEAN", "RESTART", false);
        assertDefinition(definitions, "strict-audience-validation",
                "nacos.core.auth.plugin.oidc.strict-audience-validation", "BOOLEAN", "RESTART", false);

        JsonNode config = detail.get("config");
        assertEquals(14, config.size(), config.toString());
        assertEquals("", config.get("issuer-uri").asText(), config.toString());
        assertEquals("", config.get("client-secret").asText(), config.toString());
        assertEquals("openid profile email", config.get("scope").asText(), config.toString());
        assertEquals("jwt", config.get("token-validation-method").asText(), config.toString());
        assertEquals("3600", config.get("jwks-cache-ttl-seconds").asText(), config.toString());
        assertEquals("5000", config.get("authorization-timeout-ms").asText(), config.toString());
        assertEquals("true", config.get("strict-nonce-validation").asText(), config.toString());

        JsonNode metas = detail.get("configValueMetas");
        assertEquals(14, metas.size(), metas.toString());
        for (JsonNode meta : metas) {
            assertEquals("DEFAULT", meta.get("source").asText(), meta.toString());
        }

        assertError(putRaw(ADMIN_CORE_PLUGIN_PATH + "/config",
                Query.newInstance().addParam("pluginType", "auth").addParam("pluginName", "oidc")
                        .addParam("config", "{\"client-id\":\"updated\"}")),
                400, ErrorCode.PARAMETER_VALIDATE_ERROR, "requires restart");
    }

    @Test
    public void testPluginDetailNotFoundReturnsControlledError() throws Exception {
        assertError(getRaw(ADMIN_CORE_PLUGIN_PATH + "/detail",
                Query.newInstance().addParam("pluginType", "auth").addParam("pluginName", "missing-plugin")),
                404, ErrorCode.RESOURCE_NOT_FOUND, "auth:missing-plugin");
    }

    @Test
    public void testPluginMutationValidationReturnsBadRequest() throws Exception {
        assertError(putRaw(ADMIN_CORE_PLUGIN_PATH + "/status",
                Query.newInstance().addParam("pluginType", "auth").addParam("enabled", "true")),
                400, ErrorCode.PARAMETER_MISSING, "pluginName");
        assertError(putRaw(ADMIN_CORE_PLUGIN_PATH + "/config",
                Query.newInstance().addParam("pluginType", "auth").addParam("pluginName", "missing-plugin")),
                400, ErrorCode.PARAMETER_MISSING, "config");
    }

    @Test
    public void testCriticalAndExclusiveStateChangesAreRejected() throws Exception {
        JsonNode authPlugins = getJsonOk(ADMIN_CORE_PLUGIN_PATH + "/list",
                Query.newInstance().addParam("pluginType", "auth")).get("data");
        JsonNode enabled = findByEnabled(authPlugins, true);
        JsonNode disabled = findByEnabled(authPlugins, false);
        assertNotNull(enabled, authPlugins.toString());
        assertNotNull(disabled, authPlugins.toString());

        assertError(putRaw(ADMIN_CORE_PLUGIN_PATH + "/status",
                Query.newInstance().addParam("pluginType", "auth")
                        .addParam("pluginName", enabled.get("pluginName").asText())
                        .addParam("enabled", "false")),
                400, ErrorCode.PARAMETER_VALIDATE_ERROR, "critical plugin type");
        assertError(putRaw(ADMIN_CORE_PLUGIN_PATH + "/status",
                Query.newInstance().addParam("pluginType", "auth")
                        .addParam("pluginName", disabled.get("pluginName").asText())
                        .addParam("enabled", "true")),
                400, ErrorCode.PARAMETER_VALIDATE_ERROR, "requires restart");
    }

    @Test
    public void testNonConfigurablePluginRejectsConfigUpdate() throws Exception {
        JsonNode plugins = getJsonOk(ADMIN_CORE_PLUGIN_PATH + "/list", Query.newInstance()).get("data");
        JsonNode nonConfigurable = null;
        for (JsonNode plugin : plugins) {
            if (!plugin.get("configurable").asBoolean()) {
                nonConfigurable = plugin;
                break;
            }
        }
        assertNotNull(nonConfigurable, plugins.toString());

        assertError(putRaw(ADMIN_CORE_PLUGIN_PATH + "/config",
                Query.newInstance().addParam("pluginType", nonConfigurable.get("pluginType").asText())
                        .addParam("pluginName", nonConfigurable.get("pluginName").asText())
                        .addParam("config", "{}")),
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

    private JsonNode findByEnabled(JsonNode plugins, boolean enabled) {
        for (JsonNode plugin : plugins) {
            if (plugin.get("enabled").asBoolean() == enabled) {
                return plugin;
            }
        }
        return null;
    }
}
