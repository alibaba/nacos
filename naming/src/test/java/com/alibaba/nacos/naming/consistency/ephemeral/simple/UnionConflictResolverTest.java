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
package com.alibaba.nacos.naming.consistency.ephemeral.simple;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.naming.consistency.KeyBuilder;
import com.alibaba.nacos.naming.consistency.weak.Operation;
import com.alibaba.nacos.naming.consistency.weak.OperationType;
import com.alibaba.nacos.naming.consistency.weak.tree.UnionConflictResolver;
import com.alibaba.nacos.naming.core.Instance;
import com.alibaba.nacos.naming.core.Instances;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.UUID;

/**
 * @author lostcharlie
 */
public class UnionConflictResolverTest {
    @Test
    public void testAddInstance() throws NacosException {
        // Two add operations which are not in a conflicted state.
        long timeDifference = 1000;
        SimpleDataStore simpleDataStore = new SimpleDataStore();
        UnionConflictResolver unionConflictResolver = new UnionConflictResolver(timeDifference);
        SimpleConsistencyServiceImpl simpleConsistencyService = new SimpleConsistencyServiceImpl(unionConflictResolver);
        ReflectionTestUtils.setField(simpleConsistencyService, "dataStore", simpleDataStore);

        Long realTimeOne = System.nanoTime();
        Long realTimeTwo = realTimeOne + 2000L;
        String namespaceId = UUID.randomUUID().toString();
        String serviceName = UUID.randomUUID().toString();
        boolean ephemeral = true;
        String key = KeyBuilder.buildInstanceListKey(namespaceId, serviceName, ephemeral);

        Instance one = new Instance("192.168.0.1", 8888);
        Instances targetValueOne = new Instances();
        targetValueOne.setInstanceList(new ArrayList<>());
        targetValueOne.getInstanceList().add(one);
        Operation operationOne = new Operation();
        operationOne.setOperationType(OperationType.ADD_INSTANCE);
        operationOne.setTargetValue(targetValueOne);
        operationOne.setRealTime(realTimeOne);

        simpleConsistencyService.performOperation(key, operationOne);
        Assert.assertNotNull(simpleConsistencyService.get(key));
        Assert.assertEquals(1, ((Instances) simpleConsistencyService.get(key).value).getInstanceList().size());
        Assert.assertTrue(((Instances) simpleConsistencyService.get(key).value).getInstanceList().contains(one));

        Instance two = new Instance("192.168.0.2", 8889);
        Instances targetValueTwo = new Instances();
        targetValueTwo.setInstanceList(new ArrayList<>());
        targetValueTwo.getInstanceList().add(two);
        Operation operationTwo = new Operation();
        operationTwo.setOperationType(OperationType.ADD_INSTANCE);
        operationTwo.setTargetValue(targetValueTwo);
        operationTwo.setRealTime(realTimeTwo);

        simpleConsistencyService.performOperation(key, operationTwo);
        Assert.assertNotNull(simpleConsistencyService.get(key));
        Assert.assertEquals(2, ((Instances) simpleConsistencyService.get(key).value).getInstanceList().size());
        Assert.assertTrue(((Instances) simpleConsistencyService.get(key).value).getInstanceList().contains(one));
        Assert.assertTrue(((Instances) simpleConsistencyService.get(key).value).getInstanceList().contains(two));

    }
}
