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
import com.alibaba.nacos.api.config.remote.request.ConfigBatchListenRequest;
import com.alibaba.nacos.api.config.remote.request.ConfigChangeNotifyRequest;
import com.alibaba.nacos.api.config.remote.request.ConfigPublishRequest;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.auth.api.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConfigGrpcResourceParserTest {
    
    private ConfigGrpcResourceParser resourceParser;
    
    @BeforeEach
    void setUp() throws Exception {
        resourceParser = new ConfigGrpcResourceParser();
    }
    
    @Test
    @Secured(signType = Constants.Config.CONFIG_MODULE)
    void testParseWithFullContext() throws NoSuchMethodException {
        Secured secured = getMethodSecure();
        Request request = mockConfigRequest("testNs", "testG", "testD");
        Resource actual = resourceParser.parse(request, secured);
        assertEquals("testNs", actual.getNamespaceId());
        assertEquals("testG", actual.getGroup());
        assertEquals("testD", actual.getName());
        assertEquals(Constants.Config.CONFIG_MODULE, actual.getType());
    }
    
    @Test
    @Secured(signType = Constants.Config.CONFIG_MODULE)
    void testParseWithoutNamespace() throws NoSuchMethodException {
        Secured secured = getMethodSecure();
        Request request = mockConfigRequest("", "testG", "testD");
        Resource actual = resourceParser.parse(request, secured);
        assertEquals("", actual.getNamespaceId());
        assertEquals("testG", actual.getGroup());
        assertEquals("testD", actual.getName());
        assertEquals(Constants.Config.CONFIG_MODULE, actual.getType());
    }
    
    @Test
    @Secured(signType = Constants.Config.CONFIG_MODULE)
    void testParseWithoutGroup() throws NoSuchMethodException {
        Secured secured = getMethodSecure();
        Request request = mockConfigRequest("testNs", "", "testD");
        Resource actual = resourceParser.parse(request, secured);
        assertEquals("testNs", actual.getNamespaceId());
        assertEquals(StringUtils.EMPTY, actual.getGroup());
        assertEquals("testD", actual.getName());
        assertEquals(Constants.Config.CONFIG_MODULE, actual.getType());
    }
    
    @Test
    @Secured(signType = Constants.Config.CONFIG_MODULE)
    void testParseWithoutDataId() throws NoSuchMethodException {
        Secured secured = getMethodSecure();
        Request request = mockConfigRequest("testNs", "testG", "");
        Resource actual = resourceParser.parse(request, secured);
        assertEquals("testNs", actual.getNamespaceId());
        assertEquals("testG", actual.getGroup());
        assertEquals(StringUtils.EMPTY, actual.getName());
        assertEquals(Constants.Config.CONFIG_MODULE, actual.getType());
    }
    
    @Test
    @Secured(signType = Constants.Config.CONFIG_MODULE)
    void testParseWithConfigBatchListenRequest() throws NoSuchMethodException {
        Secured secured = getMethodSecure();
        ConfigBatchListenRequest request = new ConfigBatchListenRequest();
        request.addConfigListenContext("testG", "testD", "testNs", "111");
        Resource actual = resourceParser.parse(request, secured);
        assertEquals("testNs", actual.getNamespaceId());
        assertEquals(StringUtils.EMPTY, actual.getGroup());
        assertEquals(StringUtils.EMPTY, actual.getName());
        assertEquals(Constants.Config.CONFIG_MODULE, actual.getType());
        request.getConfigListenContexts().clear();
        actual = resourceParser.parse(request, secured);
        assertEquals(StringUtils.EMPTY, actual.getNamespaceId());
        assertEquals(StringUtils.EMPTY, actual.getGroup());
        assertEquals(StringUtils.EMPTY, actual.getName());
        assertEquals(Constants.Config.CONFIG_MODULE, actual.getType());
    }
    
    @Test
    @Secured(signType = Constants.Config.CONFIG_MODULE)
    void testParseWithReflectionRequest() throws NoSuchMethodException {
        Secured secured = getMethodSecure();
        Request request = ConfigChangeNotifyRequest.build("rTestD", "rTestG", "rTestNs");
        Resource actual = resourceParser.parse(request, secured);
        assertEquals("rTestNs", actual.getNamespaceId());
        assertEquals("rTestG", actual.getGroup());
        assertEquals("rTestD", actual.getName());
    }
    
    private Request mockConfigRequest(String tenant, String group, String dataId) {
        ConfigPublishRequest request = new ConfigPublishRequest();
        request.setTenant(tenant);
        request.setGroup(group);
        request.setDataId(dataId);
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
