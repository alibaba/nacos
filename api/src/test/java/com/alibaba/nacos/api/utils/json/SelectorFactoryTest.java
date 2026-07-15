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

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.selector.AbstractSelector;
import com.alibaba.nacos.api.selector.ExpressionSelector;
import com.alibaba.nacos.api.selector.NoneSelector;
import com.alibaba.nacos.api.selector.Selector;
import com.alibaba.nacos.api.selector.SelectorFactory;
import com.alibaba.nacos.api.selector.SelectorType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SelectorFactoryTest {
    
    @BeforeEach
    void setUp() {
        JsonUtils.resetForTest();
    }
    
    @AfterEach
    void tearDown() {
        JsonUtils.resetForTest();
    }
    
    @Test
    void testPreloadRegistersBuiltInSelectorSubtypes() {
        RecordingAdapter adapter = selectRecordingAdapter();
        
        SelectorFactory.preload();
        JsonUtils.preload();
        
        assertEquals(10, adapter.subtypes.size());
        assertRegistration(adapter, Selector.class, NoneSelector.class,
            SelectorType.none.name());
        assertRegistration(adapter, Selector.class, NoneSelector.class, "NoneSelector");
        assertRegistration(adapter, Selector.class, ExpressionSelector.class,
            SelectorType.label.name());
        assertRegistration(adapter, Selector.class, ExpressionSelector.class, "LabelSelector");
        assertRegistration(adapter, Selector.class, ExpressionSelector.class,
            "ExpressionSelector");
        assertRegistration(adapter, AbstractSelector.class, NoneSelector.class,
            SelectorType.none.name());
        assertRegistration(adapter, AbstractSelector.class, NoneSelector.class,
            "NoneSelector");
        assertRegistration(adapter, AbstractSelector.class, ExpressionSelector.class,
            SelectorType.label.name());
        assertRegistration(adapter, AbstractSelector.class, ExpressionSelector.class,
            "LabelSelector");
        assertRegistration(adapter, AbstractSelector.class, ExpressionSelector.class,
            "ExpressionSelector");
    }
    
    @Test
    void testRegisterSubTypeWithoutAbstractSelector() {
        SelectorFactory.preload();
        JsonUtils.resetForTest();
        RecordingAdapter adapter = selectRecordingAdapter();
        
        SelectorFactory.registerSubType(TestSelector.class, "test");
        JsonUtils.preload();
        
        assertEquals(1, adapter.subtypes.size());
        assertRegistration(adapter, Selector.class, TestSelector.class, "test");
    }
    
    private RecordingAdapter selectRecordingAdapter() {
        RecordingAdapter adapter = new RecordingAdapter();
        JsonUtils.setAdapterSelectorForTest(
            new JsonAdapterSelector(Collections.<NacosJsonAdapter>singletonList(adapter)));
        return adapter;
    }
    
    private void assertRegistration(RecordingAdapter adapter, Class<?> baseType,
        Class<?> subtype, String typeName) {
        assertTrue(adapter.subtypes.contains(new NacosJsonSubtype(baseType, subtype, typeName)));
    }
    
    private static final class TestSelector
        implements Selector<List<Instance>, List<Instance>, String> {
        
        private static final long serialVersionUID = 4929105441031362361L;
        
        @Override
        public Selector<List<Instance>, List<Instance>, String> parse(String expression)
            throws NacosException {
            return this;
        }
        
        @Override
        public List<Instance> select(List<Instance> context) {
            return context;
        }
        
        @Override
        public String getType() {
            return "test";
        }
        
        @Override
        public String getContextType() {
            return "test";
        }
    }
    
    private static final class RecordingAdapter implements NacosJsonAdapter {
        
        private final List<NacosJsonSubtype> subtypes = new ArrayList<NacosJsonSubtype>();
        
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
            subtypes.add(subtype);
        }
    }
}
