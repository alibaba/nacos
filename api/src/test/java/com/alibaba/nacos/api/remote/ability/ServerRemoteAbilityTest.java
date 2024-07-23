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

package com.alibaba.nacos.api.remote.ability;

import com.alibaba.nacos.api.ability.ClientAbilities;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerRemoteAbilityTest {
    
    private static ObjectMapper mapper;
    
    private ServerRemoteAbility serverAbilities;
    
    @BeforeAll
    static void setUpBeforeClass() throws Exception {
        mapper = new ObjectMapper();
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        mapper.enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY);
    }
    
    @BeforeEach
    void setUp() throws Exception {
        serverAbilities = new ServerRemoteAbility();
    }
    
    @Test
    void testSerialize() throws JsonProcessingException {
        serverAbilities = new ServerRemoteAbility();
        String json = mapper.writeValueAsString(serverAbilities);
        assertTrue(json.contains("\"supportRemoteConnection\":false"));
        assertTrue(json.contains("\"grpcReportEnabled\":true"));
    }
    
    @Test
    void testDeserialize() throws JsonProcessingException {
        String json = "{\"supportRemoteConnection\":true,\"grpcReportEnabled\":true}";
        ServerRemoteAbility abilities = mapper.readValue(json, ServerRemoteAbility.class);
        assertTrue(abilities.isSupportRemoteConnection());
        assertTrue(abilities.isGrpcReportEnabled());
    }
    
    @Test
    void testEqualsAndHashCode() {
        assertEquals(serverAbilities, serverAbilities);
        assertEquals(serverAbilities.hashCode(), serverAbilities.hashCode());
        assertNotEquals(null, serverAbilities);
        assertNotEquals(serverAbilities, new ClientAbilities());
        ServerRemoteAbility test = new ServerRemoteAbility();
        assertEquals(serverAbilities, test);
        assertEquals(serverAbilities.hashCode(), test.hashCode());
        test.setSupportRemoteConnection(true);
        assertNotEquals(serverAbilities, test);
        assertNotEquals(serverAbilities.hashCode(), test.hashCode());
        test.setSupportRemoteConnection(false);
        test.setGrpcReportEnabled(false);
        assertNotEquals(serverAbilities, test);
        assertNotEquals(serverAbilities.hashCode(), test.hashCode());
    }
}