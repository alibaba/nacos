/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.naming.core.v2.event.client;

import com.alibaba.nacos.naming.core.v2.client.Client;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientEventTest {
    
    @Test
    void testClientChangedEventReturnsClient() {
        Client client = Mockito.mock(Client.class);
        
        ClientEvent.ClientChangedEvent actual = new ClientEvent.ClientChangedEvent(client);
        
        assertSame(client, actual.getClient());
    }
    
    @Test
    void testClientDisconnectEventReturnsNativeFlag() {
        Client client = Mockito.mock(Client.class);
        
        ClientEvent.ClientDisconnectEvent nativeEvent =
            new ClientEvent.ClientDisconnectEvent(client, true);
        ClientEvent.ClientDisconnectEvent remoteEvent =
            new ClientEvent.ClientDisconnectEvent(client, false);
        
        assertSame(client, nativeEvent.getClient());
        assertTrue(nativeEvent.isNative());
        assertFalse(remoteEvent.isNative());
    }
    
    @Test
    void testClientVerifyFailedEventReturnsFields() {
        ClientEvent.ClientVerifyFailedEvent actual =
            new ClientEvent.ClientVerifyFailedEvent("clientId", "targetServer");
        
        assertEquals("clientId", actual.getClientId());
        assertEquals("targetServer", actual.getTargetServer());
    }
}
