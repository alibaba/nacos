/*
 *  Copyright 1999-2021 Alibaba Group Holding Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package com.alibaba.nacos.core.remote;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Objects;

/**
 * {@link ClientConnectionEventListenerRegistry} uint test.
 *
 * @author chenglu
 * @date 2021-07-02 14:43
 */
@RunWith(MockitoJUnitRunner.class)
public class ClientConnectionEventListenerRegistryTest {
    
    @InjectMocks
    private ClientConnectionEventListenerRegistry registry;
    
    @Mock
    private Connection connection;
    
    @Test
    public void testRegistryMethods() {
        try {
            registry.registerClientConnectionEventListener(new MockClientConnectionEventListener());
            
            registry.notifyClientConnected(connection);
            
            registry.notifyClientDisConnected(connection);
            
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail(e.getMessage());
        }
    }
    
    class MockClientConnectionEventListener extends ClientConnectionEventListener {
        
        @Override
        public void clientConnected(Connection connect) {
            Assert.assertTrue(Objects.nonNull(connect));
        }
        
        @Override
        public void clientDisConnected(Connection connect) {
            Assert.assertTrue(Objects.nonNull(connect));
        }
    }
}
