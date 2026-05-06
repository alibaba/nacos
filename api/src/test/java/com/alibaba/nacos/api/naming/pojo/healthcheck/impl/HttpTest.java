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

package com.alibaba.nacos.api.naming.pojo.healthcheck.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpTest {
    
    private ObjectMapper objectMapper;
    
    private Http http;
    
    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        http = new Http();
    }
    
    @Test
    void testGetExpectedResponseCodeWithEmpty() {
        http.setHeaders("");
        assertTrue(http.getCustomHeaders().isEmpty());
    }
    
    @Test
    void testGetExpectedResponseCodeWithoutEmpty() {
        http.setHeaders("x:a|y:");
        Map<String, String> actual = http.getCustomHeaders();
        assertFalse(actual.isEmpty());
        assertEquals(1, actual.size());
        assertEquals("a", actual.get("x"));
    }
    
    @Test
    void testSerialize() throws JsonProcessingException {
        http.setHeaders("x:a|y:");
        http.setPath("/x");
        String actual = objectMapper.writeValueAsString(http);
        assertTrue(actual.contains("\"path\":\"/x\""));
        assertTrue(actual.contains("\"type\":\"HTTP\""));
        assertTrue(actual.contains("\"headers\":\"x:a|y:\""));
        assertTrue(actual.contains("\"expectedResponseCode\":200"));
    }
    
    @Test
    void testDeserialize() throws IOException {
        String testChecker = "{\"type\":\"HTTP\",\"path\":\"/x\",\"headers\":\"x:a|y:\",\"expectedResponseCode\":200}";
        Http actual = objectMapper.readValue(testChecker, Http.class);
        assertEquals("x:a|y:", actual.getHeaders());
        assertEquals("/x", actual.getPath());
        assertEquals(200, actual.getExpectedResponseCode());
        assertEquals("x:a|y:", actual.getHeaders());
        assertEquals(Http.TYPE, actual.getType());
    }
    
    @Test
    void testClone() throws CloneNotSupportedException {
        Http cloned = http.clone();
        assertEquals(http.hashCode(), cloned.hashCode());
        assertEquals(http, cloned);
    }
    
    @Test
    void testNotEquals() throws CloneNotSupportedException {
        assertNotEquals(http, new Tcp());
        Http cloned = http.clone();
        cloned.setPath("aaa");
        assertNotEquals(http, cloned);
        cloned = http.clone();
        cloned.setHeaders("aaa");
        assertNotEquals(http, cloned);
        cloned = http.clone();
        cloned.setExpectedResponseCode(500);
        assertNotEquals(http, cloned);
    }
}
