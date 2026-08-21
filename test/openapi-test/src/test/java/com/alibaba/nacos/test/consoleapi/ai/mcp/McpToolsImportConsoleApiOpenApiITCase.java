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

package com.alibaba.nacos.test.consoleapi.ai.mcp;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for {@code GET /v3/console/ai/mcp/importToolsFromMcp}.
 *
 * <p>Scenario coverage:
 * <ul>
 *     <li>Expected capability: private or local MCP targets are rejected with an operator-facing
 *     remediation message before a connection is attempted. Public-target protocol success and
 *     operator-approved private-target success require an external MCP runtime or non-default
 *     server configuration and are not exercised by this standalone suite.</li>
 *     <li>Boundary/validation: transportType, baseUrl, and endpoint are required; unsupported
 *     transports, invalid base URLs, and origin-overriding endpoints return wrapped business
 *     errors. Optional authToken forwarding is intentionally not exercised because the rejected
 *     local target must not receive a request.</li>
 *     <li>Exception/error handling: policy and validation failures return controlled HTTP and
 *     {@code Result} contracts instead of an unhandled server exception.</li>
 * </ul>
 *
 * @author Nacos
 */
public class McpToolsImportConsoleApiOpenApiITCase {
    
    private static final Logger LOGGER =
        LoggerFactory.getLogger(McpToolsImportConsoleApiOpenApiITCase.class);
    
    private static final String NACOS_HOST = System.getProperty("nacos.host", "127.0.0.1");
    
    private static final String CONSOLE_PORT = System.getProperty("nacos.console.port", "8080");
    
    private static final String CONSOLE_BASE_URL =
        "http://" + NACOS_HOST + ":" + CONSOLE_PORT;
    
    private static final String IMPORT_TOOLS_PATH =
        "/v3/console/ai/mcp/importToolsFromMcp";
    
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
    public void testImportToolsRejectsPrivateEndpointByDefault() throws Exception {
        HttpRestResult<String> response = get(Query.newInstance()
            .addParam("transportType", "mcp-streamable")
            .addParam("baseUrl", "http://127.0.0.1:1")
            .addParam("endpoint", "/mcp")
            .addParam("authToken", "must-not-be-forwarded"));
        
        JsonNode root = assertWrappedError(response, 200, ErrorCode.ACCESS_DENIED);
        assertTrue(root.get("message").asText().contains(
            "nacos.console.ai.mcp.import.allowed-private-addresses"), responseBody(response));
        assertTrue(root.get("message").asText().contains("private or local"),
            responseBody(response));
    }
    
    @Test
    public void testImportToolsValidatesRequiredParameters() throws Exception {
        assertMissingParameter(get(Query.newInstance()
            .addParam("baseUrl", "http://127.0.0.1:1")
            .addParam("endpoint", "/mcp")), "transportType");
        assertMissingParameter(get(Query.newInstance()
            .addParam("transportType", "mcp-streamable")
            .addParam("endpoint", "/mcp")), "baseUrl");
        assertMissingParameter(get(Query.newInstance()
            .addParam("transportType", "mcp-streamable")
            .addParam("baseUrl", "http://127.0.0.1:1")), "endpoint");
    }
    
    @Test
    public void testImportToolsRejectsUnsupportedTransportBeforeEndpointValidation()
        throws Exception {
        HttpRestResult<String> response = get(Query.newInstance()
            .addParam("transportType", "stdio")
            .addParam("baseUrl", "http://127.0.0.1:1")
            .addParam("endpoint", "/mcp"));
        
        JsonNode root = assertWrappedError(response, 200, ErrorCode.SERVER_ERROR);
        assertTrue(root.get("message").asText().contains("Unsupported transport type"),
            responseBody(response));
    }
    
    @Test
    public void testImportToolsRejectsInvalidBaseUrlAndEndpointOverride() throws Exception {
        HttpRestResult<String> invalidBaseUrl = get(Query.newInstance()
            .addParam("transportType", "mcp-sse")
            .addParam("baseUrl", "file:///etc/passwd")
            .addParam("endpoint", "/mcp"));
        JsonNode invalidBaseUrlRoot = assertWrappedError(invalidBaseUrl, 200,
            ErrorCode.PARAMETER_VALIDATE_ERROR);
        assertTrue(invalidBaseUrlRoot.get("message").asText().contains("HTTP or HTTPS"),
            responseBody(invalidBaseUrl));
        
        HttpRestResult<String> endpointOverride = get(Query.newInstance()
            .addParam("transportType", "mcp-sse")
            .addParam("baseUrl", "http://127.0.0.1:1")
            .addParam("endpoint", "//192.0.2.1/mcp"));
        JsonNode endpointOverrideRoot = assertWrappedError(endpointOverride, 200,
            ErrorCode.PARAMETER_VALIDATE_ERROR);
        assertTrue(endpointOverrideRoot.get("message").asText().contains("must not override"),
            responseBody(endpointOverride));
    }
    
    private HttpRestResult<String> get(Query query) throws Exception {
        return nacosRestTemplate.get(CONSOLE_BASE_URL + IMPORT_TOOLS_PATH, Header.EMPTY, query,
            String.class);
    }
    
    private void assertMissingParameter(HttpRestResult<String> response, String parameter) {
        JsonNode root = assertWrappedError(response, 400, ErrorCode.PARAMETER_MISSING);
        assertTrue(root.get("message").asText().contains(parameter), responseBody(response));
    }
    
    private JsonNode assertWrappedError(HttpRestResult<String> response, int httpCode,
        ErrorCode errorCode) {
        String responseBody = responseBody(response);
        assertEquals(httpCode, response.getCode(), responseBody);
        JsonNode root = JacksonUtils.toObj(responseBody);
        assertEquals(errorCode.getCode().intValue(), root.get("code").asInt(), responseBody);
        assertTrue(root.has("message"), responseBody);
        assertTrue(root.has("data"), responseBody);
        return root;
    }
    
    private String responseBody(HttpRestResult<String> response) {
        return response.getData() == null ? response.getMessage() : response.getData();
    }
}
