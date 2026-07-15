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

import com.alibaba.nacos.core.code.ControllerMethodsCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class NamingConfigTest {
    
    private ControllerMethodsCache methodsCache;
    
    private NamingConfig namingConfig;
    
    @BeforeEach
    void setUp() {
        methodsCache = mock(ControllerMethodsCache.class);
        namingConfig = new NamingConfig(methodsCache);
    }
    
    @Test
    void testInit() {
        namingConfig.init();
        
        verify(methodsCache).initClassMethod("com.alibaba.nacos.naming.controllers");
    }
    
    @Test
    void testDistroFilterRegistration() {
        FilterRegistrationBean<DistroFilter> registration =
            namingConfig.distroFilterRegistration();
        
        assertInstanceOf(DistroFilter.class, registration.getFilter());
        assertEquals("distroFilter", registration.getFilterName());
        assertEquals(7, registration.getOrder());
        assertEquals(3, registration.getUrlPatterns().size());
        assertTrue(registration.getUrlPatterns().contains("/v1/ns/*"));
        assertTrue(registration.getUrlPatterns().contains("/v3/client/ns/*"));
        assertTrue(registration.getUrlPatterns().contains("/v3/admin/ns/*"));
    }
    
    @Test
    void testServiceNameFilterRegistration() {
        FilterRegistrationBean<ServiceNameFilter> registration =
            namingConfig.serviceNameFilterRegistration();
        
        assertInstanceOf(ServiceNameFilter.class, registration.getFilter());
        assertEquals("serviceNameFilter", registration.getFilterName());
        assertEquals(5, registration.getOrder());
        assertEquals(1, registration.getUrlPatterns().size());
        assertEquals("/v1/ns/*", registration.getUrlPatterns().iterator().next());
    }
    
    @Test
    void testTrafficReviseFilterRegistration() {
        FilterRegistrationBean<TrafficReviseFilter> registration =
            namingConfig.trafficReviseFilterRegistration();
        
        assertInstanceOf(TrafficReviseFilter.class, registration.getFilter());
        assertEquals("trafficReviseFilter", registration.getFilterName());
        assertEquals(1, registration.getOrder());
        assertEquals(1, registration.getUrlPatterns().size());
        assertEquals("/v1/ns/*", registration.getUrlPatterns().iterator().next());
    }
    
    @Test
    void testClientAttributesFilterRegistration() {
        FilterRegistrationBean<ClientAttributesFilter> registration =
            namingConfig.clientAttributesFilterRegistration();
        
        assertInstanceOf(ClientAttributesFilter.class, registration.getFilter());
        assertEquals("clientAttributes_filter", registration.getFilterName());
        assertEquals(8, registration.getOrder());
        assertEquals(2, registration.getUrlPatterns().size());
    }
    
    @Test
    void testFilterBeans() {
        assertNotNull(namingConfig.distroFilter());
        assertNotNull(namingConfig.trafficReviseFilter());
        assertNotNull(namingConfig.serviceNameFilter());
        assertNotNull(namingConfig.clientAttributesFilter());
    }
}
