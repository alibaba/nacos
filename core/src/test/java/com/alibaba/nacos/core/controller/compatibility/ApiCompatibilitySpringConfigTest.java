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

package com.alibaba.nacos.core.controller.compatibility;

import com.alibaba.nacos.core.code.ControllerMethodsCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class ApiCompatibilitySpringConfigTest {
    
    @Mock
    ControllerMethodsCache controllerMethodsCache;
    
    ApiCompatibilitySpringConfig apiCompatibilitySpringConfig;
    
    @BeforeEach
    void setUp() {
        apiCompatibilitySpringConfig = new ApiCompatibilitySpringConfig();
    }
    
    @Test
    public void testApiCompatibilityFilterRegistration() {
        ApiCompatibilityFilter apiCompatibilityFilter = apiCompatibilitySpringConfig.apiCompatibilityFilter(
                controllerMethodsCache);
        FilterRegistrationBean<ApiCompatibilityFilter> registrationBean = apiCompatibilitySpringConfig.apiCompatibilityFilterRegistration(
                apiCompatibilityFilter);
        assertEquals(5, registrationBean.getOrder());
        assertTrue(registrationBean.getUrlPatterns().contains("/v1/*"));
        assertTrue(registrationBean.getUrlPatterns().contains("/v2/*"));
    }
}