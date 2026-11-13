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

package com.alibaba.nacos.auth.parser.grpc;

import com.alibaba.nacos.api.config.remote.request.ConfigPublishRequest;
import com.alibaba.nacos.api.lock.common.LockConstants;
import com.alibaba.nacos.api.lock.model.LockInstance;
import com.alibaba.nacos.api.lock.remote.request.LockOperationRequest;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.auth.api.Resource;
import com.alibaba.nacos.plugin.auth.constant.SignType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LockGrpcResourceParserTest {
    
    private LockGrpcResourceParser resourceParser;
    
    @BeforeEach
    void setUp() throws Exception {
        resourceParser = new LockGrpcResourceParser();
    }
    
    @Test
    @Secured(signType = SignType.LOCK)
    void testParseWithLockKey() throws NoSuchMethodException {
        Secured secured = getMethodSecure();
        Request request = mockLockRequest("testKey");
        Resource actual = resourceParser.parse(request, secured);
        assertEquals(StringUtils.EMPTY, actual.getNamespaceId());
        assertEquals(StringUtils.EMPTY, actual.getGroup());
        assertEquals("testKey", actual.getName());
        assertEquals(SignType.LOCK, actual.getType());
    }
    
    @Test
    @Secured(signType = SignType.LOCK)
    void testParseWithoutLockInstance() throws NoSuchMethodException {
        Secured secured = getMethodSecure();
        LockOperationRequest request = new LockOperationRequest();
        Resource actual = resourceParser.parse(request, secured);
        assertEquals(StringUtils.EMPTY, actual.getNamespaceId());
        assertEquals(StringUtils.EMPTY, actual.getGroup());
        assertEquals(StringUtils.EMPTY, actual.getName());
        assertEquals(SignType.LOCK, actual.getType());
    }
    
    @Test
    @Secured(signType = SignType.LOCK)
    void testParseWithBlankLockKey() throws NoSuchMethodException {
        Secured secured = getMethodSecure();
        Request request = mockLockRequest(StringUtils.EMPTY);
        Resource actual = resourceParser.parse(request, secured);
        assertEquals(StringUtils.EMPTY, actual.getName());
        assertEquals(SignType.LOCK, actual.getType());
    }
    
    @Test
    @Secured(signType = SignType.LOCK)
    void testParseWithNullLockKey() throws NoSuchMethodException {
        Secured secured = getMethodSecure();
        Request request = mockLockRequest(null);
        Resource actual = resourceParser.parse(request, secured);
        assertEquals(StringUtils.EMPTY, actual.getName());
        assertEquals(SignType.LOCK, actual.getType());
    }
    
    @Test
    @Secured(signType = SignType.LOCK)
    void testParseWithNonLockRequest() throws NoSuchMethodException {
        Secured secured = getMethodSecure();
        Request request = new ConfigPublishRequest();
        Resource actual = resourceParser.parse(request, secured);
        assertEquals(StringUtils.EMPTY, actual.getNamespaceId());
        assertEquals(StringUtils.EMPTY, actual.getGroup());
        assertEquals(StringUtils.EMPTY, actual.getName());
        assertEquals(SignType.LOCK, actual.getType());
    }
    
    private Request mockLockRequest(String key) {
        LockOperationRequest request = new LockOperationRequest();
        LockInstance lockInstance = new LockInstance(key, 30000L,
            LockConstants.REENTRANT_LOCK_TYPE);
        request.setLockInstance(lockInstance);
        return request;
    }
    
    private Secured getMethodSecure() throws NoSuchMethodException {
        StackTraceElement[] traces = new Exception().getStackTrace();
        StackTraceElement callerElement = traces[1];
        String methodName = callerElement.getMethodName();
        Method method = this.getClass().getDeclaredMethod(methodName);
        return method.getAnnotation(Secured.class);
    }
}
