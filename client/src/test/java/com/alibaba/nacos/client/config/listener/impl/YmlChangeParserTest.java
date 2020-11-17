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

package com.alibaba.nacos.client.config.listener.impl;

import com.alibaba.nacos.api.config.ConfigChangeItem;
import com.alibaba.nacos.client.config.impl.YmlChangeParser;
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
}

