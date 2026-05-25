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

package com.alibaba.nacos.naming.web;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.naming.CommonParams;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;

class ServiceNameFilterTest {
    
    private final ServiceNameFilter serviceNameFilter = new ServiceNameFilter();
    
    @Test
    void testDoFilterAddsDefaultGroup() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter(CommonParams.SERVICE_NAME, " service ");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<ServletRequest> actualRequest = new AtomicReference<>();
        FilterChain filterChain =
            (servletRequest, servletResponse) -> actualRequest.set(servletRequest);
        
        serviceNameFilter.doFilter(request, response, filterChain);
        
        assertNotNull(actualRequest.get());
        assertEquals(Constants.DEFAULT_GROUP + Constants.SERVICE_INFO_SPLITER + "service",
            ((HttpServletRequest) actualRequest.get()).getParameter(CommonParams.SERVICE_NAME));
    }
    
    @Test
    void testDoFilterSendsBadRequestWhenServiceNameInvalid() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter(CommonParams.SERVICE_NAME, "@@service");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = Mockito.mock(FilterChain.class);
        
        serviceNameFilter.doFilter(request, response, filterChain);
        
        assertEquals(HttpServletResponse.SC_BAD_REQUEST, response.getStatus());
    }
    
    @Test
    void testDoFilterSendsInternalServerErrorWhenExceptionThrown() throws Exception {
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        HttpServletResponse response = Mockito.mock(HttpServletResponse.class);
        FilterChain filterChain = Mockito.mock(FilterChain.class);
        Mockito.when(request.getParameter(CommonParams.SERVICE_NAME))
            .thenThrow(new RuntimeException("boom"));
        
        serviceNameFilter.doFilter(request, response, filterChain);
        
        Mockito.verify(response).sendError(eq(HttpServletResponse.SC_INTERNAL_SERVER_ERROR),
            contains("boom"));
    }
}
