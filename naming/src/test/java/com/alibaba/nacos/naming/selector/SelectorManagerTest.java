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
import com.alibaba.nacos.consistency.SerializeFactory;
import com.alibaba.nacos.consistency.Serializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
