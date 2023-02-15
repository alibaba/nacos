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

package com.alibaba.nacos.api.ability;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ServerAbilitiesTest {
    
    private static ObjectMapper mapper;
    
    private ServerAbilities serverAbilities;
    
    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        mapper = new ObjectMapper();
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }
    
    @Before
    public void setUp() throws Exception {
        serverAbilities = new ServerAbilities();
    }
    
    @Test
    public void testSerialize() throws JsonProcessingException {
        serverAbilities = new ServerAbilities();
        String json = mapper.writeValueAsString(serverAbilities);
        assertTrue(json.contains("\"remoteAbility\":{"));
        assertTrue(json.contains("\"configAbility\":{"));
        assertTrue(json.contains("\"namingAbility\":{"));
    }
    
    @Test
    public void testDeserialize() throws JsonProcessingException {
        String json = "{\"remoteAbility\":{\"supportRemoteConnection\":false},"
                + "\"configAbility\":{\"supportRemoteMetrics\":false},\"namingAbility\":{\"supportDeltaPush\":false,"
                + "\"supportRemoteMetric\":false}}";
        ServerAbilities abilities = mapper.readValue(json, ServerAbilities.class);
        assertNotNull(abilities.getRemoteAbility());
        assertNotNull(abilities.getNamingAbility());
        assertNotNull(abilities.getConfigAbility());
    }
    
    @Test
    public void testEqualsAndHashCode() {
        assertEquals(serverAbilities, serverAbilities);
        assertEquals(serverAbilities.hashCode(), serverAbilities.hashCode());
        assertNotEquals(serverAbilities, null);
        assertNotEquals(serverAbilities, new ClientAbilities());
        ServerAbilities test = new ServerAbilities();
        assertEquals(serverAbilities, test);
        assertEquals(serverAbilities.hashCode(), test.hashCode());
        test.setRemoteAbility(null);
        assertNotEquals(serverAbilities, test);
        assertNotEquals(serverAbilities.hashCode(), test.hashCode());
    }
}