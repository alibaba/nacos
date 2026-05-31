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

import com.alibaba.nacos.api.naming.CommonParams;
import com.alibaba.nacos.common.constant.HttpHeaderConsts;
import com.alibaba.nacos.common.model.RestResult;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.core.code.ControllerMethodsCache;
import com.alibaba.nacos.core.utils.ReuseHttpServletRequest;
import com.alibaba.nacos.naming.cluster.ServerStatus;
import com.alibaba.nacos.naming.cluster.ServerStatusManager;
import com.alibaba.nacos.naming.core.DistroMapper;
import com.alibaba.nacos.naming.healthcheck.RsInfo;
import com.alibaba.nacos.naming.misc.HttpClient;
import com.alibaba.nacos.naming.misc.SwitchDomain;
import com.alibaba.nacos.sys.env.Constants;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NamingWebFilterTest {
    
    @Test
    void testServiceNameFilterAddsDefaultGroup() throws Exception {
        ServiceNameFilter filter = new ServiceNameFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/nacos/v1/ns/instance");
        request.addParameter(CommonParams.SERVICE_NAME, " service ");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<ServletRequest> filteredRequest = new AtomicReference<>();
        
        filter.doFilter(request, response, new CaptureRequestChain(filteredRequest));
        
        assertEquals(HttpServletResponse.SC_OK, response.getStatus());
        assertEquals("DEFAULT_GROUP@@service",
            filteredRequest.get().getParameter(CommonParams.SERVICE_NAME));
    }
    
    @Test
    void testServiceNameFilterKeepsGroupedServiceName() throws Exception {
        ServiceNameFilter filter = new ServiceNameFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/nacos/v1/ns/instance");
        request.addParameter(CommonParams.SERVICE_NAME, "group@@service");
        request.addParameter(CommonParams.GROUP_NAME, "ignored");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<ServletRequest> filteredRequest = new AtomicReference<>();
        
        filter.doFilter(request, response, new CaptureRequestChain(filteredRequest));
        
        assertEquals("group@@service",
            filteredRequest.get().getParameter(CommonParams.SERVICE_NAME));
    }
    
    @Test
    void testTrafficReviseFilterLimitsConfiguredUrl() throws Exception {
        TrafficReviseFilter filter =
            trafficReviseFilter(null, null, Collections.singletonMap("/nacos/v1/ns/instance", 429));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/nacos/v1/ns/instance");
        request.setQueryString("serviceName=test");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        
        filter.doFilter(request, response, chain);
        
        assertEquals(429, response.getStatus());
        verify(chain, never()).doFilter(any(), any());
    }
    
    @Test
    void testTrafficReviseFilterContinuesWhenLimitedUrlNotMatched() throws Exception {
        TrafficReviseFilter filter =
            trafficReviseFilter(ServerStatus.UP, null,
                Collections.singletonMap("/nacos/v1/ns/other", 429));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/nacos/v1/ns/instance");
        request.setQueryString("serviceName=test");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        
        filter.doFilter(request, response, chain);
        
        verify(chain).doFilter(same(request), same(response));
    }
    
    @Test
    void testTrafficReviseFilterPassesWhenServerUp() throws Exception {
        TrafficReviseFilter filter =
            trafficReviseFilter(ServerStatus.UP, null, Collections.emptyMap());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/nacos/v1/ns/instance");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        
        filter.doFilter(request, response, chain);
        
        verify(chain).doFilter(same(request), same(response));
    }
    
    @Test
    void testTrafficReviseFilterPassesPeerServerRequest() throws Exception {
        TrafficReviseFilter filter =
            trafficReviseFilter(ServerStatus.DOWN, null, Collections.emptyMap());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/nacos/v1/ns/instance");
        request.addHeader(HttpHeaderConsts.USER_AGENT_HEADER, Constants.NACOS_SERVER_HEADER);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        
        filter.doFilter(request, response, chain);
        
        verify(chain).doFilter(same(request), same(response));
    }
    
    @Test
    void testTrafficReviseFilterAllowsWriteOnlyPost() throws Exception {
        TrafficReviseFilter filter =
            trafficReviseFilter(ServerStatus.WRITE_ONLY, null, Collections.emptyMap());
        MockHttpServletRequest request =
            new MockHttpServletRequest("POST", "/nacos/v1/ns/instance");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        
        filter.doFilter(request, response, chain);
        
        verify(chain).doFilter(same(request), same(response));
    }
    
    @Test
    void testTrafficReviseFilterAllowsReadOnlyGet() throws Exception {
        TrafficReviseFilter filter =
            trafficReviseFilter(ServerStatus.READ_ONLY, null, Collections.emptyMap());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/nacos/v1/ns/instance");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        
        filter.doFilter(request, response, chain);
        
        verify(chain).doFilter(same(request), same(response));
    }
    
    @Test
    void testTrafficReviseFilterRejectsUnavailableServerWithDetail() throws Exception {
        TrafficReviseFilter filter =
            trafficReviseFilter(ServerStatus.DOWN, Optional.of("warmup"), Collections.emptyMap());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/nacos/v1/ns/instance");
        MockHttpServletResponse response = new MockHttpServletResponse();
        
        filter.doFilter(request, response, mock(FilterChain.class));
        
        assertEquals(HttpServletResponse.SC_SERVICE_UNAVAILABLE, response.getStatus());
        assertTrue(response.getContentAsString().contains("detailed error message: warmup"));
    }
    
    @Test
    void testTrafficReviseFilterRejectsUnavailableServerWithoutDetail() throws Exception {
        TrafficReviseFilter filter =
            trafficReviseFilter(ServerStatus.DOWN, Optional.empty(), Collections.emptyMap());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/nacos/v1/ns/instance");
        MockHttpServletResponse response = new MockHttpServletResponse();
        
        filter.doFilter(request, response, mock(FilterChain.class));
        
        assertEquals(HttpServletResponse.SC_SERVICE_UNAVAILABLE, response.getStatus());
        assertTrue(response.getContentAsString().contains("please try again later"));
    }
    
    @Test
    void testDistroIpPortTagGeneratorUsesDirectIpAndPort() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("ip", " 1.1.1.1 ");
        request.addParameter("port", " 8848 ");
        
        String actual = new DistroIpPortTagGenerator()
            .getResponsibleTag(new ReuseHttpServletRequest(request));
        
        assertEquals("1.1.1.1:8848", actual);
    }
    
    @Test
    void testDistroIpPortTagGeneratorUsesBeatFallback() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        RsInfo beat = new RsInfo();
        beat.setIp("2.2.2.2");
        beat.setPort(9848);
        request.addParameter("beat", JacksonUtils.toJson(beat));
        
        String actual = new DistroIpPortTagGenerator()
            .getResponsibleTag(new ReuseHttpServletRequest(request));
        
        assertEquals("2.2.2.2:9848", actual);
    }
    
    @Test
    void testDistroTagGeneratorImplDelegatesToIpPortGenerator() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("ip", "1.1.1.1");
        
        String actual = new DistroTagGeneratorImpl()
            .getResponsibleTag(new ReuseHttpServletRequest(request));
        
        assertEquals("1.1.1.1:0", actual);
    }
    
    @Test
    void testDistroFilterPassesWhenMethodNotCanDistro() throws Exception {
        DistroFilter filter = distroFilter();
        ControllerMethodsCache methodsCache =
            (ControllerMethodsCache) ReflectionTestUtils.getField(filter, "controllerMethodsCache");
        Method method = FilterMethodHolder.class.getDeclaredMethod("plainMethod");
        when(methodsCache.getMethod(any())).thenReturn(method);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/nacos/v1/ns/instance");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        
        filter.doFilter(request, response, chain);
        
        verify(chain).doFilter(any(ReuseHttpServletRequest.class), same(response));
    }
    
    @Test
    void testDistroFilterPassesWhenCurrentServerResponsible() throws Exception {
        DistroFilter filter = distroFilter();
        ControllerMethodsCache methodsCache =
            (ControllerMethodsCache) ReflectionTestUtils.getField(filter, "controllerMethodsCache");
        DistroTagGenerator tagGenerator =
            (DistroTagGenerator) ReflectionTestUtils.getField(filter, "distroTagGenerator");
        DistroMapper distroMapper =
            (DistroMapper) ReflectionTestUtils.getField(filter, "distroMapper");
        when(methodsCache.getMethod(any())).thenReturn(canDistroMethod());
        when(tagGenerator.getResponsibleTag(any())).thenReturn("1.1.1.1:8848");
        when(distroMapper.responsible("1.1.1.1:8848")).thenReturn(true);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/nacos/v1/ns/instance");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        
        filter.doFilter(request, response, chain);
        
        verify(chain).doFilter(any(ReuseHttpServletRequest.class), same(response));
    }
    
    @Test
    void testDistroFilterRejectsPeerRedirect() throws Exception {
        DistroFilter filter = distroFilter();
        stubCanDistroNotResponsible(filter);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/nacos/v1/ns/instance");
        request.addHeader(HttpHeaderConsts.USER_AGENT_HEADER, "Nacos-Server:v3");
        MockHttpServletResponse response = new MockHttpServletResponse();
        
        filter.doFilter(request, response, mock(FilterChain.class));
        
        assertEquals(HttpServletResponse.SC_BAD_REQUEST, response.getStatus());
        assertTrue(response.getErrorMessage().contains("receive invalid redirect request"));
    }
    
    @Test
    void testDistroFilterReturnsNotImplementedWhenMethodMissing() throws Exception {
        DistroFilter filter = distroFilter();
        ControllerMethodsCache methodsCache =
            (ControllerMethodsCache) ReflectionTestUtils.getField(filter, "controllerMethodsCache");
        when(methodsCache.getMethod(any())).thenReturn(null);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/nacos/v1/ns/missing");
        MockHttpServletResponse response = new MockHttpServletResponse();
        
        filter.doFilter(request, response, mock(FilterChain.class));
        
        assertEquals(HttpServletResponse.SC_NOT_IMPLEMENTED, response.getStatus());
        assertTrue(response.getErrorMessage().contains("no such api:GET:/nacos/v1/ns/missing"));
    }
    
    @Test
    void testDistroFilterProxiesToMappedServer() throws Exception {
        DistroFilter filter = distroFilter();
        DistroMapper distroMapper = stubCanDistroNotResponsible(filter);
        when(distroMapper.mapSrv("1.1.1.1:8848")).thenReturn("2.2.2.2:8848");
        MockHttpServletRequest request =
            new MockHttpServletRequest("POST", "/nacos/v1/ns/instance");
        request.setContent("body".getBytes(StandardCharsets.UTF_8));
        request.addHeader("X-Test", "value");
        request.addParameter("ip", "1.1.1.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        RestResult<String> result = new RestResult<>(200, "ok", "proxied");
        
        ConfigurableEnvironment cachedEnvironment = EnvUtil.getEnvironment();
        EnvUtil.setEnvironment(new MockEnvironment());
        try {
            try (MockedStatic<HttpClient> httpClient = mockStatic(HttpClient.class)) {
                httpClient.when(() -> HttpClient.translateParameterMap(anyMap()))
                    .thenCallRealMethod();
                httpClient.when(() -> HttpClient.request(anyString(), anyList(), anyMap(),
                    anyString(), anyInt(), anyInt(), anyString(), eq("POST")))
                    .thenReturn(result);
                filter.doFilter(request, response, mock(FilterChain.class));
            }
        } finally {
            EnvUtil.setEnvironment(cachedEnvironment);
        }
        
        assertEquals(HttpServletResponse.SC_OK, response.getStatus());
        assertEquals("proxied", response.getContentAsString());
    }
    
    private TrafficReviseFilter trafficReviseFilter(ServerStatus serverStatus,
        Optional<String> errorMsg, Map<String, Integer> limitedUrlMap) {
        TrafficReviseFilter filter = new TrafficReviseFilter();
        ServerStatusManager serverStatusManager = mock(ServerStatusManager.class);
        SwitchDomain switchDomain = mock(SwitchDomain.class);
        when(switchDomain.getLimitedUrlMap()).thenReturn(limitedUrlMap);
        if (serverStatus != null) {
            when(serverStatusManager.getServerStatus()).thenReturn(serverStatus);
        }
        if (errorMsg != null) {
            when(serverStatusManager.getErrorMsg()).thenReturn(errorMsg);
        }
        ReflectionTestUtils.setField(filter, "serverStatusManager", serverStatusManager);
        ReflectionTestUtils.setField(filter, "switchDomain", switchDomain);
        return filter;
    }
    
    private DistroFilter distroFilter() {
        DistroFilter filter = new DistroFilter();
        ReflectionTestUtils.setField(filter, "controllerMethodsCache",
            mock(ControllerMethodsCache.class));
        ReflectionTestUtils.setField(filter, "distroMapper", mock(DistroMapper.class));
        ReflectionTestUtils.setField(filter, "distroTagGenerator", mock(DistroTagGenerator.class));
        return filter;
    }
    
    private DistroMapper stubCanDistroNotResponsible(DistroFilter filter) throws Exception {
        ControllerMethodsCache methodsCache =
            (ControllerMethodsCache) ReflectionTestUtils.getField(filter, "controllerMethodsCache");
        DistroTagGenerator tagGenerator =
            (DistroTagGenerator) ReflectionTestUtils.getField(filter, "distroTagGenerator");
        DistroMapper distroMapper =
            (DistroMapper) ReflectionTestUtils.getField(filter, "distroMapper");
        when(methodsCache.getMethod(any())).thenReturn(canDistroMethod());
        when(tagGenerator.getResponsibleTag(any())).thenReturn("1.1.1.1:8848");
        when(distroMapper.responsible("1.1.1.1:8848")).thenReturn(false);
        return distroMapper;
    }
    
    private Method canDistroMethod() throws NoSuchMethodException {
        return FilterMethodHolder.class.getDeclaredMethod("canDistroMethod");
    }
    
    private static class CaptureRequestChain implements FilterChain {
        
        private final AtomicReference<ServletRequest> filteredRequest;
        
        CaptureRequestChain(AtomicReference<ServletRequest> filteredRequest) {
            this.filteredRequest = filteredRequest;
        }
        
        @Override
        public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse) {
            filteredRequest.set(servletRequest);
        }
    }
    
    private static class FilterMethodHolder {
        
        @CanDistro
        void canDistroMethod() {
        }
        
        void plainMethod() {
        }
    }
}
