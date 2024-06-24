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

package com.alibaba.nacos.common.remote.client;

import com.alibaba.nacos.api.ability.constant.AbilityKey;
import com.alibaba.nacos.api.ability.constant.AbilityStatus;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.remote.RequestCallBack;
import com.alibaba.nacos.api.remote.RequestFuture;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.response.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectionTest {
    
    Connection connection;
    
    @BeforeEach
    void setUp() throws Exception {
        connection = new Connection(new RpcClient.ServerInfo("127.0.0.1", 8848)) {
            @Override
            public Response request(Request request, long timeoutMills) throws NacosException {
                return null;
            }
            
            @Override
            public RequestFuture requestFuture(Request request) throws NacosException {
                return null;
            }
            
            @Override
            public void asyncRequest(Request request, RequestCallBack requestCallBack) throws NacosException {
            }
            
            @Override
            public void close() {
            }
        };
    }
    
    @AfterEach
    void tearDown() throws Exception {
        connection.close();
    }
    
    @Test
    void testSetConnectionId() {
        assertNull(connection.getConnectionId());
        connection.setConnectionId("testConnectionId");
        assertEquals("testConnectionId", connection.getConnectionId());
    }
    
    @Test
    void testGetConnectionAbility() {
        assertFalse(connection.isAbilitiesSet());
        assertEquals(AbilityStatus.UNKNOWN, connection.getConnectionAbility(AbilityKey.SDK_CLIENT_TEST_1));
        connection.setAbilityTable(Collections.singletonMap(AbilityKey.SERVER_TEST_2.getName(), true));
        assertTrue(connection.isAbilitiesSet());
        assertEquals(AbilityStatus.UNKNOWN, connection.getConnectionAbility(AbilityKey.SDK_CLIENT_TEST_1));
        assertEquals(AbilityStatus.SUPPORTED, connection.getConnectionAbility(AbilityKey.SERVER_TEST_2));
        connection.setAbilityTable(Collections.singletonMap(AbilityKey.SERVER_TEST_2.getName(), false));
        assertEquals(AbilityStatus.NOT_SUPPORTED, connection.getConnectionAbility(AbilityKey.SERVER_TEST_2));
    }
    
    @Test
    void testSetAbandon() {
        assertFalse(connection.isAbandon());
        connection.setAbandon(true);
        assertTrue(connection.isAbandon());
    }
}