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

package com.alibaba.nacos.prometheus.filter;

import jakarta.servlet.Filter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.security.authentication.AuthenticationManager;

import java.util.HashSet;
import java.util.Set;

import static com.alibaba.nacos.prometheus.api.ApiConstants.PROMETHEUS_CONTROLLER_PATH;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class PrometheusAuthFilterTest {
    
    private final PrometheusAuthFilter prometheusAuthFilter = new PrometheusAuthFilter();
    
    @Test
    void testAllAuthFiltersCoverPrometheusSubPaths() {
        assertUrlPatterns(prometheusAuthFilter.basicAuthenticationFilter(
            mock(AuthenticationManager.class)));
        assertUrlPatterns(prometheusAuthFilter.anonymousAuthenticationFilter());
        assertUrlPatterns(prometheusAuthFilter.authorizationFilter());
        assertUrlPatterns(prometheusAuthFilter.exceptionTranslationFilter());
    }
    
    private void assertUrlPatterns(FilterRegistrationBean<? extends Filter> registration) {
        Set<String> expected =
            Set.of(PROMETHEUS_CONTROLLER_PATH, PROMETHEUS_CONTROLLER_PATH + "/*");
        assertEquals(expected, new HashSet<>(registration.getUrlPatterns()));
    }
}
