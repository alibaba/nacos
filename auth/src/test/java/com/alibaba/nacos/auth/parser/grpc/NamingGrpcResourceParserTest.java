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
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.auth.api.Resource;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class NamingGrpcResourceParserTest {
    
    private NamingGrpcResourceParser resourceParser;
    
    @Before
    public void setUp() throws Exception {
        resourceParser = new NamingGrpcResourceParser();
    }
    
    @Test
    public void testParseWithFullContextForNamingRequest() {
        AbstractNamingRequest request = mockNamingRequest("testNs", "testG", "testS");
        Resource actual = resourceParser.parse(request, Constants.Naming.NAMING_MODULE);
        assertEquals("testNs", actual.getNamespaceId());
        assertEquals("testG", actual.getGroup());
        assertEquals("testS", actual.getName());
        assertEquals(Constants.Naming.NAMING_MODULE, actual.getType());
    }
    
    @Test
    public void testParseWithFullContextForOtherRequest() {
        Request request = mockOtherRequest("testNs", "testG", "testS");
        Resource actual = resourceParser.parse(request, Constants.Naming.NAMING_MODULE);
        assertEquals("testNs", actual.getNamespaceId());
        assertEquals("testG", actual.getGroup());
        assertEquals("testS", actual.getName());
        assertEquals(Constants.Naming.NAMING_MODULE, actual.getType());
    }
    
    @Test
    public void testParseWithoutNamespaceForNamingRequest() {
        AbstractNamingRequest request = mockNamingRequest(null, "testG", "testS");
        Resource actual = resourceParser.parse(request, Constants.Naming.NAMING_MODULE);
        assertNull(actual.getNamespaceId());
        assertEquals("testG", actual.getGroup());
        assertEquals("testS", actual.getName());
        assertEquals(Constants.Naming.NAMING_MODULE, actual.getType());
    }
    
    @Test
    public void testParseWithoutNamespaceForOtherRequest() {
        Request request = mockOtherRequest(null, "testG", "testS");
        Resource actual = resourceParser.parse(request, Constants.Naming.NAMING_MODULE);
        assertNull(actual.getNamespaceId());
        assertEquals("testG", actual.getGroup());
        assertEquals("testS", actual.getName());
        assertEquals(Constants.Naming.NAMING_MODULE, actual.getType());
    }
    
    @Test
    public void testParseWithoutGroupForNamingRequest() {
        AbstractNamingRequest request = mockNamingRequest("testNs", null, "testS");
        Resource actual = resourceParser.parse(request, Constants.Naming.NAMING_MODULE);
        assertEquals("testNs", actual.getNamespaceId());
        assertEquals(StringUtils.EMPTY, actual.getGroup());
        assertEquals("testS", actual.getName());
        assertEquals(Constants.Naming.NAMING_MODULE, actual.getType());
    }
    
    @Test
    public void testParseWithoutGroupForOtherRequest() {
        Request request = mockOtherRequest("testNs", null, "testS");
        Resource actual = resourceParser.parse(request, Constants.Naming.NAMING_MODULE);
        assertEquals("testNs", actual.getNamespaceId());
        assertEquals(StringUtils.EMPTY, actual.getGroup());
        assertEquals("testS", actual.getName());
        assertEquals(Constants.Naming.NAMING_MODULE, actual.getType());
    }
    
    @Test
    public void testParseWithoutDataIdForNamingRequest() {
        AbstractNamingRequest request = mockNamingRequest("testNs", "testG", null);
        Resource actual = resourceParser.parse(request, Constants.Naming.NAMING_MODULE);
        assertEquals("testNs", actual.getNamespaceId());
        assertEquals("testG", actual.getGroup());
        assertEquals(StringUtils.EMPTY, actual.getName());
        assertEquals(Constants.Naming.NAMING_MODULE, actual.getType());
    }
    
    @Test
    public void testParseWithoutDataIdForOtherRequest() {
        Request request = mockOtherRequest("testNs", "testG", null);
        Resource actual = resourceParser.parse(request, Constants.Naming.NAMING_MODULE);
        assertEquals("testNs", actual.getNamespaceId());
        assertEquals("testG", actual.getGroup());
        assertEquals(StringUtils.EMPTY, actual.getName());
        assertEquals(Constants.Naming.NAMING_MODULE, actual.getType());
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
    
    private class MockNamingRequest extends AbstractNamingRequest {
    
    }
}
