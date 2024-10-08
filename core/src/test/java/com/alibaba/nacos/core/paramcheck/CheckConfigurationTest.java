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

package com.alibaba.nacos.core.paramcheck;

import com.alibaba.nacos.core.code.ControllerMethodsCache;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * {@link CheckConfiguration} unit tests.
 *
 * @author lynn.lqp
 * @date 2023/12/28
 */
class CheckConfigurationTest {
    
    @Test
    void testCheckerFilter() {
        CheckConfiguration checkConfiguration = new CheckConfiguration();
        ControllerMethodsCache methodsCache = Mockito.mock(ControllerMethodsCache.class);
        ParamCheckerFilter checkerFilter = checkConfiguration.checkerFilter(methodsCache);
        assertNotNull(checkerFilter);
    }
    
    @Test
    void testCheckerFilterRegistration() {
        ParamCheckerFilter checkerFilter = Mockito.mock(ParamCheckerFilter.class);
        CheckConfiguration configuration = new CheckConfiguration();
        FilterRegistrationBean<ParamCheckerFilter> registration = configuration.checkerFilterRegistration(checkerFilter);
        String name = (String) ReflectionTestUtils.getField(registration, "name");
        assertEquals("checkerFilter", name);
        assertEquals(8, registration.getOrder());
    }
}
