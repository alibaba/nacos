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
import com.alibaba.nacos.api.naming.selector.NamingResult;
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
        int total = random.nextInt(32) + 1;
        int health = random.nextInt(total);
        
        NamingContext namingContext = getMockNamingContext(total, health);
        NamingResult result = namingSelector.select(namingContext);
        
        assertEquals(health, result.getResult().size());
        result.getResult().forEach(ins -> assertTrue(ins.isHealthy()));
    }
    
    private NamingContext getMockNamingContext(int total, int health) {
        NamingContext namingContext = mock(NamingContext.class);
        when(namingContext.getInstances()).thenReturn(getInstance(total, health));
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
