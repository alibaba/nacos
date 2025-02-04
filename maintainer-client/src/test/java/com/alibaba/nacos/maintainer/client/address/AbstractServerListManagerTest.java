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

package com.alibaba.nacos.maintainer.client.address;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.http.client.NacosRestTemplate;
import com.alibaba.nacos.maintainer.client.env.NacosClientProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AbstractServerListManagerTest {
    
    @Mock
    NacosRestTemplate restTemplate;
    
    NacosClientProperties properties;
    
    AbstractServerListManager serverListManager;
    
    @BeforeEach
    void setUp() {
        properties = NacosClientProperties.PROTOTYPE.derive();
    }
    
    @AfterEach
    void tearDown() throws NacosException {
        if (null != serverListManager) {
            serverListManager.shutdown();
        }
    }
    
    @Test
    void testStartWithoutProvider() {
        serverListManager = new MockServerListManager(properties);
        assertThrows(NacosException.class, () -> serverListManager.start());
    }
    
    @Test
    void testGetServerList() throws NacosException {
        properties.setProperty("MockTest", "true");
        serverListManager = new MockServerListManager(properties);
        serverListManager.start();
        // Mock provider will call this method in init.
        verify(restTemplate).getInterceptors();
        assertEquals(1, serverListManager.getServerList().size());
        assertEquals("mock-server-list", serverListManager.getServerList().get(0));
    }
    
    @Test
    void testGetContextPathDefault() throws NacosException {
        properties.setProperty("MockTest", "true");
        serverListManager = new MockServerListManager(properties);
        serverListManager.start();
        assertEquals("nacos", serverListManager.getContextPath());
    }
    
    @Test
    void testGetContextPath() throws NacosException {
        properties.setProperty("MockTest", "true");
        properties.setProperty("ReturnMock", "true");
        serverListManager = new MockServerListManager(properties);
        serverListManager.start();
        assertEquals("MockContextPath", serverListManager.getContextPath());
    }
    
    @Test
    void testGetAddressSourceDefault() throws NacosException {
        properties.setProperty("MockTest", "true");
        serverListManager = new MockServerListManager(properties);
        serverListManager.start();
        assertEquals("", serverListManager.getAddressSource());
    }
    
    @Test
    void testGetAddressSource() throws NacosException {
        properties.setProperty("MockTest", "true");
        properties.setProperty("ReturnMock", "true");
        serverListManager = new MockServerListManager(properties);
        serverListManager.start();
        assertEquals("MockAddressSource", serverListManager.getAddressSource());
    }
    
    @Test
    void testIsFixedDefault() throws NacosException {
        properties.setProperty("MockTest", "true");
        serverListManager = new MockServerListManager(properties);
        serverListManager.start();
        assertFalse(serverListManager.isFixed());
    }
    
    @Test
    void testIsFixed() throws NacosException {
        properties.setProperty("MockTest", "true");
        properties.setProperty("ReturnMock", "true");
        serverListManager = new MockServerListManager(properties);
        serverListManager.start();
        assertTrue(serverListManager.isFixed());
    }
    
    private class MockServerListManager extends AbstractServerListManager {
        
        public MockServerListManager(NacosClientProperties properties) {
            super(properties);
        }
        
        public MockServerListManager(NacosClientProperties properties, String namespace) {
            super(properties);
        }
        
        @Override
        protected NacosRestTemplate getNacosRestTemplate() {
            return restTemplate;
        }
        
        @Override
        public String genNextServer() {
            return "";
        }
        
        @Override
        public String getCurrentServer() {
            return "";
        }
    }
}