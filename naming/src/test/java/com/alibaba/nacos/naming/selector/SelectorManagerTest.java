/*
 *  Copyright 1999-2021 Alibaba Group Holding Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package com.alibaba.nacos.naming.selector;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.selector.Selector;
import com.alibaba.nacos.api.selector.context.SelectorContextBuilder;
import com.alibaba.nacos.consistency.SerializeFactory;
import com.alibaba.nacos.consistency.Serializer;
import com.alibaba.nacos.common.spi.NacosServiceLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;

/**
 * {@link SelectorManager} unit test.
 *
 * @author chenglu
 * @date 2021-07-14 18:58
 */
class SelectorManagerTest {
    
    private SelectorManager selectorManager;
    
    @BeforeEach
    void setUp() {
        selectorManager = new SelectorManager();
        selectorManager.init();
    }
    
    @Test
    void testGetAllSelectorTypes() {
        List<String> selectorTypes = selectorManager.getAllSelectorTypes();
        assertTrue(selectorTypes.contains("mock"));
    }
    
    @Test
    void testParseSelector() throws NacosException {
        Selector selector = selectorManager.parseSelector("mock", "key=value");
        assertTrue(selector instanceof MockSelector);
        
        assertEquals("mock", selector.getType());
    }
    
    @Test
    void testParseSelectorReturnsNullWhenTypeBlankOrUnknown() throws NacosException {
        assertNull(selectorManager.parseSelector("", "key=value"));
        assertNull(selectorManager.parseSelector("unknown", "key=value"));
    }
    
    @Test
    void testInitSkipsDuplicateContextBuildersAndSelectors() {
        Map<String, SelectorContextBuilder> contextBuilders = new HashMap<>();
        contextBuilders.put("MOCK_CMDB", Mockito.mock(SelectorContextBuilder.class));
        contextBuilders.put("CMDB", Mockito.mock(SelectorContextBuilder.class));
        contextBuilders.put("NONE", Mockito.mock(SelectorContextBuilder.class));
        ReflectionTestUtils.setField(selectorManager, "contextBuilders", contextBuilders);
        Map<String, Class<? extends Selector>> selectorTypes = new HashMap<>();
        selectorTypes.put("mock", MockSelector.class);
        selectorTypes.put("label", LabelSelector.class);
        selectorTypes.put("none", NoneSelector.class);
        ReflectionTestUtils.setField(selectorManager, "selectorTypes", selectorTypes);
        
        ReflectionTestUtils.invokeMethod(selectorManager, "initSelectorContextBuilders");
        ReflectionTestUtils.invokeMethod(selectorManager, "initSelectorTypes");
        
        assertEquals(3, contextBuilders.size());
        assertEquals(3, selectorTypes.size());
    }
    
    @Test
    void testInitSkipsSelectorWithoutDefaultConstructor() {
        Map<String, Class<? extends Selector>> selectorTypes = new HashMap<>();
        ReflectionTestUtils.setField(selectorManager, "selectorTypes", selectorTypes);
        try (MockedStatic<NacosServiceLoader> mockedLoader =
            mockStatic(NacosServiceLoader.class)) {
            mockedLoader.when(() -> NacosServiceLoader.load(Selector.class))
                .thenReturn(Collections.singletonList(new NoDefaultConstructorSelector("broken")));
            
            ReflectionTestUtils.invokeMethod(selectorManager, "initSelectorTypes");
        }
        
        assertTrue(selectorTypes.isEmpty());
    }
    
    @Test
    void testParseSelectorThrowsWhenParseFailed() {
        Map<String, Class<? extends Selector>> selectorTypes = new HashMap<>();
        selectorTypes.put("mock", MockSelector.class);
        ReflectionTestUtils.setField(selectorManager, "selectorTypes", selectorTypes);
        
        NacosException exception = assertThrows(NacosException.class,
            () -> selectorManager.parseSelector("mock", "invalid"));
        
        assertEquals(NacosException.SERVER_ERROR, exception.getErrCode());
    }
    
    @Test
    void testSelect() throws NacosException {
        Selector selector = selectorManager.parseSelector("mock", "key=value");
        Instance instance = new Instance();
        instance.setIp("2.2.2.2");
        List<Instance> providers = Collections.singletonList(instance);
        
        List<Instance> instances0 = selectorManager.select(selector, "1.1.1.1", providers);
        assertEquals(1, instances0.size());
        assertEquals("2.2.2.2", instances0.get(0).getIp());
        
        // test json serial for Selector
        Serializer serializer0 = SerializeFactory.getSerializer("JSON");
        byte[] bytes = serializer0.serialize(selector);
        Selector jsonSelector = serializer0.deserialize(bytes, Selector.class);
        
        List<Instance> instances1 = selectorManager.select(jsonSelector, "1.1.1.1", providers);
        assertEquals(1, instances1.size());
        assertEquals("2.2.2.2", instances1.get(0).getIp());
        
        // test hessian serial for Selector
        Serializer serializer1 = SerializeFactory.getDefault();
        byte[] bytes1 = serializer1.serialize(selector);
        Selector hessianSelector = serializer1.deserialize(bytes1);
        
        List<Instance> instances2 = selectorManager.select(hessianSelector, "1.1.1.1", providers);
        assertEquals(1, instances2.size());
        assertEquals("2.2.2.2", instances2.get(0).getIp());
    }
    
    @Test
    void testSelectReturnsProvidersWhenSelectorIsNullOrContextBuilderMissing()
        throws NacosException {
        Instance instance = new Instance();
        instance.setIp("2.2.2.2");
        List<Instance> providers = Collections.singletonList(instance);
        Selector selector = new MockSelector().parse("key=value");
        SelectorManager emptySelectorManager = new SelectorManager();
        
        assertSame(providers, selectorManager.select(null, "1.1.1.1", providers));
        assertSame(providers, emptySelectorManager.select(selector, "1.1.1.1", providers));
    }
    
    @Test
    void testSelectReturnsProvidersWhenContextBuilderThrows() {
        Instance instance = new Instance();
        instance.setIp("2.2.2.2");
        List<Instance> providers = Collections.singletonList(instance);
        Selector selector = Mockito.mock(Selector.class);
        SelectorContextBuilder contextBuilder = Mockito.mock(SelectorContextBuilder.class);
        Map<String, SelectorContextBuilder> contextBuilders = new HashMap<>();
        contextBuilders.put("broken", contextBuilder);
        ReflectionTestUtils.setField(selectorManager, "contextBuilders", contextBuilders);
        Mockito.when(selector.getContextType()).thenReturn("broken");
        Mockito.when(contextBuilder.build(Mockito.anyString(), Mockito.anyList()))
            .thenThrow(new RuntimeException("broken"));
        
        List<Instance> result = selectorManager.select(selector, "1.1.1.1", providers);
        
        assertSame(providers, result);
    }
    
    private static class NoDefaultConstructorSelector implements Selector<Object, Object, Object> {
        
        private static final long serialVersionUID = -240945908940366348L;
        
        private final String type;
        
        NoDefaultConstructorSelector(String type) {
            this.type = type;
        }
        
        @Override
        public Selector<Object, Object, Object> parse(Object expression) {
            return this;
        }
        
        @Override
        public Object select(Object context) {
            return context;
        }
        
        @Override
        public String getType() {
            return type;
        }
        
        @Override
        public String getContextType() {
            return "broken";
        }
    }
}
