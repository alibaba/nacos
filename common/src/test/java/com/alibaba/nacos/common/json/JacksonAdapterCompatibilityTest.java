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

import com.alibaba.nacos.api.ai.model.agent.Endpoint;
import com.alibaba.nacos.api.ai.remote.request.AgentEndpointRegisterRpcRequest;
import com.alibaba.nacos.api.ai.utils.EndpointCanonicalizer;
import com.alibaba.nacos.api.ai.utils.RadModelValidator;
import com.alibaba.nacos.api.utils.json.JsonUtils;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;

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
    
    @ParameterizedTest
    @ValueSource(strings = {"jackson2", "jackson3"})
    void testEndpointWireDefaultsAndExplicitNull(String adapterName) throws Exception {
        System.setProperty(JsonUtils.ADAPTER_PROPERTY_NAME, adapterName);
        resetJsonUtils();
        assertEquals(adapterName, JsonUtils.selectedAdapterName());
        String key = "\"uri\":\"https://example.com/rpc\",\"transport\":\"HTTP\"";
        Endpoint defaults = JsonUtils.toObj("{" + key + "}", Endpoint.class);
        assertEquals(0, defaults.getPriority());
        assertEquals(1D, defaults.getWeight());
        assertTrue(defaults.getHealthy());
        assertTrue(defaults.getEnabled());
        Endpoint disabled = JsonUtils.toObj("{" + key
            + ",\"enabled\":false,\"healthy\":false}", Endpoint.class);
        assertFalse(EndpointCanonicalizer.canonicalize(disabled).getEnabled());
        assertFalse(disabled.getHealthy());
        assertFalse(JsonUtils.toJson(disabled).contains("\"state\""));
        for (String field : new String[] {"priority", "weight", "healthy", "enabled"}) {
            String endpointJson = "{" + key + ",\"" + field + "\":null}";
            Endpoint invalid = JsonUtils.toObj(endpointJson, Endpoint.class);
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> EndpointCanonicalizer.canonicalize(invalid));
            assertTrue(error.getMessage().contains(field));
            String rpcJson = "{\"namespaceId\":\"public\",\"registrationBatch\":{"
                + "\"agentName\":\"demo\",\"protocol\":\"a2a\",\"runtimeVersion\":\"1.0.0\","
                + "\"endpoints\":[" + endpointJson + "]}}";
            AgentEndpointRegisterRpcRequest request = JsonUtils.toObj(rpcJson,
                AgentEndpointRegisterRpcRequest.class);
            assertThrows(IllegalArgumentException.class,
                () -> RadModelValidator.validate("public", request.getRegistrationBatch()));
        }
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
