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

package com.alibaba.nacos.core.web;

import com.alibaba.nacos.core.code.ControllerMethodsCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.unit.DataSize;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;

/**
 * {@link NacosCoreWebConfiguration} unit tests.
 */
@ExtendWith(MockitoExtension.class)
class NacosCoreWebConfigurationTest {
    
    @Mock
    private ControllerMethodsCache methodsCache;
    
    private NacosCoreWebConfiguration configuration;
    
    @BeforeEach
    void setUp() {
        configuration = new NacosCoreWebConfiguration(methodsCache);
    }
    
    @Test
    void initCallsMethodsCacheInitClassMethod() {
        configuration.init();
        verify(methodsCache).initClassMethod("com.alibaba.nacos.core.controller");
    }
    
    @Test
    void formSizeFilterRegistrationReturnsBeanWithCorrectSettings() {
        FormSizeFilter filter = new FormSizeFilter(1024);
        FilterRegistrationBean<FormSizeFilter> registration =
            configuration.formSizeFilterRegistration(filter);
        
        assertNotNull(registration);
        assertEquals(filter, registration.getFilter());
        assertEquals(1, registration.getUrlPatterns().size());
        assertEquals("/*", registration.getUrlPatterns().iterator().next());
        assertEquals("formSizeFilter", ReflectionTestUtils.getField(registration, "name"));
        assertEquals(5, registration.getOrder());
    }
    
    @Test
    void formSizeFilterBeanCreatesFilterWithCorrectSize() {
        FormSizeFilter filter = configuration.formSizeFilter(DataSize.ofMegabytes(2));
        
        assertNotNull(filter);
    }
}
