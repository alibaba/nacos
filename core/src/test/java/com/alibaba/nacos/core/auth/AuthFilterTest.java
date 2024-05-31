/*
 *  Copyright 1999-2021 Alibaba Group Holding Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package com.alibaba.nacos.core.auth;

import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.auth.config.AuthConfigs;
import com.alibaba.nacos.common.constant.HttpHeaderConsts;
import com.alibaba.nacos.core.code.ControllerMethodsCache;
import com.alibaba.nacos.sys.env.Constants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * {@link AuthFilter} unit test.
 *
 * @author chenglu
 * @date 2021-07-06 13:44
 */
@ExtendWith(MockitoExtension.class)
class AuthFilterTest {
    
    @InjectMocks
    private AuthFilter authFilter;
    
    @Mock
    private AuthConfigs authConfigs;
    
    @Mock
    private ControllerMethodsCache methodsCache;
    
    @Test
    void testDoFilter() {
        try {
            FilterChain filterChain = new MockFilterChain();
            Mockito.when(authConfigs.isAuthEnabled()).thenReturn(true);
            MockHttpServletRequest request = new MockHttpServletRequest();
            HttpServletResponse response = new MockHttpServletResponse();
            authFilter.doFilter(request, response, filterChain);
            
            Mockito.when(authConfigs.isEnableUserAgentAuthWhite()).thenReturn(true);
            request.addHeader(HttpHeaderConsts.USER_AGENT_HEADER, Constants.NACOS_SERVER_HEADER);
            authFilter.doFilter(request, response, filterChain);
            
            Mockito.when(authConfigs.isEnableUserAgentAuthWhite()).thenReturn(false);
            Mockito.when(authConfigs.getServerIdentityKey()).thenReturn("1");
            Mockito.when(authConfigs.getServerIdentityValue()).thenReturn("2");
            request.addHeader("1", "2");
            authFilter.doFilter(request, response, filterChain);
            
            Mockito.when(authConfigs.getServerIdentityValue()).thenReturn("3");
            authFilter.doFilter(request, response, filterChain);
            
            Mockito.when(methodsCache.getMethod(Mockito.any())).thenReturn(filterChain.getClass().getMethod("testSecured"));
            authFilter.doFilter(request, response, filterChain);
            
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }
    
    class MockFilterChain implements FilterChain {
        
        @Override
        public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse) throws IOException, ServletException {
            System.out.println("filter chain executed");
        }
        
        @Secured(resource = "xx")
        public void testSecured() {
        
        }
    }
}
