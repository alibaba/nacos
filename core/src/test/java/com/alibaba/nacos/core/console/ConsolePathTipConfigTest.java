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

package com.alibaba.nacos.core.console;

import org.junit.jupiter.api.Test;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ConsolePathTipConfig} unit test.
 *
 * @author cxhello
 * @date 2025/7/24
 */
class ConsolePathTipConfigTest {
    
    @Test
    void testNacosConsolePathTipFilterRegistration() {
        ConsolePathTipConfig config = new ConsolePathTipConfig();
        FilterRegistrationBean<NacosConsolePathTipFilter> registration =
            config.nacosConsolePathTipFilterRegistration();
        assertNotNull(registration);
        assertNotNull(registration.getFilter());
        assertTrue(registration.getFilter() instanceof NacosConsolePathTipFilter);
        assertTrue(registration.getUrlPatterns().contains("/*"));
        assertEquals("nacosConsolePathTipFilter",
            ReflectionTestUtils.getField(registration, "name"));
        assertEquals(7, registration.getOrder());
    }
}
