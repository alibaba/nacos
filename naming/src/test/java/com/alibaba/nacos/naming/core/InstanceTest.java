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
import com.alibaba.nacos.naming.healthcheck.RsInfo;
import org.junit.Before;
import org.junit.Test;

import java.util.Map;
import java.util.HashMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * @author nkorange
 */
public class InstanceTest {

    private Instance instance;

    @Before
    public void before() {
        instance = new Instance();
    }

    @Test
    public void updateIp() {
        instance.setIp("1.1.1.1");
        instance.setPort(1234);
        instance.setWeight(5);

        assertEquals("1.1.1.1", instance.getIp());
        assertEquals(1234, instance.getPort());
        assertEquals(5, instance.getWeight(), 0.001);
    }

    @Test
    public void testToJsonWithAllParam() {
        instance = new Instance("1.1.1.1", 1234, "TEST", "TENANT", "APP");
        String actual = instance.toJSON();
        assertTrue(actual.contains("\"app\":\"APP\""));
        assertTrue(actual.contains("\"clusterName\":\"TEST\""));
        assertTrue(actual.contains("\"enabled\":true"));
        assertTrue(actual.contains("\"ephemeral\":true"));
        assertTrue(actual.contains("\"healthy\":true"));
        assertTrue(actual.contains("\"instanceHeartBeatInterval\":5000"));
        assertTrue(actual.contains("\"instanceHeartBeatTimeOut\":15000"));
        assertTrue(actual.contains("\"instanceIdGenerator\":\"simple\""));
        assertTrue(actual.contains("\"ip\":\"1.1.1.1\""));
        assertTrue(actual.contains("\"ipDeleteTimeout\":30000"));
        assertTrue(actual.contains("\"lastBeat\":" + instance.getLastBeat()));
        assertTrue(actual.contains("\"marked\":false"));
        assertTrue(actual.contains("\"metadata\":{}"));
        assertTrue(actual.contains("\"port\":1234"));
        assertTrue(actual.contains("\"tenant\":\"TENANT\""));
        assertTrue(actual.contains("\"weight\":1.0"));
        assertFalse(actual.contains("\"mockValid\""));
        assertFalse(actual.contains("\"failCount\""));
    }

    @Test
    public void testToJsonWithoutTenantAndApp() {
        instance = new Instance("1.1.1.1", 1234, "TEST");
        String actual = instance.toJSON();
        System.out.println(actual);
        assertTrue(actual.contains("\"clusterName\":\"TEST\""));
        assertTrue(actual.contains("\"enabled\":true"));
        assertTrue(actual.contains("\"ephemeral\":true"));
        assertTrue(actual.contains("\"healthy\":true"));
        assertTrue(actual.contains("\"instanceHeartBeatInterval\":5000"));
        assertTrue(actual.contains("\"instanceHeartBeatTimeOut\":15000"));
        assertTrue(actual.contains("\"instanceIdGenerator\":\"simple\""));
        assertTrue(actual.contains("\"ip\":\"1.1.1.1\""));
        assertTrue(actual.contains("\"ipDeleteTimeout\":30000"));
        assertTrue(actual.contains("\"lastBeat\":" + instance.getLastBeat()));
        assertTrue(actual.contains("\"marked\":false"));
        assertTrue(actual.contains("\"metadata\":{}"));
        assertTrue(actual.contains("\"port\":1234"));
        assertTrue(actual.contains("\"weight\":1.0"));
        assertFalse(actual.contains("\"app\""));
        assertFalse(actual.contains("\"tenant\":"));
        assertFalse(actual.contains("\"mockValid\""));
        assertFalse(actual.contains("\"failCount\""));
    }

    @Test
    public void testFromJsonByJson() {
        instance = Instance.fromJSON("{\"clusterName\":\"TEST\",\"enabled\":true,\"ephemeral\":true,\"healthy\":true,\"instanceHeartBeatInterval\":5000,\"instanceHeartBeatTimeOut\":15000,\"instanceIdGenerator\":\"simple\",\"ip\":\"1.1.1.1\",\"ipDeleteTimeout\":30000,\"lastBeat\":1590043805463,\"marked\":false,\"metadata\":{},\"port\":1234,\"weight\":1.0}\n");
        assertEquals("1.1.1.1", instance.getIp());
        assertEquals(1234, instance.getPort());
        assertEquals("TEST", instance.getClusterName());
        assertNull(instance.getApp());
        assertNull(instance.getTenant());
    }

    @Test
    public void testFromJsonByNoJson() {
        instance = Instance.fromJSON("2.2.2.2:8888_2_TEST1");
        assertEquals("2.2.2.2", instance.getIp());
        assertEquals(8888, instance.getPort());
        assertEquals(2, instance.getWeight(), 0.001);
        assertEquals("TEST1", instance.getClusterName());
    }

    @Test
    public void rsInfo() throws Exception {

        RsInfo info = new RsInfo();
        Map<String, String> metadata = new HashMap<>();
        metadata.put("version", "2222");
        info.setMetadata(metadata);
        System.out.println(JacksonUtils.toJson(info));

        String json = JacksonUtils.toJson(info);
        RsInfo info1 = JacksonUtils.toObj(json, RsInfo.class);
        System.out.println(info1);
    }
}
