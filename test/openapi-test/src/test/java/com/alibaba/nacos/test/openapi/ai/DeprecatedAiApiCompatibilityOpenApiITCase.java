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

package com.alibaba.nacos.test.openapi.ai;

import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.http.client.NacosRestTemplate;
import com.alibaba.nacos.common.http.client.request.DefaultHttpClientRequest;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for deprecated v3 AI APIs controlled by the shared compatibility gate.
 *
 * <p>The v3.2.4 maintenance branch uses self-contained OpenAPI integration tests, so this case
 * covers the six gated endpoints and verifies that their canonical replacements remain active.
 *
 * @author xiweng.yy
 */
public class DeprecatedAiApiCompatibilityOpenApiITCase {
    
    private static final Logger LOGGER =
        LoggerFactory.getLogger(DeprecatedAiApiCompatibilityOpenApiITCase.class);
    
    private static final String NACOS_HOST = System.getProperty("nacos.host", "127.0.0.1");
    
    private static final String NACOS_PORT = System.getProperty("nacos.port", "8848");
    
    private static final String CONSOLE_PORT = System.getProperty("nacos.console.port", "8080");
    
    private static final String SERVER_BASE_URL =
        "http://" + NACOS_HOST + ":" + NACOS_PORT + "/nacos";
    
    private static final String CONSOLE_BASE_URL =
        "http://" + NACOS_HOST + ":" + CONSOLE_PORT;
    
    private static final String ADMIN_PIPELINE_PATH = "/v3/admin/ai/pipelines";
    
    private static final String CONSOLE_PIPELINE_PATH = "/v3/console/ai/pipelines";
    
    private static final String CONSOLE_MCP_IMPORT_PATH = "/v3/console/ai/mcp/import";
    
    private static final String CONSOLE_IMPORT_PATH = "/v3/console/ai/import";
    
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
    public void testAdminPipelineCompatibilityGate() throws Exception {
        Query listQuery = pipelineListQuery();
        assertSuccessPage(get(SERVER_BASE_URL, ADMIN_PIPELINE_PATH + "/list", listQuery));
        assertGone(get(SERVER_BASE_URL, ADMIN_PIPELINE_PATH, listQuery),
            "GET /v3/admin/ai/pipelines/list");
        
        String absentPipelineId = "pipeline-" + UUID.randomUUID();
        assertError(get(SERVER_BASE_URL, ADMIN_PIPELINE_PATH + "/detail",
            Query.newInstance().addParam("pipelineId", absentPipelineId)), 404,
            ErrorCode.RESOURCE_NOT_FOUND);
        assertGone(get(SERVER_BASE_URL, ADMIN_PIPELINE_PATH + "/" + absentPipelineId,
            Query.newInstance()),
            "GET /v3/admin/ai/pipelines/detail?pipelineId={pipelineId}");
    }
    
    @Test
    public void testConsolePipelineCompatibilityGate() throws Exception {
        Query listQuery = pipelineListQuery();
        assertSuccessPage(get(CONSOLE_BASE_URL, CONSOLE_PIPELINE_PATH + "/list", listQuery));
        assertGone(get(CONSOLE_BASE_URL, CONSOLE_PIPELINE_PATH, listQuery),
            "GET /v3/console/ai/pipelines/list");
        
        String absentPipelineId = "pipeline-" + UUID.randomUUID();
        assertError(get(CONSOLE_BASE_URL, CONSOLE_PIPELINE_PATH + "/detail",
            Query.newInstance().addParam("pipelineId", absentPipelineId)), 404,
            ErrorCode.RESOURCE_NOT_FOUND);
        assertGone(get(CONSOLE_BASE_URL, CONSOLE_PIPELINE_PATH + "/" + absentPipelineId,
            Query.newInstance()),
            "GET /v3/console/ai/pipelines/detail?pipelineId={pipelineId}");
    }
    
    @Test
    public void testLegacyMcpImportCompatibilityGate() throws Exception {
        assertGone(post(CONSOLE_BASE_URL, CONSOLE_MCP_IMPORT_PATH + "/validate"),
            "POST /v3/console/ai/import/validate");
        assertGone(post(CONSOLE_BASE_URL, CONSOLE_MCP_IMPORT_PATH + "/execute"),
            "POST /v3/console/ai/import/execute");
        
        assertEquals(400, post(CONSOLE_BASE_URL, CONSOLE_IMPORT_PATH + "/validate").getCode());
        assertEquals(400, post(CONSOLE_BASE_URL, CONSOLE_IMPORT_PATH + "/execute").getCode());
    }
    
    private Query pipelineListQuery() {
        return Query.newInstance().addParam("resourceType", "prompt")
            .addParam("resourceName", "pipeline-" + UUID.randomUUID())
            .addParam("namespaceId", "public").addParam("version", "1.0.0")
            .addParam("pageNo", "1").addParam("pageSize", "10");
    }
    
    private HttpRestResult<String> get(String baseUrl, String path, Query query)
        throws Exception {
        return nacosRestTemplate.get(baseUrl + path, Header.EMPTY, query, String.class);
    }
    
    private HttpRestResult<String> post(String baseUrl, String path) throws Exception {
        return nacosRestTemplate.postForm(baseUrl + path, Header.EMPTY, Collections.emptyMap(),
            String.class);
    }
    
    private void assertSuccessPage(HttpRestResult<String> response) {
        assertEquals(200, response.getCode(), response.getData());
        JsonNode root = JacksonUtils.toObj(response.getData());
        assertEquals(ErrorCode.SUCCESS.getCode().intValue(), root.get("code").asInt(),
            response.getData());
        JsonNode data = root.get("data");
        assertNotNull(data, response.getData());
        assertTrue(data.has("pageItems"), response.getData());
    }
    
    private void assertGone(HttpRestResult<String> response, String replacement) {
        assertError(response, 410, ErrorCode.API_DEPRECATED);
        assertTrue(response.getData().contains(replacement), response.getData());
    }
    
    private void assertError(HttpRestResult<String> response, int httpCode, ErrorCode errorCode) {
        assertEquals(httpCode, response.getCode(), response.getData());
        JsonNode root = JacksonUtils.toObj(response.getData());
        assertEquals(errorCode.getCode().intValue(), root.get("code").asInt(), response.getData());
    }
}
