/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.auth.util;

import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.auth.config.NacosAuthConfig;
import com.alibaba.nacos.common.http.param.Header;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthHeaderUtilTest {
    
    private static final String TEST_KEY = "testKey";
    private static final String TEST_VALUE = "testValue";
    
    @Mock
    private NacosAuthConfig authConfig;
    
    @Mock
    private Request request;
    
    @Test
    void testAddIdentityToHttpHeaderWhenNotSupport() {
        when(authConfig.isSupportServerIdentity()).thenReturn(false);
        
        Header header = Header.newInstance();
        AuthHeaderUtil.addIdentityToHeader(header, authConfig);
        
        assertNull(header.getValue(TEST_KEY));
    }
    
    @Test
    void testAddIdentityToHttpHeaderWithBlankKey() {
        when(authConfig.isSupportServerIdentity()).thenReturn(true);
        when(authConfig.getServerIdentityKey()).thenReturn("");
        
        Header header = Header.newInstance();
        AuthHeaderUtil.addIdentityToHeader(header, authConfig);
        
        assertNull(header.getValue(TEST_KEY));
    }
    
    @Test
    void testAddIdentityToHttpHeaderSuccess() {
        when(authConfig.isSupportServerIdentity()).thenReturn(true);
        when(authConfig.getServerIdentityKey()).thenReturn(TEST_KEY);
        when(authConfig.getServerIdentityValue()).thenReturn(TEST_VALUE);
        
        Header header = Header.newInstance();
        AuthHeaderUtil.addIdentityToHeader(header, authConfig);
        
        assertEquals(TEST_VALUE, header.getValue(TEST_KEY));
    }
    
    @Test
    void testAddIdentityToGrpcRequestWhenNotSupport() {
        when(authConfig.isSupportServerIdentity()).thenReturn(false);
        
        AuthHeaderUtil.addIdentityToHeader(request, authConfig);
        
        verify(request, never()).putHeader(anyString(), anyString());
    }
    
    @Test
    void testAddIdentityToGrpcRequestWithBlankKey() {
        when(authConfig.isSupportServerIdentity()).thenReturn(true);
        when(authConfig.getServerIdentityKey()).thenReturn("   ");
        
        AuthHeaderUtil.addIdentityToHeader(request, authConfig);
        
        verify(request, never()).putHeader(anyString(), anyString());
    }
    
    @Test
    void testAddIdentityToGrpcRequestSuccess() {
        when(authConfig.isSupportServerIdentity()).thenReturn(true);
        when(authConfig.getServerIdentityKey()).thenReturn(TEST_KEY);
        when(authConfig.getServerIdentityValue()).thenReturn(TEST_VALUE);
        
        AuthHeaderUtil.addIdentityToHeader(request, authConfig);
        
        verify(request).putHeader(TEST_KEY, TEST_VALUE);
    }
    
    @Test
    void testConstructor() {
        new AuthHeaderUtil();
    }
}
