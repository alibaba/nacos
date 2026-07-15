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

import com.alibaba.nacos.api.exception.runtime.NacosDeserializationException;
import com.alibaba.nacos.api.exception.runtime.NacosSerializationException;
import com.alibaba.nacos.api.utils.json.NacosJsonAdapter;
import com.alibaba.nacos.api.utils.json.NacosJsonAdapterNames;
import com.alibaba.nacos.api.utils.json.NacosJsonSubtype;
import com.alibaba.nacos.api.utils.json.NacosTypeReference;
import com.alibaba.nacos.common.utils.TypeUtils;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Jackson2JsonAdapterTest {
    
    private final Jackson2JsonAdapter adapter = new Jackson2JsonAdapter();
    
    @Test
    void testNameAndAvailability() {
        assertEquals(NacosJsonAdapterNames.JACKSON2, adapter.name());
        assertTrue(adapter.isAvailable());
    }
    
    @Test
    void testServiceLoaderProvider() {
        NacosJsonAdapter loadedAdapter = null;
        for (NacosJsonAdapter each : ServiceLoader.load(NacosJsonAdapter.class)) {
            if (NacosJsonAdapterNames.JACKSON2.equals(each.name())) {
                loadedAdapter = each;
                break;
            }
        }
        
        assertNotNull(loadedAdapter);
        assertInstanceOf(Jackson2JsonAdapter.class, loadedAdapter);
    }
    
    @Test
    void testToJson() {
        SampleModel model = new SampleModel("nacos", null);
        
        assertEquals("{\"name\":\"nacos\"}", adapter.toJson(model));
        assertArrayEquals("{\"name\":\"nacos\"}".getBytes(StandardCharsets.UTF_8),
            adapter.toJsonBytes(model));
    }
    
    @Test
    void testToCanonicalJson() {
        Map<String, Object> source = new LinkedHashMap<String, Object>();
        source.put("z", "last");
        source.put("a", "first");
        source.put("n", null);
        
        assertEquals("{\"a\":\"first\",\"z\":\"last\"}", adapter.toCanonicalJson(source));
    }
    
    @Test
    void testSerializeFailure() {
        assertThrows(NacosSerializationException.class, () -> adapter.toJson(new Object()));
    }
    
    @Test
    void testToObjWithClass() {
        byte[] jsonBytes =
            "{\"name\":\"nacos\",\"unknown\":\"ignored\"}".getBytes(StandardCharsets.UTF_8);
        
        assertEquals(new SampleModel("nacos", null), adapter.toObj(jsonBytes, SampleModel.class));
        assertEquals(new SampleModel("nacos", null),
            adapter.toObj("{\"name\":\"nacos\",\"unknown\":\"ignored\"}", SampleModel.class));
        assertEquals(new SampleModel("nacos", null),
            adapter.toObj(new ByteArrayInputStream(jsonBytes), SampleModel.class));
    }
    
    @Test
    void testToObjWithType() {
        Type listType = TypeUtils.parameterize(List.class, SampleModel.class);
        byte[] jsonBytes = "[{\"name\":\"nacos\"}]".getBytes(StandardCharsets.UTF_8);
        
        assertEquals(new SampleModel("nacos", null),
            ((List<?>) adapter.toObj(jsonBytes, listType)).get(0));
        assertEquals(new SampleModel("nacos", null),
            ((List<?>) adapter.toObj("[{\"name\":\"nacos\"}]", listType)).get(0));
        assertEquals(new SampleModel("nacos", null),
            ((List<?>) adapter.toObj(new ByteArrayInputStream(jsonBytes), listType)).get(0));
    }
    
    @Test
    void testToObjWithNacosTypeReference() {
        NacosTypeReference<Map<String, SampleModel>> typeReference =
            new NacosTypeReference<Map<String, SampleModel>>() {
            };
        byte[] jsonBytes = "{\"item\":{\"name\":\"nacos\"}}".getBytes(StandardCharsets.UTF_8);
        
        assertEquals(new SampleModel("nacos", null),
            adapter.toObj(jsonBytes, typeReference).get("item"));
        assertEquals(new SampleModel("nacos", null),
            adapter.toObj("{\"item\":{\"name\":\"nacos\"}}", typeReference).get("item"));
    }
    
    @Test
    void testDeserializeFailureWithClass() {
        assertThrows(NacosDeserializationException.class,
            () -> adapter.toObj("{broken}".getBytes(StandardCharsets.UTF_8), SampleModel.class));
    }
    
    @Test
    void testDeserializeFailureWithType() {
        Type listType = TypeUtils.parameterize(List.class, SampleModel.class);
        
        assertThrows(NacosDeserializationException.class,
            () -> adapter.toObj("{broken}".getBytes(StandardCharsets.UTF_8), listType));
    }
    
    @Test
    void testRegisterSubtype() {
        adapter.registerSubtype(new NacosJsonSubtype(BaseModel.class, SubModel.class, "sub"));
        
        BaseModel result = adapter.toObj("{\"@type\":\"sub\",\"name\":\"nacos\"}", BaseModel.class);
        
        assertEquals(new SubModel("nacos"), result);
    }
    
    public static class SampleModel {
        
        private String name;
        
        private String optional;
        
        public SampleModel() {
        }
        
        SampleModel(String name, String optional) {
            this.name = name;
            this.optional = optional;
        }
        
        public String getName() {
            return name;
        }
        
        public void setName(String name) {
            this.name = name;
        }
        
        public String getOptional() {
            return optional;
        }
        
        public void setOptional(String optional) {
            this.optional = optional;
        }
        
        @Override
        public boolean equals(Object o) {
            if (!(o instanceof SampleModel)) {
                return false;
            }
            SampleModel that = (SampleModel) o;
            return String.valueOf(name).equals(String.valueOf(that.name))
                && String.valueOf(optional).equals(String.valueOf(that.optional));
        }
        
        @Override
        public int hashCode() {
            int result = name == null ? 0 : name.hashCode();
            result = 31 * result + (optional == null ? 0 : optional.hashCode());
            return result;
        }
    }
    
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "@type")
    public abstract static class BaseModel {
        
        private String name;
        
        public String getName() {
            return name;
        }
        
        public void setName(String name) {
            this.name = name;
        }
    }
    
    public static class SubModel extends BaseModel {
        
        public SubModel() {
        }
        
        SubModel(String name) {
            setName(name);
        }
        
        @Override
        public boolean equals(Object o) {
            if (!(o instanceof SubModel)) {
                return false;
            }
            SubModel that = (SubModel) o;
            return String.valueOf(getName()).equals(String.valueOf(that.getName()));
        }
        
        @Override
        public int hashCode() {
            return getName() == null ? 0 : getName().hashCode();
        }
    }
}
