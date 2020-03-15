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
import org.mockito.Mock;
import org.mockito.Spy;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    @Test
    public void testBasicQueryApplication() throws Exception {
        prepareAppData();
        List<Application> applicationList = serviceManager.getApplications(Constants.DEFAULT_NAMESPACE_ID, null, null);
        Assert.assertEquals(20,applicationList.size());
        for (Application application : applicationList) {
            Assert.assertEquals(application.getInstanceCount(),1);
        }
    }

    @Test
    public void testQueryAppNsNotExist() throws Exception {
        prepareAppData();
        List<Application> applicationList = serviceManager.getApplications("NotExistNamespaceId", null, null);
        Assert.assertEquals(0,applicationList.size());
    }

    @Test
    public void testQueryAppByIp() throws Exception {
        prepareAppData();
        List<Application> applicationList = serviceManager.getApplications(Constants.DEFAULT_NAMESPACE_ID, IP_PREFIX + "1", null);
        Assert.assertEquals(11,applicationList.size());
    }

    @Test
    public void testQueryAppByPort() throws Exception {
        prepareAppData();
        List<Application> applicationList = serviceManager.getApplications(Constants.DEFAULT_NAMESPACE_ID, IP_PREFIX + "1", 20);
        Assert.assertEquals(0,applicationList.size());
        applicationList = serviceManager.getApplications(Constants.DEFAULT_NAMESPACE_ID, IP_PREFIX + "20", 20);
        Assert.assertEquals(1,applicationList.size());
        Assert.assertEquals(IP_PREFIX + "20",applicationList.get(0).getIp() );
        Assert.assertEquals(20,applicationList.get(0).getPort());
        applicationList = serviceManager.getApplications(Constants.DEFAULT_NAMESPACE_ID, IP_PREFIX + "20", 21);
        Assert.assertEquals(0,applicationList.size());
    }

    @Test
    public void testQueryInstanceListForApp() throws Exception {
        prepareAppData();
        List<Instance> instanceList = serviceManager.getInstancesForApp(Constants.DEFAULT_NAMESPACE_ID, IP_PREFIX + "1", 1);
        Assert.assertEquals(1,instanceList.size());
        Assert.assertEquals(IP_PREFIX + "1", instanceList.get(0).getIp() );
        Assert.assertEquals(1, instanceList.get(0).getPort());
    }

    private void prepareAppData() throws Exception {
        Service service = new Service();
        service.setName(TEST_SERVICE_NAME);
        Cluster cluster = new Cluster(TEST_CLUSTER_NAME,service);
        service.addCluster(cluster);
        ReflectionTestUtils.setField(serviceManager, "consistencyService", consistencyService);

        List<Instance> instances = new ArrayList<>();
        for (int j = 1; j <= 20; j++) {
            Instance instance = new Instance();
            instance.setIp(IP_PREFIX + j);
            instance.setPort(j);
            instance.setServiceName(TEST_SERVICE_NAME);
            instances.add(instance);
            instance.setClusterName(TEST_CLUSTER_NAME);
            instance.setWeight(j);
            instance.setHealthy(true);
        }
        cluster.updateIPs(instances, false);
        // build cluster map
        Map<String, Service> groupServiceMap = new HashMap<>();
        groupServiceMap.put(TEST_GROUP_NAME + "@@" + TEST_SERVICE_NAME, service);
        Map<String, Map<String, Service>> serviceMap = new HashMap<>();
        serviceMap.put(Constants.DEFAULT_NAMESPACE_ID, groupServiceMap);
        ReflectionTestUtils.setField(serviceManager,"serviceMap",serviceMap);
    }
    private static final String IP_PREFIX = "192.168.1.";
}
