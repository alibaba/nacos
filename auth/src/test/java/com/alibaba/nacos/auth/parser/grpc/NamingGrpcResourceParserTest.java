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

package com.alibaba.nacos.auth.parser.grpc;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.naming.remote.request.AbstractNamingRequest;
import com.alibaba.nacos.api.naming.remote.request.NotifySubscriberRequest;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.auth.api.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class NamingGrpcResourceParserTest {
    
    private NamingGrpcResourceParser resourceParser;
    
    @BeforeEach
    void setUp() throws Exception {
        resourceParser = new NamingGrpcResourceParser();
    }
    
    @Test
    @Secured()
    void testParseWithFullContextForNamingRequest() throws NoSuchMethodException {
        Secured secured = getMethodSecure();
        AbstractNamingRequest request = mockNamingRequest("testNs", "testG", "testS");
        Resource actual = resourceParser.parse(request, secured);
        assertEquals("testNs", actual.getNamespaceId());
        assertEquals("testG", actual.getGroup());
        assertEquals("testS", actual.getName());
        assertEquals(Constants.Naming.NAMING_MODULE, actual.getType());
        assertEquals(MockNamingRequest.class.getSimpleName(), actual.getProperties()
                .getProperty(com.alibaba.nacos.plugin.auth.constant.Constants.Resource.REQUEST_CLASS));
    }
    
    @Test
    @Secured()
    void testParseWithFullContextForOtherRequest() throws NoSuchMethodException {
        Secured secured = getMethodSecure();
        Request request = mockOtherRequest("testNs", "testG", "testS");
        Resource actual = resourceParser.parse(request, secured);
        assertEquals("testNs", actual.getNamespaceId());
        assertEquals("testG", actual.getGroup());
        assertEquals("testS", actual.getName());
        assertEquals(Constants.Naming.NAMING_MODULE, actual.getType());
        assertEquals(NotifySubscriberRequest.class.getSimpleName(), actual.getProperties()
                .getProperty(com.alibaba.nacos.plugin.auth.constant.Constants.Resource.REQUEST_CLASS));
    }
    
    @Test
    @Secured()
    void testParseWithoutNamespaceForNamingRequest() throws NoSuchMethodException {
        Secured secured = getMethodSecure();
        AbstractNamingRequest request = mockNamingRequest(null, "testG", "testS");
        Resource actual = resourceParser.parse(request, secured);
        assertNull(actual.getNamespaceId());
        assertEquals("testG", actual.getGroup());
        assertEquals("testS", actual.getName());
        assertEquals(Constants.Naming.NAMING_MODULE, actual.getType());
        assertEquals(MockNamingRequest.class.getSimpleName(), actual.getProperties()
                .getProperty(com.alibaba.nacos.plugin.auth.constant.Constants.Resource.REQUEST_CLASS));
    }
    
    @Test
    @Secured()
    void testParseWithoutNamespaceForOtherRequest() throws NoSuchMethodException {
        Secured secured = getMethodSecure();
        Request request = mockOtherRequest(null, "testG", "testS");
        Resource actual = resourceParser.parse(request, secured);
        assertNull(actual.getNamespaceId());
        assertEquals("testG", actual.getGroup());
        assertEquals("testS", actual.getName());
        assertEquals(Constants.Naming.NAMING_MODULE, actual.getType());
        assertEquals(NotifySubscriberRequest.class.getSimpleName(), actual.getProperties()
                .getProperty(com.alibaba.nacos.plugin.auth.constant.Constants.Resource.REQUEST_CLASS));
    }
    
    @Test
    @Secured()
    void testParseWithoutGroupForNamingRequest() throws NoSuchMethodException {
        Secured secured = getMethodSecure();
        AbstractNamingRequest request = mockNamingRequest("testNs", null, "testS");
        Resource actual = resourceParser.parse(request, secured);
        assertEquals("testNs", actual.getNamespaceId());
        assertEquals(StringUtils.EMPTY, actual.getGroup());
        assertEquals("testS", actual.getName());
        assertEquals(Constants.Naming.NAMING_MODULE, actual.getType());
        assertEquals(MockNamingRequest.class.getSimpleName(), actual.getProperties()
                .getProperty(com.alibaba.nacos.plugin.auth.constant.Constants.Resource.REQUEST_CLASS));
    }
    
    @Test
    @Secured()
    void testParseWithoutGroupForOtherRequest() throws NoSuchMethodException {
        Secured secured = getMethodSecure();
        Request request = mockOtherRequest("testNs", null, "testS");
        Resource actual = resourceParser.parse(request, secured);
        assertEquals("testNs", actual.getNamespaceId());
        assertEquals(StringUtils.EMPTY, actual.getGroup());
        assertEquals("testS", actual.getName());
        assertEquals(Constants.Naming.NAMING_MODULE, actual.getType());
        assertEquals(NotifySubscriberRequest.class.getSimpleName(), actual.getProperties()
                .getProperty(com.alibaba.nacos.plugin.auth.constant.Constants.Resource.REQUEST_CLASS));
    }
    
    @Test
    @Secured()
    void testParseWithoutDataIdForNamingRequest() throws NoSuchMethodException {
        Secured secured = getMethodSecure();
        AbstractNamingRequest request = mockNamingRequest("testNs", "testG", null);
        Resource actual = resourceParser.parse(request, secured);
        assertEquals("testNs", actual.getNamespaceId());
        assertEquals("testG", actual.getGroup());
        assertEquals(StringUtils.EMPTY, actual.getName());
        assertEquals(Constants.Naming.NAMING_MODULE, actual.getType());
        assertEquals(MockNamingRequest.class.getSimpleName(), actual.getProperties()
                .getProperty(com.alibaba.nacos.plugin.auth.constant.Constants.Resource.REQUEST_CLASS));
    }
    
    @Test
    @Secured()
    void testParseWithoutDataIdForOtherRequest() throws NoSuchMethodException {
        Secured secured = getMethodSecure();
        Request request = mockOtherRequest("testNs", "testG", null);
        Resource actual = resourceParser.parse(request, secured);
        assertEquals("testNs", actual.getNamespaceId());
        assertEquals("testG", actual.getGroup());
        assertEquals(StringUtils.EMPTY, actual.getName());
        assertEquals(Constants.Naming.NAMING_MODULE, actual.getType());
        assertEquals(NotifySubscriberRequest.class.getSimpleName(), actual.getProperties()
                .getProperty(com.alibaba.nacos.plugin.auth.constant.Constants.Resource.REQUEST_CLASS));
    }
    
    private AbstractNamingRequest mockNamingRequest(String testNs, String testG, String testS) {
        MockNamingRequest result = new MockNamingRequest();
        result.setNamespace(testNs);
        result.setGroupName(testG);
        result.setServiceName(testS);
        return result;
    }
    
    private Request mockOtherRequest(String testNs, String testG, String testS) {
        NotifySubscriberRequest result = new NotifySubscriberRequest();
        result.setNamespace(testNs);
        result.setGroupName(testG);
        result.setServiceName(testS);
        return result;
    }
    
    private Secured getMethodSecure() throws NoSuchMethodException {
        StackTraceElement[] traces = new Exception().getStackTrace();
        StackTraceElement callerElement = traces[1];
        String methodName = callerElement.getMethodName();
        Method method = this.getClass().getDeclaredMethod(methodName);
        return method.getAnnotation(Secured.class);
    }
    
    private class MockNamingRequest extends AbstractNamingRequest {
    
    }
}
