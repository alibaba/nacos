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

package com.alibaba.nacos.test.openapi.hub;

import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.test.adminapi.ai.AiAdminApiBaseITCase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Prompt sharing uses existing publication/permissions and never requires a scope operation. */
class HubPromptSharingOpenApiITCase extends AiAdminApiBaseITCase {

    @ParameterizedTest
    @ValueSource(strings = {"admin", "console"})
    void publishedPromptSharesWithoutScopeSetupAndRevokesOnOffline(String audience)
        throws Exception {
        assumeTrue(AUTH_ENABLED, "Requires auth-enabled Hub and default visibility policy");
        String name = randomPromptKey("hub-no-scope");
        String template = "Hello {{name}} from fixed shared Prompt";
        String management = "admin".equals(audience)
            ? nacosPath("/v3/admin/ai/hub/prompt") : "/v3/console/ai/hub/prompt";
        postFormOk(ADMIN_PROMPT_PATH + "/draft",
            promptDraftForm(name, "1.0.0", template, "Hub Prompt", "hub"));
        addCleanup(() -> deletePromptQuietly(name));
        postFormOk(ADMIN_PROMPT_PATH + "/force-publish",
            withUpdateLatestLabel(promptPublishForm(name, "1.0.0")));
        Map<String, String> source = Map.of("sourceNamespaceId", "public", "sourceName", name,
            "sourceVersion", "1.0.0");
        assertEquals(403, postRaw(management + "/share", queryFrom(source),
            AuthIdentity.CLIENT_NO_PERMISSION).code());
        JsonNode shared = postFormOk(management + "/share", source).get("data");
        assertTrue(shared.get("active").asBoolean());
        String id = shared.get("hubEntryId").asText();
        addCleanup(() -> {
            JsonNode state = getJsonOk(management + "/source-share", Query.newInstance()
                .addParam("sourceNamespaceId", "public").addParam("sourceName", name)).get("data");
            if (state != null && state.path("active").asBoolean()) {
                postFormOk(management + "/unshare", Map.of("hubEntryId", id,
                    "expectedMetaVersion", state.get("metaVersion").asText()));
            }
        });
        String clientPath = nacosPath("/v3/client/ai/prompt");
        Query query = Query.newInstance().addParam("namespaceId", "__nacos_ai_hub__")
            .addParam("promptKey", id).addParam("version", "1.0.0");
        JsonNode content = getJsonOk(clientPath, query,
            AuthIdentity.CLIENT_NO_PERMISSION).get("data");
        assertEquals(template, content.get("template").asText());
        assertEquals(template, getJsonOk(ADMIN_PROMPT_VERSION_PATH,
            promptVersionQuery(name, "1.0.0")).get("data").get("template").asText());
        postFormOk(ADMIN_PROMPT_PATH + "/offline", promptPublishForm(name, "1.0.0"));
        assertError(getRaw(clientPath, query, AuthIdentity.CLIENT_NO_PERMISSION),
            404, ErrorCode.RESOURCE_NOT_FOUND, "");
        JsonNode state = getJsonOk(management + "/source-share", Query.newInstance()
            .addParam("sourceNamespaceId", "public").addParam("sourceName", name)).get("data");
        assertFalse(state.get("active").asBoolean());
    }

    @Override
    protected String requestUrl(String path) {
        if (path.startsWith("/v3/console/")) {
            return "http://" + NACOS_HOST + ":" + System.getProperty("nacos.console.port", "8080") + path;
        }
        return super.requestUrl(path);
    }
}
