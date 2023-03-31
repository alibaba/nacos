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

package com.alibaba.nacos.api.cmdb.pojo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class EntityEventTest {
    
    ObjectMapper mapper = new ObjectMapper();
    
    @Before
    public void setUp() throws Exception {
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }
    
    @Test
    public void testSerialization() throws JsonProcessingException {
        EntityEvent entity = new EntityEvent();
        entity.setEntityName("test-entity");
        entity.setEntityType("CMDB");
        entity.setType(EntityEventType.ENTITY_ADD_OR_UPDATE);
        String actual = mapper.writeValueAsString(entity);
        System.out.println(actual);
        assertTrue(actual.contains("\"entityName\":\"test-entity\""));
        assertTrue(actual.contains("\"entityType\":\"CMDB\""));
        assertTrue(actual.contains("\"type\":\"ENTITY_ADD_OR_UPDATE\""));
    }
    
    @Test
    public void testDeserialization() throws JsonProcessingException {
        String json = "{\"type\":\"ENTITY_REMOVE\",\"entityName\":\"test-entity\",\"entityType\":\"CMDB\"}";
        EntityEvent entity = mapper.readValue(json, EntityEvent.class);
        assertEquals("test-entity", entity.getEntityName());
        assertEquals("CMDB", entity.getEntityType());
        assertEquals(EntityEventType.ENTITY_REMOVE, entity.getType());
    }
}