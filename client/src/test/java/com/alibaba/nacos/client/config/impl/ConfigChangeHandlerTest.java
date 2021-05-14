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
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.Map;

public class ConfigChangeHandlerTest {
    
    @Test
    public void testParseProperties() throws IOException {
        Map properties = ConfigChangeHandler.getInstance().parseChangeData("", "app.name = nacos", "properties");
        Assert.assertEquals("nacos", ((ConfigChangeItem) properties.get("app.name")).getNewValue());
    }
    
    @Test
    public void testParseYaml() throws IOException {
        Map properties = ConfigChangeHandler.getInstance().parseChangeData("", "app:\n  name: nacos", "yaml");
        Assert.assertEquals("nacos", ((ConfigChangeItem) properties.get("app.name")).getNewValue());
    }
}
