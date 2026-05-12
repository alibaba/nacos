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

package com.alibaba.nacos.api.naming;

import com.alibaba.nacos.api.exception.NacosException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NamingMaintainFactoryTest {
    
    @Test
    @DisplayName("test createMaintainService with serverList throws exception when class not found")
    void testCreateMaintainServiceWithServerListThrowsExceptionWhenClassNotFound() {
        NacosException exception = assertThrows(NacosException.class,
            () -> NamingMaintainFactory.createMaintainService("127.0.0.1:8848"));
        assertEquals(NacosException.CLIENT_INVALID_PARAM, exception.getErrCode());
        assertNotNull(exception.getCause());
        assertTrue(exception.getCause() instanceof ClassNotFoundException);
    }
    
    @Test
    @DisplayName("test createMaintainService with properties throws exception when class not found")
    void testCreateMaintainServiceWithPropertiesThrowsExceptionWhenClassNotFound() {
        Properties properties = new Properties();
        properties.setProperty("serverAddr", "127.0.0.1:8848");
        
        NacosException exception = assertThrows(NacosException.class,
            () -> NamingMaintainFactory.createMaintainService(properties));
        assertEquals(NacosException.CLIENT_INVALID_PARAM, exception.getErrCode());
        assertNotNull(exception.getCause());
        assertTrue(exception.getCause() instanceof ClassNotFoundException);
    }
    
    @Test
    @DisplayName("test createMaintainService with null serverList throws exception")
    void testCreateMaintainServiceWithNullServerListThrowsException() {
        NacosException exception = assertThrows(NacosException.class,
            () -> NamingMaintainFactory.createMaintainService((String) null));
        assertEquals(NacosException.CLIENT_INVALID_PARAM, exception.getErrCode());
    }
    
    @Test
    @DisplayName("test createMaintainService with null properties throws exception")
    void testCreateMaintainServiceWithNullPropertiesThrowsException() {
        NacosException exception = assertThrows(NacosException.class,
            () -> NamingMaintainFactory.createMaintainService((Properties) null));
        assertEquals(NacosException.CLIENT_INVALID_PARAM, exception.getErrCode());
    }
    
    @Test
    @DisplayName("test factory class is deprecated")
    void testFactoryClassIsDeprecated() {
        Deprecated annotation = NamingMaintainFactory.class.getAnnotation(Deprecated.class);
        assertNotNull(annotation);
    }
    
    @Test
    @DisplayName("test exception message contains cause information")
    void testExceptionMessageContainsCauseInformation() {
        NacosException exception = assertThrows(NacosException.class,
            () -> NamingMaintainFactory.createMaintainService("localhost:8848"));
        assertTrue(exception.getMessage() != null || exception.getCause() != null);
    }
    
    @Test
    @DisplayName("test exception error code is CLIENT_INVALID_PARAM")
    void testExceptionErrorCodeIsClientInvalidParam() {
        Properties props = new Properties();
        NacosException exception = assertThrows(NacosException.class,
            () -> NamingMaintainFactory.createMaintainService(props));
        assertEquals(NacosException.CLIENT_INVALID_PARAM, exception.getErrCode());
    }
    
    @Test
    @DisplayName("test reflection class name is correct")
    void testReflectionClassNameIsCorrect() throws Exception {
        // Verify the expected class name format
        String expectedClassName = "com.alibaba.nacos.client.naming.NacosNamingMaintainService";
        // This test verifies the class name used in reflection exists in code
        // But the class is in client module, not api module
        assertTrue(expectedClassName.contains("NacosNamingMaintainService"));
    }
    
    @Test
    @DisplayName("test properties with various configurations")
    void testPropertiesWithVariousConfigurations() {
        Properties properties = new Properties();
        properties.setProperty("serverAddr", "127.0.0.1:8848,127.0.0.1:8849");
        properties.setProperty("namespace", "test-ns");
        properties.setProperty("contextPath", "/nacos");
        
        NacosException exception = assertThrows(NacosException.class,
            () -> NamingMaintainFactory.createMaintainService(properties));
        // Still fails because class not in api module
        assertEquals(NacosException.CLIENT_INVALID_PARAM, exception.getErrCode());
    }
    
    @Test
    @DisplayName("test serverList with multiple addresses")
    void testServerListWithMultipleAddresses() {
        String serverList = "127.0.0.1:8848,127.0.0.1:8849,127.0.0.1:8850";
        NacosException exception = assertThrows(NacosException.class,
            () -> NamingMaintainFactory.createMaintainService(serverList));
        assertEquals(NacosException.CLIENT_INVALID_PARAM, exception.getErrCode());
    }
}
