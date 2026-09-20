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

package com.alibaba.nacos.core.code;

import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ControllerMethodsCacheTest {

    private ControllerMethodsCache methodsCache;

    @BeforeEach
    void setUp() {
        EnvUtil.setContextPath("/nacos");
        methodsCache = new ControllerMethodsCache();
        methodsCache.initClassMethod(Collections.singleton(TestController.class));
    }

    @AfterEach
    void tearDown() {
        EnvUtil.setContextPath(null);
    }

    @Test
    void testHeadMethodFallbackToGet() {
        MockHttpServletRequest request = new MockHttpServletRequest("HEAD", "/nacos/test");
        Method method = methodsCache.getMethod(request);
        assertNotNull(method);
        assertEquals("get", method.getName());
        assertEquals("HEAD", request.getMethod());
    }

    @Test
    void testGetMethod() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/nacos/test");
        Method method = methodsCache.getMethod(request);
        assertNotNull(method);
        assertEquals("get", method.getName());
    }

    @Test
    void testHeadMethodWithParams() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("HEAD", "/nacos/test/params");
        request.setParameter("required", "yes");
        assertEquals(TestController.class.getMethod("getWithParams"), methodsCache.getMethod(request));
    }

    @Test
    void testHeadMethodReturnsNullWhenParamsDoNotMatch() {
        MockHttpServletRequest request = new MockHttpServletRequest("HEAD", "/nacos/test/params");
        request.setParameter("required", "other");
        assertNull(methodsCache.getMethod(request));
    }

    @Test
    void testHeadMethodDoesNotMatchPostMapping() {
        MockHttpServletRequest request = new MockHttpServletRequest("HEAD", "/nacos/test/post");
        assertNull(methodsCache.getMethod(request));
    }

    @Test
    void testPostMethod() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/nacos/test/post");
        assertEquals(TestController.class.getMethod("post"), methodsCache.getMethod(request));
    }

    @Test
    void testHeadMethodReturnsNullWhenNoMapping() {
        MockHttpServletRequest request = new MockHttpServletRequest("HEAD", "/nacos/test/missing");
        assertNull(methodsCache.getMethod(request));
    }

    @RequestMapping("/test")
    public static class TestController {

        @GetMapping
        public void get() {
        }

        @GetMapping(value = "/params", params = "required=yes")
        public void getWithParams() {
        }

        @PostMapping("/post")
        public void post() {
        }
    }
}
