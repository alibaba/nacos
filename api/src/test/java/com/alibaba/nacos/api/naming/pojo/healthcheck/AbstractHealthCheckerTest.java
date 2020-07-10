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

package com.alibaba.nacos.api.naming.pojo.healthcheck;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AbstractHealthCheckerTest {
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Before
    public void setUp() {
        objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        objectMapper.registerSubtypes(new NamedType(TestChecker.class, TestChecker.TYPE));
    }
    
    @Test
    public void testSerialize() throws JsonProcessingException {
        TestChecker testChecker = new TestChecker();
        testChecker.setTestValue("");
        String actual = objectMapper.writeValueAsString(testChecker);
        assertTrue(actual.contains("\"testValue\":\"\""));
        assertTrue(actual.contains("\"type\":\"TEST\""));
    }
    
    @Test
    public void testDeserialize() throws IOException {
        String testChecker = "{\"type\":\"TEST\",\"testValue\":\"\"}";
        TestChecker actual = objectMapper.readValue(testChecker, TestChecker.class);
        assertEquals("", actual.getTestValue());
        assertEquals(TestChecker.TYPE, actual.getType());
    }
}
