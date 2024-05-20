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

package com.alibaba.nacos.config.server.remote;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@ExtendWith(MockitoExtension.class)
class ConfigChangeListenContextTest {
    
    private ConfigChangeListenContext configChangeListenContext;
    
    @BeforeEach
    void setUp() throws Exception {
        configChangeListenContext = new ConfigChangeListenContext();
    }
    
    @Test
    void testAddListen() {
        configChangeListenContext.addListen("groupKey", "md5", "connectionId");
        Set<String> groupKey = configChangeListenContext.getListeners("groupKey");
        assertEquals(1, groupKey.size());
    }
    
    @Test
    void testRemoveListen() {
        configChangeListenContext.addListen("groupKey", "md5", "connectionId");
        configChangeListenContext.removeListen("groupKey", "connectionId");
        Set<String> groupKey = configChangeListenContext.getListeners("groupKey");
        assertNull(groupKey);
    }
    
    @Test
    void testGetListeners() {
        configChangeListenContext.addListen("groupKey", "md5", "connectionId");
        Set<String> groupKey = configChangeListenContext.getListeners("groupKey");
        assertEquals(1, groupKey.size());
    }
    
    @Test
    void testClearContextForConnectionId() {
        configChangeListenContext.addListen("groupKey", "md5", "connectionId");
        Map<String, String> connectionIdBefore = configChangeListenContext.getListenKeys("connectionId");
        assertNotNull(connectionIdBefore);
        configChangeListenContext.clearContextForConnectionId("connectionId");
        Map<String, String> connectionIdAfter = configChangeListenContext.getListenKeys("connectionId");
        assertNull(connectionIdAfter);
    }
    
    @Test
    void testGetListenKeys() {
        configChangeListenContext.addListen("groupKey", "md5", "connectionId");
        Set<String> groupKey = configChangeListenContext.getListeners("groupKey");
        assertEquals(1, groupKey.size());
    }
    
    @Test
    void testGetListenKeyMd5() {
        configChangeListenContext.addListen("groupKey", "md5", "connectionId");
        String listenKeyMd5 = configChangeListenContext.getListenKeyMd5("connectionId", "groupKey");
        assertEquals("md5", listenKeyMd5);
    }
    
}