/*
 * Copyright 1999-2021 Alibaba Group Holding Ltd.
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

import com.alibaba.nacos.api.exception.runtime.NacosRuntimeException;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test for {@link ControllerMethodsCache}.
 */
class ControllerMethodsCacheTest {
    
    private ControllerMethodsCache cache;
    
    @BeforeEach
    void setUp() {
        cache = new ControllerMethodsCache();
        EnvUtil.setContextPath("/nacos");
    }
    
    @AfterEach
    void tearDown() {
        EnvUtil.setContextPath(null);
    }
    
    @Test
    void getMethodReturnsNullWhenNoMapping() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/nacos/v1/cs/configs");
        request.setRequestURI("/nacos/v1/cs/configs");
        assertNull(cache.getMethod(request));
    }
    
    @Test
    void getMethodReturnsNullWhenNoParamMatch() {
        cache.initClassMethod(Collections.singleton(TestController.class));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/nacos/api/get");
        request.setRequestURI("/nacos/api/get");
        request.setParameter("required", "other");
        assertNull(cache.getMethod(request));
    }
    
    @Test
    void getMethodReturnsMethodWhenMappingAndParamMatch() throws Exception {
        cache.initClassMethod(Collections.singleton(TestController.class));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/nacos/api/get");
        request.setRequestURI("/nacos/api/get");
        request.setParameter("required", "yes");
        Method method = cache.getMethod(request);
        assertNotNull(method);
        assertEquals("get", method.getName());
    }
    
    @Test
    void getMethodWithPostMapping() throws Exception {
        cache.initClassMethod(Collections.singleton(TestController.class));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/nacos/api/post");
        request.setRequestURI("/nacos/api/post");
        Method method = cache.getMethod(request);
        assertNotNull(method);
        assertEquals("post", method.getName());
    }
    
    @Test
    void getPathThrowsOnInvalidUri() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("invalid%GG");
        assertThrows(NacosRuntimeException.class, () -> cache.getMethod(request));
    }
    
    @Test
    void initClassMethodWithSetRegistersMappings() throws Exception {
        Set<Class<?>> set = new HashSet<>();
        set.add(TestController.class);
        cache.initClassMethod(set);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/nacos/api/get");
        request.setRequestURI("/nacos/api/get");
        request.setParameter("required", "yes");
        assertNotNull(cache.getMethod(request));
    }
    
    @Test
    void initClassMethodIdempotentWhenSameClassScannedAgain() throws Exception {
        cache.initClassMethod(Collections.singleton(TestController.class));
        cache.initClassMethod(Collections.singleton(TestController.class));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/nacos/api/get");
        request.setRequestURI("/nacos/api/get");
        request.setParameter("required", "yes");
        assertNotNull(cache.getMethod(request));
    }
    
    @Test
    void getMethodWithContextPathStripped() throws Exception {
        cache.initClassMethod(Collections.singleton(TestController.class));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/nacos/api/get");
        request.setRequestURI("/nacos/api/get");
        request.setParameter("required", "yes");
        Method method = cache.getMethod(request);
        assertNotNull(method);
        assertEquals("get", method.getName());
    }
    
    @Test
    void getMethodWithRequestSpecificContextPathResolvesConsolePath() throws Exception {
        cache.initClassMethod(Collections.singleton(ConsolePathController.class));
        MockHttpServletRequest request =
            new MockHttpServletRequest("POST", "/src/v3/console/ai/skills/draft");
        request.setContextPath("/src");
        request.setRequestURI("/src/v3/console/ai/skills/draft");
        Method method = cache.getMethod(request);
        assertNotNull(method);
        assertEquals("createDraft", method.getName());
    }
    
    @Test
    void ambiguousMappingThrowsIllegalStateException() {
        cache.initClassMethod(Collections.singleton(AmbiguousController.class));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/nacos/ambig/same");
        request.setRequestURI("/nacos/ambig/same");
        request.setParameter("p", "v");
        IllegalStateException ex =
            assertThrows(IllegalStateException.class, () -> cache.getMethod(request));
        assertTrue(ex.getMessage().contains("Ambiguous methods"));
    }
    
    @Test
    void getMethodWithTrailingSlashResolvesSameMapping() throws Exception {
        cache.initClassMethod(Collections.singleton(TestController.class));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/nacos/api/get/");
        request.setRequestURI("/nacos/api/get/");
        request.setParameter("required", "yes");
        Method method = cache.getMethod(request);
        assertNotNull(method);
        assertEquals("get", method.getName());
    }
    
    @Test
    void getMethodMultipleMatchesPicksBestByParamCount() throws Exception {
        cache.initClassMethod(Collections.singleton(MultiParamController.class));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/nacos/multi/one");
        request.setRequestURI("/nacos/multi/one");
        request.setParameter("a", "1");
        request.setParameter("b", "2");
        Method method = cache.getMethod(request);
        assertNotNull(method);
        assertEquals("twoParams", method.getName());
    }
    
    @Test
    void initClassMethodByPackageNameRegistersMappings() throws Exception {
        cache.initClassMethod("com.alibaba.nacos.core.code");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/nacos/api/get");
        request.setRequestURI("/nacos/api/get");
        request.setParameter("required", "yes");
        Method method = cache.getMethod(request);
        assertNotNull(method);
        assertEquals("get", method.getName());
    }
    
    @Test
    void getMethodWithPutMapping() throws Exception {
        cache.initClassMethod(Collections.singleton(CrudController.class));
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/nacos/crud/1");
        request.setRequestURI("/nacos/crud/1");
        Method method = cache.getMethod(request);
        assertNotNull(method);
        assertEquals("update", method.getName());
    }
    
    @Test
    void getMethodWithDeleteMapping() throws Exception {
        cache.initClassMethod(Collections.singleton(CrudController.class));
        MockHttpServletRequest request = new MockHttpServletRequest("DELETE", "/nacos/crud/1");
        request.setRequestURI("/nacos/crud/1");
        Method method = cache.getMethod(request);
        assertNotNull(method);
        assertEquals("delete", method.getName());
    }
    
    @Test
    void getMethodWithPatchMapping() throws Exception {
        cache.initClassMethod(Collections.singleton(CrudController.class));
        MockHttpServletRequest request = new MockHttpServletRequest("PATCH", "/nacos/crud/1");
        request.setRequestURI("/nacos/crud/1");
        Method method = cache.getMethod(request);
        assertNotNull(method);
        assertEquals("patch", method.getName());
    }
    
    @Test
    void getMethodWithGetMappingNoPathUsesClassPathOnly() throws Exception {
        cache.initClassMethod(Collections.singleton(ClassPathOnlyController.class));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/nacos/only");
        request.setRequestURI("/nacos/only");
        Method method = cache.getMethod(request);
        assertNotNull(method);
        assertEquals("index", method.getName());
    }
    
    @Test
    void getMethodWithMethodLevelRequestMapping() throws Exception {
        cache.initClassMethod(Collections.singleton(MethodLevelRequestMappingController.class));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/nacos/req/action");
        request.setRequestURI("/nacos/req/action");
        Method method = cache.getMethod(request);
        assertNotNull(method);
        assertEquals("action", method.getName());
    }
    
    @Test
    void getMethodWithMethodLevelRequestMappingDefaultGet() throws Exception {
        cache.initClassMethod(Collections.singleton(MethodLevelRequestMappingController.class));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/nacos/req/default");
        request.setRequestURI("/nacos/req/default");
        Method method = cache.getMethod(request);
        assertNotNull(method);
        assertEquals("defaultGet", method.getName());
    }
    
    @Test
    void getMethodWithMultipleClassPaths() throws Exception {
        cache.initClassMethod(Collections.singleton(DualPathController.class));
        MockHttpServletRequest req1 = new MockHttpServletRequest("GET", "/nacos/primary/info");
        req1.setRequestURI("/nacos/primary/info");
        MockHttpServletRequest req2 = new MockHttpServletRequest("GET", "/nacos/second/info");
        req2.setRequestURI("/nacos/second/info");
        assertNotNull(cache.getMethod(req1));
        assertNotNull(cache.getMethod(req2));
        assertEquals("info", cache.getMethod(req1).getName());
        assertEquals("info", cache.getMethod(req2).getName());
    }
    
    /**
     * Test controller with GetMapping and param condition.
     */
    @RequestMapping("/api")
    public static class TestController {
        
        @GetMapping(value = "/get", params = "required=yes")
        public void get() {
        }
        
        @PostMapping(value = "/post")
        public void post() {
        }
    }
    
    /**
     * Two methods same path and param, so comparator gives 0 -> ambiguous.
     */
    @RequestMapping("/ambig")
    public static class AmbiguousController {
        
        @GetMapping(value = "/same", params = "p=v")
        public void same1() {
        }
        
        @GetMapping(value = "/same", params = "p=v")
        public void same2() {
        }
    }
    
    /**
     * Same path, different param count; comparator picks the one with more params when both match.
     */
    @RequestMapping("/multi")
    public static class MultiParamController {
        
        @GetMapping(value = "/one", params = "a=1")
        public void oneParam() {
        }
        
        @GetMapping(value = "/one", params = {"a=1", "b=2"})
        public void twoParams() {
        }
    }
    
    @RequestMapping("/crud")
    public static class CrudController {
        
        @PutMapping("/1")
        public void update() {
        }
        
        @DeleteMapping("/1")
        public void delete() {
        }
        
        @PatchMapping("/1")
        public void patch() {
        }
    }
    
    @RequestMapping("/only")
    public static class ClassPathOnlyController {
        
        @GetMapping
        public void index() {
        }
    }
    
    @RequestMapping("/req")
    public static class MethodLevelRequestMappingController {
        
        @RequestMapping(value = "/action", method = RequestMethod.POST)
        public void action() {
        }
        
        @RequestMapping(value = "/default")
        public void defaultGet() {
        }
    }
    
    @RequestMapping(value = {"/primary", "/second"})
    public static class DualPathController {
        
        @GetMapping("/info")
        public void info() {
        }
    }
    
    @RequestMapping("/v3/console/ai/skills")
    public static class ConsolePathController {
        
        @PostMapping("/draft")
        public void createDraft() {
        }
        
    }
}
