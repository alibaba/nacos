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

package com.alibaba.nacos.naming.cluster.transport;

import com.alibaba.nacos.common.utils.ByteUtils;
import com.alibaba.nacos.naming.consistency.Datum;
import com.alibaba.nacos.naming.core.Instance;
import com.alibaba.nacos.naming.core.Instances;
import org.junit.Before;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SerializerTest {
    
    private Serializer serializer;
    
    private Instances instances;
    
    @Before
    public void setUp() throws Exception {
        serializer = new JacksonSerializer();
        instances = new Instances();
        instances.getInstanceList().add(new Instance("1.1.1.1", 1234, "cluster"));
    }
    
    @Test
    public void testSerialize() {
        String actual = new String(serializer.serialize(instances));
        assertTrue(actual.contains("\"instanceList\":["));
        assertTrue(actual.contains("\"clusterName\":\"cluster\""));
        assertTrue(actual.contains("\"ip\":\"1.1.1.1\""));
        assertTrue(actual.contains("\"port\":1234"));
    }
    
    @Test
    @SuppressWarnings("checkstyle:linelength")
    public void testDeserialize() {
        String example = "{\"instanceList\":[{\"ip\":\"1.1.1.1\",\"port\":1234,\"weight\":1.0,\"healthy\":true,\"enabled\":true,\"ephemeral\":true,\"clusterName\":\"cluster\",\"metadata\":{},\"lastBeat\":1590563397264,\"marked\":false,\"instanceIdGenerator\":\"simple\",\"instanceHeartBeatInterval\":5000,\"instanceHeartBeatTimeOut\":15000,\"ipDeleteTimeout\":30000}]}";
        Instances actual = serializer.deserialize(ByteUtils.toBytes(example), Instances.class);
        assertEquals(1, actual.getInstanceList().size());
        Instance actualInstance = actual.getInstanceList().get(0);
        assertEquals("1.1.1.1", actualInstance.getIp());
        assertEquals("cluster", actualInstance.getClusterName());
        assertEquals(1234, actualInstance.getPort());
    }
    
    @Test
    @SuppressWarnings("checkstyle:linelength")
    public void testDeserializeMap() {
        String example = "{\"datum\":{\"key\":\"instances\",\"value\":{\"instanceList\":[{\"ip\":\"1.1.1.1\",\"port\":1234,\"weight\":1.0,\"healthy\":true,\"enabled\":true,\"ephemeral\":true,\"clusterName\":\"cluster\",\"metadata\":{},\"lastBeat\":1590563397533,\"marked\":false,\"instanceIdGenerator\":\"simple\",\"instanceHeartBeatInterval\":5000,\"instanceHeartBeatTimeOut\":15000,\"ipDeleteTimeout\":30000}]},\"timestamp\":100000}}";
        Map<String, Datum<Instances>> actual = serializer.deserializeMap(ByteUtils.toBytes(example), Instances.class);
        assertEquals(actual.size(), 1);
        assertTrue(actual.containsKey("datum"));
        Datum<Instances> actualDatum = actual.get("datum");
        assertEquals("instances", actualDatum.key);
        assertEquals(100000L, actualDatum.timestamp.get());
        assertEquals(1, actualDatum.value.getInstanceList().size());
        Instance actualInstance = actualDatum.value.getInstanceList().get(0);
        assertEquals("1.1.1.1", actualInstance.getIp());
        assertEquals("cluster", actualInstance.getClusterName());
        assertEquals(1234, actualInstance.getPort());
    }
}
