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

package com.alibaba.nacos.config.server.model;

import com.alibaba.nacos.config.server.model.capacity.Capacity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.assertTrue;

class JacksonSerializationCompatibilityTest {
    
    private static final long LARGE_ID = 9007199254740993L;
    
    private final ObjectMapper jackson2Mapper = new ObjectMapper();
    
    private final JsonMapper jackson3Mapper = new JsonMapper();
    
    @Test
    void shouldSerializeConfigHistoryInfoIdAsString() throws Exception {
        ConfigHistoryInfo model = new ConfigHistoryInfo();
        model.setId(LARGE_ID);
        model.setCreatedTime(new Timestamp(0L));
        model.setLastModifiedTime(new Timestamp(0L));
        assertIdIsString(model);
    }
    
    @Test
    void shouldSerializeConfigInfoBaseIdAsString() throws Exception {
        ConfigInfoBase model = new ConfigInfoBase();
        model.setId(LARGE_ID);
        assertIdIsString(model);
    }
    
    @Test
    void shouldSerializeCapacityIdAsString() throws Exception {
        Capacity model = new Capacity();
        model.setId(LARGE_ID);
        assertIdIsString(model);
    }
    
    private void assertIdIsString(Object model) throws Exception {
        String expected = "\"id\":\"" + LARGE_ID + "\"";
        String jackson2Json = jackson2Mapper.writeValueAsString(model);
        String jackson3Json = jackson3Mapper.writeValueAsString(model);
        assertTrue(jackson2Json.contains(expected), jackson2Json);
        assertTrue(jackson3Json.contains(expected), jackson3Json);
    }
}
