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
import com.alibaba.nacos.naming.core.Instances;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.UUID;

/**
 * @author lostcharlie
 */
public class SimpleConsistencyServiceTest {
    @Test
    public void testGet() throws NacosException {
        SimpleDataStore simpleDataStore = new SimpleDataStore();
        SimpleConsistencyServiceImpl simpleConsistencyService = new SimpleConsistencyServiceImpl(null);
        ReflectionTestUtils.setField(simpleConsistencyService, "dataStore", simpleDataStore);

        String namespaceId = UUID.randomUUID().toString();
        String serviceName = UUID.randomUUID().toString();
        String key = KeyBuilder.buildInstanceListKey(namespaceId, serviceName, true);
        Instances value = new Instances();
        value.setInstanceList(new ArrayList<>());

        simpleConsistencyService.put(key, value);
        Assert.assertNotNull(simpleConsistencyService.get(key));
    }

    @Test
    public void testRemove() throws NacosException {
        SimpleDataStore simpleDataStore = new SimpleDataStore();
        SimpleConsistencyServiceImpl simpleConsistencyService = new SimpleConsistencyServiceImpl(null);
        ReflectionTestUtils.setField(simpleConsistencyService, "dataStore", simpleDataStore);

        String namespaceId = UUID.randomUUID().toString();
        String serviceName = UUID.randomUUID().toString();
        String key = KeyBuilder.buildInstanceListKey(namespaceId, serviceName, true);
        Instances value = new Instances();
        value.setInstanceList(new ArrayList<>());

        simpleConsistencyService.put(key, value);
        Assert.assertNotNull(simpleConsistencyService.get(key));
        simpleConsistencyService.remove(key);
        Assert.assertNull(simpleConsistencyService.get(key));
    }
}
