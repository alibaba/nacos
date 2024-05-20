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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MysqlTest {
    
    private ObjectMapper objectMapper;
    
    private Mysql mysql;
    
    @BeforeEach
    void setUp() throws Exception {
        mysql = new Mysql();
        mysql.setUser("user");
        mysql.setPwd("pwd");
        mysql.setCmd("cmd");
        objectMapper = new ObjectMapper();
    }
    
    @Test
    void testSerialize() throws JsonProcessingException {
        String actual = objectMapper.writeValueAsString(mysql);
        assertTrue(actual.contains("\"user\":\"user\""));
        assertTrue(actual.contains("\"type\":\"MYSQL\""));
        assertTrue(actual.contains("\"pwd\":\"pwd\""));
        assertTrue(actual.contains("\"cmd\":\"cmd\""));
    }
    
    @Test
    void testDeserialize() throws IOException {
        String testChecker = "{\"type\":\"MYSQL\",\"user\":\"user\",\"pwd\":\"pwd\",\"cmd\":\"cmd\"}";
        Mysql actual = objectMapper.readValue(testChecker, Mysql.class);
        assertEquals("cmd", actual.getCmd());
        assertEquals("pwd", actual.getPwd());
        assertEquals("user", actual.getUser());
        assertEquals(Mysql.TYPE, actual.getType());
    }
    
    @Test
    void testClone() throws CloneNotSupportedException {
        Mysql cloned = mysql.clone();
        assertEquals(mysql.hashCode(), cloned.hashCode());
        assertEquals(mysql, cloned);
    }
    
    @Test
    void testNotEquals() throws CloneNotSupportedException {
        assertNotEquals(mysql, new Tcp());
        Mysql cloned = mysql.clone();
        cloned.setUser("aaa");
        assertNotEquals(mysql, cloned);
        cloned = mysql.clone();
        cloned.setPwd("aaa");
        assertNotEquals(mysql, cloned);
        cloned = mysql.clone();
        cloned.setCmd("aaa");
        assertNotEquals(mysql, cloned);
    }
}
