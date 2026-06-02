/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.test.openapi.client.config;

import com.alibaba.nacos.api.config.remote.response.ConfigQueryResponse;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.MD5Utils;
import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.test.openapi.OpenApiBaseITCase;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for {@link com.alibaba.nacos.config.server.controller.v3.ConfigOpenApiController} (client Open API:
 * {@code GET /nacos/v3/client/cs/config}), aligned with
 * <a href="https://nacos.io/swagger/client/zh/api.json">Nacos HTTP 客户端 API</a>.
 *
 * <p>Scenario coverage:
 * <ul>
 *     <li>Expected capability: a config published through the admin API can be queried through the client OpenAPI with
 *     content, md5, lastModified, contentType, and beta response fields.</li>
 *     <li>Boundary/validation: omitted {@code namespaceId} uses the public namespace; wrong namespace returns a wrapped
 *     not-found result; {@code dataId} and {@code groupName} are required; v3 does not accept the legacy {@code group}
 *     parameter as a replacement for {@code groupName}; invalid namespace values are rejected by parameter checking.</li>
 *     <li>Exception/error handling: absent config returns HTTP 2xx with {@code RESOURCE_NOT_FOUND}; invalid namespace
 *     returns HTTP 400 with wrapped {@code Result} fields; required-field validation currently returns controlled
 *     HTTP 400 text from this controller path rather than HTTP 500.</li>
 * </ul>
 *
 * @author xiweng.yy
 */
public class ConfigOpenApiITCase extends OpenApiBaseITCase {
    
    private static final String CLIENT_CONFIG_PATH = nacosPath(Constants.CONFIG_V3_CLIENT_API_PATH);
    
    private static final String ADMIN_CONFIG_PATH = nacosPath(Constants.CONFIG_ADMIN_V3_PATH);
    
    private static final String DEFAULT_NAMESPACE = "public";
    
    private static final String TEST_GROUP = "openapi_it_group";
    
    @Test
    public void testGetConfigWhenNotExists() throws Exception {
        String dataId = "openapi_it_absent_" + UUID.randomUUID();
        HttpRestResult<String> httpResult = getConfig(dataId, TEST_GROUP, DEFAULT_NAMESPACE);
        logger().debug("getConfig result: {}", JacksonUtils.toJson(httpResult));
        assertTrue(httpResult.ok(), "HTTP status should be 2xx");
        assertNotNull(httpResult.getData());
        Result<ConfigQueryResponse> actual =
            JacksonUtils.toObj(httpResult.getData(), new TypeReference<>() {
            });
        assertEquals(ErrorCode.RESOURCE_NOT_FOUND.getCode(), actual.getCode());
    }
    
    @Test
    public void testGetConfigSuccessAfterPublish() throws Exception {
        String dataId = "openapi_it_ok_" + UUID.randomUUID();
        String content = "hello-openapi-it-" + UUID.randomUUID();
        assertTrue(publishConfig(dataId, TEST_GROUP, "", content));
        addCleanup(() -> deleteConfig(dataId, TEST_GROUP, ""));
        Result<ConfigQueryResponse> actual = null;
        int retryTime = 10;
        while (retryTime-- > 0) {
            HttpRestResult<String> httpResult = getConfig(dataId, TEST_GROUP, DEFAULT_NAMESPACE);
            assertTrue(httpResult.ok());
            actual = JacksonUtils.toObj(httpResult.getData(), new TypeReference<>() {
            });
            if (ErrorCode.SUCCESS.getCode().equals(actual.getCode())) {
                break;
            }
            // After publish success, nacos will async cache into disk from storage.
            TimeUnit.MILLISECONDS.sleep(100);
        }
        assertEquals(ErrorCode.SUCCESS.getCode(), actual.getCode(),
            "Expected success after retry, but not still failed.");
        assertNotNull(actual.getData());
        assertEquals(content, actual.getData().getContent());
        assertEquals(MD5Utils.md5Hex(content, StandardCharsets.UTF_8.name()),
            actual.getData().getMd5());
        assertTrue(actual.getData().getLastModified() > 0L);
        assertNotNull(actual.getData().getContentType());
        assertFalse(actual.getData().isBeta());
    }
    
    @Test
    public void testGetConfigOmitNamespaceUsesPublic() throws Exception {
        String dataId = "openapi_it_public_" + UUID.randomUUID();
        String content = "ns-default";
        assertTrue(publishConfig(dataId, TEST_GROUP, "", content));
        addCleanup(() -> deleteConfig(dataId, TEST_GROUP, ""));
        Query query =
            Query.newInstance().addParam("dataId", dataId).addParam("groupName", TEST_GROUP);
        Result<ConfigQueryResponse> actual = null;
        int retryTime = 10;
        while (retryTime-- > 0) {
            HttpRestResult<String> httpResult =
                nacosRestTemplate.get(url(CLIENT_CONFIG_PATH), Header.EMPTY, query,
                    String.class);
            assertTrue(httpResult.ok());
            actual = JacksonUtils.toObj(httpResult.getData(), new TypeReference<>() {
            });
            if (ErrorCode.SUCCESS.getCode().equals(actual.getCode())) {
                break;
            }
            // After publish success, nacos will async cache into disk from storage.
            TimeUnit.MILLISECONDS.sleep(100);
        }
        assertEquals(ErrorCode.SUCCESS.getCode(), actual.getCode(),
            "Expected success after retry, but not still failed.");
        assertEquals(content, actual.getData().getContent());
    }
    
    @Test
    public void testGetConfigWrongNamespaceNotFound() throws Exception {
        String dataId = "openapi_it_tenant_" + UUID.randomUUID();
        String alienNamespace = "openapi_it_other_ns_" + UUID.randomUUID();
        assertTrue(publishConfig(dataId, TEST_GROUP, "", "only-in-public"));
        addCleanup(() -> deleteConfig(dataId, TEST_GROUP, ""));
        HttpRestResult<String> httpResult = getConfig(dataId, TEST_GROUP, alienNamespace);
        assertTrue(httpResult.ok());
        Result<ConfigQueryResponse> actual =
            JacksonUtils.toObj(httpResult.getData(), new TypeReference<>() {
            });
        assertEquals(ErrorCode.RESOURCE_NOT_FOUND.getCode(), actual.getCode());
    }
    
    @Test
    public void testGetConfigMissingDataIdReturnsBadRequest() throws Exception {
        Query query = Query.newInstance().addParam("groupName", TEST_GROUP).addParam("namespaceId",
            DEFAULT_NAMESPACE);
        assertPlainBadRequest(getRaw(CLIENT_CONFIG_PATH, query), "dataId");
    }
    
    @Test
    public void testGetConfigMissingGroupNameReturnsBadRequest() throws Exception {
        Query query = Query.newInstance().addParam("dataId", "any").addParam("namespaceId",
            DEFAULT_NAMESPACE);
        assertPlainBadRequest(getRaw(CLIENT_CONFIG_PATH, query), "groupName");
    }
    
    @Test
    public void testGetConfigLegacyGroupParameterDoesNotReplaceGroupName() throws Exception {
        Query query = Query.newInstance().addParam("dataId", "any").addParam("group", TEST_GROUP)
            .addParam("namespaceId", DEFAULT_NAMESPACE);
        assertPlainBadRequest(getRaw(CLIENT_CONFIG_PATH, query), "groupName");
    }
    
    @Test
    public void testGetConfigInvalidNamespaceReturnsBadRequest() throws Exception {
        Query query =
            Query.newInstance().addParam("dataId", "any").addParam("groupName", TEST_GROUP)
                .addParam("namespaceId", "invalid namespace");
        assertBadRequestResult(getRaw(CLIENT_CONFIG_PATH, query),
            ErrorCode.PARAMETER_VALIDATE_ERROR, "namespaceId");
    }
    
    private HttpRestResult<String> getConfig(String dataId, String group, String namespace)
        throws Exception {
        Query query = Query.newInstance().addParam("dataId", dataId).addParam("groupName", group)
            .addParam("namespaceId", namespace);
        return nacosRestTemplate.get(url(CLIENT_CONFIG_PATH), Header.EMPTY, query, String.class);
    }
    
    private void assertBadRequestResult(HttpResponse response, ErrorCode errorCode,
        String expectedData)
        throws Exception {
        assertEquals(400, response.code(), response.body());
        Result<String> actual = JacksonUtils.toObj(response.body(), new TypeReference<>() {
        });
        assertEquals(errorCode.getCode(), actual.getCode(), response.body());
        assertNotNull(actual.getMessage(), response.body());
        assertNotNull(actual.getData(), response.body());
        assertTrue(actual.getData().contains(expectedData), response.body());
    }
    
    private void assertPlainBadRequest(HttpResponse response, String expectedField) {
        assertEquals(400, response.code(), response.body());
        assertTrue(response.body().contains("Required parameter"), response.body());
        assertTrue(response.body().contains(expectedField), response.body());
    }
    
    private boolean publishConfig(String dataId, String groupName, String namespaceId,
        String content) throws Exception {
        Map<String, String> form = buildPublishForm(dataId, groupName, namespaceId, content);
        HttpRestResult<String> httpResult =
            nacosRestTemplate.postForm(url(ADMIN_CONFIG_PATH), Header.EMPTY, form,
                String.class);
        assertTrue(httpResult.ok(),
            "publish HTTP status should be 2xx, body=" + httpResult.getData());
        JsonNode root = JacksonUtils.toObj(httpResult.getData());
        assertNotNull(root);
        assertEquals(ErrorCode.SUCCESS.getCode(), root.get("code").asInt(), httpResult.getData());
        return root.get("data").asBoolean();
    }
    
    private void deleteConfig(String dataId, String groupName, String namespaceId)
        throws Exception {
        Query query =
            Query.newInstance().addParam("dataId", dataId).addParam("groupName", groupName)
                .addParam("namespaceId", namespaceId).addParam("tag", "");
        HttpRestResult<String> httpResult =
            nacosRestTemplate.delete(url(ADMIN_CONFIG_PATH), Header.EMPTY, query,
                String.class);
        if (!httpResult.ok()) {
            logger().warn("deleteConfig non-OK: code={} body={}", httpResult.getCode(),
                httpResult.getData());
        }
    }
    
    /**
     * Mirrors {@link com.alibaba.nacos.config.server.controller.v3.ConfigControllerV3Test#testPublishConfig} form fields.
     */
    private static Map<String, String> buildPublishForm(String dataId, String groupName,
        String namespaceId,
        String content) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("dataId", dataId);
        form.put("groupName", groupName);
        form.put("namespaceId", namespaceId);
        form.put("content", content);
        form.put("tag", "");
        form.put("appName", "");
        form.put("src_user", "");
        form.put("config_tags", "");
        form.put("desc", "");
        form.put("use", "");
        form.put("effect", "");
        form.put("type", "");
        form.put("schema", "");
        return form;
    }
}
