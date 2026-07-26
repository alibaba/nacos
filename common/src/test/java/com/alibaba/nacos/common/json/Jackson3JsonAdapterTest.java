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
import com.alibaba.nacos.api.exception.runtime.NacosLoadException;
import com.alibaba.nacos.api.exception.runtime.NacosSerializationException;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.api.utils.json.JsonUtils;
import com.alibaba.nacos.api.utils.json.NacosJsonAdapter;
import com.alibaba.nacos.api.utils.json.NacosJsonAdapterNames;
import com.alibaba.nacos.api.utils.json.NacosJsonSubtype;
import com.alibaba.nacos.api.utils.json.NacosTypeReference;
import com.alibaba.nacos.common.utils.TypeUtils;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Jackson3JsonAdapterTest {
    
    private final Jackson3JsonAdapter adapter = new Jackson3JsonAdapter();
    
    @AfterEach
    void tearDown() {
        System.clearProperty(JsonUtils.ADAPTER_PROPERTY_NAME);
    }
    
    @Test
    void testNameAndAvailability() {
        assertEquals(NacosJsonAdapterNames.JACKSON3, adapter.name());
        assertTrue(adapter.isAvailable());
    }
    
    @Test
    void testDelegateNameAndAvailability() {
        Jackson3JsonAdapterDelegate delegate = new Jackson3JsonAdapterDelegate();
        
        assertEquals(NacosJsonAdapterNames.JACKSON3, delegate.name());
        assertTrue(delegate.isAvailable());
    }
    
    @Test
    void testUnavailableWhenJackson3ClassMissing() {
        Jackson3JsonAdapter unavailableAdapter =
            new Jackson3JsonAdapter(new MissingJackson3ClassLoader(
                Jackson3JsonAdapterTest.class.getClassLoader()));
        
        assertFalse(unavailableAdapter.isAvailable());
        assertThrows(NacosLoadException.class, () -> unavailableAdapter.toJson("nacos"));
    }
    
    @Test
    void testUnavailableWhenJackson3ClassHasLinkageError() {
        Jackson3JsonAdapter unavailableAdapter =
            new Jackson3JsonAdapter(new LinkageErrorJackson3ClassLoader(
                Jackson3JsonAdapterTest.class.getClassLoader()));
        
        assertFalse(unavailableAdapter.isAvailable());
    }
    
    @Test
    void testUnavailableWhenJackson3ClassHasServiceConfigurationError() {
        Jackson3JsonAdapter unavailableAdapter =
            new Jackson3JsonAdapter(new ServiceConfigurationErrorJackson3ClassLoader(
                Jackson3JsonAdapterTest.class.getClassLoader()));
        
        assertFalse(unavailableAdapter.isAvailable());
    }
    
    @Test
    void testUnavailableWhenJackson3ClassHasRuntimeException() {
        Jackson3JsonAdapter unavailableAdapter =
            new Jackson3JsonAdapter(new RuntimeExceptionJackson3ClassLoader(
                Jackson3JsonAdapterTest.class.getClassLoader()));
        
        assertFalse(unavailableAdapter.isAvailable());
    }
    
    @Test
    void testServiceLoaderProvider() {
        NacosJsonAdapter loadedAdapter = null;
        for (NacosJsonAdapter each : ServiceLoader.load(NacosJsonAdapter.class)) {
            if (NacosJsonAdapterNames.JACKSON3.equals(each.name())) {
                loadedAdapter = each;
                break;
            }
        }
        
        assertNotNull(loadedAdapter);
        assertInstanceOf(Jackson3JsonAdapter.class, loadedAdapter);
    }
    
    @Test
    void testJsonUtilsAutoSelectsJackson3() {
        assertEquals(NacosJsonAdapterNames.JACKSON3, JsonUtils.selectedAdapterName());
        assertEquals("{\"name\":\"nacos\"}", JsonUtils.toJson(new SampleModel("nacos", null)));
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
        assertThrows(NacosSerializationException.class, () -> adapter.toJson(new FailingModel()));
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
    void testToObjWithNacosTypeReferenceForFinalFieldResult() {
        NacosTypeReference<Result<Boolean>> typeReference =
            new NacosTypeReference<Result<Boolean>>() {
            };
        Result<Boolean> result = adapter.toObj(
            "{\"code\":0,\"message\":\"success\",\"data\":true}", typeReference);
        assertEquals(Integer.valueOf(0), result.getCode());
        assertEquals("success", result.getMessage());
        assertTrue(result.getData());
    }
    
    @Test
    void testToObjWithFinalFieldsAndNoSetter() {
        FinalFieldModel result =
            adapter.toObj("{\"name\":\"nacos\",\"enabled\":true}", FinalFieldModel.class);
        assertEquals("nacos", result.getName());
        assertTrue(result.isEnabled());
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
    void testRegisterSubtypeBeforeDelegateInitialization() {
        adapter.registerSubtype(new NacosJsonSubtype(BaseModel.class, SubModel.class, "sub"));
        
        BaseModel result = adapter.toObj("{\"@type\":\"sub\",\"name\":\"nacos\"}", BaseModel.class);
        
        assertEquals(new SubModel("nacos"), result);
    }
    
    @Test
    void testRegisterSubtypeAfterDelegateInitialization() {
        assertEquals("{\"name\":\"nacos\"}", adapter.toJson(new SampleModel("nacos", null)));
        
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
    
    public static class FailingModel {
        
        public String getName() {
            throw new IllegalStateException("broken");
        }
    }
    
    public static class FinalFieldModel {
        
        private final String name;
        private final boolean enabled;
        public FinalFieldModel() {
            this(null, false);
        }
        
        FinalFieldModel(String name, boolean enabled) {
            this.name = name;
            this.enabled = enabled;
        }
        
        public String getName() {
            return name;
        }
        
        public boolean isEnabled() {
            return enabled;
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
    
    private static class MissingJackson3ClassLoader extends ClassLoader {
        
        MissingJackson3ClassLoader(ClassLoader parent) {
            super(parent);
        }
        
        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.startsWith("tools.jackson.")) {
                throw new ClassNotFoundException(name);
            }
            return super.loadClass(name, resolve);
        }
    }
    
    private static class LinkageErrorJackson3ClassLoader extends ClassLoader {
        
        LinkageErrorJackson3ClassLoader(ClassLoader parent) {
            super(parent);
        }
        
        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.startsWith("tools.jackson.")) {
                throw new UnsupportedClassVersionError(name);
            }
            return super.loadClass(name, resolve);
        }
    }
    
    private static class ServiceConfigurationErrorJackson3ClassLoader extends ClassLoader {
        
        ServiceConfigurationErrorJackson3ClassLoader(ClassLoader parent) {
            super(parent);
        }
        
        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.startsWith("tools.jackson.")) {
                throw new ServiceConfigurationError(name);
            }
            return super.loadClass(name, resolve);
        }
    }
    
    private static class RuntimeExceptionJackson3ClassLoader extends ClassLoader {
        
        RuntimeExceptionJackson3ClassLoader(ClassLoader parent) {
            super(parent);
        }
        
        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.startsWith("tools.jackson.")) {
                throw new IllegalStateException(name);
            }
            return super.loadClass(name, resolve);
        }
    }
}
