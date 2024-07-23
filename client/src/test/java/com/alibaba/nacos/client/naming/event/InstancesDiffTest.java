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

package com.alibaba.nacos.client.naming.event;

import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.builder.InstanceBuilder;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class InstancesDiffTest {
    
    private static List<Instance> getInstanceList(int count) {
        ArrayList<Instance> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            list.add(new Instance());
        }
        return list;
    }
    
    @Test
    public void testGetDiff() {
        String serviceName = "testService";
        Instance addedIns = InstanceBuilder.newBuilder().setServiceName(serviceName).setClusterName("a").build();
        Instance removedIns = InstanceBuilder.newBuilder().setServiceName(serviceName).setClusterName("b").build();
        Instance modifiedIns = InstanceBuilder.newBuilder().setServiceName(serviceName).setClusterName("c").build();
        
        InstancesDiff instancesDiff = new InstancesDiff();
        instancesDiff.setAddedInstances(Collections.singletonList(addedIns));
        instancesDiff.setRemovedInstances(Collections.singletonList(removedIns));
        instancesDiff.setModifiedInstances(Collections.singletonList(modifiedIns));
        
        assertTrue(instancesDiff.hasDifferent());
        assertTrue(instancesDiff.isAdded());
        assertTrue(instancesDiff.isRemoved());
        assertTrue(instancesDiff.isModified());
        assertEquals(addedIns, instancesDiff.getAddedInstances().get(0));
        assertEquals(removedIns, instancesDiff.getRemovedInstances().get(0));
        assertEquals(modifiedIns, instancesDiff.getModifiedInstances().get(0));
    }
    
    @Test
    public void testWithFullConstructor() {
        Random random = new Random();
        int addedCount = random.nextInt(32) + 1;
        int removedCount = random.nextInt(32) + 1;
        int modifiedCount = random.nextInt(32) + 1;
        InstancesDiff instancesDiff = new InstancesDiff(getInstanceList(addedCount), getInstanceList(removedCount),
                getInstanceList(modifiedCount));
        
        assertTrue(instancesDiff.hasDifferent());
        assertTrue(instancesDiff.isAdded());
        assertTrue(instancesDiff.isRemoved());
        assertTrue(instancesDiff.isModified());
        assertEquals(addedCount, instancesDiff.getAddedInstances().size());
        assertEquals(removedCount, instancesDiff.getRemovedInstances().size());
        assertEquals(modifiedCount, instancesDiff.getModifiedInstances().size());
        instancesDiff.getAddedInstances().clear();
        instancesDiff.getRemovedInstances().clear();
        instancesDiff.getModifiedInstances().clear();
        assertFalse(instancesDiff.hasDifferent());
        assertFalse(instancesDiff.hasDifferent());
        assertFalse(instancesDiff.isAdded());
        assertFalse(instancesDiff.isRemoved());
        assertFalse(instancesDiff.isModified());
    }
    
    @Test
    public void testWithNoConstructor() {
        Random random = new Random();
        int addedCount = random.nextInt(32) + 1;
        int removedCount = random.nextInt(32) + 1;
        int modifiedCount = random.nextInt(32) + 1;
        InstancesDiff instancesDiff = new InstancesDiff();
        instancesDiff.setAddedInstances(getInstanceList(addedCount));
        instancesDiff.setRemovedInstances(getInstanceList(removedCount));
        instancesDiff.setModifiedInstances(getInstanceList(modifiedCount));
        
        assertTrue(instancesDiff.hasDifferent());
        assertEquals(addedCount, instancesDiff.getAddedInstances().size());
        assertEquals(removedCount, instancesDiff.getRemovedInstances().size());
        assertEquals(modifiedCount, instancesDiff.getModifiedInstances().size());
        instancesDiff.getAddedInstances().clear();
        instancesDiff.getRemovedInstances().clear();
        instancesDiff.getModifiedInstances().clear();
        assertFalse(instancesDiff.hasDifferent());
        assertFalse(instancesDiff.isAdded());
        assertFalse(instancesDiff.isRemoved());
        assertFalse(instancesDiff.isModified());
    }
}
