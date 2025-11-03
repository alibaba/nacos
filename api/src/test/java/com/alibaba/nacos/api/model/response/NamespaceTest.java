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

package com.alibaba.nacos.api.model.response;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class NamespaceTest {
    
    private ObjectMapper mapper;
    
    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }
    
    @Test
    void testNoArgsConstructor() {
        Namespace namespace = new Namespace();
        
        assertNull(namespace.getNamespace());
        assertNull(namespace.getNamespaceShowName());
        assertNull(namespace.getNamespaceDesc());
        assertEquals(0, namespace.getQuota());
        assertEquals(0, namespace.getConfigCount());
        assertEquals(0, namespace.getType());
    }
    
    @Test
    void testConstructorWithNamespaceAndShowName() {
        Namespace namespace = new Namespace("testNamespace", "testShowName");
        
        assertEquals("testNamespace", namespace.getNamespace());
        assertEquals("testShowName", namespace.getNamespaceShowName());
        assertNull(namespace.getNamespaceDesc());
        assertEquals(0, namespace.getQuota());
        assertEquals(0, namespace.getConfigCount());
        assertEquals(0, namespace.getType());
    }
    
    @Test
    void testConstructorWithAllParamsExceptDesc() {
        Namespace namespace = new Namespace("testNamespace", "testShowName", 100, 50, 1);
        
        assertEquals("testNamespace", namespace.getNamespace());
        assertEquals("testShowName", namespace.getNamespaceShowName());
        assertNull(namespace.getNamespaceDesc());
        assertEquals(100, namespace.getQuota());
        assertEquals(50, namespace.getConfigCount());
        assertEquals(1, namespace.getType());
    }
    
    @Test
    void testConstructorWithAllParams() {
        Namespace namespace = new Namespace("testNamespace", "testShowName", "testDesc", 100, 50, 1);
        
        assertEquals("testNamespace", namespace.getNamespace());
        assertEquals("testShowName", namespace.getNamespaceShowName());
        assertEquals("testDesc", namespace.getNamespaceDesc());
        assertEquals(100, namespace.getQuota());
        assertEquals(50, namespace.getConfigCount());
        assertEquals(1, namespace.getType());
    }
    
    @Test
    void testGetterSetter() {
        Namespace namespace = new Namespace();
        
        namespace.setNamespace("testNamespace");
        namespace.setNamespaceShowName("testShowName");
        namespace.setNamespaceDesc("testDesc");
        namespace.setQuota(100);
        namespace.setConfigCount(50);
        namespace.setType(1);
        
        assertEquals("testNamespace", namespace.getNamespace());
        assertEquals("testShowName", namespace.getNamespaceShowName());
        assertEquals("testDesc", namespace.getNamespaceDesc());
        assertEquals(100, namespace.getQuota());
        assertEquals(50, namespace.getConfigCount());
        assertEquals(1, namespace.getType());
    }
    
    @Test
    void testSerialize() throws Exception {
        Namespace namespace = new Namespace("testNamespace", "testShowName", "testDesc", 100, 50, 1);
        String json = mapper.writeValueAsString(namespace);
        
        Namespace deserializedNamespace = mapper.readValue(json, Namespace.class);
        
        assertEquals(namespace.getNamespace(), deserializedNamespace.getNamespace());
        assertEquals(namespace.getNamespaceShowName(), deserializedNamespace.getNamespaceShowName());
        assertEquals(namespace.getNamespaceDesc(), deserializedNamespace.getNamespaceDesc());
        assertEquals(namespace.getQuota(), deserializedNamespace.getQuota());
        assertEquals(namespace.getConfigCount(), deserializedNamespace.getConfigCount());
        assertEquals(namespace.getType(), deserializedNamespace.getType());
    }
    
    @Test
    void testDeserialize() throws Exception {
        String json = "{\"namespace\":\"testNamespace\",\"namespaceShowName\":\"testShowName\","
                + "\"namespaceDesc\":\"testDesc\",\"quota\":100,\"configCount\":50,\"type\":1}";
        Namespace namespace = mapper.readValue(json, Namespace.class);
        
        assertEquals("testNamespace", namespace.getNamespace());
        assertEquals("testShowName", namespace.getNamespaceShowName());
        assertEquals("testDesc", namespace.getNamespaceDesc());
        assertEquals(100, namespace.getQuota());
        assertEquals(50, namespace.getConfigCount());
        assertEquals(1, namespace.getType());
    }
}