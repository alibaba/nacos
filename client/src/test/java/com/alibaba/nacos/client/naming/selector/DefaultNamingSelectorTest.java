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

package com.alibaba.nacos.client.naming.selector;

import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.selector.NamingContext;
import com.alibaba.nacos.client.naming.listener.NamingChangeEvent;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class DefaultNamingSelectorTest {

    @Test
    public void testSelect() {
        DefaultNamingSelector namingSelector = new DefaultNamingSelector(Instance::isHealthy);
        Random random = new Random();
        int[] size = new int[8];
        for (int i = 0; i < size.length; i++) {
            if (i % 2 == 0) {
                size[i] = random.nextInt(32) + 1;
            } else {
                size[i] = random.nextInt(size[i - 1]);
            }
        }

        NamingContext namingContext = getMockNamingContext(size);
        NamingChangeEvent namingEvent = (NamingChangeEvent) namingSelector.select(namingContext);

        assertEquals(size[1], namingEvent.getInstances().size());
        assertEquals(size[3], namingEvent.getAddedInstances().size());
        assertEquals(size[5], namingEvent.getRemovedInstances().size());
        assertEquals(size[7], namingEvent.getModifiedInstances().size());

        namingEvent.getInstances().forEach(ins -> assertTrue(ins.isHealthy()));
        namingEvent.getAddedInstances().forEach(ins -> assertTrue(ins.isHealthy()));
        namingEvent.getRemovedInstances().forEach(ins -> assertTrue(ins.isHealthy()));
        namingEvent.getModifiedInstances().forEach(ins -> assertTrue(ins.isHealthy()));
    }

    private NamingContext getMockNamingContext(int[] size) {
        NamingContext namingContext = mock(NamingContext.class);
        when(namingContext.getCurrentInstances()).thenReturn(getInstance(size[0], size[1]));
        when(namingContext.getAddedInstances()).thenReturn(getInstance(size[2], size[3]));
        when(namingContext.getRemovedInstances()).thenReturn(getInstance(size[4], size[5]));
        when(namingContext.getModifiedInstances()).thenReturn(getInstance(size[6], size[7]));
        return namingContext;
    }

    private List<Instance> getInstance(int total, int health) {
        List<Instance> list = new ArrayList<>(total);
        for (int i = 0; i < total; i++) {
            Instance instance = new Instance();
            instance.setHealthy(false);
            list.add(instance);
        }

        for (int i = 0; i < health; i++) {
            list.get(i).setHealthy(true);
        }

        return list;
    }
}
