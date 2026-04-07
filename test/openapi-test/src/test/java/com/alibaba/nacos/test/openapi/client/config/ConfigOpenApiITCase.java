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
import com.alibaba.nacos.common.http.client.NacosRestTemplate;
import com.alibaba.nacos.common.http.client.request.DefaultHttpClientRequest;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.MD5Utils;
import com.alibaba.nacos.config.server.constant.Constants;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for {@link com.alibaba.nacos.config.server.controller.v3.ConfigOpenApiController} (client Open API:
 * {@code GET /nacos/v3/client/cs/config}), aligned with
 * <a href="https://nacos.io/swagger/client/zh/api.json">Nacos HTTP 客户端 API</a>.
 *
 * <p><b>How this fits the new IT pipeline ({@code .github/workflows/it-new.yml})</b>
 * <ol>
 *     <li>Workflow builds the server distribution, patches {@code application.properties} (auth secrets / identity),
 *     starts standalone Nacos, then waits on console port {@code 8080}.</li>
 *     <li>{@code mvn clean verify -Pintegration-test} under {@code test/} runs Failsafe; {@code openapi-test} uses
 *     system properties {@code nacos.host} / {@code nacos.port} (default {@code 127.0.0.1:8848}) to call the
 *     <em>main</em> server port.</li>
 *     <li>Config is published and removed through the <em>admin</em> API ({@code /v3/admin/cs/config}), because the
 *     client API is read-only by design.</li>
 *     <li>With default {@code nacos.core.auth.enabled=false}, {@code @Secured} does not require a token; if your
 *     environment enables auth, extend these tests to attach {@code accessToken} like other IT modules.</li>
 * </ol>
 *
 * @author xiweng.yy
 */
public class ConfigOpenApiITCase {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigOpenApiITCase.class);
    
    private static final String NACOS_HOST = System.getProperty("nacos.host", "127.0.0.1");
    
    private static final String NACOS_PORT = System.getProperty("nacos.port", "8848");
    
    private static final String BASE_URL = "http://" + NACOS_HOST + ":" + NACOS_PORT;
    
    private static final String CLIENT_CONFIG_PATH = "/nacos" + Constants.CONFIG_V3_CLIENT_API_PATH;
    
    private static final String ADMIN_CONFIG_PATH = "/nacos" + Constants.CONFIG_ADMIN_V3_PATH;
    
    private static final String DEFAULT_NAMESPACE = "public";
    
    private static final String TEST_GROUP = "openapi_it_group";
    
    private CloseableHttpClient httpClient;
    
    private NacosRestTemplate nacosRestTemplate;
    
    @BeforeEach
    public void setUp() throws Exception {
        httpClient = HttpClientBuilder.create().build();
        nacosRestTemplate = new NacosRestTemplate(LOGGER,
                new DefaultHttpClientRequest(httpClient, RequestConfig.DEFAULT));
    }
    
    @AfterEach
    public void tearDown() throws Exception {
        nacosRestTemplate.close();
    }
    
    @Test
    public void testGetConfigWhenNotExists() throws Exception {
        String dataId = "openapi_it_absent_" + UUID.randomUUID();
        HttpRestResult<String> httpResult = getConfig(dataId, TEST_GROUP, DEFAULT_NAMESPACE);
        LOGGER.debug("getConfig result: {}", JacksonUtils.toJson(httpResult));
        assertTrue(httpResult.ok(), "HTTP status should be 2xx");
        assertNotNull(httpResult.getData());
        Result<ConfigQueryResponse> actual = JacksonUtils.toObj(httpResult.getData(), new TypeReference<>() {
        });
        assertEquals(ErrorCode.RESOURCE_NOT_FOUND.getCode(), actual.getCode());
    }
    
    @Test
    public void testGetConfigSuccessAfterPublish() throws Exception {
        String dataId = "openapi_it_ok_" + UUID.randomUUID();
        String content = "hello-openapi-it-" + UUID.randomUUID();
        try {
            assertTrue(publishConfig(dataId, TEST_GROUP, "", content));
            HttpRestResult<String> httpResult = getConfig(dataId, TEST_GROUP, DEFAULT_NAMESPACE);
            assertTrue(httpResult.ok());
            Result<ConfigQueryResponse> actual = JacksonUtils.toObj(httpResult.getData(), new TypeReference<>() {
            });
            assertEquals(ErrorCode.SUCCESS.getCode(), actual.getCode());
            assertNotNull(actual.getData());
            assertEquals(content, actual.getData().getContent());
            assertEquals(MD5Utils.md5Hex(content, StandardCharsets.UTF_8.name()), actual.getData().getMd5());
            assertTrue(actual.getData().getLastModified() > 0L);
            assertFalse(actual.getData().isBeta());
        } finally {
            deleteConfig(dataId, TEST_GROUP, "");
        }
    }
    
    @Test
    public void testGetConfigOmitNamespaceUsesPublic() throws Exception {
        String dataId = "openapi_it_public_" + UUID.randomUUID();
        String content = "ns-default";
        try {
            assertTrue(publishConfig(dataId, TEST_GROUP, "", content));
            String url = BASE_URL + CLIENT_CONFIG_PATH;
            Query query = Query.newInstance().addParam("dataId", dataId).addParam("groupName", TEST_GROUP);
            HttpRestResult<String> httpResult = nacosRestTemplate.get(url, Header.EMPTY, query, String.class);
            assertTrue(httpResult.ok());
            Result<ConfigQueryResponse> actual = JacksonUtils.toObj(httpResult.getData(), new TypeReference<>() {
            });
            assertEquals(ErrorCode.SUCCESS.getCode(), actual.getCode());
            assertEquals(content, actual.getData().getContent());
        } finally {
            deleteConfig(dataId, TEST_GROUP, "");
        }
    }
    
    @Test
    public void testGetConfigWrongNamespaceNotFound() throws Exception {
        String dataId = "openapi_it_tenant_" + UUID.randomUUID();
        String alienNamespace = "openapi_it_other_ns_" + UUID.randomUUID();
        try {
            assertTrue(publishConfig(dataId, TEST_GROUP, "", "only-in-public"));
            HttpRestResult<String> httpResult = getConfig(dataId, TEST_GROUP, alienNamespace);
            assertTrue(httpResult.ok());
            Result<ConfigQueryResponse> actual = JacksonUtils.toObj(httpResult.getData(), new TypeReference<>() {
            });
            assertEquals(ErrorCode.RESOURCE_NOT_FOUND.getCode(), actual.getCode());
        } finally {
            deleteConfig(dataId, TEST_GROUP, "");
        }
    }
    
    @Test
    public void testGetConfigMissingDataIdReturnsBadRequest() throws Exception {
        String url = BASE_URL + CLIENT_CONFIG_PATH;
        Query query = Query.newInstance().addParam("groupName", TEST_GROUP).addParam("namespaceId", DEFAULT_NAMESPACE);
        HttpRestResult<String> httpResult = nacosRestTemplate.get(url, Header.EMPTY, query, String.class);
        assertEquals(400, httpResult.getCode());
    }
    
    @Test
    public void testGetConfigMissingGroupNameReturnsBadRequest() throws Exception {
        String url = BASE_URL + CLIENT_CONFIG_PATH;
        Query query = Query.newInstance().addParam("dataId", "any").addParam("namespaceId", DEFAULT_NAMESPACE);
        HttpRestResult<String> httpResult = nacosRestTemplate.get(url, Header.EMPTY, query, String.class);
        assertEquals(400, httpResult.getCode());
    }
    
    private HttpRestResult<String> getConfig(String dataId, String group, String namespace) throws Exception {
        String url = BASE_URL + CLIENT_CONFIG_PATH;
        Query query = Query.newInstance().addParam("dataId", dataId).addParam("groupName", group)
                .addParam("namespaceId", namespace);
        return nacosRestTemplate.get(url, Header.EMPTY, query, String.class);
    }
    
    private boolean publishConfig(String dataId, String groupName, String namespaceId, String content) throws Exception {
        String url = BASE_URL + ADMIN_CONFIG_PATH;
        Map<String, String> form = buildPublishForm(dataId, groupName, namespaceId, content);
        HttpRestResult<String> httpResult = nacosRestTemplate.postForm(url, Header.EMPTY, form, String.class);
        assertTrue(httpResult.ok(), "publish HTTP status should be 2xx, body=" + httpResult.getData());
        JsonNode root = JacksonUtils.toObj(httpResult.getData());
        assertNotNull(root);
        assertEquals(ErrorCode.SUCCESS.getCode(), root.get("code").asInt(), httpResult.getData());
        return root.get("data").asBoolean();
    }
    
    private void deleteConfig(String dataId, String groupName, String namespaceId) throws Exception {
        String url = BASE_URL + ADMIN_CONFIG_PATH;
        Query query = Query.newInstance().addParam("dataId", dataId).addParam("groupName", groupName)
                .addParam("namespaceId", namespaceId).addParam("tag", "");
        HttpRestResult<String> httpResult = nacosRestTemplate.delete(url, Header.EMPTY, query, String.class);
        if (!httpResult.ok()) {
            LOGGER.warn("deleteConfig non-OK: code={} body={}", httpResult.getCode(), httpResult.getData());
        }
    }
    
    /**
     * Mirrors {@link com.alibaba.nacos.config.server.controller.v3.ConfigControllerV3Test#testPublishConfig} form fields.
     */
    private static Map<String, String> buildPublishForm(String dataId, String groupName, String namespaceId,
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
