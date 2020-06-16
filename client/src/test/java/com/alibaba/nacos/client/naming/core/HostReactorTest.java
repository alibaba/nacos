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

package com.alibaba.nacos.client.naming.core;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.client.naming.beat.BeatInfo;
import com.alibaba.nacos.client.naming.beat.BeatReactor;
import com.alibaba.nacos.client.naming.net.NamingProxy;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.HashMap;

import static org.junit.Assert.*;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class HostReactorTest {

    private static final String CACHE_DIR = HostReactorTest.class.getResource("/").getPath() + "cache/";

    @Mock
    private NamingProxy namingProxy;

    @Mock
    private EventDispatcher eventDispatcher;

    private HostReactor hostReactor;

    private BeatReactor beatReactor;

    @Before
    public void setUp() throws Exception {
        beatReactor = new BeatReactor(namingProxy);
        BeatInfo beatInfo = new BeatInfo();
        beatInfo.setServiceName("testName");
        beatInfo.setIp("1.1.1.1");
        beatInfo.setPort(1234);
        beatInfo.setCluster("clusterName");
        beatInfo.setWeight(1);
        beatInfo.setMetadata(new HashMap<String, String>());
        beatInfo.setScheduled(false);
        beatInfo.setPeriod(1000L);
        beatReactor.addBeatInfo("testName", beatInfo);
        hostReactor = new HostReactor(eventDispatcher, namingProxy, beatReactor, CACHE_DIR);
    }

    @Test
    public void testProcessServiceJSON() {
        ServiceInfo actual = hostReactor.processServiceJSON(EXAMPLE);
        assertServiceInfo(actual);
        hostReactor.processServiceJSON(CHANGE_DATA_EXAMPLE);
        BeatInfo actualBeatInfo = beatReactor.dom2Beat.get(beatReactor.buildKey("testName", "1.1.1.1", 1234));
        assertEquals(2.0, actualBeatInfo.getWeight(), 0.0);
    }

    @Test
    public void testGetServiceInfoDirectlyFromServer() throws NacosException {
        when(namingProxy.queryList("testName", "testClusters", 0, false)).thenReturn(EXAMPLE);
        ServiceInfo actual = hostReactor.getServiceInfoDirectlyFromServer("testName", "testClusters");
        assertServiceInfo(actual);
    }

    private void assertServiceInfo(ServiceInfo actual) {
        assertEquals("testName", actual.getName());
        assertEquals("testClusters", actual.getClusters());
        assertEquals("", actual.getChecksum());
        assertEquals(1000, actual.getCacheMillis());
        assertEquals(0, actual.getLastRefTime());
        assertNull(actual.getGroupName());
        assertTrue(actual.isValid());
        assertFalse(actual.isAllIPs());
        assertEquals(1, actual.getHosts().size());
        assertInstance(actual.getHosts().get(0));
    }

    private void assertInstance(Instance actual) {
        assertEquals("1.1.1.1", actual.getIp());
        assertEquals("testClusters", actual.getClusterName());
        assertEquals("testName", actual.getServiceName());
        assertEquals(1234, actual.getPort());
    }

    private static final String EXAMPLE = "{\n"
        + "\t\"name\": \"testName\",\n"
        + "\t\"clusters\": \"testClusters\",\n"
        + "\t\"cacheMillis\": 1000,\n"
        + "\t\"hosts\": [{\n"
        + "\t\t\"ip\": \"1.1.1.1\",\n"
        + "\t\t\"port\": 1234,\n"
        + "\t\t\"weight\": 1.0,\n"
        + "\t\t\"healthy\": true,\n"
        + "\t\t\"enabled\": true,\n"
        + "\t\t\"ephemeral\": true,\n"
        + "\t\t\"clusterName\": \"testClusters\",\n"
        + "\t\t\"serviceName\": \"testName\",\n"
        + "\t\t\"metadata\": {},\n"
        + "\t\t\"instanceHeartBeatInterval\": 5000,\n"
        + "\t\t\"instanceHeartBeatTimeOut\": 15000,\n"
        + "\t\t\"ipDeleteTimeout\": 30000,\n"
        + "\t\t\"instanceIdGenerator\": \"simple\"\n"
        + "\t}],\n"
        + "\t\"lastRefTime\": 0,\n"
        + "\t\"checksum\": \"\",\n"
        + "\t\"allIPs\": false,\n"
        + "\t\"valid\": true\n"
        + "}";

    //the weight changed from 1.0 to 2.0
    private static final String CHANGE_DATA_EXAMPLE = "{\n"
        + "\t\"name\": \"testName\",\n"
        + "\t\"clusters\": \"testClusters\",\n"
        + "\t\"cacheMillis\": 1000,\n"
        + "\t\"hosts\": [{\n"
        + "\t\t\"ip\": \"1.1.1.1\",\n"
        + "\t\t\"port\": 1234,\n"
        + "\t\t\"weight\": 2.0,\n"
        + "\t\t\"healthy\": true,\n"
        + "\t\t\"enabled\": true,\n"
        + "\t\t\"ephemeral\": true,\n"
        + "\t\t\"clusterName\": \"testClusters\",\n"
        + "\t\t\"serviceName\": \"testName\",\n"
        + "\t\t\"metadata\": {},\n"
        + "\t\t\"instanceHeartBeatInterval\": 5000,\n"
        + "\t\t\"instanceHeartBeatTimeOut\": 15000,\n"
        + "\t\t\"ipDeleteTimeout\": 30000,\n"
        + "\t\t\"instanceIdGenerator\": \"simple\"\n"
        + "\t}],\n"
        + "\t\"lastRefTime\": 0,\n"
        + "\t\"checksum\": \"\",\n"
        + "\t\"allIPs\": false,\n"
        + "\t\"valid\": true\n"
        + "}";
}
