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

package com.alibaba.nacos.core.remote;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NacosRuntimeConnectionEjectorTest {

    @Mock
    private ConnectionManager connectionManager;

    private NacosRuntimeConnectionEjector ejector;

    @BeforeEach
    void setUp() {
        ejector = new NacosRuntimeConnectionEjector();
        ejector.setConnectionManager(connectionManager);
        ReflectionTestUtils.setField(connectionManager, "connections", new HashMap<String, Connection>());
    }

    @Test
    void testGetName() {
        assertEquals("nacos", ejector.getName());
    }

    @Test
    void testDoEjectWithEmptyConnections() {
        when(connectionManager.currentSdkClientCount()).thenReturn(0);
        ejector.doEject();
    }

    @Test
    void testDoEjectOverLimitPath() {
        when(connectionManager.currentSdkClientCount()).thenReturn(0);
        when(connectionManager.getCurrentConnectionCount()).thenReturn(5);
        ejector.setLoadClient(2);
        ejector.setRedirectAddress("127.0.0.1:8848");
        ejector.doEject();
    }
}
