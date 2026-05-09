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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ClientConnectionEventListenerTest {
    
    @Mock
    private ClientConnectionEventListenerRegistry registry;
    
    @Test
    void testInitRegistersListener() {
        ClientConnectionEventListener listener = new ClientConnectionEventListener() {
            
            @Override
            public void clientConnected(Connection connect) {
            }
            
            @Override
            public void clientDisConnected(Connection connect) {
            }
        };
        ReflectionTestUtils.setField(listener, "clientConnectionEventListenerRegistry", registry);
        listener.init();
        verify(registry).registerClientConnectionEventListener(listener);
    }
    
    @Test
    void testGetNameAndSetName() {
        ClientConnectionEventListener listener = new ClientConnectionEventListener() {
            
            @Override
            public void clientConnected(Connection connect) {
            }
            
            @Override
            public void clientDisConnected(Connection connect) {
            }
        };
        assertNull(listener.getName());
        listener.setName("test-listener");
        assertEquals("test-listener", listener.getName());
    }
    
    @Test
    void testClientConnectedAndDisConnectedInvoked() {
        final boolean[] connected = {false};
        final boolean[] disconnected = {false};
        ClientConnectionEventListener listener = new ClientConnectionEventListener() {
            
            @Override
            public void clientConnected(Connection connect) {
                connected[0] = true;
            }
            
            @Override
            public void clientDisConnected(Connection connect) {
                disconnected[0] = true;
            }
        };
        Connection conn = null;
        listener.clientConnected(conn);
        listener.clientDisConnected(conn);
        assertTrue(connected[0]);
        assertTrue(disconnected[0]);
    }
}
