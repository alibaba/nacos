/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.core.paramcheck;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.runtime.NacosRuntimeException;
import com.alibaba.nacos.common.paramcheck.ParamInfo;
import com.alibaba.nacos.core.code.ControllerMethodsCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the {@link ParamCheckerFilter}.
 *
 * @author lynn.lqp
 * @date 2023/11/7
 */
@ExtendWith(MockitoExtension.class)
class ParamCheckerFilterTest {

    private ParamCheckerFilter filter;

    private ControllerMethodsCache methodsCache;

    private HttpServletRequest request;

    private HttpServletResponse response;

    private FilterChain chain;

    @BeforeEach
    void setUp() {
        methodsCache = mock(ControllerMethodsCache.class);
        filter = new ParamCheckerFilter(methodsCache);
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        chain = mock(FilterChain.class);
    }

    @Test
    void testDoFilterParamCheckDisabled() throws IOException, ServletException {
        ServerParamCheckConfig.getInstance().setParamCheckEnabled(false);
        try {
            filter.doFilter(request, response, chain);
            verify(chain).doFilter(request, response);
        } finally {
            ServerParamCheckConfig.getInstance().setParamCheckEnabled(true);
        }
    }

    @Test
    void testDoFilterMethodNotFound() throws IOException, ServletException {
        when(methodsCache.getMethod(request)).thenReturn(null);
        filter.doFilter(request, response, chain);
        verify(chain).doFilter(request, response);
    }

    @Test
    void testDoFilterWhenExtractorNullFromMethodAndClass() throws Exception {
        Method method = NoExtractorController.class.getMethod("handle");
        when(methodsCache.getMethod(request)).thenReturn(method);
        filter.doFilter(request, response, chain);
        verify(chain).doFilter(request, response);
    }

    @Test
    void testDoFilterWhenExtractorFromClassParamCheckSuccess() throws Exception {
        Method method = ParamExtractorTest.Controller.class.getMethod("testCheckNull");
        when(methodsCache.getMethod(request)).thenReturn(method);
        filter.doFilter(request, response, chain);
        verify(chain).doFilter(request, response);
    }

    @Test
    void testDoFilterWhenParamCheckFails() throws Exception {
        Method method = ParamExtractorTest.Controller.class.getMethod("testCheck");
        when(methodsCache.getMethod(request)).thenReturn(method);
        when(request.getParameter("dataId")).thenReturn("invalid@dataId");
        when(request.getRequestURI()).thenReturn("/test");
        jakarta.servlet.ServletOutputStream outputStream = mock(jakarta.servlet.ServletOutputStream.class);
        when(response.getOutputStream()).thenReturn(outputStream);
        filter.doFilter(request, response, chain);
        verify(response).setStatus(HttpServletResponse.SC_BAD_REQUEST);
    }

    @Test
    void testDoFilterWhenExtractParamThrowsNacosException() throws Exception {
        Method method = ParamExtractorTest.Controller.class.getMethod("testCheck");
        when(methodsCache.getMethod(request)).thenReturn(method);
        AbstractHttpParamExtractor failingExtractor = new AbstractHttpParamExtractor() {
            @Override
            public List<ParamInfo> extractParam(HttpServletRequest request) throws NacosException {
                throw new NacosException(500, "extract fail");
            }
        };
        try (MockedStatic<ExtractorManager> extractorMock = org.mockito.Mockito.mockStatic(ExtractorManager.class)) {
            extractorMock.when(() -> ExtractorManager.getHttpExtractor(any())).thenReturn(failingExtractor);
            NacosRuntimeException ex = assertThrows(NacosRuntimeException.class,
                    () -> filter.doFilter(request, response, chain));
        }
    }

    @Test
    void testGenerate400Response() throws IOException {
        jakarta.servlet.ServletOutputStream outputStream = mock(jakarta.servlet.ServletOutputStream.class);
        when(response.getOutputStream()).thenReturn(outputStream);
        filter.generate400Response(response, "invalid param");
        verify(response).setHeader("Pragma", "no-cache");
        verify(response).setDateHeader(eq("Expires"), eq(0L));
        verify(response).setHeader("Cache-Control", "no-cache,no-store");
        verify(response).setStatus(HttpServletResponse.SC_BAD_REQUEST);
    }

    @Test
    void testGenerate400ResponseWhenGetOutputStreamThrows() throws IOException {
        when(response.getOutputStream()).thenThrow(new IOException("output error"));
        filter.generate400Response(response, "msg");
        verify(response).setStatus(HttpServletResponse.SC_BAD_REQUEST);
    }

    public static class NoExtractorController {
        public void handle() {
        }
    }
}
