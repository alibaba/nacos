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
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.naming.BaseTest;
import com.alibaba.nacos.naming.consistency.ConsistencyService;
import com.alibaba.nacos.naming.consistency.Datum;
import com.alibaba.nacos.naming.consistency.KeyBuilder;
import com.alibaba.nacos.naming.core.ServiceManager.ServiceChecksum;
import com.alibaba.nacos.naming.misc.Message;
import com.alibaba.nacos.naming.misc.Synchronizer;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import com.google.common.collect.Maps;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Spy;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

/**
 * @author jifengnan 2019-04-29
 */
public class ServiceManagerTest extends BaseTest {

    @Spy
    private ServiceManager serviceManager;

    @Mock
    private ConsistencyService consistencyService;

    @Mock
    private Synchronizer synchronizer;

    @Before
    public void before() {
        super.before();
        mockInjectHealthCheckProcessor();
        mockInjectDistroMapper();
        mockInjectSwitchDomain();
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
    public void testUpdatedHealthStatus() {
        ReflectionTestUtils.setField(serviceManager, "synchronizer", synchronizer);
        String namespaceId = "namespaceId";
        String serviceName = "testService";
        String serverIp = "127.0.0.1";
        String example = "{\"ips\":[\"127.0.0.1:8848_true\"]}";
        Message message = new Message();
        message.setData(example);
        when(synchronizer.get(serverIp, UtilsAndCommons.assembleFullServiceName(namespaceId, serviceName))).thenReturn(message);
        serviceManager.updatedHealthStatus(namespaceId, serviceName, serverIp);
    }

    @Test
    public void testSerializeServiceChecksum() {
        ServiceChecksum checksum = new ServiceChecksum();
        checksum.addItem("test", "1234567890");
        String actual = JacksonUtils.toJson(checksum);
        assertTrue(actual.contains("\"namespaceId\":\"public\""));
        assertTrue(actual.contains("\"serviceName2Checksum\":{\"test\":\"1234567890\"}"));
    }
}
