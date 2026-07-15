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
import com.alibaba.nacos.api.utils.json.NacosJsonAdapter;
import com.alibaba.nacos.api.utils.json.NacosJsonAdapterNames;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JacksonAdapterCompatibilityTest {
    
    @AfterEach
    void tearDown() throws Exception {
        System.clearProperty(JsonUtils.ADAPTER_PROPERTY_NAME);
        resetJsonUtils();
    }
    
    @Test
    void testDefaultAdaptersAreRegisteredAndAvailable() {
        Map<String, NacosJsonAdapter> adapters = loadAdapters();
        
        assertTrue(adapters.containsKey(NacosJsonAdapterNames.JACKSON2));
        assertTrue(adapters.containsKey(NacosJsonAdapterNames.JACKSON3));
        assertTrue(adapters.get(NacosJsonAdapterNames.JACKSON2).isAvailable());
        assertTrue(adapters.get(NacosJsonAdapterNames.JACKSON3).isAvailable());
    }
    
    @Test
    void testAutoSelectsJackson3WhenBothAdaptersAreAvailable() {
        assertEquals(NacosJsonAdapterNames.JACKSON3, JsonUtils.selectedAdapterName());
        assertEquals("{\"name\":\"nacos\"}", JsonUtils.toJson(new SampleModel("nacos")));
    }
    
    @Test
    void testExplicitJackson2SelectionWorks() throws Exception {
        System.setProperty(JsonUtils.ADAPTER_PROPERTY_NAME, NacosJsonAdapterNames.JACKSON2);
        resetJsonUtils();
        
        assertEquals(NacosJsonAdapterNames.JACKSON2, JsonUtils.selectedAdapterName());
        assertEquals(new SampleModel("nacos"),
            JsonUtils.toObj("{\"name\":\"nacos\",\"unknown\":\"ignored\"}", SampleModel.class));
    }
    
    @Test
    void testExplicitJackson3SelectionWorks() throws Exception {
        System.setProperty(JsonUtils.ADAPTER_PROPERTY_NAME, NacosJsonAdapterNames.JACKSON3);
        resetJsonUtils();
        
        assertEquals(NacosJsonAdapterNames.JACKSON3, JsonUtils.selectedAdapterName());
        assertEquals(new SampleModel("nacos"),
            JsonUtils.toObj("{\"name\":\"nacos\",\"unknown\":\"ignored\"}", SampleModel.class));
    }
    
    private Map<String, NacosJsonAdapter> loadAdapters() {
        Map<String, NacosJsonAdapter> result = new HashMap<String, NacosJsonAdapter>();
        for (NacosJsonAdapter adapter : ServiceLoader.load(NacosJsonAdapter.class)) {
            result.put(adapter.name(), adapter);
        }
        return result;
    }
    
    private void resetJsonUtils() throws Exception {
        Method resetMethod = JsonUtils.class.getDeclaredMethod("resetForTest");
        resetMethod.setAccessible(true);
        resetMethod.invoke(null);
    }
    
    public static class SampleModel {
        
        private String name;
        
        public SampleModel() {
        }
        
        SampleModel(String name) {
            this.name = name;
        }
        
        public String getName() {
            return name;
        }
        
        public void setName(String name) {
            this.name = name;
        }
        
        @Override
        public boolean equals(Object o) {
            if (!(o instanceof SampleModel)) {
                return false;
            }
            SampleModel that = (SampleModel) o;
            return String.valueOf(name).equals(String.valueOf(that.name));
        }
        
        @Override
        public int hashCode() {
            return name == null ? 0 : name.hashCode();
        }
    }
}
