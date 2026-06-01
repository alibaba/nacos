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

package com.alibaba.nacos.test.openapi.client.ai;

import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.test.openapi.OpenApiBaseITCase;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Shared helpers for AI client OpenAPI integration tests.
 *
 * @author xiweng.yy
 */
public abstract class AiOpenApiBaseITCase extends OpenApiBaseITCase {
    
    protected static final String DEFAULT_NAMESPACE = "public";
    
    protected JsonNode postFormOk(String path, Map<String, String> form) throws Exception {
        HttpRestResult<String> restResult = nacosRestTemplate.postForm(url(path), Header.EMPTY, form, String.class);
        assertTrue(restResult.ok(), "HTTP status should be 2xx, code=" + restResult.getCode() + ", body="
                + restResult.getData() + ", message=" + restResult.getMessage());
        JsonNode root = JacksonUtils.toObj(restResult.getData());
        assertSuccess(root);
        return root;
    }
    
    protected JsonNode postFormOk(String path, Query query) throws Exception {
        HttpRestResult<String> restResult = nacosRestTemplate.postForm(url(path), Header.EMPTY, query,
                Collections.emptyMap(), String.class);
        assertTrue(restResult.ok(), "HTTP status should be 2xx, body=" + restResult.getData());
        JsonNode root = JacksonUtils.toObj(restResult.getData());
        assertSuccess(root);
        return root;
    }
    
    protected JsonNode putFormOk(String path, Map<String, String> form) throws Exception {
        HttpRestResult<String> restResult = nacosRestTemplate.putForm(url(path), Header.EMPTY, form, String.class);
        assertTrue(restResult.ok(), "HTTP status should be 2xx, code=" + restResult.getCode() + ", body="
                + restResult.getData() + ", message=" + restResult.getMessage());
        JsonNode root = JacksonUtils.toObj(restResult.getData());
        assertSuccess(root);
        return root;
    }
    
    protected JsonNode getJsonOk(String path, Query query) throws Exception {
        HttpRestResult<String> restResult = nacosRestTemplate.get(url(path), Header.EMPTY, query, String.class);
        assertTrue(restResult.ok(), "HTTP status should be 2xx, body=" + restResult.getData());
        JsonNode root = JacksonUtils.toObj(restResult.getData());
        assertSuccess(root);
        return root;
    }
    
    protected void deleteQuietly(String path, Query query) throws Exception {
        HttpRestResult<String> restResult = nacosRestTemplate.delete(url(path), Header.EMPTY, query, String.class);
        if (!restResult.ok()) {
            logger().warn("delete non-OK: path={} code={} body={}", path, restResult.getCode(), restResult.getData());
        }
    }
    
    protected void assertSuccess(JsonNode root) {
        assertNotNull(root);
        assertEquals(ErrorCode.SUCCESS.getCode(), root.get("code").asInt(), root.toString());
        assertEquals(ErrorCode.SUCCESS.getMsg(), root.get("message").asText(), root.toString());
    }
    
    protected void assertError(HttpResponse response, int expectedStatus, ErrorCode expectedCode, String expectedData)
            throws Exception {
        assertEquals(expectedStatus, response.code(), response.body());
        JsonNode root = JacksonUtils.toObj(response.body());
        assertNotNull(root, response.body());
        assertEquals(expectedCode.getCode(), root.get("code").asInt(), response.body());
        assertNotNull(root.get("message").asText(), response.body());
        assertTrue(root.get("data").asText().contains(expectedData), response.body());
    }
}
