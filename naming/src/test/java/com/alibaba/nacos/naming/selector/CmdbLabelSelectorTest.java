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

import com.alibaba.nacos.api.naming.selector.Selector;
import com.alibaba.nacos.naming.core.Instance;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/**
 * {@link CmdbLabelSelector} unit test.
 *
 * @author chenglu
 * @date 2021-07-16 17:41
 */
public class CmdbLabelSelectorTest {
    
    private SelectorManager selectorManager;
    
    @Before
    public void setUp() {
        selectorManager = new SelectorManager();
        selectorManager.init();
    }
    
    @Test
    public void testParseSelector() {
        Selector selector = selectorManager.parseSelector("label", "CONSUMER.label.A=PROVIDER.label.A &CONSUMER.label.B=PROVIDER.label.B");
        Assert.assertTrue(selector instanceof CmdbLabelSelector);
    
        CmdbLabelSelector cmdbLabelSelector = (CmdbLabelSelector) selector;
        Assert.assertEquals(2, cmdbLabelSelector.getLabels().size());
        Assert.assertTrue(cmdbLabelSelector.getLabels().contains("A"));
        Assert.assertTrue(cmdbLabelSelector.getLabels().contains("B"));
    }
    
    @Test
    public void testSelect() {
        Selector selector = selectorManager.parseSelector("label", "CONSUMER.label.A=PROVIDER.label.A &CONSUMER.label.B=PROVIDER.label.B");
    
        Instance provider = new Instance();
        provider.setApp("aaa");
        provider.setIp("2.2.2.2");
        List<Instance> providers = Arrays.asList(provider);
        // selectorManager.select(selector, "1.1.1.1", providers);
    }
}
