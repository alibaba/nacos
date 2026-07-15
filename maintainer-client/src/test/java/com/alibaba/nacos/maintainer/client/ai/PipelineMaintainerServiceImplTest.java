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
import com.alibaba.nacos.api.ai.model.pipeline.PipelineExecution;
import com.alibaba.nacos.api.ai.model.pipeline.PipelineExecutionStatus;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.api.utils.json.JsonUtils;
import com.alibaba.nacos.common.http.HttpRestResult;
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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
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
        PipelineExecution payload = newPipelineExecution("exec-1");
        HttpRestResult<String> mockRestResult = newRestResult(Result.success(payload));
        doAnswer(invocation -> mockRestResult).when(clientHttpProxy)
            .executeSyncHttpRequest(any(HttpRequest.class));
        
        Result<PipelineExecution> result = pipelineMaintainerService.getPipelineDetail("exec-1");
        
        assertNotNull(result);
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertNotNull(result.getData());
        assertEquals("exec-1", result.getData().getExecutionId());
        assertEquals(PipelineExecutionStatus.APPROVED, result.getData().getStatus());
    }
    
    @Test
    void getPipelineDetailReturnsBusinessFailureWithoutThrowing() throws NacosException {
        HttpRestResult<String> mockRestResult =
            newRestResult(Result.failure(ErrorCode.PARAMETER_VALIDATE_ERROR));
        doAnswer(invocation -> mockRestResult).when(clientHttpProxy)
            .executeSyncHttpRequest(any(HttpRequest.class));
        
        Result<PipelineExecution> result = pipelineMaintainerService.getPipelineDetail("exec-1");
        
        assertNotNull(result);
        assertEquals(ErrorCode.PARAMETER_VALIDATE_ERROR.getCode(), result.getCode());
    }
    
    @Test
    void getPipelineDeprecatedUnwrapsSuccessData() throws NacosException {
        PipelineExecution payload = newPipelineExecution("exec-1");
        HttpRestResult<String> mockRestResult = newRestResult(Result.success(payload));
        doAnswer(invocation -> mockRestResult).when(clientHttpProxy)
            .executeSyncHttpRequest(any(HttpRequest.class));
        
        JsonNode data = pipelineMaintainerService.getPipeline("exec-1");
        
        assertNotNull(data);
        assertEquals("exec-1", data.get("executionId").asText());
    }
    
    @Test
    void getPipelineDeprecatedThrowsWhenResultNotSuccess() throws NacosException {
        HttpRestResult<String> mockRestResult =
            newRestResult(Result.failure(ErrorCode.DATA_ACCESS_ERROR));
        doAnswer(invocation -> mockRestResult).when(clientHttpProxy)
            .executeSyncHttpRequest(any(HttpRequest.class));
        
        NacosException ex = assertThrows(NacosException.class,
            () -> pipelineMaintainerService.getPipeline("exec-1"));
        assertTrue(ex.getErrMsg().contains(ErrorCode.DATA_ACCESS_ERROR.getMsg())
            || ex.getErrMsg().equals(ErrorCode.DATA_ACCESS_ERROR.getMsg()));
    }
    
    @Test
    void listPipelineExecutionsReturnsPage() throws NacosException {
        Page<PipelineExecution> page = newPipelinePage(newPipelineExecution("exec-1"), 1);
        HttpRestResult<String> mockRestResult = newRestResult(Result.success(page));
        doAnswer(invocation -> mockRestResult).when(clientHttpProxy)
            .executeSyncHttpRequest(any(HttpRequest.class));
        
        Result<Page<PipelineExecution>> result =
            pipelineMaintainerService.listPipelineExecutions("SKILL", null, null, null, 1, 10);
        
        assertNotNull(result);
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertNotNull(result.getData());
        assertEquals(1, result.getData().getPageNumber());
        assertEquals("exec-1", result.getData().getPageItems().get(0).getExecutionId());
    }
    
    @Test
    void getPipelineDetailFallsBackOn404() throws NacosException {
        PipelineExecution payload = newPipelineExecution("legacy");
        HttpRestResult<String> ok = newRestResult(Result.success(payload));
        doThrow(new NacosException(NacosException.NOT_FOUND, "not found"))
            .doAnswer(invocation -> ok).when(clientHttpProxy)
            .executeSyncHttpRequest(any(HttpRequest.class));
        
        Result<PipelineExecution> result = pipelineMaintainerService.getPipelineDetail("legacy");
        
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals("legacy", result.getData().getExecutionId());
    }
    
    // ========== Additional Tests for Coverage ==========
    
    @Test
    @DisplayName("listPipelineExecutions falls back on 404")
    void listPipelineExecutionsFallsBackOn404() throws NacosException {
        Page<PipelineExecution> page = newPipelinePage(newPipelineExecution("legacy"), 1);
        HttpRestResult<String> ok = newRestResult(Result.success(page));
        doThrow(new NacosException(NacosException.NOT_FOUND, "not found"))
            .doAnswer(invocation -> ok).when(clientHttpProxy)
            .executeSyncHttpRequest(any(HttpRequest.class));
        
        Result<Page<PipelineExecution>> result =
            pipelineMaintainerService.listPipelineExecutions("SKILL", "test", "public", "v1",
                1, 10);
        
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertNotNull(result.getData());
        assertEquals("legacy", result.getData().getPageItems().get(0).getExecutionId());
    }
    
    @Test
    @DisplayName("listPipelineExecutions rethrows non-404 request exception")
    void listPipelineExecutionsRethrowsNon404Exception() throws NacosException {
        doThrow(new NacosException(NacosException.SERVER_ERROR, "server error"))
            .when(clientHttpProxy).executeSyncHttpRequest(any(HttpRequest.class));
        
        NacosException ex = assertThrows(NacosException.class,
            () -> pipelineMaintainerService.listPipelineExecutions("SKILL", "test", "public",
                "v1", 1, 10));
        
        assertEquals(NacosException.SERVER_ERROR, ex.getErrCode());
        assertTrue(ex.getErrMsg().contains("server error"));
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
    @DisplayName("parseResultFromHttp with null wrapper should throw SERVER_ERROR")
    void testParseResultFromHttpWithNullWrapper() throws NacosException {
        HttpRestResult<String> mockRestResult = new HttpRestResult<>();
        mockRestResult.setData("null");
        doAnswer(invocation -> mockRestResult).when(clientHttpProxy)
            .executeSyncHttpRequest(any(HttpRequest.class));
        
        NacosException ex = assertThrows(NacosException.class,
            () -> pipelineMaintainerService.getPipelineDetail("exec-1"));
        assertEquals(NacosException.SERVER_ERROR, ex.getErrCode());
        assertTrue(ex.getErrMsg().contains("failed to parse Result wrapper"));
    }
    
    @Test
    @DisplayName("getPipeline deprecated throws when result is not success")
    void getPipelineDeprecatedThrowsOnBusinessFailure() throws NacosException {
        HttpRestResult<String> mockRestResult =
            newRestResult(Result.failure(ErrorCode.SERVER_ERROR));
        doAnswer(invocation -> mockRestResult).when(clientHttpProxy)
            .executeSyncHttpRequest(any(HttpRequest.class));
        
        NacosException ex = assertThrows(NacosException.class,
            () -> pipelineMaintainerService.getPipeline("exec-1"));
        assertEquals(NacosException.SERVER_ERROR, ex.getErrCode());
    }
    
    @Test
    @DisplayName("getPipeline deprecated accepts HTTP 200 result code")
    void getPipelineDeprecatedAcceptsHttp200Code() throws NacosException {
        PipelineExecution payload = newPipelineExecution("exec-200");
        HttpRestResult<String> mockRestResult =
            newRestResult(new Result<>(200, "success", payload));
        doAnswer(invocation -> mockRestResult).when(clientHttpProxy)
            .executeSyncHttpRequest(any(HttpRequest.class));
        
        JsonNode data = pipelineMaintainerService.getPipeline("exec-200");
        
        assertNotNull(data);
        assertEquals("exec-200", data.get("executionId").asText());
    }
    
    @Test
    @DisplayName("getPipeline deprecated uses default error message for null code")
    void getPipelineDeprecatedUsesDefaultErrorMessageForNullCode() throws NacosException {
        HttpRestResult<String> mockRestResult = new HttpRestResult<>();
        mockRestResult.setData("{\"code\":null,\"message\":null,\"data\":{}}");
        doAnswer(invocation -> mockRestResult).when(clientHttpProxy)
            .executeSyncHttpRequest(any(HttpRequest.class));
        
        NacosException ex = assertThrows(NacosException.class,
            () -> pipelineMaintainerService.getPipeline("exec-null-code"));
        
        assertEquals(NacosException.SERVER_ERROR, ex.getErrCode());
        assertEquals("request failed", ex.getErrMsg());
    }
    
    @Test
    @DisplayName("unwrapSuccessData rejects null Result")
    void unwrapSuccessDataRejectsNullResult()
        throws NoSuchMethodException, IllegalAccessException {
        Method method =
            PipelineMaintainerServiceImpl.class.getDeclaredMethod("unwrapSuccessData",
                Result.class);
        method.setAccessible(true);
        
        InvocationTargetException ex =
            assertThrows(InvocationTargetException.class, () -> method.invoke(null,
                new Object[] {null}));
        
        NacosException cause = (NacosException) ex.getCause();
        assertEquals(NacosException.SERVER_ERROR, cause.getErrCode());
        assertEquals("empty Result", cause.getErrMsg());
    }
    
    @Test
    @DisplayName("listPipelines deprecated unwraps success data")
    void listPipelinesDeprecatedUnwrapsSuccessData() throws NacosException {
        Map<String, Object> page = newRawPage(2, 20, 5);
        HttpRestResult<String> mockRestResult = newRestResult(Result.success(page));
        doAnswer(invocation -> mockRestResult).when(clientHttpProxy)
            .executeSyncHttpRequest(any(HttpRequest.class));
        
        JsonNode data =
            pipelineMaintainerService.listPipelines("AGENT_SPEC", "agent", "public", "v1", 2, 20);
        assertNotNull(data);
        assertEquals(2, data.get("pageNumber").asInt());
        assertEquals(20, data.get("pageSize").asInt());
        assertEquals(5, data.get("totalCount").asInt());
    }
    
    private PipelineExecution newPipelineExecution(String executionId) {
        PipelineExecution execution = new PipelineExecution();
        execution.setExecutionId(executionId);
        execution.setResourceType("SKILL");
        execution.setResourceName("skill");
        execution.setNamespaceId("public");
        execution.setVersion("v1");
        execution.setStatus(PipelineExecutionStatus.APPROVED);
        return execution;
    }
    
    private Page<PipelineExecution> newPipelinePage(PipelineExecution execution, int pageNo) {
        Page<PipelineExecution> page = new Page<>();
        page.setPageNumber(pageNo);
        page.setPageItems(Collections.singletonList(execution));
        page.setTotalCount(1);
        page.setPagesAvailable(1);
        return page;
    }
    
    private Map<String, Object> newRawPage(int pageNo, int pageSize, int totalCount) {
        Map<String, Object> page = new HashMap<>();
        page.put("pageNumber", pageNo);
        page.put("pageSize", pageSize);
        page.put("totalCount", totalCount);
        page.put("pageItems", Collections.emptyList());
        return page;
    }
    
    private HttpRestResult<String> newRestResult(Result<?> result) {
        HttpRestResult<String> restResult = new HttpRestResult<>();
        restResult.setData(JsonUtils.toJson(result));
        return restResult;
    }
}
