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
import com.alibaba.nacos.api.selector.Selector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
        Selector selector = selectorManager.parseSelector("label", "CONSUMER.label.A=PROVIDER.label.A &CONSUMER.label.B=PROVIDER.label.B");
        assertTrue(selector instanceof LabelSelector);
        
        LabelSelector labelSelector = (LabelSelector) selector;
        assertEquals(2, labelSelector.getLabels().size());
        assertTrue(labelSelector.getLabels().contains("A"));
        assertTrue(labelSelector.getLabels().contains("B"));
    }
}
