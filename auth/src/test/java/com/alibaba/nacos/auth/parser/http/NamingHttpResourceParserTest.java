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

package com.alibaba.nacos.auth.parser.http;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.naming.CommonParams;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.auth.api.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;

@ExtendWith(MockitoExtension.class)
// todo remove this
@MockitoSettings(strictness = Strictness.LENIENT)
class NamingHttpResourceParserTest {
    
    @Mock
    private HttpServletRequest request;
    
    private NamingHttpResourceParser resourceParser;
    
    @BeforeEach
    void setUp() throws Exception {
        resourceParser = new NamingHttpResourceParser();
    }
    
    @Test
    @Secured()
    void testParseWithFullContext() throws NoSuchMethodException {
        Secured secured = getMethodSecure();
        Mockito.when(request.getParameter(eq(CommonParams.NAMESPACE_ID))).thenReturn("testNs");
        Mockito.when(request.getParameter(eq(CommonParams.GROUP_NAME))).thenReturn("testG");
        Mockito.when(request.getParameter(eq(CommonParams.SERVICE_NAME))).thenReturn("testS");
        Resource actual = resourceParser.parse(request, secured);
        assertEquals("testNs", actual.getNamespaceId());
        assertEquals("testG", actual.getGroup());
        assertEquals("testS", actual.getName());
        assertEquals(Constants.Naming.NAMING_MODULE, actual.getType());
    }
    
    @Test
    @Secured()
    void testParseWithoutNamespace() throws NoSuchMethodException {
        Secured secured = getMethodSecure();
        Mockito.when(request.getParameter(eq(CommonParams.GROUP_NAME))).thenReturn("testG");
        Mockito.when(request.getParameter(eq(CommonParams.SERVICE_NAME))).thenReturn("testS");
        Resource actual = resourceParser.parse(request, secured);
        assertEquals(StringUtils.EMPTY, actual.getNamespaceId());
        assertEquals("testG", actual.getGroup());
        assertEquals("testS", actual.getName());
        assertEquals(Constants.Naming.NAMING_MODULE, actual.getType());
    }
    
    @Test
    @Secured()
    void testParseWithoutGroup() throws NoSuchMethodException {
        Secured secured = getMethodSecure();
        Mockito.when(request.getParameter(eq(CommonParams.NAMESPACE_ID))).thenReturn("testNs");
        Mockito.when(request.getParameter(eq(CommonParams.SERVICE_NAME))).thenReturn("testS");
        Resource actual = resourceParser.parse(request, secured);
        assertEquals("testNs", actual.getNamespaceId());
        assertEquals(Constants.DEFAULT_GROUP, actual.getGroup());
        assertEquals("testS", actual.getName());
        assertEquals(Constants.Naming.NAMING_MODULE, actual.getType());
    }
    
    @Test
    @Secured()
    void testParseWithGroupInService() throws NoSuchMethodException {
        Secured secured = getMethodSecure();
        Mockito.when(request.getParameter(eq(CommonParams.NAMESPACE_ID))).thenReturn("testNs");
        Mockito.when(request.getParameter(eq(CommonParams.SERVICE_NAME))).thenReturn("testG@@testS");
        Resource actual = resourceParser.parse(request, secured);
        assertEquals("testNs", actual.getNamespaceId());
        assertEquals("testG", actual.getGroup());
        assertEquals("testS", actual.getName());
        assertEquals(Constants.Naming.NAMING_MODULE, actual.getType());
    }
    
    @Test
    @Secured()
    void testParseWithoutService() throws NoSuchMethodException {
        Secured secured = getMethodSecure();
        Mockito.when(request.getParameter(eq(CommonParams.NAMESPACE_ID))).thenReturn("testNs");
        Mockito.when(request.getParameter(eq(CommonParams.GROUP_NAME))).thenReturn("testG");
        Resource actual = resourceParser.parse(request, secured);
        assertEquals("testNs", actual.getNamespaceId());
        assertEquals("testG", actual.getGroup());
        assertEquals(StringUtils.EMPTY, actual.getName());
        assertEquals(Constants.Naming.NAMING_MODULE, actual.getType());
    }
    
    @Test
    @Secured()
    void testParseWithoutGroupAndService() throws NoSuchMethodException {
        Secured secured = getMethodSecure();
        Mockito.when(request.getParameter(eq(CommonParams.NAMESPACE_ID))).thenReturn("testNs");
        Resource actual = resourceParser.parse(request, secured);
        assertEquals("testNs", actual.getNamespaceId());
        assertEquals(StringUtils.EMPTY, actual.getGroup());
        assertEquals(StringUtils.EMPTY, actual.getName());
        assertEquals(Constants.Naming.NAMING_MODULE, actual.getType());
    }
    
    @Test
    @Secured(tags = {"testTag"})
    void testParseWithTags() throws NoSuchMethodException {
        Secured secured = getMethodSecure();
        Mockito.when(request.getParameter(eq(CommonParams.NAMESPACE_ID))).thenReturn("testNs");
        Mockito.when(request.getParameter(eq(CommonParams.GROUP_NAME))).thenReturn("testG");
        Mockito.when(request.getParameter(eq(CommonParams.SERVICE_NAME))).thenReturn("testS");
        Resource actual = resourceParser.parse(request, secured);
        assertEquals("testNs", actual.getNamespaceId());
        assertEquals("testG", actual.getGroup());
        assertEquals("testS", actual.getName());
        assertEquals(Constants.Naming.NAMING_MODULE, actual.getType());
        assertTrue(actual.getProperties().containsKey("testTag"));
    }
    
    private Secured getMethodSecure() throws NoSuchMethodException {
        StackTraceElement[] traces = new Exception().getStackTrace();
        StackTraceElement callerElement = traces[1];
        String methodName = callerElement.getMethodName();
        Method method = this.getClass().getDeclaredMethod(methodName);
        return method.getAnnotation(Secured.class);
    }
}
