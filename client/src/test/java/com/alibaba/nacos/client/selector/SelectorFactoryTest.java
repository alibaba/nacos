/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.client.selector;

import com.alibaba.nacos.api.naming.listener.NamingEvent;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.selector.NamingContext;
import com.alibaba.nacos.api.naming.selector.NamingSelector;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SelectorFactoryTest {

    @Test
    public void testNewClusterSelector1() {
        Instance ins1 = new Instance();
        ins1.setClusterName("a");
        Instance ins2 = new Instance();
        ins2.setClusterName("b");
        Instance ins3 = new Instance();
        ins3.setClusterName("c");

        NamingContext namingContext = mock(NamingContext.class);
        when(namingContext.getCurrentInstances()).thenReturn(Arrays.asList(ins1, ins2, ins3));

        NamingSelector namingSelector1 = SelectorFactory.newClusterSelector(Collections.singletonList("a"));
        NamingEvent namingEvent1 = namingSelector1.select(namingContext);
        assertEquals("a", namingEvent1.getInstances().get(0).getClusterName());

        NamingSelector namingSelector2 = SelectorFactory.newClusterSelector(Collections.emptyList());
        NamingEvent namingEvent2 = namingSelector2.select(namingContext);
        assertEquals(3, namingEvent2.getInstances().size());
    }

    @Test
    public void testNewClusterSelector2() {
        NamingSelector namingSelector1 = SelectorFactory.newClusterSelector(Arrays.asList("a", "b", "c"));
        NamingSelector namingSelector2 = SelectorFactory.newClusterSelector(Arrays.asList("c", "b", "a"));
        NamingSelector namingSelector3 = SelectorFactory.newClusterSelector(Arrays.asList("a", "b", "c", "c"));
        NamingSelector namingSelector4 = SelectorFactory.newClusterSelector(Arrays.asList("d", "e"));
        assertEquals(namingSelector1, namingSelector2);
        assertEquals(namingSelector1, namingSelector3);
        assertNotEquals(namingSelector1, namingSelector4);
    }
}
