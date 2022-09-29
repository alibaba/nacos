/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.naming.core.v2.client.impl;

import com.alibaba.nacos.naming.misc.ClientConfig;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ConnectionBasedClientTest {
    
    private final String connectionId = System.currentTimeMillis() + "_127.0.0.1_80";
    
    private final boolean isNative = false;
    
    private ConnectionBasedClient connectionBasedClient;
    
    @Before
    public void setUp() throws Exception {
        connectionBasedClient = new ConnectionBasedClient(connectionId, isNative, null);
    }
    
    @Test
    public void testIsEphemeral() {
        assertTrue(connectionBasedClient.isEphemeral());
    }
    
    @Test
    public void testIsExpire() {
        connectionBasedClient.setLastRenewTime();
        long mustExpireTime =
                connectionBasedClient.getLastRenewTime() + 2 * ClientConfig.getInstance().getClientExpiredTime();
        assertTrue(connectionBasedClient.isExpire(mustExpireTime));
    }
    
    @Test
    public void testRecalculateRevision() {
        assertEquals(0, connectionBasedClient.getRevision());
        connectionBasedClient.recalculateRevision();
        assertEquals(1, connectionBasedClient.getRevision());
    }
    
    @Test
    public void testRecalculateRevisionAsync() throws InterruptedException {
        assertEquals(0, connectionBasedClient.getRevision());
        for (int i = 0; i < 10; i++) {
            Thread thread = new Thread(() -> {
                for (int j = 0; j < 10; j++) {
                    connectionBasedClient.recalculateRevision();
                }
            });
            thread.start();
        }
        TimeUnit.SECONDS.sleep(1);
        assertEquals(100, connectionBasedClient.getRevision());
    }
}
