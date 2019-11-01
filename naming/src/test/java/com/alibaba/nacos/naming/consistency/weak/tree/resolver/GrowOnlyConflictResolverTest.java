/*
 * Copyright 1999-2019 Alibaba Group Holding Ltd.
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
package com.alibaba.nacos.naming.consistency.weak.tree.resolver;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.naming.consistency.Datum;
import com.alibaba.nacos.naming.consistency.KeyBuilder;
import com.alibaba.nacos.naming.core.Instance;
import com.alibaba.nacos.naming.core.Instances;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.UUID;

/**
 * @author lostcharlie
 */
public class GrowOnlyConflictResolverTest {
    @Test
    public void testMergeTwoOrderedStates() throws NacosException {
        // Merge two states which are not conflicted.
        long maxTimeDifference = 1000;
        GrowOnlyConflictResolver growOnlyConflictResolver = new GrowOnlyConflictResolver(maxTimeDifference);

        long currentTimestamp = System.nanoTime();
        long targetTimestamp = currentTimestamp + 2000L;
        String namespaceId = UUID.randomUUID().toString();
        String serviceName = UUID.randomUUID().toString();
        String key = KeyBuilder.buildInstanceListKey(namespaceId, serviceName, true);

        Instances currentState = new Instances();
        currentState.setInstanceList(new ArrayList<>());
        currentState.getInstanceList().add(new Instance("192.168.0.1", 8888));
        Datum current = new Datum();
        current.key = key;
        current.value = currentState;
        current.timestamp.set(currentTimestamp);

        Assert.assertNotNull(current.value);
        Assert.assertEquals(1, ((Instances) current.value).getInstanceList().size());
        Assert.assertTrue(((Instances) current.value).getInstanceList().contains(new Instance("192.168.0.1", 8888)));

        Instances targetState = new Instances();
        targetState.setInstanceList(new ArrayList<>());
        targetState.getInstanceList().add(new Instance("192.168.0.2", 8889));
        Datum target = new Datum();
        target.key = key;
        target.value = targetState;
        target.timestamp.set(targetTimestamp);

        growOnlyConflictResolver.merge(current, target);

        // It should accept target state and update timestamp
        Assert.assertNotNull(current.value);
        Assert.assertEquals(targetTimestamp, current.timestamp.get());
        Assert.assertEquals(1, ((Instances) current.value).getInstanceList().size());
        Assert.assertTrue(((Instances) current.value).getInstanceList().contains(new Instance("192.168.0.2", 8889)));
    }

    @Test
    public void testMergeTwoConflictedStatesInAscendingOrder() throws NacosException {
        // Merge two states which are conflicted and timestamps are in an ascending order.
        long maxTimeDifference = 1000;
        GrowOnlyConflictResolver growOnlyConflictResolver = new GrowOnlyConflictResolver(maxTimeDifference);

        long currentTimestamp = System.nanoTime();
        long targetTimestamp = currentTimestamp + 1L;
        String namespaceId = UUID.randomUUID().toString();
        String serviceName = UUID.randomUUID().toString();
        String key = KeyBuilder.buildInstanceListKey(namespaceId, serviceName, true);

        Instances currentState = new Instances();
        currentState.setInstanceList(new ArrayList<>());
        currentState.getInstanceList().add(new Instance("192.168.0.1", 8888));
        Datum current = new Datum();
        current.key = key;
        current.value = currentState;
        current.timestamp.set(currentTimestamp);

        Assert.assertNotNull(current.value);
        Assert.assertEquals(1, ((Instances) current.value).getInstanceList().size());
        Assert.assertTrue(((Instances) current.value).getInstanceList().contains(new Instance("192.168.0.1", 8888)));

        Instances targetState = new Instances();
        targetState.setInstanceList(new ArrayList<>());
        targetState.getInstanceList().add(new Instance("192.168.0.2", 8889));
        Datum target = new Datum();
        target.key = key;
        target.value = targetState;
        target.timestamp.set(targetTimestamp);

        growOnlyConflictResolver.merge(current, target);

        // It should merge two states and update timestamp
        Assert.assertNotNull(current.value);
        Assert.assertEquals(targetTimestamp, current.timestamp.get());
        Assert.assertEquals(2, ((Instances) current.value).getInstanceList().size());
        Assert.assertTrue(((Instances) current.value).getInstanceList().contains(new Instance("192.168.0.1", 8888)));
        Assert.assertTrue(((Instances) current.value).getInstanceList().contains(new Instance("192.168.0.2", 8889)));
    }

    @Test
    public void testMergeTwoConflictedStatesInDescendingOrder() throws NacosException {
        // Merge two states which are conflicted and timestamps are in a descending order.
        long maxTimeDifference = 1000;
        GrowOnlyConflictResolver growOnlyConflictResolver = new GrowOnlyConflictResolver(maxTimeDifference);

        long currentTimestamp = System.nanoTime();
        long targetTimestamp = currentTimestamp - 1L;
        String namespaceId = UUID.randomUUID().toString();
        String serviceName = UUID.randomUUID().toString();
        String key = KeyBuilder.buildInstanceListKey(namespaceId, serviceName, true);

        Instances currentState = new Instances();
        currentState.setInstanceList(new ArrayList<>());
        currentState.getInstanceList().add(new Instance("192.168.0.1", 8888));
        Datum current = new Datum();
        current.key = key;
        current.value = currentState;
        current.timestamp.set(currentTimestamp);

        Assert.assertNotNull(current.value);
        Assert.assertEquals(1, ((Instances) current.value).getInstanceList().size());
        Assert.assertTrue(((Instances) current.value).getInstanceList().contains(new Instance("192.168.0.1", 8888)));

        Instances targetState = new Instances();
        targetState.setInstanceList(new ArrayList<>());
        targetState.getInstanceList().add(new Instance("192.168.0.2", 8889));
        Datum target = new Datum();
        target.key = key;
        target.value = targetState;
        target.timestamp.set(targetTimestamp);

        growOnlyConflictResolver.merge(current, target);

        // It should merge two states and update timestamp
        Assert.assertNotNull(current.value);
        Assert.assertEquals(currentTimestamp, current.timestamp.get());
        Assert.assertEquals(2, ((Instances) current.value).getInstanceList().size());
        Assert.assertTrue(((Instances) current.value).getInstanceList().contains(new Instance("192.168.0.1", 8888)));
        Assert.assertTrue(((Instances) current.value).getInstanceList().contains(new Instance("192.168.0.2", 8889)));
    }
}
