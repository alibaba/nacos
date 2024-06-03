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
import com.alibaba.nacos.api.naming.selector.NamingSelector;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class NamingSelectorFactoryTest {
    
    @Test
    public void testNewClusterSelector1() {
        Instance ins1 = new Instance();
        ins1.setClusterName("a");
        Instance ins2 = new Instance();
        ins2.setClusterName("b");
        Instance ins3 = new Instance();
        ins3.setClusterName("c");
        
        NamingContext namingContext = mock(NamingContext.class);
        when(namingContext.getInstances()).thenReturn(Arrays.asList(ins1, ins2, ins3));
        
        NamingSelector namingSelector1 = NamingSelectorFactory.newClusterSelector(Collections.singletonList("a"));
        NamingResult result1 = namingSelector1.select(namingContext);
        assertEquals("a", result1.getResult().get(0).getClusterName());
        
        NamingSelector namingSelector2 = NamingSelectorFactory.newClusterSelector(Collections.emptyList());
        NamingResult result2 = namingSelector2.select(namingContext);
        assertEquals(3, result2.getResult().size());
    }
    
    @Test
    public void testNewClusterSelector2() {
        NamingSelector namingSelector1 = NamingSelectorFactory.newClusterSelector(Arrays.asList("a", "b", "c"));
        NamingSelector namingSelector2 = NamingSelectorFactory.newClusterSelector(Arrays.asList("c", "b", "a"));
        NamingSelector namingSelector3 = NamingSelectorFactory.newClusterSelector(Arrays.asList("a", "b", "c", "c"));
        NamingSelector namingSelector4 = NamingSelectorFactory.newClusterSelector(Arrays.asList("d", "e"));
        
        assertEquals(namingSelector1, namingSelector2);
        assertEquals(namingSelector1, namingSelector3);
        assertNotEquals(namingSelector1, namingSelector4);
    }
    
    @Test
    public void testNewIpSelector() {
        Instance ins1 = new Instance();
        ins1.setIp("172.18.137.120");
        Instance ins2 = new Instance();
        ins2.setIp("172.18.137.121");
        Instance ins3 = new Instance();
        ins3.setIp("172.18.136.111");
        
        NamingContext namingContext = mock(NamingContext.class);
        when(namingContext.getInstances()).thenReturn(Arrays.asList(ins1, ins2, ins3));
        
        NamingSelector ipSelector = NamingSelectorFactory.newIpSelector("^172\\.18\\.137.*");
        NamingResult result = ipSelector.select(namingContext);
        List<Instance> list = result.getResult();
        
        assertEquals(2, list.size());
        assertEquals(ins1.getIp(), list.get(0).getIp());
        assertEquals(ins2.getIp(), list.get(1).getIp());
    }
    
    @Test
    public void testNewMetadataSelector() {
        Instance ins1 = new Instance();
        ins1.addMetadata("a", "1");
        ins1.addMetadata("b", "2");
        Instance ins2 = new Instance();
        ins2.addMetadata("a", "1");
        Instance ins3 = new Instance();
        ins3.addMetadata("b", "2");
        
        NamingContext namingContext = mock(NamingContext.class);
        when(namingContext.getInstances()).thenReturn(Arrays.asList(ins1, ins2, ins3));
        
        NamingSelector metadataSelector = NamingSelectorFactory.newMetadataSelector(new HashMap() {
            {
                put("a", "1");
                put("b", "2");
            }
        });
        List<Instance> result = metadataSelector.select(namingContext).getResult();
        
        assertEquals(1, result.size());
        assertEquals(ins1, result.get(0));
    }
    
    @Test
    public void testNewMetadataSelector2() {
        Instance ins1 = new Instance();
        ins1.addMetadata("a", "1");
        ins1.addMetadata("c", "3");
        Instance ins2 = new Instance();
        ins2.addMetadata("b", "2");
        Instance ins3 = new Instance();
        ins3.addMetadata("c", "3");
        
        NamingContext namingContext = mock(NamingContext.class);
        when(namingContext.getInstances()).thenReturn(Arrays.asList(ins1, ins2, ins3));
        
        NamingSelector metadataSelector = NamingSelectorFactory.newMetadataSelector(new HashMap() {
            {
                put("a", "1");
                put("b", "2");
            }
        }, true);
        List<Instance> result = metadataSelector.select(namingContext).getResult();
        
        assertEquals(2, result.size());
        assertEquals(ins1, result.get(0));
        assertEquals(ins2, result.get(1));
    }
    
    @Test
    public void testHealthSelector() {
        Instance ins1 = new Instance();
        Instance ins2 = new Instance();
        Instance ins3 = new Instance();
        ins3.setHealthy(false);
        
        NamingContext namingContext = mock(NamingContext.class);
        when(namingContext.getInstances()).thenReturn(Arrays.asList(ins1, ins2, ins3));
        
        List<Instance> result = NamingSelectorFactory.HEALTHY_SELECTOR.select(namingContext).getResult();
        
        assertEquals(2, result.size());
        assertTrue(result.contains(ins1));
        assertTrue(result.contains(ins2));
        assertTrue(result.get(0).isHealthy());
        assertTrue(result.get(1).isHealthy());
    }
    
    @Test
    public void testEmptySelector() {
        Instance ins1 = new Instance();
        Instance ins2 = new Instance();
        Instance ins3 = new Instance();
        
        NamingContext namingContext = mock(NamingContext.class);
        when(namingContext.getInstances()).thenReturn(Arrays.asList(ins1, ins2, ins3));
        
        List<Instance> result = NamingSelectorFactory.EMPTY_SELECTOR.select(namingContext).getResult();
        
        assertEquals(3, result.size());
        assertTrue(result.contains(ins1));
        assertTrue(result.contains(ins2));
        assertTrue(result.contains(ins3));
    }
}
