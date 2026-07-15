/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.config.model;

import com.alibaba.nacos.plugin.config.constants.ConfigChangeExecuteTypes;
import com.alibaba.nacos.plugin.config.constants.ConfigChangeConstants;
import com.alibaba.nacos.plugin.config.constants.ConfigChangePointCutTypes;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigChangeModelTest {
    
    @Test
    void testConfigChangeRequestStoresArguments() {
        ConfigChangeRequest request =
            new ConfigChangeRequest(ConfigChangePointCutTypes.PUBLISH_BY_HTTP);
        
        request.setArg("dataId", "test");
        
        assertEquals(ConfigChangePointCutTypes.PUBLISH_BY_HTTP, request.getRequestType());
        assertEquals("test", request.getArg("dataId"));
        assertNull(request.getArg("missing"));
        assertSame(request.getRequestArgs(), request.getRequestArgs());
    }
    
    @Test
    void testConfigChangeResponseAccessors() {
        Object[] args = new Object[] {"dataId", "group"};
        ConfigChangeResponse response =
            new ConfigChangeResponse(ConfigChangePointCutTypes.PUBLISH_BY_HTTP);
        
        response.setResponseType(ConfigChangePointCutTypes.REMOVE_BY_HTTP);
        response.setSuccess(true);
        response.setRetVal("ok");
        response.setMsg("success");
        response.setArgs(args);
        
        assertEquals(ConfigChangePointCutTypes.REMOVE_BY_HTTP, response.getResponseType());
        assertTrue(response.isSuccess());
        assertEquals("ok", response.getRetVal());
        assertEquals("success", response.getMsg());
        assertArrayEquals(args, response.getArgs());
        response.setSuccess(false);
        assertFalse(response.isSuccess());
    }
    
    @Test
    void testEnumValues() {
        assertEquals(ConfigChangeExecuteTypes.EXECUTE_BEFORE_TYPE,
            ConfigChangeExecuteTypes.valueOf("EXECUTE_BEFORE_TYPE"));
        assertEquals(ConfigChangeExecuteTypes.EXECUTE_AFTER_TYPE,
            ConfigChangeExecuteTypes.values()[1]);
        assertEquals("publishOrUpdateByHttp", ConfigChangePointCutTypes.PUBLISH_BY_HTTP.value());
        assertEquals("nacos.core.config.plugin.",
            ConfigChangeConstants.NACOS_CORE_CONFIG_PLUGIN_PREFIX);
        assertEquals("pluginProperties", ConfigChangeConstants.PLUGIN_PROPERTIES);
        assertEquals("originalArgs", ConfigChangeConstants.ORIGINAL_ARGS);
        assertEquals(ConfigChangeConstants.class, new ConfigChangeConstants().getClass());
    }
}
