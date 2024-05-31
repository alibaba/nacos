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

package com.alibaba.nacos.common.http;

import org.apache.http.client.methods.HttpRequestBase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BaseHttpMethodTest {
    
    @Test
    void testHttpGet() {
        BaseHttpMethod method = BaseHttpMethod.GET;
        HttpRequestBase request = method.init("http://example.com");
        assertEquals("GET", request.getMethod());
    }
    
    @Test
    void testHttpGetLarge() {
        BaseHttpMethod method = BaseHttpMethod.GET_LARGE;
        HttpRequestBase request = method.init("http://example.com");
        assertEquals("GET", request.getMethod());
    }
    
    @Test
    void testHttpPost() {
        BaseHttpMethod method = BaseHttpMethod.POST;
        HttpRequestBase request = method.init("http://example.com");
        assertEquals("POST", request.getMethod());
    }
    
    @Test
    void testHttpPut() {
        BaseHttpMethod method = BaseHttpMethod.PUT;
        HttpRequestBase request = method.init("http://example.com");
        assertEquals("PUT", request.getMethod());
    }
    
    @Test
    void testHttpDelete() {
        BaseHttpMethod method = BaseHttpMethod.DELETE;
        HttpRequestBase request = method.init("http://example.com");
        assertEquals("DELETE", request.getMethod());
    }
    
    @Test
    void testHttpDeleteLarge() {
        BaseHttpMethod method = BaseHttpMethod.DELETE_LARGE;
        HttpRequestBase request = method.init("http://example.com");
        assertEquals("DELETE", request.getMethod());
    }
    
    @Test
    void testHttpHead() {
        BaseHttpMethod method = BaseHttpMethod.HEAD;
        HttpRequestBase request = method.init("http://example.com");
        assertEquals("HEAD", request.getMethod());
    }
    
    @Test
    void testHttpTrace() {
        BaseHttpMethod method = BaseHttpMethod.TRACE;
        HttpRequestBase request = method.init("http://example.com");
        assertEquals("TRACE", request.getMethod());
    }
    
    @Test
    void testHttpPatch() {
        BaseHttpMethod method = BaseHttpMethod.PATCH;
        HttpRequestBase request = method.init("http://example.com");
        assertEquals("PATCH", request.getMethod());
    }
    
    @Test
    void testHttpOptions() {
        BaseHttpMethod method = BaseHttpMethod.OPTIONS;
        HttpRequestBase request = method.init("http://example.com");
        assertEquals("TRACE", request.getMethod());
    }
    
    @Test
    void testSourceOf() {
        BaseHttpMethod method = BaseHttpMethod.sourceOf("GET");
        assertEquals(BaseHttpMethod.GET, method);
    }
    
    @Test
    void testSourceOfNotFound() {
        assertThrows(IllegalArgumentException.class, () -> {
            BaseHttpMethod.sourceOf("Not Found");
        });
    }
}