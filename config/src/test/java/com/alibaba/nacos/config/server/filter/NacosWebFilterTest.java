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

import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NacosWebFilterTest {
    
    @Mock
    private FilterConfig filterConfig;
    
    @Mock
    private ServletContext servletContext;
    
    @Mock
    private FilterChain filterChain;
    
    @Test
    void testInit() throws ServletException {
        NacosWebFilter filter = new NacosWebFilter();
        when(filterConfig.getServletContext()).thenReturn(servletContext);
        when(servletContext.getRealPath("/")).thenReturn("/test/path");
        filter.init(filterConfig);
        assertEquals("/test/path", NacosWebFilter.rootPath());
    }
    
    @Test
    void testDoFilter() throws IOException, ServletException {
        NacosWebFilter filter = new NacosWebFilter();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, filterChain);
        verify(filterChain).doFilter(request, response);
        assertEquals("UTF-8", request.getCharacterEncoding());
    }
    
    @Test
    void testDoFilterWithIoException()
        throws IOException, ServletException {
        NacosWebFilter filter = new NacosWebFilter();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        doThrow(new IOException("io error"))
            .when(filterChain).doFilter(any(), any());
        assertThrows(IOException.class,
            () -> filter.doFilter(request, response, filterChain));
    }
    
    @Test
    void testSetWebRootPath() {
        NacosWebFilter.setWebRootPath("/custom/path");
        assertEquals("/custom/path", NacosWebFilter.rootPath());
    }
    
    @Test
    void testDestroy() {
        NacosWebFilter filter = new NacosWebFilter();
        assertDoesNotThrow(filter::destroy);
    }
}
