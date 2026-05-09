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

package com.alibaba.nacos.config.server.filter;

import com.alibaba.nacos.consistency.cp.CPProtocol;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.lang.reflect.Field;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CircuitFilterTest {
    
    private CircuitFilter filter;
    
    @Mock
    private ServerMemberManager memberManager;
    
    @Mock
    private CPProtocol protocol;
    
    @Mock
    private HttpServletRequest request;
    
    @Mock
    private HttpServletResponse response;
    
    @Mock
    private FilterChain chain;
    
    @BeforeEach
    void setUp() throws Exception {
        filter = new CircuitFilter();
        injectField(filter, "memberManager", memberManager);
        injectField(filter, "protocol", protocol);
        injectField(filter, "isOpenService", true);
        injectField(filter, "isDowngrading", false);
    }
    
    @Test
    void testSecurityExceptionReturns403() throws Exception {
        doThrow(new SecurityException("access denied")).when(chain).doFilter(request, response);
        
        filter.doFilter(request, response, chain);
        
        verify(response).sendError(eq(HttpServletResponse.SC_FORBIDDEN), contains("access denied"));
    }
    
    @Test
    void testSecurityExceptionFromDownstreamAuthFilterReturns403() throws Exception {
        doThrow(new SecurityException("Authority validation failed")).when(chain).doFilter(request,
            response);
        
        filter.doFilter(request, response, chain);
        
        verify(response).sendError(eq(HttpServletResponse.SC_FORBIDDEN),
            contains("Authority validation failed"));
    }
    
    @Test
    void testServiceNotOpenReturns503() throws Exception {
        injectField(filter, "isOpenService", false);
        
        filter.doFilter(request, response, chain);
        
        verify(response).sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE,
            "In the node initialization, unable to process any requests at this time");
        verify(chain, never()).doFilter(request, response);
    }
    
    @Test
    void testDowngradingReturns503() throws Exception {
        injectField(filter, "isDowngrading", true);
        
        filter.doFilter(request, response, chain);
        
        verify(response).sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE,
            "Unable to process the request at this time: System triggered degradation");
        verify(chain, never()).doFilter(request, response);
    }
    
    @Test
    void testGenericExceptionReturns500() throws Exception {
        doThrow(new RuntimeException("internal error")).when(chain).doFilter(request, response);
        
        filter.doFilter(request, response, chain);
        
        verify(response).sendError(eq(HttpServletResponse.SC_INTERNAL_SERVER_ERROR),
            contains("internal error"));
    }
    
    @Test
    void testNormalRequestPassesThrough() throws Exception {
        filter.doFilter(request, response, chain);
        
        verify(chain).doFilter(request, response);
        verify(response, never()).sendError(eq(HttpServletResponse.SC_FORBIDDEN),
            contains("access denied"));
    }
    
    private static void injectField(Object target, String fieldName, Object value)
        throws Exception {
        Field field = CircuitFilter.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
