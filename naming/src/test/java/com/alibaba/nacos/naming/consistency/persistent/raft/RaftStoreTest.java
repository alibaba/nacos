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
package com.alibaba.nacos.naming.consistency.persistent.raft;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.naming.BaseTest;
import com.alibaba.nacos.naming.consistency.Datum;
import com.alibaba.nacos.naming.consistency.KeyBuilder;
import com.alibaba.nacos.naming.core.Cluster;
import com.alibaba.nacos.naming.core.Instance;
import com.alibaba.nacos.naming.core.Instances;
import com.alibaba.nacos.naming.core.Service;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Spy;

/**
 * @author nkorange
 * @author jifengnan 2019-05-18
 */
public class RaftStoreTest extends BaseTest {

    @InjectMocks
    public RaftCore raftCore;

    @Spy
    public RaftStore raftStore;

    @Test
    public void testWrite_Instances() throws Exception {
        Datum<Instances> datum = new Datum<>();
        String key = KeyBuilder.buildInstanceListKey(TEST_NAMESPACE, TEST_SERVICE_NAME, false);
        datum.key = key;
        datum.timestamp.getAndIncrement();
        datum.value = new Instances();
        Cluster cluster = new Cluster(TEST_CLUSTER_NAME, new Service(TEST_SERVICE_NAME, TEST_NAMESPACE));
        cluster.setDefaultPort(180);
        Instance instance = new Instance(IP1, 1, cluster);
        instance.setEphemeral(false);
        datum.value.getInstanceList().add(instance);
        instance = new Instance(IP2, 2, cluster);
        instance.setWeight(2.2);
        datum.value.getInstanceList().add(instance);

        raftStore.write(datum);
        raftCore.init();
        Datum result = raftCore.getDatum(key);

        Assert.assertEquals(key, result.key);
        Assert.assertEquals(1, result.timestamp.intValue());
        Instances instances = (Instances) result.value;
        for (Instance i : instances.getInstanceList()) {
            Assert.assertEquals(180, i.getCluster().getDefaultPort());
            Assert.assertEquals(TEST_CLUSTER_NAME, i.getCluster().getName());
            Assert.assertEquals(TEST_SERVICE_NAME, i.getCluster().getService().getName());
            Assert.assertEquals(TEST_NAMESPACE, i.getCluster().getService().getNamespaceId());
            if (IP1.equals(i.getIp())) {
                Assert.assertEquals(1, i.getPort());
                Assert.assertFalse(i.isEphemeral());
            } else {
                Assert.assertEquals(2, i.getPort());
                Assert.assertTrue(i.isEphemeral());
                Assert.assertEquals(2.2, i.getWeight(), 0.01);
            }
        }
    }

    @Test
    public void testWrite_Service() throws Exception {
        Datum<Service> datum = new Datum<>();
        String serviceName = TEST_GROUP_NAME + Constants.SERVICE_INFO_SPLITER + TEST_SERVICE_NAME;
        String key = KeyBuilder.buildServiceMetaKey(TEST_NAMESPACE, serviceName);
        datum.key = key;
        datum.timestamp.getAndIncrement();
        datum.value = new Service(serviceName, TEST_NAMESPACE);
        datum.value.setIpDeleteTimeout(100);
        Cluster cluster = new Cluster(TEST_CLUSTER_NAME, datum.value);
        cluster.setDefaultPort(180);

        raftStore.write(datum);
        raftCore.init();
        Datum result = raftCore.getDatum(key);

        Assert.assertEquals(key, result.key);
        Assert.assertEquals(1, result.timestamp.intValue());
        Service service = (Service) result.value;
        Assert.assertEquals(TEST_NAMESPACE, service.getNamespaceId());
        Assert.assertEquals(serviceName, service.getName());
        Assert.assertEquals(100, service.getIpDeleteTimeout());
        Assert.assertEquals(1, service.getClusterMap().size());
        Assert.assertEquals(cluster, service.getClusterMap().get(TEST_CLUSTER_NAME));
        Assert.assertEquals(180, service.getClusterMap().get(TEST_CLUSTER_NAME).getDefaultPort());
    }
}
