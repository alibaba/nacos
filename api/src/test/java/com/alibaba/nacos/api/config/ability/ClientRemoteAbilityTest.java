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

package com.alibaba.nacos.api.config.ability;

import com.alibaba.nacos.api.remote.ability.ClientRemoteAbility;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ClientRemoteAbilityTest {
    
    private static ObjectMapper mapper;
    
    @BeforeClass
    public static void setUp() throws Exception {
        mapper = new ObjectMapper();
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }
    
    @Test
    public void testSerialize() throws JsonProcessingException {
        ClientRemoteAbility abilities = new ClientRemoteAbility();
        String json = mapper.writeValueAsString(abilities);
        assertEquals("{\"supportRemoteConnection\":false}", json);
    }
    
    @Test
    public void testDeserialize() throws JsonProcessingException {
        String json = "{\"supportRemoteConnection\":true}";
        ClientRemoteAbility abilities = mapper.readValue(json, ClientRemoteAbility.class);
        assertTrue(abilities.isSupportRemoteConnection());
    }
    
}