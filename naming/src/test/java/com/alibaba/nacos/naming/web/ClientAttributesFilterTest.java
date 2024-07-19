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

package com.alibaba.nacos.naming.web;

import com.alibaba.nacos.common.constant.HttpHeaderConsts;
import com.alibaba.nacos.core.context.RequestContextHolder;
import com.alibaba.nacos.naming.core.v2.client.ClientAttributes;
import com.alibaba.nacos.naming.core.v2.client.impl.IpPortBasedClient;
import com.alibaba.nacos.naming.core.v2.client.manager.ClientManager;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClientAttributesFilterTest {
    
    @Mock
    ClientManager clientManager;
    
    @Mock
    IpPortBasedClient client;
    
    @Mock
    HttpServletRequest request;
    
    @Mock
    HttpServletResponse response;
    
    @Mock
    Servlet servlet;
    
    @InjectMocks
    ClientAttributesFilter filter;
    
    @BeforeEach
    void setUp() {
        RequestContextHolder.getContext().getBasicContext().setUserAgent("Nacos-Java-Client:v2.4.0");
        RequestContextHolder.getContext().getBasicContext().setApp("testApp");
        RequestContextHolder.getContext().getBasicContext().getAddressContext().setRemoteIp("1.1.1.1");
        RequestContextHolder.getContext().getBasicContext().getAddressContext().setSourceIp("2.2.2.2");
    }
    
    @AfterEach
    void tearDown() {
        RequestContextHolder.removeContext();
    }
    
    @Test
    void testDoFilterForRegisterUri() throws IOException {
        when(request.getRequestURI()).thenReturn(
                UtilsAndCommons.NACOS_SERVER_CONTEXT + UtilsAndCommons.NACOS_NAMING_CONTEXT
                        + UtilsAndCommons.NACOS_NAMING_INSTANCE_CONTEXT);
        when(request.getMethod()).thenReturn("POST");
        filter.doFilter(request, response, new MockFilterChain(servlet, new MockRegisterFilter()));
    }
    
    @Test
    void testDoFilterForBeatUri() throws IOException {
        when(request.getParameter("ip")).thenReturn("127.0.0.1");
        when(request.getParameter("port")).thenReturn("8848");
        when(request.getParameter("encoding")).thenReturn("utf-8");
        when(clientManager.getClient("127.0.0.1:8848#true")).thenReturn(client);
        when(request.getRequestURI()).thenReturn(
                UtilsAndCommons.NACOS_SERVER_CONTEXT + UtilsAndCommons.NACOS_NAMING_CONTEXT
                        + UtilsAndCommons.NACOS_NAMING_INSTANCE_CONTEXT + "/beat");
        when(request.getMethod()).thenReturn("PUT");
        filter.doFilter(request, response, new MockFilterChain());
        verify(client).setAttributes(any(ClientAttributes.class));
    }
    
    private static class MockRegisterFilter implements Filter {
        
        @Override
        public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
                throws IOException, ServletException {
            Optional<ClientAttributes> clientAttributes = ClientAttributesFilter.getCurrentClientAttributes();
            assertTrue(clientAttributes.isPresent());
            assertEquals("Nacos-Java-Client:v2.4.0",
                    clientAttributes.get().getClientAttribute(HttpHeaderConsts.CLIENT_VERSION_HEADER));
            assertEquals("testApp", clientAttributes.get().getClientAttribute(HttpHeaderConsts.APP_FILED));
            assertEquals("2.2.2.2", clientAttributes.get().getClientAttribute(HttpHeaderConsts.CLIENT_IP));
        }
    }
}