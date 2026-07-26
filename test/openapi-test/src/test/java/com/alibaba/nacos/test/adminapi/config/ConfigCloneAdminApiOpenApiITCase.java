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

package com.alibaba.nacos.test.adminapi.config;

import com.alibaba.nacos.api.config.ConfigType;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for config clone admin OpenAPI {@code POST /nacos/v3/admin/cs/config/clone}.
 *
 * <p>Scenario coverage:
 * <ul>
 *     <li>Expected capability: clone copies an existing config id to a target dataId/groupName in the target namespace
 *     and the cloned config can be queried with the original content and type.</li>
 *     <li>Boundary/validation: {@code namespaceId} is required, omitted {@code sourceNamespaceId} defaults to the
 *     target namespace, empty clone lists are rejected with {@code NO_SELECTED_CONFIG}, and unknown source ids
 *     return {@code DATA_EMPTY} without creating data.</li>
 *     <li>Exception/error handling: request-parameter errors return HTTP 400 and business failures keep the v3
 *     {@code Result} envelope instead of leaking HTTP 500.</li>
 * </ul>
 *
 * @author xiweng.yy
 */
public class ConfigCloneAdminApiOpenApiITCase extends ConfigAdminApiBaseITCase {

    @Test
    public void testCloneConfigToTargetIdentity() throws Exception {
        String sourceDataId = randomDataId("clone-source");
        String sourceGroup = randomGroupName("clone-source");
        String targetDataId = randomDataId("clone-target");
        String targetGroup = randomGroupName("clone-target");
        String content = "clone-content";
        publishConfig(sourceDataId, sourceGroup, "", content, ConfigType.JSON.getType(), "clone desc", "");
        addCleanup(() -> deleteConfigQuietly(sourceDataId, sourceGroup, ""));
        addCleanup(() -> deleteConfigQuietly(targetDataId, targetGroup, ""));
        JsonNode source = queryConfig(sourceDataId, sourceGroup, "").get("data");

        String body = "[{\"configId\":" + source.get("id").asText() + ",\"targetDataId\":\""
                + targetDataId + "\",\"targetGroupName\":\"" + targetGroup + "\"}]";
        JsonNode root = postJsonOk(ADMIN_CONFIG_CLONE_PATH,
                Query.newInstance().addParam("namespaceId", DEFAULT_NAMESPACE).addParam("policy", "OVERWRITE"),
                body);
        assertTrue(root.get("data").get("succCount").asInt() >= 1, root.toString());

        JsonNode cloned = queryConfig(targetDataId, targetGroup, DEFAULT_NAMESPACE).get("data");
        assertConfigDetail(cloned, targetDataId, targetGroup, DEFAULT_NAMESPACE, content, ConfigType.JSON.getType());
    }

    @Test
    public void testCloneConfigWithSourceNamespace() throws Exception {
        String sourceNamespaceId = randomNamespaceId("clone_src");
        String targetNamespaceId = randomNamespaceId("clone_tgt");
        String sourceDataId = randomDataId("clone-source-ns");
        String sourceGroup = randomGroupName("clone-source-ns");
        String targetDataId = randomDataId("clone-target-ns");
        String targetGroup = randomGroupName("clone-target-ns");
        String content = "clone-source-namespace-content";
        createNamespace(sourceNamespaceId);
        createNamespace(targetNamespaceId);
        addCleanup(() -> deleteNamespaceQuietly(sourceNamespaceId));
        addCleanup(() -> deleteNamespaceQuietly(targetNamespaceId));
        publishConfig(sourceDataId, sourceGroup, sourceNamespaceId, content, ConfigType.JSON.getType(), "clone desc",
                "");
        addCleanup(() -> deleteConfigQuietly(sourceDataId, sourceGroup, sourceNamespaceId));
        addCleanup(() -> deleteConfigQuietly(targetDataId, targetGroup, targetNamespaceId));
        JsonNode source = queryConfig(sourceDataId, sourceGroup, sourceNamespaceId).get("data");

        String body = "[{\"configId\":" + source.get("id").asText() + ",\"targetDataId\":\""
                + targetDataId + "\",\"targetGroupName\":\"" + targetGroup + "\"}]";
        JsonNode root = postJsonOk(ADMIN_CONFIG_CLONE_PATH,
                Query.newInstance().addParam("namespaceId", targetNamespaceId)
                        .addParam("sourceNamespaceId", sourceNamespaceId).addParam("policy", "OVERWRITE"),
                body);
        assertTrue(root.get("data").get("succCount").asInt() >= 1, root.toString());

        JsonNode cloned = queryConfig(targetDataId, targetGroup, targetNamespaceId).get("data");
        assertConfigDetail(cloned, targetDataId, targetGroup, targetNamespaceId, content, ConfigType.JSON.getType());
    }

    @Test
    public void testCloneConfigSkipsIdsOutsideSourceNamespace() throws Exception {
        String sourceNamespaceId = randomNamespaceId("clone_src_mismatch");
        String targetNamespaceId = randomNamespaceId("clone_tgt_mismatch");
        String otherNamespaceId = randomNamespaceId("clone_other_mismatch");
        String otherDataId = randomDataId("clone-other-ns");
        String otherGroup = randomGroupName("clone-other-ns");
        String targetDataId = randomDataId("clone-target-mismatch");
        String targetGroup = randomGroupName("clone-target-mismatch");
        createNamespace(sourceNamespaceId);
        createNamespace(targetNamespaceId);
        createNamespace(otherNamespaceId);
        addCleanup(() -> deleteNamespaceQuietly(sourceNamespaceId));
        addCleanup(() -> deleteNamespaceQuietly(targetNamespaceId));
        addCleanup(() -> deleteNamespaceQuietly(otherNamespaceId));
        publishConfig(otherDataId, otherGroup, otherNamespaceId, "other namespace content");
        addCleanup(() -> deleteConfigQuietly(otherDataId, otherGroup, otherNamespaceId));
        addCleanup(() -> deleteConfigQuietly(targetDataId, targetGroup, targetNamespaceId));
        JsonNode other = queryConfig(otherDataId, otherGroup, otherNamespaceId).get("data");

        String body = "[{\"configId\":" + other.get("id").asText() + ",\"targetDataId\":\""
                + targetDataId + "\",\"targetGroupName\":\"" + targetGroup + "\"}]";
        HttpResponse response = postJsonRaw(ADMIN_CONFIG_CLONE_PATH,
                Query.newInstance().addParam("namespaceId", targetNamespaceId)
                        .addParam("sourceNamespaceId", sourceNamespaceId),
                body);

        assertEquals(200, response.code(), response.body());
        JsonNode root = JacksonUtils.toObj(response.body());
        assertFailureResult(root, ErrorCode.DATA_EMPTY, "succCount");
    }

    @Test
    public void testCloneValidationAndBusinessFailuresReturnResultEnvelope() throws Exception {
        assertError(postJsonRaw(ADMIN_CONFIG_CLONE_PATH, Query.newInstance(), "[]"), 400,
                ErrorCode.PARAMETER_MISSING, "namespaceId");

        HttpResponse emptyResponse = postJsonRaw(ADMIN_CONFIG_CLONE_PATH,
                Query.newInstance().addParam("namespaceId", DEFAULT_NAMESPACE), "[]");
        assertEquals(200, emptyResponse.code(), emptyResponse.body());
        JsonNode emptyRoot = JacksonUtils.toObj(emptyResponse.body());
        assertFailureResult(emptyRoot, ErrorCode.NO_SELECTED_CONFIG, "succCount");

        HttpResponse absentSourceResponse = postJsonRaw(ADMIN_CONFIG_CLONE_PATH,
                Query.newInstance().addParam("namespaceId", DEFAULT_NAMESPACE),
                "[{\"configId\":999999999999,\"targetDataId\":\"absent\",\"targetGroupName\":\"absent\"}]");
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
