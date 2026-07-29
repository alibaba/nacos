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
import com.alibaba.nacos.naming.core.v2.client.impl.HttpConnectionBasedClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

class HttpConnectionBasedClientFactoryTest {
    
    @Test
    void testFactory() {
        HttpConnectionBasedClientFactory factory = new HttpConnectionBasedClientFactory();
        ClientAttributes attributes = new ClientAttributes();
        
        HttpConnectionBasedClient client =
            factory.newClient("HTTP_CLIENT@@client", attributes);
        HttpConnectionBasedClient syncedClient =
            factory.newSyncedClient("HTTP_CLIENT@@client", attributes);
        
        assertEquals(ClientConstants.HTTP_CONNECTION_BASED, factory.getType());
        assertEquals("HTTP_CLIENT@@client", client.getClientId());
        assertEquals("HTTP_CLIENT@@client", syncedClient.getClientId());
        assertNotSame(client, syncedClient);
    }
}
