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

package com.alibaba.nacos.test.adminapi.ai.hub;

import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.test.adminapi.ai.AiAdminApiBaseITCase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Disabled-Hub Admin/Console contract: empty lists, validation, controlled disabled
 * reads/mutations/import and unaffected Namespace lists. Requires a dedicated auth-enabled
 * server with nacos.ai.hub.enabled=false; never changes shared server configuration.
 *
 * @author nacos
 */
class HubDisabledApiOpenApiITCase extends AiAdminApiBaseITCase {

    @BeforeEach
    void requireDisabledHubFixture() throws Exception {
        assumeTrue(AUTH_ENABLED, "Disabled-Hub HTTP IT requires enabled authentication");
        JsonNode state = getJsonOk(nacosPath("/v3/admin/core/state"), Query.newInstance(),
                AuthIdentity.ANONYMOUS).path("data");
        assumeTrue("false".equals(state.path("ai_hub_enabled").asText()),
                "Use a dedicated server with nacos.ai.hub.enabled=false");
    }

    @Test
    void adminEndpointsHonorDisabledHub() throws Exception {
        verifyDisabled(nacosPath("/v3/admin/ai/hub/skills"));
    }

    @Test
    void consoleEndpointsHonorDisabledHub() throws Exception {
        verifyDisabled("/v3/console/ai/hub/skills");
    }

    @Override
    protected String requestUrl(String path) {
        if (path.startsWith("/v3/console/")) {
            return "http://" + NACOS_HOST + ":" + System.getProperty("nacos.console.port", "8080") + path;
        }
        return super.requestUrl(path);
    }

    private void verifyDisabled(String path) throws Exception {
        String skillPath = path.replace("/hub/skills", "/skills");
        JsonNode page = getJsonOk(skillPath + "/list", Query.newInstance().addParam("namespaceId", "__nacos_ai_hub__")
                .addParam("pageNo", "3").addParam("pageSize", "10")).path("data");
        assertEquals(3, page.path("pageNumber").asInt());
        assertEquals(0, page.path("totalCount").asInt());
        assertEquals(0, page.path("pagesAvailable").asInt());
        assertTrue(page.path("pageItems").isArray());
        assertEquals(0, page.path("pageItems").size());
        assertError(getRaw(skillPath + "/list", Query.newInstance().addParam("namespaceId", "__nacos_ai_hub__").addParam("pageNo", "0")),
                400, ErrorCode.PARAMETER_VALIDATE_ERROR, "");
        for (String suffix : new String[] {"", "/version", "/version/download"}) {
            assertError(getRaw(skillPath + suffix, Query.newInstance()
                    .addParam("namespaceId", "__nacos_ai_hub__").addParam("skillName", "1")
                    .addParam("version", "1.0.0")), 501, ErrorCode.API_FUNCTION_DISABLED, "Hub is disabled");
        }
        assertError(getRaw(path + "/governance-state", Query.newInstance().addParam("hubEntryId", "1")),
                501, ErrorCode.API_FUNCTION_DISABLED, "Hub is disabled");
        assertError(getRaw(path + "/source-share", Query.newInstance()
                .addParam("sourceNamespaceId", "public").addParam("sourceName", "hub-disabled-it")),
                501, ErrorCode.API_FUNCTION_DISABLED, "Hub is disabled");
        assertError(postRaw(path + "/share", Query.newInstance()
                .addParam("sourceNamespaceId", "public").addParam("sourceName", "hub-disabled-it")
                .addParam("sourceVersion", "1.0.0")),
                501, ErrorCode.API_FUNCTION_DISABLED, "Hub is disabled");
        assertError(postRaw(path + "/adjust", Query.newInstance().addParam("hubEntryId", "1")
                .addParam("targetVersion", "1.0.0").addParam("expectedMetaVersion", "1")),
                501, ErrorCode.API_FUNCTION_DISABLED, "Hub is disabled");
        for (String suffix : new String[] {"unshare", "force-unshare"}) {
            assertError(postRaw(path + "/" + suffix, Query.newInstance().addParam("hubEntryId", "1")
                    .addParam("expectedMetaVersion", "1").addParam("reason", "disabled Hub IT")),
                    501, ErrorCode.API_FUNCTION_DISABLED, "Hub is disabled");
        }
        assertError(postRaw(path + "/import", Query.newInstance().addParam("hubEntryId", "1")
                .addParam("targetNamespaceId", "public").addParam("targetName", "hub-disabled-it")
                .addParam("targetVersion", "1.0.0")),
                501, ErrorCode.API_FUNCTION_DISABLED, "Hub is disabled");
        JsonNode namespacePage = getJsonOk(ADMIN_SKILL_PATH + "/list", Query.newInstance()
                .addParam("namespaceId", "public").addParam("pageNo", "1")
                .addParam("pageSize", "1")).path("data");
        assertTrue(namespacePage.path("pageItems").isArray(), namespacePage.toString());
    }
}
