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

import com.alibaba.nacos.api.cmdb.pojo.Entity;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.selector.Selector;
import com.alibaba.nacos.api.selector.context.CmdbContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link LabelSelector} unit test.
 *
 * @author chenglu
 * @date 2021-07-16 17:41
 */
class LabelSelectorTest {
    
    private SelectorManager selectorManager;
    
    @BeforeEach
    void setUp() {
        selectorManager = new SelectorManager();
        selectorManager.init();
    }
    
    @Test
    void testParseSelector() throws NacosException {
        Selector selector = selectorManager.parseSelector("label",
            "CONSUMER.label.A=PROVIDER.label.A &CONSUMER.label.B=PROVIDER.label.B");
        assertTrue(selector instanceof LabelSelector);
        
        LabelSelector labelSelector = (LabelSelector) selector;
        assertEquals(2, labelSelector.getLabels().size());
        assertTrue(labelSelector.getLabels().contains("A"));
        assertTrue(labelSelector.getLabels().contains("B"));
    }
    
    @Test
    void testSelectReturnsAllProvidersWhenLabelsEmpty() {
        LabelSelector<Instance> labelSelector = new LabelSelector<>();
        List<CmdbContext.CmdbInstance<Instance>> providers = Arrays.asList(
            cmdbInstance("1.1.1.1", labels("zone", "A")), cmdbInstance("2.2.2.2",
                labels("zone", "B")));
        CmdbContext<Instance> context =
            cmdbContext(cmdbInstance("0.0.0.0", labels("zone", "A")), providers);
        
        List<Instance> result = labelSelector.select(context);
        
        assertEquals(2, result.size());
        assertEquals("1.1.1.1", result.get(0).getIp());
        assertEquals("2.2.2.2", result.get(1).getIp());
    }
    
    @Test
    void testSelectReturnsMatchedProviders() {
        LabelSelector<Instance> labelSelector = new LabelSelector<>();
        labelSelector.setLabels(Collections.singleton("zone"));
        List<CmdbContext.CmdbInstance<Instance>> providers = Arrays.asList(
            cmdbInstance("1.1.1.1", labels("zone", "A")),
            cmdbInstance("2.2.2.2", labels("zone", "B")));
        CmdbContext<Instance> context =
            cmdbContext(cmdbInstance("0.0.0.0", labels("zone", "A")), providers);
        
        List<Instance> result = labelSelector.select(context);
        
        assertEquals(1, result.size());
        assertEquals("1.1.1.1", result.get(0).getIp());
    }
    
    @Test
    void testSelectFallsBackToAllProvidersWhenNoProviderMatched() {
        LabelSelector<Instance> labelSelector = new LabelSelector<>();
        labelSelector.setLabels(Collections.singleton("zone"));
        CmdbContext.CmdbInstance<Instance> providerWithoutEntity =
            cmdbInstance("1.1.1.1", labels("zone", "A"));
        providerWithoutEntity.setEntity(null);
        List<CmdbContext.CmdbInstance<Instance>> providers = Arrays.asList(providerWithoutEntity,
            cmdbInstance("2.2.2.2", null));
        CmdbContext<Instance> context =
            cmdbContext(cmdbInstance("0.0.0.0", labels("zone", "A")), providers);
        
        List<Instance> result = labelSelector.select(context);
        
        assertEquals(2, result.size());
    }
    
    @Test
    void testSelectFallsBackToAllProvidersWhenConsumerLabelBlank() {
        LabelSelector<Instance> labelSelector = new LabelSelector<>();
        labelSelector.setLabels(Collections.singleton("zone"));
        List<CmdbContext.CmdbInstance<Instance>> providers = Collections
            .singletonList(cmdbInstance("1.1.1.1", labels("zone", "A")));
        CmdbContext<Instance> context =
            cmdbContext(cmdbInstance("0.0.0.0", labels("zone", "")), providers);
        
        List<Instance> result = labelSelector.select(context);
        
        assertEquals(1, result.size());
        assertEquals("1.1.1.1", result.get(0).getIp());
    }
    
    @Test
    void testGetType() {
        LabelSelector<Instance> labelSelector = new LabelSelector<>();
        
        assertEquals("label", labelSelector.getType());
    }
    
    private CmdbContext<Instance> cmdbContext(CmdbContext.CmdbInstance<Instance> consumer,
        List<CmdbContext.CmdbInstance<Instance>> providers) {
        CmdbContext<Instance> result = new CmdbContext<>();
        result.setConsumer(consumer);
        result.setProviders(providers);
        return result;
    }
    
    private CmdbContext.CmdbInstance<Instance> cmdbInstance(String ip, Map<String, String> labels) {
        Instance instance = new Instance();
        instance.setIp(ip);
        Entity entity = new Entity();
        entity.setLabels(labels);
        CmdbContext.CmdbInstance<Instance> result = new CmdbContext.CmdbInstance<>();
        result.setInstance(instance);
        result.setEntity(entity);
        return result;
    }
    
    private Map<String, String> labels(String key, String value) {
        Map<String, String> result = new HashMap<>();
        result.put(key, value);
        return result;
    }
}
