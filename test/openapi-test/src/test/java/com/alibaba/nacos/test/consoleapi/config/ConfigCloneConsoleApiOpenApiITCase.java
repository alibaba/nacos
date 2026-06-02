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

package com.alibaba.nacos.test.consoleapi.config;

import com.alibaba.nacos.api.config.ConfigType;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for console config clone OpenAPI {@code POST /nacos/v3/console/cs/config/clone}.
 *
 * <p>Scenario coverage:
 * <ul>
 *     <li>Expected capability: clone copies an existing {@code cfgId} to target {@code dataId}/{@code group} in
 *     {@code targetNamespaceId}; the cloned config can be queried with original content and type.</li>
 *     <li>Boundary/validation: {@code targetNamespaceId} is required, empty clone lists are rejected with
 *     {@code NO_SELECTED_CONFIG}, and unknown source ids return {@code DATA_EMPTY} without creating data.</li>
 *     <li>Exception/error handling: request-parameter errors return HTTP 400 and business failures keep the v3
 *     {@code Result} envelope instead of leaking HTTP 500.</li>
 * </ul>
 *
 * @author xiweng.yy
 */
public class ConfigCloneConsoleApiOpenApiITCase extends ConfigConsoleApiBaseITCase {

    @Test
    public void testCloneConfigToTargetIdentity() throws Exception {
        String sourceDataId = randomDataId("clone-source");
        String sourceGroup = randomGroupName("clone-source");
        String targetDataId = randomDataId("clone-target");
        String targetGroup = randomGroupName("clone-target");
        String content = "console-clone-content";
        publishConfig(sourceDataId, sourceGroup, "", content, ConfigType.JSON.getType(), "clone desc", "");
        addCleanup(() -> deleteConfigQuietly(sourceDataId, sourceGroup, ""));
        addCleanup(() -> deleteConfigQuietly(targetDataId, targetGroup, ""));
        JsonNode source = queryConfig(sourceDataId, sourceGroup, "").get("data");

        String body = "[{\"cfgId\":" + source.get("id").asText() + ",\"dataId\":\""
                + targetDataId + "\",\"group\":\"" + targetGroup + "\"}]";
        JsonNode root = postJsonOk(CONSOLE_CONFIG_CLONE_PATH,
                Query.newInstance().addParam("targetNamespaceId", DEFAULT_NAMESPACE).addParam("policy",
                        "OVERWRITE"),
                body);
        assertTrue(root.get("data").get("succCount").asInt() >= 1, root.toString());

        JsonNode cloned = queryConfig(targetDataId, targetGroup, DEFAULT_NAMESPACE).get("data");
        assertConfigDetail(cloned, targetDataId, targetGroup, DEFAULT_NAMESPACE, content, ConfigType.JSON.getType());
    }

    @Test
    public void testCloneValidationAndBusinessFailuresReturnResultEnvelope() throws Exception {
        assertError(postJsonRaw(CONSOLE_CONFIG_CLONE_PATH, Query.newInstance(), "[]"), 400,
                ErrorCode.PARAMETER_MISSING, "targetNamespaceId");

        HttpResponse emptyResponse = postJsonRaw(CONSOLE_CONFIG_CLONE_PATH,
                Query.newInstance().addParam("targetNamespaceId", DEFAULT_NAMESPACE), "[]");
        assertEquals(200, emptyResponse.code(), emptyResponse.body());
        JsonNode emptyRoot = JacksonUtils.toObj(emptyResponse.body());
        assertFailureResult(emptyRoot, ErrorCode.NO_SELECTED_CONFIG, "succCount");

        HttpResponse absentSourceResponse = postJsonRaw(CONSOLE_CONFIG_CLONE_PATH,
                Query.newInstance().addParam("targetNamespaceId", DEFAULT_NAMESPACE),
                "[{\"cfgId\":999999999999,\"dataId\":\"absent\",\"group\":\"absent\"}]");
        assertEquals(200, absentSourceResponse.code(), absentSourceResponse.body());
        JsonNode absentSourceRoot = JacksonUtils.toObj(absentSourceResponse.body());
        assertFailureResult(absentSourceRoot, ErrorCode.DATA_EMPTY, "succCount");
    }

    private void assertFailureResult(JsonNode root, ErrorCode errorCode, String dataField) {
        assertEquals(errorCode.getCode(), root.get("code").asInt(), root.toString());
        assertEquals(errorCode.getMsg(), root.get("message").asText(), root.toString());
        assertTrue(root.get("data").has(dataField), root.toString());
    }
}
