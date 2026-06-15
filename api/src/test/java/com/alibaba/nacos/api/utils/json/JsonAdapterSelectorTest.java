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
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonAdapterSelectorTest {
    
    @AfterEach
    void tearDown() {
        System.clearProperty(JsonUtils.ADAPTER_PROPERTY_NAME);
    }
    
    @Test
    void testSelectOnlyAvailableAdapter() {
        FakeAdapter adapter = new FakeAdapter("custom", true);
        
        assertSame(adapter, new JsonAdapterSelector(Collections.singletonList(adapter)).select());
    }
    
    @Test
    void testSelectJackson3WhenJackson2AndJackson3Available() {
        FakeAdapter jackson2 = new FakeAdapter(NacosJsonAdapterNames.JACKSON2, true);
        FakeAdapter jackson3 = new FakeAdapter(NacosJsonAdapterNames.JACKSON3, true);
        
        NacosJsonAdapter selected =
            new JsonAdapterSelector(Arrays.<NacosJsonAdapter>asList(jackson2, jackson3)).select();
        
        assertSame(jackson3, selected);
    }
    
    @Test
    void testSelectJackson2WhenExplicitlyConfigured() {
        System.setProperty(JsonUtils.ADAPTER_PROPERTY_NAME, NacosJsonAdapterNames.JACKSON2);
        FakeAdapter jackson2 = new FakeAdapter(NacosJsonAdapterNames.JACKSON2, true);
        FakeAdapter jackson3 = new FakeAdapter(NacosJsonAdapterNames.JACKSON3, true);
        
        NacosJsonAdapter selected =
            new JsonAdapterSelector(Arrays.<NacosJsonAdapter>asList(jackson2, jackson3)).select();
        
        assertSame(jackson2, selected);
    }
    
    @Test
    void testSelectJackson3WhenExplicitlyConfigured() {
        System.setProperty(JsonUtils.ADAPTER_PROPERTY_NAME, NacosJsonAdapterNames.JACKSON3);
        FakeAdapter jackson2 = new FakeAdapter(NacosJsonAdapterNames.JACKSON2, true);
        FakeAdapter jackson3 = new FakeAdapter(NacosJsonAdapterNames.JACKSON3, true);
        
        NacosJsonAdapter selected =
            new JsonAdapterSelector(Arrays.<NacosJsonAdapter>asList(jackson2, jackson3)).select();
        
        assertSame(jackson3, selected);
    }
    
    @Test
    void testFailWhenNoAdapterAvailable() {
        NacosLoadException exception = assertThrows(NacosLoadException.class,
            () -> new JsonAdapterSelector(Collections.<NacosJsonAdapter>emptyList()).select());
        
        assertTrue(exception.getMessage().contains("No available JSON adapter"));
    }
    
    @Test
    void testFailWhenSelectedAdapterUnavailable() {
        System.setProperty(JsonUtils.ADAPTER_PROPERTY_NAME, NacosJsonAdapterNames.JACKSON3);
        FakeAdapter jackson2 = new FakeAdapter(NacosJsonAdapterNames.JACKSON2, true);
        FakeAdapter jackson3 = new FakeAdapter(NacosJsonAdapterNames.JACKSON3, false);
        
        NacosLoadException exception = assertThrows(NacosLoadException.class,
            () -> new JsonAdapterSelector(Arrays.<NacosJsonAdapter>asList(jackson2, jackson3))
                .select());
        
        assertTrue(exception.getMessage().contains("jackson3"));
    }
    
    @Test
    void testBrokenAdapterIsIgnoredAndDiagnosed() {
        BrokenAdapter brokenAdapter = new BrokenAdapter();
        FakeAdapter jackson2 = new FakeAdapter(NacosJsonAdapterNames.JACKSON2, true);
        
        NacosJsonAdapter selected = new JsonAdapterSelector(
            Arrays.<NacosJsonAdapter>asList(brokenAdapter, jackson2)).select();
        
        assertSame(jackson2, selected);
    }
    
    @Test
    void testFailWhenUnsupportedAdapterConfigured() {
        System.setProperty(JsonUtils.ADAPTER_PROPERTY_NAME, "other");
        
        NacosLoadException exception = assertThrows(NacosLoadException.class,
            () -> new JsonAdapterSelector(Collections.<NacosJsonAdapter>emptyList()).select());
        
        assertTrue(exception.getMessage().contains("Unsupported JSON adapter"));
    }
    
    private static class FakeAdapter implements NacosJsonAdapter {
        
        private final String name;
        
        private final boolean available;
        
        FakeAdapter(String name, boolean available) {
            this.name = name;
            this.available = available;
        }
        
        @Override
        public String name() {
            return name;
        }
        
        @Override
        public boolean isAvailable() {
            return available;
        }
        
        @Override
        public String toJson(Object obj) {
            return null;
        }
        
        @Override
        public byte[] toJsonBytes(Object obj) {
            return new byte[0];
        }
        
        @Override
        public String toCanonicalJson(Object obj) {
            return null;
        }
        
        @Override
        public <T> T toObj(byte[] json, Class<T> cls) {
            return null;
        }
        
        @Override
        public <T> T toObj(byte[] json, Type type) {
            return null;
        }
        
        @Override
        public <T> T toObj(byte[] json, NacosTypeReference<T> typeReference) {
            return null;
        }
        
        @Override
        public <T> T toObj(String json, Class<T> cls) {
            return null;
        }
        
        @Override
        public <T> T toObj(String json, Type type) {
            return null;
        }
        
        @Override
        public <T> T toObj(String json, NacosTypeReference<T> typeReference) {
            return null;
        }
        
        @Override
        public <T> T toObj(InputStream inputStream, Class<T> cls) {
            return null;
        }
        
        @Override
        public <T> T toObj(InputStream inputStream, Type type) {
            return null;
        }
        
        @Override
        public void registerSubtype(NacosJsonSubtype subtype) {
        }
    }
    
    private static class BrokenAdapter extends FakeAdapter {
        
        BrokenAdapter() {
            super("broken", true);
        }
        
        @Override
        public boolean isAvailable() {
            throw new NoClassDefFoundError("missing");
        }
    }
}
