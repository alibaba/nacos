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
package com.alibaba.nacos.naming.core;

import com.alibaba.nacos.naming.BaseTest;
import com.alibaba.nacos.naming.consistency.ConsistencyService;
import com.alibaba.nacos.naming.consistency.Datum;
import com.alibaba.nacos.naming.consistency.KeyBuilder;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Spy;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.mockito.Mockito.when;

/**
 * @author jifengnan 2019-04-29
 */
public class ServiceManagerTest extends BaseTest {

    @Spy
    private ServiceManager serviceManager;

    @Mock
    private ConsistencyService consistencyService;

    @Before
    public void before() {
        super.before();
    }

    @Test
    public void testUpdateIpAddresses() throws Exception {
        ReflectionTestUtils.setField(serviceManager, "consistencyService", consistencyService);
        Service service = new Service(TEST_SERVICE_NAME);
        service.setNamespaceId(TEST_NAMESPACE);
        Instance instance = new Instance("1.1.1.1", 1);
        instance.setClusterName(TEST_CLUSTER_NAME);
        List<Instance> instanceList = serviceManager.updateIpAddresses(service, UtilsAndCommons.UPDATE_INSTANCE_ACTION_ADD, true, instance);
        Assert.assertEquals(1, instanceList.size());
        Assert.assertEquals(instance, instanceList.get(0));
        Assert.assertEquals(1, service.getClusterMap().size());
        Assert.assertEquals(new Cluster(instance.getClusterName(), service), service.getClusterMap().get(TEST_CLUSTER_NAME));

        Datum datam = new Datum();
        datam.key = KeyBuilder.buildInstanceListKey(TEST_NAMESPACE, TEST_SERVICE_NAME, true);
        Instances instances = new Instances();
        instanceList.add(new Instance("2.2.2.2", 2));
        instances.setInstanceList(instanceList);
        datam.value = instances;
        when(consistencyService.get(KeyBuilder.buildInstanceListKey(TEST_NAMESPACE, TEST_SERVICE_NAME, true))).thenReturn(datam);
        service.getClusterMap().get(TEST_CLUSTER_NAME).updateIPs(instanceList, true);
        instanceList = serviceManager.updateIpAddresses(service, UtilsAndCommons.UPDATE_INSTANCE_ACTION_REMOVE, true, instance);
        Assert.assertEquals(1, instanceList.size());
        Assert.assertEquals(new Instance("2.2.2.2", 2), instanceList.get(0));
        Assert.assertEquals(1, service.getClusterMap().size());
        Assert.assertEquals(new Cluster(instance.getClusterName(), service), service.getClusterMap().get(TEST_CLUSTER_NAME));
    }

    @Test
    public void testUpdateIpAddressesNoInstance() throws Exception {
        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage("ip list can not be empty, service: test-service, ip list: []");
        ReflectionTestUtils.setField(serviceManager, "consistencyService", consistencyService);
        Service service = new Service(TEST_SERVICE_NAME);
        service.setNamespaceId(TEST_NAMESPACE);
        serviceManager.updateIpAddresses(service, UtilsAndCommons.UPDATE_INSTANCE_ACTION_ADD, true);
    }
}
