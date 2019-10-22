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

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.PreservedMetadataKeys;
import com.alibaba.nacos.naming.BaseTest;
import com.alibaba.nacos.naming.consistency.ConsistencyService;
import com.alibaba.nacos.naming.consistency.Datum;
import com.alibaba.nacos.naming.consistency.KeyBuilder;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;

import com.google.common.collect.Maps;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.mockito.Mockito.when;

/**
 * @author jifengnan 2019-04-29
 */
public class ServiceManagerTest extends BaseTest {

    @InjectMocks
    private ServiceManager serviceManager;

    @Mock
    private ConsistencyService consistencyService;

    @Before
    public void before() {
        super.before();
    }

    @Test
    public void testUpdateIpAddresses() throws Exception {
        Service service = new Service(TEST_SERVICE_NAME, TEST_NAMESPACE);
        Cluster cluster = new Cluster(TEST_CLUSTER_NAME, service);
        Instance instance = new Instance(IP1, 1, cluster);
        List<Instance> instanceList = serviceManager.updateIpAddresses(service, UtilsAndCommons.UPDATE_INSTANCE_ACTION_ADD, true, instance);
        Assert.assertEquals(1, instanceList.size());
        Assert.assertEquals(instance, instanceList.get(0));
        Assert.assertEquals(1, service.getClusterMap().size());
        Assert.assertEquals(cluster, service.getClusterMap().get(TEST_CLUSTER_NAME));

        Datum datam = new Datum();
        datam.key = KeyBuilder.buildInstanceListKey(TEST_NAMESPACE, TEST_SERVICE_NAME, true);
        Instances instances = new Instances();
        instanceList.add(new Instance(IP2, 2, cluster));
        instances.setInstanceList(instanceList);
        datam.value = instances;
        when(consistencyService.get(KeyBuilder.buildInstanceListKey(TEST_NAMESPACE, TEST_SERVICE_NAME, true))).thenReturn(datam);
        service.addCluster(cluster);
        service.getClusterMap().get(TEST_CLUSTER_NAME).updateIPs(instanceList, true);
        instanceList = serviceManager.updateIpAddresses(service, UtilsAndCommons.UPDATE_INSTANCE_ACTION_REMOVE, true, instance);
        Assert.assertEquals(1, instanceList.size());
        Assert.assertEquals(new Instance(IP2, 2, cluster), instanceList.get(0));
        Assert.assertEquals(1, service.getClusterMap().size());
        Assert.assertSame(cluster, service.getClusterMap().get(TEST_CLUSTER_NAME));
    }

    @Test
    public void testUpdateIpAddressesNoInstance() throws Exception {
        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage("ip list can not be empty, service: test-service, ip list: []");
        Service service = new Service(TEST_SERVICE_NAME, TEST_NAMESPACE);
        serviceManager.updateIpAddresses(service, UtilsAndCommons.UPDATE_INSTANCE_ACTION_ADD, true);
    }

    @Test
    public void testCreateServiceIfAbsent() throws NacosException {
        String serviceName = TEST_GROUP_NAME + Constants.SERVICE_INFO_SPLITER + TEST_SERVICE_NAME;
        Service service = serviceManager.createServiceIfAbsent(TEST_NAMESPACE,
            serviceName, true);
        Assert.assertEquals(new Service(serviceName, TEST_NAMESPACE, TEST_GROUP_NAME), service);

        Service service2 = serviceManager.createServiceIfAbsent(TEST_NAMESPACE,
            serviceName, true);
        Assert.assertSame(service, service2);

        service = serviceManager.createServiceIfAbsent("namespace",
            "group@@service", false);
        Assert.assertEquals(new Service("group@@service", "namespace", "group"), service);
    }
  
    @Test
    public void testSnowflakeInstanceId() throws Exception {
        ReflectionTestUtils.setField(serviceManager, "consistencyService", consistencyService);
        Service service = new Service(TEST_SERVICE_NAME);
        service.setNamespaceId(TEST_NAMESPACE);

        Map<String, String> metaData = Maps.newHashMap();
        metaData.put(PreservedMetadataKeys.INSTANCE_ID_GENERATOR, Constants.SNOWFLAKE_INSTANCE_ID_GENERATOR);

        Instance instance1 = new Instance("1.1.1.1", 1);
        instance1.setClusterName(TEST_CLUSTER_NAME);
        instance1.setMetadata(metaData);

        Instance instance2 = new Instance("2.2.2.2", 2);
        instance2.setClusterName(TEST_CLUSTER_NAME);
        instance2.setMetadata(metaData);

        List<Instance> instanceList = serviceManager.updateIpAddresses(service, UtilsAndCommons.UPDATE_INSTANCE_ACTION_ADD, true, instance1, instance2);
        Assert.assertNotNull(instanceList);
        Assert.assertEquals(2, instanceList.size());
        int instanceId1 = Integer.parseInt(instance1.getInstanceId());
        int instanceId2 = Integer.parseInt(instance2.getInstanceId());
        Assert.assertNotEquals(instanceId1, instanceId2);
    }
}
