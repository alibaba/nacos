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

package com.alibaba.nacos.airegistry.config;

import com.alibaba.nacos.airegistry.controller.ArdSearchController;
import com.alibaba.nacos.airegistry.controller.ArdWellKnownController;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.core.code.ControllerMethodsCache;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link ArdWebConfiguration}.
 *
 * @author nacos
 */
class ArdWebConfigurationTest {
    
    @Test
    void shouldRegisterArdControllerMethodsForAuthentication() {
        ControllerMethodsCache methodsCache = new ControllerMethodsCache();
        new ArdWebConfiguration(methodsCache).init();
        
        assertSecuredMethod(methodsCache, "POST", "/nacos/v3/ai/ard/search",
            ArdSearchController.class, "search");
        assertSecuredMethod(methodsCache, "GET", "/nacos/.well-known/ai-catalog.json",
            ArdWellKnownController.class, "catalog");
    }
    
    private void assertSecuredMethod(ControllerMethodsCache methodsCache, String httpMethod,
        String requestUri, Class<?> controllerType, String methodName) {
        MockHttpServletRequest request = new MockHttpServletRequest(httpMethod, requestUri);
        request.setContextPath("/nacos");
        request.setRequestURI(requestUri);
        
        Method method = methodsCache.getMethod(request);
        assertNotNull(method);
        assertEquals(controllerType, method.getDeclaringClass());
        assertEquals(methodName, method.getName());
        assertTrue(method.isAnnotationPresent(Secured.class));
    }
}
