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

package com.alibaba.nacos.naming.utils;

import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.core.context.RequestContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.servlet.http.HttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NamingRequestUtilTest {
    
    @Mock
    HttpServletRequest request;
    
    @Mock
    RequestMeta meta;
    
    @BeforeEach
    void setUp() {
        RequestContextHolder.getContext().getBasicContext().getAddressContext().setRemoteIp("1.1.1.1");
        RequestContextHolder.getContext().getBasicContext().getAddressContext().setSourceIp("2.2.2.2");
    }
    
    @AfterEach
    void tearDown() {
        RequestContextHolder.removeContext();
    }
    
    @Test
    void testGetSourceIp() {
        assertEquals("2.2.2.2", NamingRequestUtil.getSourceIp());
        RequestContextHolder.getContext().getBasicContext().getAddressContext().setSourceIp(null);
        assertEquals("1.1.1.1", NamingRequestUtil.getSourceIp());
        RequestContextHolder.getContext().getBasicContext().getAddressContext().setRemoteIp(null);
        assertNull(NamingRequestUtil.getSourceIp());
    }
    
    @Test
    void getSourceIpForHttpRequest() {
        when(request.getRemoteAddr()).thenReturn("3.3.3.3");
        assertEquals("2.2.2.2", NamingRequestUtil.getSourceIpForHttpRequest(request));
        RequestContextHolder.getContext().getBasicContext().getAddressContext().setSourceIp(null);
        assertEquals("1.1.1.1", NamingRequestUtil.getSourceIpForHttpRequest(request));
        RequestContextHolder.getContext().getBasicContext().getAddressContext().setRemoteIp(null);
        assertEquals("3.3.3.3", NamingRequestUtil.getSourceIpForHttpRequest(request));
    }
    
    @Test
    void getSourceIpForGrpcRequest() {
        when(meta.getClientIp()).thenReturn("3.3.3.3");
        assertEquals("2.2.2.2", NamingRequestUtil.getSourceIpForGrpcRequest(meta));
        RequestContextHolder.getContext().getBasicContext().getAddressContext().setSourceIp(null);
        assertEquals("1.1.1.1", NamingRequestUtil.getSourceIpForGrpcRequest(meta));
        RequestContextHolder.getContext().getBasicContext().getAddressContext().setRemoteIp(null);
        assertEquals("3.3.3.3", NamingRequestUtil.getSourceIpForGrpcRequest(meta));
    }
}