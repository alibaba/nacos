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

package com.alibaba.nacos.core.context.addition;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.naming.remote.request.InstanceRequest;
import com.alibaba.nacos.common.utils.VersionUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class BasicContextTest {
    
    BasicContext basicContext;
    
    @BeforeEach
    void setUp() {
        basicContext = new BasicContext();
    }
    
    @Test
    void testGetAddressContext() {
        assertNotNull(basicContext.getAddressContext());
    }
    
    @Test
    void testSetUserAgent() {
        assertNull(basicContext.getUserAgent());
        basicContext.setUserAgent(VersionUtils.getFullClientVersion());
        assertEquals(VersionUtils.getFullClientVersion(), basicContext.getUserAgent());
    }
    
    @Test
    void testSetRequestProtocol() {
        assertNull(basicContext.getRequestProtocol());
        basicContext.setRequestProtocol(BasicContext.HTTP_PROTOCOL);
        assertEquals(BasicContext.HTTP_PROTOCOL, basicContext.getRequestProtocol());
        basicContext.setRequestProtocol(BasicContext.GRPC_PROTOCOL);
        assertEquals(BasicContext.GRPC_PROTOCOL, basicContext.getRequestProtocol());
    }
    
    @Test
    void testSetRequestTarget() {
        assertNull(basicContext.getRequestTarget());
        basicContext.setRequestTarget("POST /v2/ns/instance");
        assertEquals("POST /v2/ns/instance", basicContext.getRequestTarget());
        basicContext.setRequestTarget(InstanceRequest.class.getSimpleName());
        assertEquals(InstanceRequest.class.getSimpleName(), basicContext.getRequestTarget());
    }
    
    @Test
    void testSetApp() {
        assertEquals("unknown", basicContext.getApp());
        basicContext.setApp("testApp");
        assertEquals("testApp", basicContext.getApp());
    }
    
    @Test
    void testSetEncoding() {
        assertEquals(Constants.ENCODE, basicContext.getEncoding());
        basicContext.setEncoding("GBK");
        assertEquals("GBK", basicContext.getEncoding());
    }
}