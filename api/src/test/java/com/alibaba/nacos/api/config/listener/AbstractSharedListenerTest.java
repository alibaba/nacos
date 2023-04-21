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

package com.alibaba.nacos.api.config.listener;

import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class AbstractSharedListenerTest {
    
    private static final String CONFIG_CONTENT = "test";
    
    private static Map<String, String> receivedMap;
    
    @Before
    public void setUp() {
        receivedMap = new HashMap<>();
    }
    
    @Test
    public void testFillContext() {
        assertEquals(0, receivedMap.size());
        MockShardListener listener = new MockShardListener();
        listener.receiveConfigInfo(CONFIG_CONTENT);
        assertEquals(2, receivedMap.size());
        assertNull(receivedMap.get("group"));
        assertNull(receivedMap.get("dataId"));
        listener.fillContext("aaa", "ggg");
        listener.receiveConfigInfo(CONFIG_CONTENT);
        assertEquals(2, receivedMap.size());
        assertEquals("ggg", receivedMap.get("group"));
        assertEquals("aaa", receivedMap.get("dataId"));
    }
    
    @Test
    public void getExecutor() {
        // Default listener executor is null.
        assertNull(new MockShardListener().getExecutor());
    }
    
    private static class MockShardListener extends AbstractSharedListener {
        
        @Override
        public void innerReceive(String dataId, String group, String configInfo) {
            assertEquals(CONFIG_CONTENT, configInfo);
            receivedMap.put("group", group);
            receivedMap.put("dataId", dataId);
        }
    }
}