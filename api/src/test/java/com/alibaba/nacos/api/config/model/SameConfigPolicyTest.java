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

package com.alibaba.nacos.api.config.model;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SameConfigPolicyTest {
    
    private ObjectMapper mapper;
    
    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }
    
    @Test
    public void testSerialize() throws Exception {
        String abortJson = mapper.writeValueAsString(SameConfigPolicy.ABORT);
        String skipJson = mapper.writeValueAsString(SameConfigPolicy.SKIP);
        String overwriteJson = mapper.writeValueAsString(SameConfigPolicy.OVERWRITE);
        
        assertTrue(abortJson.contains("\"ABORT\""));
        assertTrue(skipJson.contains("\"SKIP\""));
        assertTrue(overwriteJson.contains("\"OVERWRITE\""));
    }
    
    @Test
    public void testDeserialize() throws Exception {
        assertEquals(SameConfigPolicy.ABORT, mapper.readValue("\"ABORT\"", SameConfigPolicy.class));
        assertEquals(SameConfigPolicy.SKIP, mapper.readValue("\"SKIP\"", SameConfigPolicy.class));
        assertEquals(SameConfigPolicy.OVERWRITE, mapper.readValue("\"OVERWRITE\"", SameConfigPolicy.class));
    }
    
    @Test
    public void testValues() {
        SameConfigPolicy[] values = SameConfigPolicy.values();
        assertEquals(3, values.length);
        assertEquals(SameConfigPolicy.ABORT, values[0]);
        assertEquals(SameConfigPolicy.SKIP, values[1]);
        assertEquals(SameConfigPolicy.OVERWRITE, values[2]);
    }
    
    @Test
    public void testValueOf() {
        assertEquals(SameConfigPolicy.ABORT, SameConfigPolicy.valueOf("ABORT"));
        assertEquals(SameConfigPolicy.SKIP, SameConfigPolicy.valueOf("SKIP"));
        assertEquals(SameConfigPolicy.OVERWRITE, SameConfigPolicy.valueOf("OVERWRITE"));
    }
}