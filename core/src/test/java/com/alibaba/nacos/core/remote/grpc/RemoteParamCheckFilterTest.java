/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.core.remote.grpc;

import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.api.remote.response.HealthCheckResponse;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.common.paramcheck.AbstractParamChecker;
import com.alibaba.nacos.common.paramcheck.ParamCheckResponse;
import com.alibaba.nacos.common.paramcheck.ParamCheckerManager;
import com.alibaba.nacos.core.paramcheck.ExtractorManager;
import com.alibaba.nacos.core.paramcheck.ServerParamCheckConfig;
import com.alibaba.nacos.core.remote.HealthCheckRequestHandler;
import com.alibaba.nacos.api.remote.request.HealthCheckRequest;
import com.alibaba.nacos.core.remote.RequestHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class RemoteParamCheckFilterTest {
    
    @BeforeEach
    void setUp() {
        ServerParamCheckConfig.getInstance().setParamCheckEnabled(false);
    }
    
    @AfterEach
    void tearDown() {
        ServerParamCheckConfig.getInstance().setParamCheckEnabled(true);
    }
    
    @Test
    void testFilterReturnsNullWhenParamCheckDisabled() throws Exception {
        RemoteParamCheckFilter filter = new RemoteParamCheckFilter();
        Method filterMethod = RemoteParamCheckFilter.class.getDeclaredMethod("filter",
            Request.class, RequestMeta.class, Class.class);
        filterMethod.setAccessible(true);
        Object result = filterMethod.invoke(filter, null, null, HealthCheckRequestHandler.class);
        assertNull(result);
    }
    
    @Test
    void testFilterReturnsNullWhenParamCheckEnabledButNoExtractor() throws Exception {
        ServerParamCheckConfig.getInstance().setParamCheckEnabled(true);
        try {
            RemoteParamCheckFilter filter = new RemoteParamCheckFilter();
            Method filterMethod = RemoteParamCheckFilter.class.getDeclaredMethod("filter",
                Request.class, RequestMeta.class, Class.class);
            filterMethod.setAccessible(true);
            Object result =
                filterMethod.invoke(filter, null, null, HealthCheckRequestHandler.class);
            assertNull(result);
        } finally {
            ServerParamCheckConfig.getInstance().setParamCheckEnabled(false);
        }
    }
    
    @Test
    void testFilterReturnsFailResponseWhenExtractorManagerThrows() throws Exception {
        ServerParamCheckConfig.getInstance().setParamCheckEnabled(true);
        try (MockedStatic<ExtractorManager> extractorMock =
            Mockito.mockStatic(ExtractorManager.class)) {
            extractorMock.when(() -> ExtractorManager.getRpcExtractor(any()))
                .thenThrow(new RuntimeException("extractor-error"));
            RemoteParamCheckFilter filter = new RemoteParamCheckFilter();
            Method filterMethod = RemoteParamCheckFilter.class.getDeclaredMethod("filter",
                Request.class, RequestMeta.class, Class.class);
            filterMethod.setAccessible(true);
            Object result = filterMethod.invoke(filter, new HealthCheckRequest(), null,
                HandlerWithExtractor.class);
            assertNotNull(result);
            assertTrue(result instanceof Response);
            assertTrue(((Response) result).getMessage().contains("extractor-error"));
        } finally {
            ServerParamCheckConfig.getInstance().setParamCheckEnabled(false);
        }
    }
    
    @Test
    void testFilterReturnsFailResponseWhenParamCheckFails() throws Exception {
        ServerParamCheckConfig.getInstance().setParamCheckEnabled(true);
        ParamCheckResponse failResponse = new ParamCheckResponse();
        failResponse.setSuccess(false);
        failResponse.setMessage("param invalid");
        AbstractParamChecker mockChecker = Mockito.mock(AbstractParamChecker.class);
        when(mockChecker.checkParamInfoList(any())).thenReturn(failResponse);
        try (MockedStatic<ParamCheckerManager> pmMock =
            Mockito.mockStatic(ParamCheckerManager.class)) {
            ParamCheckerManager mockManager = Mockito.mock(ParamCheckerManager.class);
            pmMock.when(ParamCheckerManager::getInstance).thenReturn(mockManager);
            when(mockManager.getParamChecker(any())).thenReturn(mockChecker);
            RemoteParamCheckFilter filter = new RemoteParamCheckFilter();
            Method filterMethod = RemoteParamCheckFilter.class.getDeclaredMethod("filter",
                Request.class, RequestMeta.class, Class.class);
            filterMethod.setAccessible(true);
            Object result = filterMethod.invoke(filter, new HealthCheckRequest(), null,
                HandlerWithExtractor.class);
            assertNotNull(result);
            assertTrue(result instanceof Response);
            assertTrue(((Response) result).getMessage().contains("param invalid"));
        } finally {
            ServerParamCheckConfig.getInstance().setParamCheckEnabled(false);
        }
    }
    
    @Test
    void testGenerateFailResponseReturnsNullWhenGetDefaultResponseThrows() throws Exception {
        ServerParamCheckConfig.getInstance().setParamCheckEnabled(true);
        ParamCheckResponse failResponse = new ParamCheckResponse();
        failResponse.setSuccess(false);
        failResponse.setMessage("check fail");
        AbstractParamChecker mockChecker = Mockito.mock(AbstractParamChecker.class);
        when(mockChecker.checkParamInfoList(any())).thenReturn(failResponse);
        try (MockedStatic<ParamCheckerManager> pmMock =
            Mockito.mockStatic(ParamCheckerManager.class)) {
            ParamCheckerManager mockManager = Mockito.mock(ParamCheckerManager.class);
            pmMock.when(ParamCheckerManager::getInstance).thenReturn(mockManager);
            when(mockManager.getParamChecker(any())).thenReturn(mockChecker);
            RemoteParamCheckFilter filter = new RemoteParamCheckFilter();
            Method filterMethod = RemoteParamCheckFilter.class.getDeclaredMethod("filter",
                Request.class, RequestMeta.class, Class.class);
            filterMethod.setAccessible(true);
            Object result = filterMethod.invoke(filter, new HealthCheckRequest(), null,
                HandlerWithAbstractResponse.class);
            assertNull(result);
        } finally {
            ServerParamCheckConfig.getInstance().setParamCheckEnabled(false);
        }
    }
    
    @ExtractorManager.Extractor
    static class HandlerWithExtractor
        extends RequestHandler<HealthCheckRequest, HealthCheckResponse> {
        
        @Override
        public HealthCheckResponse handle(HealthCheckRequest request, RequestMeta meta) {
            return null;
        }
    }
    
    static abstract class AbstractFakeResponse extends Response {
    }
    
    @ExtractorManager.Extractor
    static class HandlerWithAbstractResponse extends RequestHandler<Request, AbstractFakeResponse> {
        
        @Override
        public AbstractFakeResponse handle(Request request, RequestMeta meta) {
            return null;
        }
    }
}
