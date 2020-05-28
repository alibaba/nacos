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

import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.naming.BaseTest;
import com.alibaba.nacos.naming.selector.NoneSelector;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * @author jifengnan  2019-04-28
 */
public class ServiceTest extends BaseTest {

    private Service service;

    @Before
    public void before() {
        super.before();
        service = new Service("test-service");
        mockInjectPushServer();
        mockInjectHealthCheckProcessor();
        mockInjectDistroMapper();
        mockInjectSwitchDomain();
    }

    @Test
    public void testUpdateIPs() {
        List<Instance> instances = new ArrayList<>();
        Instance instance = new Instance("1.1.1.1", 1, "test-instance1");
        instances.add(instance);
        service.updateIPs(instances, true);
        Assert.assertEquals(instances, service.allIPs(true));

        instances = new ArrayList<>();
        instance = new Instance();
        instance.setIp("2.2.2.2");
        instance.setPort(2);
        instances.add(instance);
        instances.add(null);
        service.updateIPs(instances, true);
        instances.remove(null);
        Assert.assertEquals(instances, service.allIPs(true));
    }

    @Test
    public void testSerialize() throws Exception {
        String actual = new Service("test-service").toJSON();
        System.out.println(actual);
        assertTrue(actual.contains("\"checksum\":\"394c845e1160bb880e7f26fb2149ed6d\""));
        assertTrue(actual.contains("\"clusterMap\":{}"));
        assertTrue(actual.contains("\"empty\":true"));
        assertTrue(actual.contains("\"enabled\":true"));
        assertTrue(actual.contains("\"finalizeCount\":0"));
        assertTrue(actual.contains("\"ipDeleteTimeout\":30000"));
        assertTrue(actual.contains("\"lastModifiedMillis\":0"));
        assertTrue(actual.contains("\"metadata\":{}"));
        assertTrue(actual.contains("\"name\":\"test-service\""));
        assertTrue(actual.contains("\"owners\":[]"));
        assertTrue(actual.contains("\"protectThreshold\":0.0"));
        assertTrue(actual.contains("\"resetWeight\":false"));
        assertTrue(actual.contains("\"selector\":{\"type\":\"none\"}"));
        assertFalse(actual.contains("clientBeatCheckTask"));
        assertFalse(actual.contains("serviceString"));
        assertFalse(actual.contains("pushService"));
    }

    @Test
    public void testDeserialize() throws Exception {
        String example = "{\"checksum\":\"394c845e1160bb880e7f26fb2149ed6d\",\"clusterMap\":{},\"empty\":true,\"enabled\":true,\"finalizeCount\":0,\"ipDeleteTimeout\":30000,\"lastModifiedMillis\":0,\"metadata\":{},\"name\":\"test-service\",\"owners\":[],\"protectThreshold\":0.0,\"resetWeight\":false,\"selector\":{\"type\":\"none\"}}";
        Service actual = JacksonUtils.toObj(example, Service.class);
        assertEquals("394c845e1160bb880e7f26fb2149ed6d", actual.getChecksum());
        assertEquals("test-service", actual.getName());
        assertTrue(actual.getClusterMap().isEmpty());
        assertTrue(actual.isEmpty());
        assertTrue(actual.getEnabled());
        assertTrue(actual.getMetadata().isEmpty());
        assertTrue(actual.getOwners().isEmpty());
        assertEquals(0, actual.getFinalizeCount());
        assertEquals(30000, actual.getIpDeleteTimeout());
        assertEquals(0, actual.getLastModifiedMillis());
        assertEquals(0, actual.getLastModifiedMillis());
        assertEquals(0.0, actual.getProtectThreshold(), 0);
        assertFalse(actual.getResetWeight());
        assertThat(actual.getSelector(), instanceOf(NoneSelector.class));
    }

    @Test
    public void testGetServiceString() {
        String actual = service.getServiceString();
        assertTrue(actual.contains("\"invalidIPCount\":0"));
        assertTrue(actual.contains("\"name\":\"test-service\""));
        assertTrue(actual.contains("\"ipCount\":0"));
        assertTrue(actual.contains("\"owners\":[]"));
        assertTrue(actual.contains("\"protectThreshold\":0.0"));
        assertTrue(actual.contains("\"clusters\":[]"));
    }

    @Test
    public void test() {
        String a = "{\"appName\":\"\",\"checksum\":\"d9ce515b2d3f26a06cd7ba43a4cba1a7\",\"clusterMap\":{},\"empty\":true,\"enabled\":true,\"finalizeCount\":0,\"groupName\":\"\",\"ipDeleteTimeout\":30000,\"lastModifiedMillis\":1590401570517,\"metadata\":{},\"name\":\"DEFAULT_GROUP@@nacos.test\",\"namespaceId\":\"public\",\"owners\":[],\"protectThreshold\":0.5,\"pushService\":{\"failedPushCount\":0,\"totalPush\":0},\"resetWeight\":false,\"selector\":{\"type\":\"none\"},\"serviceString\":\"{\\\"invalidIPCount\\\":0,\\\"name\\\":\\\"DEFAULT_GROUP@@nacos.test\\\",\\\"ipCount\\\":0,\\\"owners\\\":[],\\\"protectThreshold\\\":0.5,\\\"clusters\\\":[],\\\"token\\\":null}\",\"token\":\"\"}";
//        JacksonUtils.toObj(a, Service.class);
    }
}
