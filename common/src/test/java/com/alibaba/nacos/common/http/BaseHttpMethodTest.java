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

import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BaseHttpMethodTest {
    
    @Test
    void testHttpGet() {
        BaseHttpMethod method = BaseHttpMethod.GET;
        HttpUriRequestBase request = method.init("http://example.com");
        assertEquals("GET", request.getMethod());
    }
    
    @Test
    void testHttpGetLarge() {
        BaseHttpMethod method = BaseHttpMethod.GET_LARGE;
        HttpUriRequestBase request = method.init("http://example.com");
        assertEquals("GET", request.getMethod());
    }
    
    @Test
    void testHttpPost() {
        BaseHttpMethod method = BaseHttpMethod.POST;
        HttpUriRequestBase request = method.init("http://example.com");
        assertEquals("POST", request.getMethod());
    }
    
    @Test
    void testHttpPut() {
        BaseHttpMethod method = BaseHttpMethod.PUT;
        HttpUriRequestBase request = method.init("http://example.com");
        assertEquals("PUT", request.getMethod());
    }
    
    @Test
    void testHttpDelete() {
        BaseHttpMethod method = BaseHttpMethod.DELETE;
        HttpUriRequestBase request = method.init("http://example.com");
        assertEquals("DELETE", request.getMethod());
    }
    
    @Test
    void testHttpDeleteLarge() {
        BaseHttpMethod method = BaseHttpMethod.DELETE_LARGE;
        HttpUriRequestBase request = method.init("http://example.com");
        assertEquals("DELETE", request.getMethod());
    }
    
    @Test
    void testHttpHead() {
        BaseHttpMethod method = BaseHttpMethod.HEAD;
        HttpUriRequestBase request = method.init("http://example.com");
        assertEquals("HEAD", request.getMethod());
    }
    
    @Test
    void testHttpTrace() {
        BaseHttpMethod method = BaseHttpMethod.TRACE;
        HttpUriRequestBase request = method.init("http://example.com");
        assertEquals("TRACE", request.getMethod());
    }
    
    @Test
    void testHttpPatch() {
        BaseHttpMethod method = BaseHttpMethod.PATCH;
        HttpUriRequestBase request = method.init("http://example.com");
        assertEquals("PATCH", request.getMethod());
    }
    
    @Test
    void testHttpOptions() {
        BaseHttpMethod method = BaseHttpMethod.OPTIONS;
        HttpUriRequestBase request = method.init("http://example.com");
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