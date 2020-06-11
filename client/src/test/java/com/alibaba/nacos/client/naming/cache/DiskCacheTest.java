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

package com.alibaba.nacos.client.naming.cache;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;

public class DiskCacheTest {

    private static final String CACHE_DIR = DiskCacheTest.class.getResource("/").getPath() + "cache/";

    private ServiceInfo serviceInfo;

    private Instance instance;

    @Before
    public void setUp() throws Exception {
        System.out.println(CACHE_DIR);
        serviceInfo = new ServiceInfo("testName", "testClusters");
        instance = new Instance();
        instance.setClusterName("testClusters");
        instance.setIp("1.1.1.1");
        instance.setPort(1234);
        instance.setServiceName("testName");
        serviceInfo.setHosts(Collections.singletonList(instance));
    }

    @After
    public void tearDown() {
        File file = new File(CACHE_DIR);
        if (file.exists() && file.list().length > 0) {
            for (File each : file.listFiles()) {
                each.delete();
            }
        }
    }

    @Test
    public void testCache() {
        DiskCache.write(serviceInfo, CACHE_DIR);
        Map<String, ServiceInfo> actual = DiskCache.read(CACHE_DIR);
        assertEquals(1, actual.size());
        assertTrue(actual.containsKey(serviceInfo.getKeyEncoded()));
        assertServiceInfo(actual.get(serviceInfo.getKeyEncoded()), serviceInfo);
    }

    private void assertServiceInfo(ServiceInfo actual, ServiceInfo expected) {
        assertEquals(actual.getName(), expected.getName());
        assertEquals(actual.getGroupName(), expected.getGroupName());
        assertEquals(actual.getClusters(), expected.getClusters());
        assertEquals(actual.getCacheMillis(), expected.getCacheMillis());
        assertEquals(actual.getLastRefTime(), expected.getLastRefTime());
        assertEquals(actual.getKey(), expected.getKey());
        assertHosts(actual.getHosts(), expected.getHosts());
    }

    private void assertHosts(List<Instance> actual, List<Instance> expected) {
        assertEquals(actual.size(), expected.size());
        for (int i = 0; i < expected.size(); i++) {
            assertInstance(actual.get(i), expected.get(i));
        }
    }

    private void assertInstance(Instance actual, Instance expected) {
        assertEquals(actual.getServiceName(), actual.getServiceName());
        assertEquals(actual.getClusterName(), actual.getClusterName());
        assertEquals(actual.getIp(), actual.getIp());
        assertEquals(actual.getPort(), actual.getPort());
    }
}
