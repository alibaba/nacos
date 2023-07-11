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

package com.alibaba.nacos.api.selector;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class NoneSelectorTest {
    
    ObjectMapper mapper = new ObjectMapper();
    
    @Before
    public void setUp() throws Exception {
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        mapper.registerSubtypes(new NamedType(NoneSelector.class, SelectorType.none.name()));
    }
    
    @Test
    public void testSerialization() throws JsonProcessingException {
        NoneSelector selector = new NoneSelector();
        String actual = mapper.writeValueAsString(selector);
        assertTrue(actual.contains("\"type\":\"" + SelectorType.none.name() + "\""));
    }
    
    @Test
    public void testDeserialization() throws JsonProcessingException {
        String json = "{\"type\":\"none\"}";
        AbstractSelector actual = mapper.readValue(json, AbstractSelector.class);
        assertEquals(SelectorType.none.name(), actual.getType());
    }
}