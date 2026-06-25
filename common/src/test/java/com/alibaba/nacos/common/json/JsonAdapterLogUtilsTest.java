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

package com.alibaba.nacos.common.json;

import com.alibaba.nacos.api.utils.json.JsonUtils;
import com.alibaba.nacos.api.utils.json.NacosJsonAdapterNames;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class JsonAdapterLogUtilsTest {
    
    @BeforeEach
    void setUp() throws Exception {
        System.clearProperty(JsonUtils.ADAPTER_PROPERTY_NAME);
        resetJsonUtils();
        resetLoggedFlag();
    }
    
    @AfterEach
    void tearDown() throws Exception {
        System.clearProperty(JsonUtils.ADAPTER_PROPERTY_NAME);
        resetJsonUtils();
        resetLoggedFlag();
    }
    
    @Test
    void testLogSelectedAdapterWithAutoMode() {
        JsonAdapterLogUtils.logSelectedAdapter();
        JsonAdapterLogUtils.logSelectedAdapter();
        
        assertNotNull(JsonUtils.selectedAdapterName());
    }
    
    @Test
    void testLogSelectedAdapterWithExplicitJackson2() {
        System.setProperty(JsonUtils.ADAPTER_PROPERTY_NAME, NacosJsonAdapterNames.JACKSON2);
        
        JsonAdapterLogUtils.logSelectedAdapter();
        
        assertEquals(NacosJsonAdapterNames.JACKSON2, JsonUtils.selectedAdapterName());
    }
    
    @Test
    void testConstructor() throws Exception {
        Constructor<JsonAdapterLogUtils> constructor =
            JsonAdapterLogUtils.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        
        assertNotNull(constructor.newInstance());
    }
    
    private void resetJsonUtils() throws Exception {
        Method resetMethod = JsonUtils.class.getDeclaredMethod("resetForTest");
        resetMethod.setAccessible(true);
        resetMethod.invoke(null);
    }
    
    private void resetLoggedFlag() throws Exception {
        Field loggedField = JsonAdapterLogUtils.class.getDeclaredField("LOGGED");
        loggedField.setAccessible(true);
        ((AtomicBoolean) loggedField.get(null)).set(false);
    }
}
