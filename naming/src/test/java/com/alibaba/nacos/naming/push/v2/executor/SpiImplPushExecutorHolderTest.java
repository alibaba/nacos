/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.naming.push.v2.executor;

import com.alibaba.nacos.naming.pojo.Subscriber;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class SpiImplPushExecutorHolderTest {
    
    private static final String CLIENT_ID = "CLIENT_ID";
    
    @Mock
    private Subscriber subscriber;
    
    @Mock
    private SpiPushExecutor spiPushExecutor;
    
    private SpiImplPushExecutorHolder spiImplPushExecutorHolder;
    
    @BeforeEach
    void setUp() throws NoSuchFieldException, IllegalAccessException {
        spiImplPushExecutorHolder = SpiImplPushExecutorHolder.getInstance();
        Class<SpiImplPushExecutorHolder> spiImplPushExecutorHolderClass = SpiImplPushExecutorHolder.class;
        Field pushExecutors = spiImplPushExecutorHolderClass.getDeclaredField("pushExecutors");
        pushExecutors.setAccessible(true);
        Set<SpiPushExecutor> spiPushExecutorSet = (Set<SpiPushExecutor>) pushExecutors.get(spiImplPushExecutorHolder);
        spiPushExecutorSet.add(spiPushExecutor);
    }
    
    @Test
    void testGetInstance() {
        SpiImplPushExecutorHolder instance = SpiImplPushExecutorHolder.getInstance();
        
        assertNotNull(instance);
    }
    
    @Test
    void testFindPushExecutorSpiImpl() {
        Mockito.when(spiPushExecutor.isInterest(CLIENT_ID, subscriber)).thenReturn(true);
        Optional<SpiPushExecutor> pushExecutorSpiImpl = spiImplPushExecutorHolder.findPushExecutorSpiImpl(CLIENT_ID, subscriber);
        
        assertTrue(pushExecutorSpiImpl.isPresent());
    }
}