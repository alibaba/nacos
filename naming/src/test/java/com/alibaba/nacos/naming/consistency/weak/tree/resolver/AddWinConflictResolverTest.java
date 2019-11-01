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
import com.alibaba.nacos.naming.consistency.weak.Operation;
import com.alibaba.nacos.naming.consistency.weak.OperationType;
import com.alibaba.nacos.naming.core.Instance;
import com.alibaba.nacos.naming.core.Instances;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.UUID;

/**
 * @author lostcharlie
 */
public class AddWinConflictResolverTest {
    @Test
    public void testTwoOrderedAddOperations() throws NacosException {
        // Two add operations which are not in a conflicted state.
        long maxTimeDifference = 1000;
        AddWinConflictResolver addWinConflictResolver = new AddWinConflictResolver(maxTimeDifference);

        long timestampOne = System.nanoTime();
        long timestampTwo = timestampOne + 2000L;
        String namespaceId = UUID.randomUUID().toString();
        String serviceName = UUID.randomUUID().toString();
        String key = KeyBuilder.buildInstanceListKey(namespaceId, serviceName, true);

        Instance one = new Instance("192.168.0.1", 8888);
        Instances targetValueOne = new Instances();
        targetValueOne.setInstanceList(new ArrayList<>());
        targetValueOne.getInstanceList().add(one);
        Operation operationOne = new Operation();
        operationOne.setOperationType(OperationType.ADD_INSTANCE);
        operationOne.setTargetValue(targetValueOne);
        operationOne.getTimestamp().set(timestampOne);

        Datum current = new Datum();
        current.key = key;
        current.value = new Instances();
        current.timestamp.set(0L);

        addWinConflictResolver.merge(current, operationOne);
        Assert.assertNotNull(current.value);
        Assert.assertEquals(1, ((Instances) current.value).getInstanceList().size());
        Assert.assertTrue(((Instances) current.value).getInstanceList().contains(one));

        Instance two = new Instance("192.168.0.2", 8889);
        Instances targetValueTwo = new Instances();
        targetValueTwo.setInstanceList(new ArrayList<>());
        targetValueTwo.getInstanceList().add(two);
        Operation operationTwo = new Operation();
        operationTwo.setOperationType(OperationType.ADD_INSTANCE);
        operationTwo.setTargetValue(targetValueTwo);
        operationTwo.getTimestamp().set(timestampTwo);

        addWinConflictResolver.merge(current, operationTwo);
        Assert.assertNotNull(current.value);
        Assert.assertEquals(2, ((Instances) current.value).getInstanceList().size());
        Assert.assertTrue(((Instances) current.value).getInstanceList().contains(one));
        Assert.assertTrue(((Instances) current.value).getInstanceList().contains(two));
    }

    @Test
    public void testTwoConflictAddOperations() throws NacosException {
        // Two add operations which are in a conflicted state.
        long maxTimeDifference = 1000;
        AddWinConflictResolver addWinConflictResolver = new AddWinConflictResolver(maxTimeDifference);

        long timestampOne = System.nanoTime();
        long timestampTwo = timestampOne;
        String namespaceId = UUID.randomUUID().toString();
        String serviceName = UUID.randomUUID().toString();
        String key = KeyBuilder.buildInstanceListKey(namespaceId, serviceName, true);

        Instance one = new Instance("192.168.0.1", 8888);
        Instances targetValueOne = new Instances();
        targetValueOne.setInstanceList(new ArrayList<>());
        targetValueOne.getInstanceList().add(one);
        Operation operationOne = new Operation();
        operationOne.setOperationType(OperationType.ADD_INSTANCE);
        operationOne.setTargetValue(targetValueOne);
        operationOne.getTimestamp().set(timestampOne);

        Datum current = new Datum();
        current.key = key;
        current.value = new Instances();
        current.timestamp.set(0L);

        addWinConflictResolver.merge(current, operationOne);
        Assert.assertNotNull(current.value);
        Assert.assertEquals(1, ((Instances) current.value).getInstanceList().size());
        Assert.assertTrue(((Instances) current.value).getInstanceList().contains(one));

        Instance two = new Instance("192.168.0.2", 8889);
        Instances targetValueTwo = new Instances();
        targetValueTwo.setInstanceList(new ArrayList<>());
        targetValueTwo.getInstanceList().add(two);
        Operation operationTwo = new Operation();
        operationTwo.setOperationType(OperationType.ADD_INSTANCE);
        operationTwo.setTargetValue(targetValueTwo);
        operationTwo.getTimestamp().set(timestampTwo);

        addWinConflictResolver.merge(current, operationTwo);
        Assert.assertNotNull(current.value);
        Assert.assertEquals(2, ((Instances) current.value).getInstanceList().size());
        Assert.assertTrue(((Instances) current.value).getInstanceList().contains(one));
        Assert.assertTrue(((Instances) current.value).getInstanceList().contains(two));
    }

    @Test
    public void testTwoConflictRemoveOperations() throws NacosException {
        String namespaceId = UUID.randomUUID().toString();
        String serviceName = UUID.randomUUID().toString();
        String key = KeyBuilder.buildInstanceListKey(namespaceId, serviceName, true);

        Instances initialInstances = new Instances();
        initialInstances.getInstanceList().add(new Instance("192.168.0.1", 8888));
        initialInstances.getInstanceList().add(new Instance("192.168.0.2", 8889));

        Datum current = new Datum();
        current.key = key;
        current.value = initialInstances;
        current.timestamp.set(0L);
        Assert.assertNotNull(current.value);
        Assert.assertEquals(2, ((Instances) current.value).getInstanceList().size());
        Assert.assertTrue(((Instances) current.value).getInstanceList().contains(new Instance("192.168.0.1", 8888)));
        Assert.assertTrue(((Instances) current.value).getInstanceList().contains(new Instance("192.168.0.2", 8889)));

        // Two remove operations which are in a conflicted state.
        long maxTimeDifference = 1000;
        AddWinConflictResolver addWinConflictResolver = new AddWinConflictResolver(maxTimeDifference);

        long timestampOne = System.nanoTime();
        long timestampTwo = timestampOne;

        Instances targetValueOne = new Instances();
        targetValueOne.setInstanceList(new ArrayList<>());
        targetValueOne.getInstanceList().add(new Instance("192.168.0.1", 8888));
        Operation operationOne = new Operation();
        operationOne.setOperationType(OperationType.REMOVE_INSTANCE);
        operationOne.setTargetValue(targetValueOne);
        operationOne.getTimestamp().set(timestampOne);

        addWinConflictResolver.merge(current, operationOne);
        Assert.assertNotNull(current.value);
        Assert.assertEquals(1, ((Instances) current.value).getInstanceList().size());
        Assert.assertFalse(((Instances) current.value).getInstanceList().contains(new Instance("192.168.0.1", 8888)));
        Assert.assertTrue(((Instances) current.value).getInstanceList().contains(new Instance("192.168.0.2", 8889)));

        // According to the logic of AddWinConflictResolver, the latter removal operation will not be applied.
        Instances targetValueTwo = new Instances();
        targetValueTwo.setInstanceList(new ArrayList<>());
        targetValueTwo.getInstanceList().add(new Instance("192.168.0.2", 8889));
        Operation operationTwo = new Operation();
        operationTwo.setOperationType(OperationType.REMOVE_INSTANCE);
        operationTwo.setTargetValue(targetValueTwo);
        operationTwo.getTimestamp().set(timestampTwo);

        addWinConflictResolver.merge(current, operationTwo);
        Assert.assertNotNull(current.value);
        Assert.assertEquals(1, ((Instances) current.value).getInstanceList().size());
        Assert.assertFalse(((Instances) current.value).getInstanceList().contains(new Instance("192.168.0.1", 8888)));
        Assert.assertTrue(((Instances) current.value).getInstanceList().contains(new Instance("192.168.0.2", 8889)));
    }

}
