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

package com.alibaba.nacos.config.server.utils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Method;
import java.net.NetworkInterface;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mockStatic;

class SystemConfigTest {
    
    @AfterEach
    void tearDown() {
        System.clearProperty("nacos.server.ip");
    }
    
    @Test
    void testGetHostAddress() {
        assertNotNull(SystemConfig.LOCAL_IP);
    }
    
    @Test
    void testGetHostAddressFromSystemProperty() throws Exception {
        System.setProperty("nacos.server.ip", "1.1.1.1");
        
        assertEquals("1.1.1.1", invokeGetHostAddress());
    }
    
    @Test
    void testGetHostAddressFromLocalNetwork() throws Exception {
        System.clearProperty("nacos.server.ip");
        
        assertNotNull(invokeGetHostAddress());
    }
    
    @Test
    void testGetHostAddressWhenNetworkInterfaceThrowsException() {
        System.clearProperty("nacos.server.ip");
        try (MockedStatic<NetworkInterface> networkInterface = mockStatic(NetworkInterface.class)) {
            networkInterface.when(NetworkInterface::getNetworkInterfaces)
                .thenThrow(new RuntimeException("test"));
            
            assertDoesNotThrow(() -> assertNotNull(invokeGetHostAddress()));
        }
    }
    
    private String invokeGetHostAddress() throws Exception {
        Method method = SystemConfig.class.getDeclaredMethod("getHostAddress");
        method.setAccessible(true);
        return (String) method.invoke(null);
    }
    
}
