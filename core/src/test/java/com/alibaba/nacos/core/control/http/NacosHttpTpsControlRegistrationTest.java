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

package com.alibaba.nacos.core.control.http;

import com.alibaba.nacos.core.code.ControllerMethodsCache;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link NacosHttpTpsControlRegistration} unit test.
 *
 * @author xiweng.yy
 */
@ExtendWith(MockitoExtension.class)
class NacosHttpTpsControlRegistrationTest {
    
    @Mock
    private ControllerMethodsCache methodsCache;
    
    @Test
    void testTpsFilterBean() {
        NacosHttpTpsControlRegistration config = new NacosHttpTpsControlRegistration();
        NacosHttpTpsFilter filter = config.tpsFilter(methodsCache);
        assertNotNull(filter);
    }
    
    @Test
    void testTpsFilterRegistration() {
        NacosHttpTpsControlRegistration config = new NacosHttpTpsControlRegistration();
        NacosHttpTpsFilter filter = config.tpsFilter(methodsCache);
        FilterRegistrationBean<NacosHttpTpsFilter> registration =
            config.tpsFilterRegistration(filter);
        assertNotNull(registration);
        assertNotNull(registration.getFilter());
        assertTrue(registration.getFilter() instanceof NacosHttpTpsFilter);
        assertTrue(registration.getUrlPatterns().contains("/v1/ns/*"));
        assertTrue(registration.getUrlPatterns().contains("/v2/ns/*"));
        assertTrue(registration.getUrlPatterns().contains("/v1/cs/*"));
        assertTrue(registration.getUrlPatterns().contains("/v2/cs/*"));
        assertEquals("tpsFilter", ReflectionTestUtils.getField(registration, "name"));
        assertEquals(6, registration.getOrder());
    }
}
