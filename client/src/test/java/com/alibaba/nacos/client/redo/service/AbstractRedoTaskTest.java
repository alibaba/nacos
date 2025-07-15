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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AbstractRedoTaskTest {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractRedoTaskTest.class);
    
    @Mock
    private AbstractRedoService redoService;
    
    private AtomicBoolean invokedMark;
    
    private AtomicBoolean isThrowException;
    
    AbstractRedoTask<AbstractRedoService> redoTask;
    
    @BeforeEach
    void setUp() {
        invokedMark = new AtomicBoolean(false);
        isThrowException = new AtomicBoolean(false);
        redoTask = new AbstractRedoTask<AbstractRedoService>(LOGGER, redoService) {
            
            @Override
            protected void redoData() {
                invokedMark.set(true);
                if (isThrowException.get()) {
                    throw new RuntimeException("test");
                }
            }
        };
    }
    
    @Test
    void testGetRedoService() {
        assertEquals(redoService, redoTask.getRedoService());
    }
    
    @Test
    void runWithDisconnection() {
        redoTask.run();
        assertFalse(invokedMark.get());
    }
    
    @Test
    void runWithConnection() {
        when(redoService.isConnected()).thenReturn(true);
        redoTask.run();
        assertTrue(invokedMark.get());
    }
    
    @Test
    void runWithConnectionAndException() {
        isThrowException.set(true);
        when(redoService.isConnected()).thenReturn(true);
        redoTask.run();
        assertTrue(invokedMark.get());
    }
}