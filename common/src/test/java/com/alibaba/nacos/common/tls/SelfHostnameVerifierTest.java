/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.common.tls;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SelfHostnameVerifierTest {
    
    @Mock
    HostnameVerifier hostnameVerifier;
    
    @Mock
    SSLSession sslSession;
    
    SelfHostnameVerifier selfHostnameVerifier;
    
    @BeforeEach
    void setUp() {
        selfHostnameVerifier = new SelfHostnameVerifier(hostnameVerifier);
        doReturn(false).when(hostnameVerifier).verify(anyString(), eq(sslSession));
    }
    
    @Test
    void testVerify() {
        assertTrue(selfHostnameVerifier.verify("localhost", sslSession));
        assertTrue(selfHostnameVerifier.verify("127.0.0.1", sslSession));
        assertTrue(selfHostnameVerifier.verify("10.10.10.10", sslSession));
        // hit cache
        assertTrue(selfHostnameVerifier.verify("10.10.10.10", sslSession));
        
        assertFalse(selfHostnameVerifier.verify("", sslSession));
        assertFalse(selfHostnameVerifier.verify(null, sslSession));
        verify(hostnameVerifier, times(2)).verify(any(), eq(sslSession));
    }
}