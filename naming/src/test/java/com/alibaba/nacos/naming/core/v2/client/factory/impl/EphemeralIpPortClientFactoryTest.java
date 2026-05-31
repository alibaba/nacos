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

package com.alibaba.nacos.naming.core.v2.client.factory.impl;

import com.alibaba.nacos.naming.constants.ClientConstants;
import com.alibaba.nacos.naming.core.v2.client.ClientAttributes;
import com.alibaba.nacos.naming.core.v2.client.impl.IpPortBasedClient;
import org.junit.jupiter.api.Test;

import static com.alibaba.nacos.naming.constants.ClientConstants.REVISION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EphemeralIpPortClientFactoryTest {
    
    private final EphemeralIpPortClientFactory factory = new EphemeralIpPortClientFactory();
    
    @Test
    void testGetType() {
        assertEquals(ClientConstants.EPHEMERAL_IP_PORT, factory.getType());
    }
    
    @Test
    void testNewClient() {
        ClientAttributes attributes = new ClientAttributes();
        attributes.addClientAttribute(REVISION, 7);
        
        IpPortBasedClient client = factory.newClient(
            IpPortBasedClient.getClientId("1.1.1.1:8848", true), attributes);
        
        assertEquals("1.1.1.1:8848#true", client.getClientId());
        assertTrue(client.isEphemeral());
        assertSame(attributes, client.getClientAttributes());
        assertEquals(7L, client.getRevision());
    }
    
    @Test
    void testNewSyncedClient() {
        ClientAttributes attributes = new ClientAttributes();
        attributes.addClientAttribute(REVISION, 8);
        
        IpPortBasedClient client = factory.newSyncedClient(
            IpPortBasedClient.getClientId("1.1.1.1:8848", true), attributes);
        
        assertEquals("1.1.1.1:8848#true", client.getClientId());
        assertTrue(client.isEphemeral());
        assertSame(attributes, client.getClientAttributes());
        assertEquals(8L, client.getRevision());
    }
}
