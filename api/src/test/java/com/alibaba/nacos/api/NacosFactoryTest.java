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

package com.alibaba.nacos.api;

import com.alibaba.nacos.api.config.ConfigFactory;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.lock.NacosLockFactory;
import com.alibaba.nacos.api.lock.LockService;
import com.alibaba.nacos.api.naming.NamingFactory;
import com.alibaba.nacos.api.naming.NamingMaintainFactory;
import com.alibaba.nacos.api.naming.NamingMaintainService;
import com.alibaba.nacos.api.naming.NamingService;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class NacosFactoryTest {
    
    @Test
    void testCreateConfigServiceWithProperties() throws NacosException {
        ConfigService configService = mock(ConfigService.class);
        
        try (MockedStatic<ConfigFactory> configFactoryMock = mockStatic(ConfigFactory.class)) {
            configFactoryMock.when(() -> ConfigFactory.createConfigService((Properties) null))
                    .thenReturn(configService);
            
            assertNotNull(NacosFactory.createConfigService((Properties) null));
        }
    }
    
    @Test
    void testCreateConfigServiceWithServerAddr() throws NacosException {
        ConfigService configService = mock(ConfigService.class);
        
        try (MockedStatic<ConfigFactory> configFactoryMock = mockStatic(ConfigFactory.class)) {
            configFactoryMock.when(() -> ConfigFactory.createConfigService("localhost:8848"))
                    .thenReturn(configService);
            
            assertNotNull(NacosFactory.createConfigService("localhost:8848"));
        }
    }
    
    @Test
    void testCreateNamingServiceWithServerAddr() throws NacosException {
        NamingService namingService = mock(NamingService.class);
        
        try (MockedStatic<NamingFactory> namingFactoryMock = mockStatic(NamingFactory.class)) {
            namingFactoryMock.when(() -> NamingFactory.createNamingService("localhost:8848"))
                    .thenReturn(namingService);
            
            assertNotNull(NacosFactory.createNamingService("localhost:8848"));
        }
    }
    
    @Test
    void testCreateNamingServiceWithProperties() throws NacosException {
        NamingService namingService = mock(NamingService.class);
        
        try (MockedStatic<NamingFactory> namingFactoryMock = mockStatic(NamingFactory.class)) {
            namingFactoryMock.when(() -> NamingFactory.createNamingService((Properties) null))
                    .thenReturn(namingService);
            
            assertNotNull(NacosFactory.createNamingService((Properties) null));
        }
    }
    
    @Test
    void testCreateMaintainServiceWithServerAddr() throws NacosException {
        NamingMaintainService namingMaintainService = mock(NamingMaintainService.class);
        
        try (MockedStatic<NamingMaintainFactory> namingMaintainFactoryMock = mockStatic(NamingMaintainFactory.class)) {
            namingMaintainFactoryMock.when(() -> NamingMaintainFactory.createMaintainService("localhost:8848"))
                    .thenReturn(namingMaintainService);
            
            assertNotNull(NacosFactory.createMaintainService("localhost:8848"));
        }
    }
    
    @Test
    void testCreateMaintainServiceWithProperties() throws NacosException {
        NamingMaintainService namingMaintainService = mock(NamingMaintainService.class);
        
        try (MockedStatic<NamingMaintainFactory> namingMaintainFactoryMock = mockStatic(NamingMaintainFactory.class)) {
            namingMaintainFactoryMock.when(() -> NamingMaintainFactory.createMaintainService((Properties) null))
                    .thenReturn(namingMaintainService);
            
            assertNotNull(NacosFactory.createMaintainService((Properties) null));
        }
    }
    
    @Test
    void testCreateLockService() throws NacosException {
        LockService lockService = mock(LockService.class);
        
        try (MockedStatic<NacosLockFactory> nacosLockFactoryMock = mockStatic(NacosLockFactory.class)) {
            nacosLockFactoryMock.when(() -> NacosLockFactory.createLockService((Properties) null))
                    .thenReturn(lockService);
            
            assertNotNull(NacosFactory.createLockService((Properties) null));
        }
    }
    
    @Test
    void testCreateConfigServiceWithPropertiesThrowException() {
        try (MockedStatic<ConfigFactory> configFactoryMock = mockStatic(ConfigFactory.class)) {
            configFactoryMock.when(() -> ConfigFactory.createConfigService((Properties) null))
                    .thenThrow(new NacosException());
            
            assertThrows(NacosException.class, () -> NacosFactory.createConfigService((Properties) null));
        }
    }
    
    @Test
    void testCreateConfigServiceWithServerAddrThrowException() {
        try (MockedStatic<ConfigFactory> configFactoryMock = mockStatic(ConfigFactory.class)) {
            configFactoryMock.when(() -> ConfigFactory.createConfigService("localhost:8848"))
                    .thenThrow(new NacosException());
            
            assertThrows(NacosException.class, () -> NacosFactory.createConfigService("localhost:8848"));
        }
    }
    
    @Test
    void testCreateNamingServiceWithServerAddrThrowException() {
        try (MockedStatic<NamingFactory> namingFactoryMock = mockStatic(NamingFactory.class)) {
            namingFactoryMock.when(() -> NamingFactory.createNamingService("localhost:8848"))
                    .thenThrow(new NacosException());
            
            assertThrows(NacosException.class, () -> NacosFactory.createNamingService("localhost:8848"));
        }
    }
    
    @Test
    void testCreateNamingServiceWithPropertiesThrowException() {
        try (MockedStatic<NamingFactory> namingFactoryMock = mockStatic(NamingFactory.class)) {
            namingFactoryMock.when(() -> NamingFactory.createNamingService((Properties) null))
                    .thenThrow(new NacosException());
            
            assertThrows(NacosException.class, () -> NacosFactory.createNamingService((Properties) null));
        }
    }
    
    @Test
    void testCreateMaintainServiceWithServerAddrThrowException() {
        try (MockedStatic<NamingMaintainFactory> namingMaintainFactoryMock = mockStatic(NamingMaintainFactory.class)) {
            namingMaintainFactoryMock.when(() -> NamingMaintainFactory.createMaintainService("localhost:8848"))
                    .thenThrow(new NacosException());
            
            assertThrows(NacosException.class, () -> NacosFactory.createMaintainService("localhost:8848"));
        }
    }
    
    @Test
    void testCreateMaintainServiceWithPropertiesThrowException() {
        try (MockedStatic<NamingMaintainFactory> namingMaintainFactoryMock = mockStatic(NamingMaintainFactory.class)) {
            namingMaintainFactoryMock.when(() -> NamingMaintainFactory.createMaintainService((Properties) null))
                    .thenThrow(new NacosException());
            
            assertThrows(NacosException.class, () -> NacosFactory.createMaintainService((Properties) null));
        }
    }
    
    @Test
    void testCreateLockServiceThrowException() {
        try (MockedStatic<NacosLockFactory> nacosLockFactoryMock = mockStatic(NacosLockFactory.class)) {
            nacosLockFactoryMock.when(() -> NacosLockFactory.createLockService((Properties) null))
                    .thenThrow(new NacosException());
            
            assertThrows(NacosException.class, () -> NacosFactory.createLockService((Properties) null));
        }
    }
}