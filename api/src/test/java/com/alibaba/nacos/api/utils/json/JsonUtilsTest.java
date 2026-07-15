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

package com.alibaba.nacos.api.utils.json;

import com.alibaba.nacos.api.exception.runtime.NacosLoadException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonUtilsTest {
    
    @AfterEach
    void tearDown() {
        JsonUtils.resetForTest();
    }
    
    @Test
    void testDelegateSerializeMethodsToSelectedAdapter() {
        RecordingAdapter adapter = new RecordingAdapter();
        JsonUtils.setAdapterSelectorForTest(
            new JsonAdapterSelector(Collections.<NacosJsonAdapter>singletonList(adapter)));
        
        assertEquals("json:input", JsonUtils.toJson("input"));
        assertArrayEquals("bytes:input".getBytes(), JsonUtils.toJsonBytes("input"));
        assertEquals("canonical:input", JsonUtils.toCanonicalJson("input"));
        assertEquals(NacosJsonAdapterNames.JACKSON2, JsonUtils.selectedAdapterName());
    }
    
    @Test
    void testDelegateDeserializeMethodsToSelectedAdapter() {
        RecordingAdapter adapter = new RecordingAdapter();
        JsonUtils.setAdapterSelectorForTest(
            new JsonAdapterSelector(Collections.<NacosJsonAdapter>singletonList(adapter)));
        NacosTypeReference<String> typeReference = new NacosTypeReference<String>() {
        };
        
        assertEquals("string-class", JsonUtils.toObj("json", String.class));
        assertEquals("string-type", JsonUtils.<String>toObj("json", (Type) String.class));
        assertEquals("string-reference", JsonUtils.toObj("json", typeReference));
        assertEquals("bytes-class", JsonUtils.toObj("json".getBytes(), String.class));
        assertEquals("bytes-type", JsonUtils.<String>toObj("json".getBytes(),
            (Type) String.class));
        assertEquals("bytes-reference", JsonUtils.toObj("json".getBytes(), typeReference));
        assertEquals("stream-class", JsonUtils.toObj((InputStream) null, String.class));
        assertEquals("stream-type", JsonUtils.<String>toObj((InputStream) null,
            (Type) String.class));
    }
    
    @Test
    void testRegisterSubtypeBeforeAdapterSelectionReplaysToSelectedAdapter() {
        RecordingAdapter adapter = new RecordingAdapter();
        JsonUtils.registerSubtype(Number.class, Integer.class, "int");
        JsonUtils.setAdapterSelectorForTest(
            new JsonAdapterSelector(Collections.<NacosJsonAdapter>singletonList(adapter)));
        
        JsonUtils.preload();
        
        assertEquals(1, adapter.registeredSubtypes);
        assertEquals(Number.class, adapter.lastSubtype.getBaseType());
        assertEquals(Integer.class, adapter.lastSubtype.getSubtype());
        assertEquals("int", adapter.lastSubtype.getTypeName());
    }
    
    @Test
    void testRegisterSubtypeAfterAdapterSelectionReplaysImmediately() {
        RecordingAdapter adapter = new RecordingAdapter();
        JsonUtils.setAdapterSelectorForTest(
            new JsonAdapterSelector(Collections.<NacosJsonAdapter>singletonList(adapter)));
        
        JsonUtils.preload();
        JsonUtils.registerSubtype(Integer.class, "int");
        
        assertEquals(1, adapter.registeredSubtypes);
        assertEquals(Object.class, adapter.lastSubtype.getBaseType());
        assertEquals(Integer.class, adapter.lastSubtype.getSubtype());
        assertEquals("int", adapter.lastSubtype.getTypeName());
    }
    
    @Test
    void testMissingAdapterThrowsClearException() {
        JsonUtils.setAdapterSelectorForTest(
            new JsonAdapterSelector(Collections.<NacosJsonAdapter>emptyList()));
        
        NacosLoadException exception =
            assertThrows(NacosLoadException.class, () -> JsonUtils.toJson("input"));
        
        assertTrue(exception.getMessage().contains("No available JSON adapter"));
    }
    
    private static class RecordingAdapter implements NacosJsonAdapter {
        
        private int registeredSubtypes;
        
        private NacosJsonSubtype lastSubtype;
        
        @Override
        public String name() {
            return NacosJsonAdapterNames.JACKSON2;
        }
        
        @Override
        public boolean isAvailable() {
            return true;
        }
        
        @Override
        public String toJson(Object obj) {
            return "json:" + obj;
        }
        
        @Override
        public byte[] toJsonBytes(Object obj) {
            return ("bytes:" + obj).getBytes();
        }
        
        @Override
        public String toCanonicalJson(Object obj) {
            return "canonical:" + obj;
        }
        
        @Override
        public <T> T toObj(byte[] json, Class<T> cls) {
            return cast("bytes-class");
        }
        
        @Override
        public <T> T toObj(byte[] json, Type type) {
            return cast("bytes-type");
        }
        
        @Override
        public <T> T toObj(byte[] json, NacosTypeReference<T> typeReference) {
            return cast("bytes-reference");
        }
        
        @Override
        public <T> T toObj(String json, Class<T> cls) {
            return cast("string-class");
        }
        
        @Override
        public <T> T toObj(String json, Type type) {
            return cast("string-type");
        }
        
        @Override
        public <T> T toObj(String json, NacosTypeReference<T> typeReference) {
            return cast("string-reference");
        }
        
        @Override
        public <T> T toObj(InputStream inputStream, Class<T> cls) {
            return cast("stream-class");
        }
        
        @Override
        public <T> T toObj(InputStream inputStream, Type type) {
            return cast("stream-type");
        }
        
        @Override
        public void registerSubtype(NacosJsonSubtype subtype) {
            registeredSubtypes++;
            lastSubtype = subtype;
        }
        
        @SuppressWarnings("unchecked")
        private <T> T cast(String value) {
            return (T) value;
        }
    }
}
