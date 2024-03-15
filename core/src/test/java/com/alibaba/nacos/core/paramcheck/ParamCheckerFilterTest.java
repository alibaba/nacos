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

import com.alibaba.nacos.core.code.ControllerMethodsCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the {@link ParamCheckerFilter}.
 * @author lynn.lqp
 * @date 2023/11/7
 */
public class ParamCheckerFilterTest {

    private ParamCheckerFilter filter;

    private ControllerMethodsCache methodsCache;

    private ServerParamCheckConfig serverParamCheckConfig;

    private HttpServletRequest request;

    private HttpServletResponse response;

    private FilterChain chain;

    @BeforeEach
    public void setUp() {
        filter = new ParamCheckerFilter(mock(ControllerMethodsCache.class));
        methodsCache = mock(ControllerMethodsCache.class);
        serverParamCheckConfig = mock(ServerParamCheckConfig.class);
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        chain = mock(FilterChain.class);
    }

    @Test
    public void testDoFilterParamCheckDisabled() throws IOException, ServletException {
        when(serverParamCheckConfig.isParamCheckEnabled()).thenReturn(false);
        filter.doFilter(request, response, chain);
        verify(chain).doFilter(request, response);
    }

    @Test
    public void testDoFilterMethodNotFound() throws IOException, ServletException {
        when(methodsCache.getMethod(request)).thenReturn(null);
        filter.doFilter(request, response, chain);
        verify(chain).doFilter(request, response);
    }
}
