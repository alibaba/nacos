/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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
package com.alibaba.nacos.naming.consistency.ephemeral.distro;

import com.alibaba.nacos.naming.BaseTest;
import com.alibaba.nacos.naming.cluster.transport.FastJsonSerializer;
import com.alibaba.nacos.naming.consistency.Datum;
import com.alibaba.nacos.naming.consistency.KeyBuilder;
import com.alibaba.nacos.naming.core.*;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Spy;

import java.util.HashMap;
import java.util.Map;

/**
 * @author jifengnan  2019-07-11
 */
public class DistroConsistencyServiceImplTest extends BaseTest {
    @Before
    public void init() {
        super.before();
    }

    @Test
    public void testProcessData() throws Exception {
        Map<String, Datum<Instances>> datumMap = new HashMap<>();
        Datum<Instances> datum = new Datum<>();
        datum.key = KeyBuilder.buildInstanceListKey(TEST_NAMESPACE, TEST_SERVICE_NAME, true);
        datum.timestamp.getAndIncrement();
        datum.value = new Instances();
        datum.value.getInstanceList().add(createInstance(IP1, 1));
        datum.value.getInstanceList().add(createInstance(IP2, 2));
        datumMap.put(datum.key, datum);
        distroConsistencyService.listen(KeyBuilder.SERVICE_META_KEY_PREFIX, serviceManager);
        distroConsistencyService.processData(serializer.serialize(datumMap));
        Datum result = dataStore.get(datum.key);
        Assert.assertNotNull(result);
        Assert.assertEquals(1, result.timestamp.get());
        Assert.assertEquals(Instances.class, result.value.getClass());
        Instances instances = (Instances) result.value;
        Assert.assertEquals(2, instances.getInstanceList().size());

        for (Instance i : instances.getInstanceList()) {
            if (IP1.equals(i.getIp())) {
                Assert.assertEquals(i.getPort(), 1);
                Assert.assertEquals(datum.value.getInstanceList().get(0).getCluster(), i.getCluster());
                Assert.assertEquals(TEST_CLUSTER_NAME, i.getClusterName());
                Assert.assertEquals(TEST_SERVICE_NAME, i.getServiceName());
            } else {
                Assert.assertEquals(i.getPort(), 2);
                Assert.assertEquals(datum.value.getInstanceList().get(0).getCluster(), i.getCluster());
                Assert.assertEquals(TEST_CLUSTER_NAME, i.getClusterName());
                Assert.assertEquals(TEST_SERVICE_NAME, i.getServiceName());
            }
        }

        dataStore.remove(datum.key);
        distroConsistencyService.listen(datum.key, datum.value.getInstanceList().get(0).getCluster().getService());
        distroConsistencyService.processData(serializer.serialize(datumMap));
        result = dataStore.get(datum.key);
        Assert.assertNotNull(result);
        Assert.assertEquals(1, result.timestamp.get());
        Assert.assertEquals(Instances.class, result.value.getClass());
        instances = (Instances) result.value;
        Assert.assertEquals(2, instances.getInstanceList().size());
        for (Instance i : instances.getInstanceList()) {
            if (IP1.equals(i.getIp())) {
                Assert.assertEquals(i.getPort(), 1);
                Assert.assertEquals(datum.value.getInstanceList().get(0).getCluster(), i.getCluster());
                Assert.assertEquals(TEST_CLUSTER_NAME, i.getClusterName());
                Assert.assertEquals(TEST_SERVICE_NAME, i.getServiceName());
            } else {
                Assert.assertEquals(i.getPort(), 2);
                Assert.assertEquals(datum.value.getInstanceList().get(0).getCluster(), i.getCluster());
                Assert.assertEquals(TEST_CLUSTER_NAME, i.getClusterName());
                Assert.assertEquals(TEST_SERVICE_NAME, i.getServiceName());
            }
        }

    }

    @Test
    public void testProcessData_oldData() throws Exception {
        String oldData = "{\"com.alibaba.nacos.naming.iplist.ephemeral.test-namespace##test-service\":{\"key\":\"com.alibaba.nacos.naming.iplist.ephemeral.test-namespace##test-service\",\"timestamp\":1,\"value\":{\"instanceList\":[{\"clusterName\":\"test-cluster\",\"enabled\":true,\"ephemeral\":true,\"healthy\":true,\"ip\":\"1.1.1.1\",\"lastBeat\":1562940196764,\"marked\":false,\"metadata\":{},\"port\":1,\"serviceName\":\"test-service\",\"weight\":1.0},{\"clusterName\":\"test-cluster\",\"enabled\":true,\"ephemeral\":true,\"healthy\":true,\"ip\":\"2.2.2.2\",\"lastBeat\":1562940196764,\"marked\":false,\"metadata\":{},\"port\":2,\"serviceName\":\"test-service\",\"weight\":1.0}]}}}";
        String key = KeyBuilder.buildInstanceListKey(TEST_NAMESPACE, TEST_SERVICE_NAME, true);
        distroConsistencyService.listen(key, new ServiceManager());
        distroConsistencyService.processData(oldData.getBytes("UTF-8"));
        Datum result = dataStore.get(key);
        Assert.assertNotNull(result);
        Assert.assertEquals(1, result.timestamp.get());
        Assert.assertEquals(Instances.class, result.value.getClass());
        Instances instances = (Instances) result.value;
        Assert.assertEquals(2, instances.getInstanceList().size());
        for (Instance i : instances.getInstanceList()) {
            if (IP1.equals(i.getIp())) {
                Assert.assertEquals(i.getPort(), 1);
                Assert.assertEquals(TEST_CLUSTER_NAME, i.getClusterName());
                Assert.assertEquals(TEST_SERVICE_NAME, i.getServiceName());
            } else {
                Assert.assertEquals(i.getPort(), 2);
                Assert.assertEquals(TEST_CLUSTER_NAME, i.getClusterName());
                Assert.assertEquals(TEST_SERVICE_NAME, i.getServiceName());
            }
        }
    }

    @InjectMocks
    private DistroConsistencyServiceImpl distroConsistencyService;

    @Spy
    private FastJsonSerializer serializer = new FastJsonSerializer();

    @Spy
    private DataStore dataStore = new DataStore();

}
