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

package com.alibaba.nacos.config.server.model;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class GroupkeyListenserStatusTest {
    
    @Test
    void testSettersAndGetters() {
        GroupkeyListenserStatus status = new GroupkeyListenserStatus();
        status.setCollectStatus(200);
        assertEquals(200, status.getCollectStatus());
        
        Map<String, String> map = new HashMap<>();
        map.put("key", "value");
        status.setLisentersGroupkeyStatus(map);
        assertEquals(map, status.getLisentersGroupkeyStatus());
    }
    
    @Test
    void testDefaults() {
        GroupkeyListenserStatus status = new GroupkeyListenserStatus();
        assertEquals(0, status.getCollectStatus());
        assertNull(status.getLisentersGroupkeyStatus());
    }
}
