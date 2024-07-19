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

package com.alibaba.nacos.core.context.remote;

import org.junit.jupiter.api.Test;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HttpRequestContextConfigTest {
    
    @Test
    void testRequestContextFilterRegistration() {
        HttpRequestContextConfig contextConfig = new HttpRequestContextConfig();
        HttpRequestContextFilter filter = contextConfig.nacosRequestContextFilter();
        FilterRegistrationBean<HttpRequestContextFilter> actual = contextConfig.requestContextFilterRegistration(
                filter);
        assertEquals(filter, actual.getFilter());
        assertEquals("/*", actual.getUrlPatterns().iterator().next());
        assertEquals(Integer.MIN_VALUE, actual.getOrder());
    }
}