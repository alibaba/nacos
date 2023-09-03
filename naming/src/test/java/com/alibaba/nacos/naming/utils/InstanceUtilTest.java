/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.naming.utils;

import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.naming.core.v2.metadata.InstanceMetadata;
import com.alibaba.nacos.naming.core.v2.pojo.InstancePublishInfo;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.pojo.instance.DefaultInstanceIdGenerator;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class InstanceUtilTest {
    
    private Service service;
    
    private InstancePublishInfo instancePublishInfo;
    
    @Before
    public void init() {
        service = Service.newService("namespace", "group", "serviceName");
        instancePublishInfo = new InstancePublishInfo("1.1.1.1", 8080);
    }
    
    @Test
    public void testParseToApiInstance() {
        Instance instance = InstanceUtil.parseToApiInstance(service, instancePublishInfo);
        assertNotNull(instance);
    }
    
    @Test
    public void testUpdateInstanceMetadata() {
        InstanceMetadata metaData = new InstanceMetadata();
        Map<String, Object> extendData = new ConcurrentHashMap<>(1);
        extendData.put("k1", "v1");
        extendData.put("k2", "v2");
        metaData.setExtendData(extendData);
        metaData.setEnabled(true);
        metaData.setWeight(1);
        Instance instance = InstanceUtil.parseToApiInstance(service, instancePublishInfo);
        
        InstanceUtil.updateInstanceMetadata(instance, metaData);
        assertNotNull(instance.getMetadata());
        assertEquals(metaData.getExtendData().size(), 2);
    }
    
    @Test
    public void testDeepCopy() {
        Instance source = new Instance();
        source.setInstanceId("instanceId");
        source.setIp("1.1.1.1");
        source.setPort(8890);
        source.setWeight(1);
        source.setHealthy(true);
        source.setEnabled(true);
        source.setEphemeral(true);
        source.setClusterName("custerName");
        source.setServiceName("serviceName");
        Map<String, String> metaData = new HashMap<>();
        metaData.put("k1", "v1");
        metaData.put("k2", "v2");
        source.setMetadata(new HashMap<>(metaData));
        Instance instance = InstanceUtil.deepCopy(source);
        assertNotNull(instance);
    }
    
    @Test
    public void testSetInstanceIdIfEmpty() {
        Instance instance = new Instance();
        instance.setIp("1.1.1.1");
        instance.setPort(8890);
        String groupedServiceName = "test";
        InstanceUtil.setInstanceIdIfEmpty(instance, groupedServiceName);
        assertNotNull(instance.getInstanceId());
        DefaultInstanceIdGenerator idGenerator = new DefaultInstanceIdGenerator(groupedServiceName,
                instance.getClusterName(), instance.getIp(), instance.getPort());
        assertEquals(instance.getInstanceId(), idGenerator.generateInstanceId());
        String customInsId = "customInstanceId_1";
        Instance instance1 = new Instance();
        instance1.setInstanceId(customInsId);
        InstanceUtil.setInstanceIdIfEmpty(instance1, groupedServiceName);
        assertEquals(instance1.getInstanceId(), customInsId);
    }
    
    @Test
    public void testBatchSetInstanceIdIfEmpty() {
        List<Instance> instances = new ArrayList<>();
        Instance instance1 = new Instance();
        Instance instance2 = new Instance();
        Instance instance3 = new Instance();
        instances.add(instance1);
        instances.add(instance2);
        instances.add(instance3);
        String groupedServiceName = "test";
        InstanceUtil.batchSetInstanceIdIfEmpty(instances, groupedServiceName);
        assertNotNull(instance1.getInstanceId());
        assertNotNull(instance2.getInstanceId());
        assertNotNull(instance3.getInstanceId());
    }
}
