/*
 * Copyright 1999-2021 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.api.naming.ability;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ServerNamingAbilityTest {
    
    private static ObjectMapper jacksonMapper;
    
    @BeforeAll
    static void setUpClass() throws Exception {
        jacksonMapper = new ObjectMapper();
        jacksonMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        jacksonMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }
    
    @Test
    void testDeserializeServerNamingAbilityForNonExistItem() throws JsonProcessingException {
        String nonExistItemJson = "{\"exampleAbility\":false}";
        ServerNamingAbility actual = jacksonMapper.readValue(nonExistItemJson, ServerNamingAbility.class);
        assertFalse(actual.isSupportJraft());
    }
    
    @Test
    void testEquals() throws JsonProcessingException {
        ServerNamingAbility expected = new ServerNamingAbility();
        expected.setSupportJraft(true);
        String serializeJson = jacksonMapper.writeValueAsString(expected);
        ServerNamingAbility actual = jacksonMapper.readValue(serializeJson, ServerNamingAbility.class);
        assertEquals(expected, actual);
        actual = new ServerNamingAbility();
        assertNotEquals(expected, actual);
        actual.setSupportJraft(true);
        assertEquals(expected, actual);
    }
    
    @Test
    void testEqualsForOneObject() {
        ServerNamingAbility ability = new ServerNamingAbility();
        assertEquals(ability, ability);
    }
    
    @Test
    void testEqualsForOtherAbility() {
        ServerNamingAbility ability = new ServerNamingAbility();
        assertNotEquals(ability, new ClientNamingAbility());
    }
    
    @Test
    void testHashCode() throws JsonProcessingException {
        ServerNamingAbility expected = new ServerNamingAbility();
        expected.setSupportJraft(true);
        String serializeJson = jacksonMapper.writeValueAsString(expected);
        ServerNamingAbility actual = jacksonMapper.readValue(serializeJson, ServerNamingAbility.class);
        assertEquals(expected, actual);
        actual = new ServerNamingAbility();
        assertNotEquals(expected, actual);
        actual.setSupportJraft(true);
        assertEquals(expected.hashCode(), actual.hashCode());
    }
}
