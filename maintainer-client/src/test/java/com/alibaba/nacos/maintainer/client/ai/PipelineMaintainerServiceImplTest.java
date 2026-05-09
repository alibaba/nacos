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

package com.alibaba.nacos.maintainer.client.ai;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.maintainer.client.model.HttpRequest;
import com.alibaba.nacos.maintainer.client.remote.ClientHttpProxy;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;

/**
 * Unit tests for {@link PipelineMaintainerServiceImpl} and {@link PipelineAdminClient} endpoints.
 */
@ExtendWith(MockitoExtension.class)
class PipelineMaintainerServiceImplTest {
    
    @Mock
    private ClientHttpProxy clientHttpProxy;
    
    private PipelineMaintainerService pipelineMaintainerService;
    
    @BeforeEach
    void setUp() throws NacosException, NoSuchFieldException, IllegalAccessException {
        Properties properties = new Properties();
        properties.put(PropertyKeyConst.SERVER_ADDR, "127.0.0.1:8848");
        AiMaintainerService aiMaintainerService =
            AiMaintainerFactory.createAiMaintainerService(properties);
        Field pipelineField =
            NacosAiMaintainerServiceImpl.class.getDeclaredField("pipelineMaintainerService");
        pipelineField.setAccessible(true);
        pipelineMaintainerService =
            (PipelineMaintainerService) pipelineField.get(aiMaintainerService);
        Field contextField = AbstractAiDelegateMaintainerService.class.getDeclaredField("context");
        contextField.setAccessible(true);
        Object context = contextField.get(pipelineMaintainerService);
        Field clientHttpProxyField =
            AiMaintainerHttpContext.class.getDeclaredField("clientHttpProxy");
        clientHttpProxyField.setAccessible(true);
        clientHttpProxyField.set(context, clientHttpProxy);
    }
    
    @Test
    void getPipelineDetailReturnsResultWrapper() throws NacosException {
        JsonNode payload = JacksonUtils.toObj("{\"executionId\":\"exec-1\"}", JsonNode.class);
        HttpRestResult<String> mockRestResult = new HttpRestResult<>();
        mockRestResult.setData(JacksonUtils.toJson(Result.success(payload)));
        doAnswer(invocation -> mockRestResult).when(clientHttpProxy)
            .executeSyncHttpRequest(any(HttpRequest.class));
        
        Result<JsonNode> result = pipelineMaintainerService.getPipelineDetail("exec-1");
        
        assertNotNull(result);
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertNotNull(result.getData());
        assertEquals("exec-1", result.getData().get("executionId").asText());
    }
    
    @Test
    void getPipelineDetailReturnsBusinessFailureWithoutThrowing() throws NacosException {
        HttpRestResult<String> mockRestResult = new HttpRestResult<>();
        mockRestResult
            .setData(JacksonUtils.toJson(Result.failure(ErrorCode.PARAMETER_VALIDATE_ERROR)));
        doAnswer(invocation -> mockRestResult).when(clientHttpProxy)
            .executeSyncHttpRequest(any(HttpRequest.class));
        
        Result<JsonNode> result = pipelineMaintainerService.getPipelineDetail("exec-1");
        
        assertNotNull(result);
        assertEquals(ErrorCode.PARAMETER_VALIDATE_ERROR.getCode(), result.getCode());
    }
    
    @Test
    void getPipelineDeprecatedUnwrapsSuccessData() throws NacosException {
        JsonNode payload = JacksonUtils.toObj("{\"executionId\":\"exec-1\"}", JsonNode.class);
        HttpRestResult<String> mockRestResult = new HttpRestResult<>();
        mockRestResult.setData(JacksonUtils.toJson(Result.success(payload)));
        doAnswer(invocation -> mockRestResult).when(clientHttpProxy)
            .executeSyncHttpRequest(any(HttpRequest.class));
        
        JsonNode data = pipelineMaintainerService.getPipeline("exec-1");
        
        assertNotNull(data);
        assertEquals("exec-1", data.get("executionId").asText());
    }
    
    @Test
    void getPipelineDeprecatedThrowsWhenResultNotSuccess() throws NacosException {
        HttpRestResult<String> mockRestResult = new HttpRestResult<>();
        mockRestResult.setData(JacksonUtils.toJson(Result.failure(ErrorCode.DATA_ACCESS_ERROR)));
        doAnswer(invocation -> mockRestResult).when(clientHttpProxy)
            .executeSyncHttpRequest(any(HttpRequest.class));
        
        NacosException ex = assertThrows(NacosException.class,
            () -> pipelineMaintainerService.getPipeline("exec-1"));
        assertTrue(ex.getErrMsg().contains(ErrorCode.DATA_ACCESS_ERROR.getMsg())
            || ex.getErrMsg().equals(ErrorCode.DATA_ACCESS_ERROR.getMsg()));
    }
    
    @Test
    void listPipelineExecutionsReturnsPage() throws NacosException {
        final String pageJson =
            "{\"pageNumber\":1,\"pageSize\":10,\"totalCount\":0,\"pageItems\":[]}";
        JsonNode pageNode = JacksonUtils.toObj(pageJson, JsonNode.class);
        HttpRestResult<String> mockRestResult = new HttpRestResult<>();
        mockRestResult.setData(JacksonUtils.toJson(Result.success(pageNode)));
        doAnswer(invocation -> mockRestResult).when(clientHttpProxy)
            .executeSyncHttpRequest(any(HttpRequest.class));
        
        Result<JsonNode> result =
            pipelineMaintainerService.listPipelineExecutions("SKILL", null, null, null, 1, 10);
        
        assertNotNull(result);
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertNotNull(result.getData());
        assertEquals(1, result.getData().get("pageNumber").asInt());
    }
    
    @Test
    void getPipelineDetailFallsBackOn404() throws NacosException {
        JsonNode payload = JacksonUtils.toObj("{\"executionId\":\"legacy\"}", JsonNode.class);
        HttpRestResult<String> ok = new HttpRestResult<>();
        ok.setData(JacksonUtils.toJson(Result.success(payload)));
        doThrow(new NacosException(NacosException.NOT_FOUND, "not found"))
            .doAnswer(invocation -> ok).when(clientHttpProxy)
            .executeSyncHttpRequest(any(HttpRequest.class));
        
        Result<JsonNode> result = pipelineMaintainerService.getPipelineDetail("legacy");
        
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals("legacy", result.getData().get("executionId").asText());
    }
    
    // ========== Additional Tests for Coverage ==========
    
    @Test
    @DisplayName("listPipelineExecutions falls back on 404")
    void listPipelineExecutionsFallsBackOn404() throws NacosException {
        final String pageJson =
            "{\"pageNumber\":1,\"pageSize\":10,\"totalCount\":0,\"pageItems\":[]}";
        JsonNode pageNode = JacksonUtils.toObj(pageJson, JsonNode.class);
        HttpRestResult<String> ok = new HttpRestResult<>();
        ok.setData(JacksonUtils.toJson(Result.success(pageNode)));
        doThrow(new NacosException(NacosException.NOT_FOUND, "not found"))
            .doAnswer(invocation -> ok).when(clientHttpProxy)
            .executeSyncHttpRequest(any(HttpRequest.class));
        
        Result<JsonNode> result = pipelineMaintainerService.listPipelineExecutions("SKILL", "test",
            "public", "v1", 1, 10);
        
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertNotNull(result.getData());
    }
    
    @Test
    @DisplayName("parseResultFromHttp with empty body should throw SERVER_ERROR")
    void testParseResultFromHttpWithEmptyBody() throws NacosException {
        HttpRestResult<String> mockRestResult = new HttpRestResult<>();
        mockRestResult.setData("");
        doAnswer(invocation -> mockRestResult).when(clientHttpProxy)
            .executeSyncHttpRequest(any(HttpRequest.class));
        
        NacosException ex = assertThrows(NacosException.class,
            () -> pipelineMaintainerService.getPipelineDetail("exec-1"));
        assertEquals(NacosException.SERVER_ERROR, ex.getErrCode());
        assertTrue(ex.getErrMsg().contains("empty response body"));
    }
    
    @Test
    @DisplayName("getPipeline deprecated throws when result is not success")
    void getPipelineDeprecatedThrowsOnBusinessFailure() throws NacosException {
        HttpRestResult<String> mockRestResult = new HttpRestResult<>();
        mockRestResult.setData(JacksonUtils.toJson(Result.failure(ErrorCode.SERVER_ERROR)));
        doAnswer(invocation -> mockRestResult).when(clientHttpProxy)
            .executeSyncHttpRequest(any(HttpRequest.class));
        
        NacosException ex = assertThrows(NacosException.class,
            () -> pipelineMaintainerService.getPipeline("exec-1"));
        assertEquals(NacosException.SERVER_ERROR, ex.getErrCode());
    }
    
    @Test
    @DisplayName("listPipelines deprecated unwraps success data")
    void listPipelinesDeprecatedUnwrapsSuccessData() throws NacosException {
        final String pageJson =
            "{\"pageNumber\":2,\"pageSize\":20,\"totalCount\":5,\"pageItems\":[]}";
        JsonNode pageNode = JacksonUtils.toObj(pageJson, JsonNode.class);
        HttpRestResult<String> mockRestResult = new HttpRestResult<>();
        mockRestResult.setData(JacksonUtils.toJson(Result.success(pageNode)));
        doAnswer(invocation -> mockRestResult).when(clientHttpProxy)
            .executeSyncHttpRequest(any(HttpRequest.class));
        
        JsonNode data =
            pipelineMaintainerService.listPipelines("AGENT_SPEC", "agent", "public", "v1", 2, 20);
        assertNotNull(data);
        assertEquals(2, data.get("pageNumber").asInt());
        assertEquals(20, data.get("pageSize").asInt());
        assertEquals(5, data.get("totalCount").asInt());
    }
}
