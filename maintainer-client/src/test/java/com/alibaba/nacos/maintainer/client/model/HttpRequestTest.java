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

package com.alibaba.nacos.maintainer.client.model;

import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class HttpRequestTest {
    
    @Test
    void testBuild() {
        HttpRequest.Builder builder = new HttpRequest.Builder();
        builder.setHttpMethod("GET");
        builder.setPath("/nacos/v3/admin/ns/instance");
        builder.setParamValue(Collections.singletonMap("test", "value"));
        builder.addHeader(Collections.singletonMap("testH", "value"));
        builder.setBody("testBody");
        HttpRequest httpRequest = builder.build();
        assertNotNull(httpRequest);
        assertEquals("GET", httpRequest.getHttpMethod());
        assertEquals("/nacos/v3/admin/ns/instance", httpRequest.getPath());
        assertEquals("value", httpRequest.getParamValues().get("test"));
        assertEquals("value", httpRequest.getHeaders().get("testH"));
        assertEquals("testBody", httpRequest.getBody());
    }
    
    @Test
    void testSetter() {
        HttpRequest httpRequest = new HttpRequest.Builder().build();
        httpRequest.setHttpMethod("GET");
        httpRequest.setPath("/nacos/v3/admin/ns/instance");
        httpRequest.setParamValues(Collections.singletonMap("test", "value"));
        httpRequest.setHeaders(Collections.singletonMap("testH", "value"));
        httpRequest.setBody("testBody");
        assertNotNull(httpRequest);
        assertEquals("GET", httpRequest.getHttpMethod());
        assertEquals("/nacos/v3/admin/ns/instance", httpRequest.getPath());
        assertEquals("value", httpRequest.getParamValues().get("test"));
        assertEquals("value", httpRequest.getHeaders().get("testH"));
        assertEquals("testBody", httpRequest.getBody());
    }
}