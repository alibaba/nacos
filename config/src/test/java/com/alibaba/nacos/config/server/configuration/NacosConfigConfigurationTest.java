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

package com.alibaba.nacos.config.server.configuration;

import com.alibaba.nacos.config.server.filter.CircuitFilter;
import com.alibaba.nacos.config.server.filter.NacosWebFilter;
import com.alibaba.nacos.core.code.ControllerMethodsCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class NacosConfigConfigurationTest {
    
    private ControllerMethodsCache methodsCache;
    
    private NacosConfigConfiguration configuration;
    
    @BeforeEach
    void setUp() {
        methodsCache = mock(ControllerMethodsCache.class);
        configuration = new NacosConfigConfiguration(methodsCache);
    }
    
    @Test
    void testInit() {
        configuration.init();
        
        verify(methodsCache).initClassMethod("com.alibaba.nacos.config.server.controller");
    }
    
    @Test
    void testNacosWebFilterRegistration() {
        FilterRegistrationBean<NacosWebFilter> registration =
            configuration.nacosWebFilterRegistration();
        
        assertInstanceOf(NacosWebFilter.class, registration.getFilter());
        assertEquals("nacosWebFilter", registration.getFilterName());
        assertEquals(1, registration.getOrder());
        assertEquals(1, registration.getUrlPatterns().size());
        assertEquals("/v1/cs/*", registration.getUrlPatterns().iterator().next());
    }
    
    @Test
    void testNacosWebFilter() {
        assertInstanceOf(NacosWebFilter.class, configuration.nacosWebFilter());
    }
    
    @Test
    void testTransferToLeaderRegistration() {
        FilterRegistrationBean<CircuitFilter> registration =
            configuration.transferToLeaderRegistration();
        
        assertInstanceOf(CircuitFilter.class, registration.getFilter());
        assertEquals("curcuitFilter", registration.getFilterName());
        assertEquals(6, registration.getOrder());
        assertEquals(1, registration.getUrlPatterns().size());
        assertEquals("/v1/cs/*", registration.getUrlPatterns().iterator().next());
    }
    
    @Test
    void testTransferToLeader() {
        assertInstanceOf(CircuitFilter.class, configuration.transferToLeader());
    }
}
