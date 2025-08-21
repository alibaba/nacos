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

package com.alibaba.nacos.client.redo.service;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.client.env.NacosClientProperties;
import com.alibaba.nacos.client.redo.data.RedoData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class AbstractRedoServiceTest {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractRedoServiceTest.class);
    
    @Mock
    private AbstractRedoTask redoTask;
    
    MockRedoService redoService;
    
    @BeforeEach
    void setUp() {
        Properties properties = new Properties();
        properties.setProperty(PropertyKeyConst.REDO_DELAY_TIME, "300");
        NacosClientProperties clientProperties = NacosClientProperties.PROTOTYPE.derive(properties);
        redoService = new MockRedoService(LOGGER, clientProperties);
        redoService.startRedoTask();
    }
    
    @AfterEach
    void tearDown() {
        redoService.shutdown();
    }
    
    @Test
    void testRemoveForNonExistedData() {
        assertDoesNotThrow(() -> redoService.removeRedoData("test", String.class));
    }
    
    @Test
    void testMarkRegisteredForNonExistedData() {
        assertDoesNotThrow(() -> redoService.dataRegistered("test", String.class));
    }
    
    @Test
    void testMarkUnregisterForNonExistedData() {
        assertDoesNotThrow(() -> redoService.dataDeregister("test", String.class));
    }
    
    @Test
    void testMarkUnregisteredForNonExistedData() {
        assertDoesNotThrow(() -> redoService.dataDeregistered("test", String.class));
    }
    
    @Test
    void testIsDataRegisteredForNonExistedData() {
        assertFalse(redoService.isDataRegistered("test", String.class));
    }
    
    @Test
    void testGetNonExistedData() {
        assertNull(redoService.getRedoData("test", String.class));
    }
    
    @Test
    void testFindNonExistedData() {
        assertTrue(redoService.findRedoData(String.class).isEmpty());
    }
    
    @Test
    void testCacheRedoDataAndMarkRegistered() {
        MockRedoData redoData = new MockRedoData();
        redoData.set("test");
        redoService.cachedRedoData("test", redoData, String.class);
        RedoData<String> redoData1 = redoService.getRedoData("test", String.class);
        assertEquals(redoData, redoData1);
        assertFalse(redoData1.isRegistered());
        assertTrue(redoData1.isNeedRedo());
        assertFalse(redoData1.isUnregistering());
        assertFalse(redoService.isDataRegistered("test", String.class));
        redoService.dataRegistered("test", String.class);
        assertTrue(redoData1.isRegistered());
        assertFalse(redoData1.isNeedRedo());
        assertFalse(redoData1.isUnregistering());
        assertTrue(redoService.isDataRegistered("test", String.class));
        redoService.dataDeregister("test", String.class);
        assertTrue(redoData1.isRegistered());
        assertTrue(redoData1.isUnregistering());
        assertTrue(redoService.isDataRegistered("test", String.class));
        redoService.dataDeregistered("test", String.class);
        assertFalse(redoData1.isRegistered());
        assertTrue(redoData1.isUnregistering());
        assertFalse(redoService.isDataRegistered("test", String.class));
        redoService.removeRedoData("test", String.class);
        assertNull(redoService.getRedoData("test", String.class));
    }
    
    @Test
    void testRemoveExpectedRegisteredData() {
        MockRedoData redoData = new MockRedoData();
        redoData.set("test");
        redoService.cachedRedoData("test", redoData, String.class);
        redoData.setExpectedRegistered(true);
        redoService.removeRedoData("test", String.class);
        assertNotNull(redoService.getRedoData("test", String.class));
    }
    
    @Test
    void testOnConnectedAndOnDisconnected() {
        assertFalse(redoService.isConnected());
        redoService.onConnected(null);
        assertTrue(redoService.isConnected());
        MockRedoData redoData = new MockRedoData();
        redoData.set("test");
        redoData.setRegistered(true);
        redoService.cachedRedoData("test", redoData, String.class);
        redoService.onDisConnect(null);
        assertFalse(redoService.isConnected());
        assertFalse(redoData.isRegistered());
    }
    
    @Test
    void testFindAllNeedRedoData() {
        MockRedoData noNeedRedoData = new MockRedoData();
        noNeedRedoData.setRegistered(true);
        noNeedRedoData.setExpectedRegistered(true);
        MockRedoData needRedoData = new MockRedoData();
        needRedoData.setRegistered(false);
        needRedoData.setExpectedRegistered(true);
        redoService.cachedRedoData("noNeedRedoData", noNeedRedoData, String.class);
        redoService.cachedRedoData("needRedoData", needRedoData, String.class);
        Set<RedoData<String>> redoDataSet = redoService.findRedoData(String.class);
        assertEquals(1, redoDataSet.size());
        assertEquals(needRedoData, redoDataSet.iterator().next());
    }
    
    private class MockRedoService extends AbstractRedoService {
        
        protected MockRedoService(Logger logger, NacosClientProperties properties) {
            super(logger, properties, "test");
        }
        
        @Override
        protected AbstractRedoTask buildRedoTask() {
            return redoTask;
        }
    }
    
    private static class MockRedoData extends RedoData<String> {
    
    }
}