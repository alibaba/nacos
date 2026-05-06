/*
 * Copyright 1999-2022 Alibaba Group Holding Ltd.
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

import com.alibaba.nacos.naming.constants.Constants;
import com.alibaba.nacos.naming.core.v2.client.Client;
import com.alibaba.nacos.naming.core.v2.client.impl.IpPortBasedClient;
import com.alibaba.nacos.naming.core.v2.pojo.InstancePublishInfo;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static com.alibaba.nacos.api.common.Constants.DEFAULT_CLUSTER_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for {@link DistroUtils}.
 *
 * @author Pixy Yuan on 2021/10/9
 */
class DistroUtilsTest {
    
    private static final String NAMESPACE = "testNamespace-";
    
    private static final String GROUP = "testGroup-";
    
    private static final String SERVICE = "testName-";
    
    private static final int N = 100000;
    
    private IpPortBasedClient client0;
    
    private IpPortBasedClient client1;
    
    @BeforeEach
    void setUp() throws Exception {
        client0 = buildClient("127.0.0.1", 8848, false, true, DEFAULT_CLUSTER_NAME, null);
        HashMap<String, Object> metadata = new HashMap<>();
        metadata.put(Constants.PUBLISH_INSTANCE_WEIGHT, 2L);
        metadata.put(Constants.PUBLISH_INSTANCE_ENABLE, false);
        metadata.put("Custom.metadataId1", "abc");
        metadata.put("Custom.metadataId2", 123);
        metadata.put("Custom.metadataId3", null);
        client1 = buildClient("127.0.0.2", 8848, true, true, "cluster1", metadata, 20);
    }
    
    private IpPortBasedClient buildClient(String ip, int port, boolean ephemeral, boolean healthy, String cluster,
            HashMap<String, Object> extendDatum) {
        return buildClient(ip, port, ephemeral, healthy, cluster, extendDatum, 1);
    }
    
    private IpPortBasedClient buildClient(String ip, int port, boolean ephemeral, boolean healthy, String cluster,
            HashMap<String, Object> extendDatum, int serviceCount) {
        InstancePublishInfo instance = new InstancePublishInfo(ip, port);
        instance.setCluster(cluster);
        instance.setHealthy(healthy);
        IpPortBasedClient client = new IpPortBasedClient(String.format("%s:%s#%s", ip, port, ephemeral), ephemeral);
        if (extendDatum != null) {
            instance.setExtendDatum(extendDatum);
        }
        for (int i = 1; i < serviceCount + 1; i++) {
            client.putServiceInstance(
                    Service.newService(DistroUtilsTest.NAMESPACE + i, DistroUtilsTest.GROUP + i, DistroUtilsTest.SERVICE + i, ephemeral),
                    instance);
        }
        return client;
    }
    
    @Test
    void testHash0() {
        assertEquals(-1320954445, DistroUtils.hash(client0));
    }
    
    @Test
    void testRevision0() {
        assertEquals(-1713189600L, DistroUtils.stringHash(client0));
    }
    
    @Test
    void testChecksum0() {
        for (int i = 0; i < 3; i++) {
            assertEquals("2a3f62f84a4b6f2a979434276d546ac1", DistroUtils.checksum(client0));
        }
    }
    
    @Test
    void testBuildUniqueString0() {
        assertEquals("127.0.0.1:8848#false|testNamespace-1##testGroup-1@@testName-1##false_127.0.0.1:8848_1.0_true_true_DEFAULT_,",
                DistroUtils.buildUniqueString(client0));
    }
    
    @Test
    void testBuildUniqueString1() {
        HashMap<String, Object> metadata = new HashMap<>();
        metadata.put(Constants.PUBLISH_INSTANCE_WEIGHT, 2L);
        metadata.put(Constants.PUBLISH_INSTANCE_ENABLE, false);
        metadata.put("Custom.metadataId1", "abc");
        metadata.put("Custom.metadataId2", 123);
        metadata.put("Custom.metadataId3", null);
        Client client = buildClient("128.0.0.1", 8848, false, false, DEFAULT_CLUSTER_NAME, metadata);
        assertEquals("128.0.0.1:8848#false|" + "testNamespace-1##testGroup-1@@testName-1##false_128.0.0.1:8848_2.0_false_false_DEFAULT_"
                + "Custom.metadataId1:abc,Custom.metadataId2:123,Custom.metadataId3:null,"
                + "publishInstanceEnable:false,publishInstanceWeight:2,,", DistroUtils.buildUniqueString(client));
        assertEquals(2128732271L, DistroUtils.stringHash(client));
        assertEquals("ac9bf94dc4bd6a35e5ff9734868eafea", DistroUtils.checksum(client));
    }
    
    @Test
    void testBuildUniqueString2() {
        HashMap<String, Object> metadata = new HashMap<>();
        metadata.put(Constants.PUBLISH_INSTANCE_WEIGHT, 2L);
        metadata.put(Constants.PUBLISH_INSTANCE_ENABLE, true);
        metadata.put("Custom.metadataId1", "abc");
        Client client = buildClient("128.0.0.2", 7001, true, false, "cluster1", metadata);
        assertEquals("128.0.0.2:7001#true|" + "testNamespace-1##testGroup-1@@testName-1##true_128.0.0.2:7001_2.0_false_true_cluster1_"
                + "Custom.metadataId1:abc,publishInstanceEnable:true,publishInstanceWeight:2,,", DistroUtils.buildUniqueString(client));
        assertEquals(775352583L, DistroUtils.stringHash(client));
        assertEquals("82d8e086a880f088320349b895b22948", DistroUtils.checksum(client));
    }
    
    @Test
    void performanceTestOfChecksum() {
        long start = System.nanoTime();
        for (int i = 0; i < N; i++) {
            DistroUtils.checksum(client1);
        }
        System.out.printf("Distro Verify Checksum Performance: %.2f ivk/ns\n", ((double) System.nanoTime() - start) / N);
    }
    
    @Test
    void performanceTestOfStringHash() {
        long start = System.nanoTime();
        for (int i = 0; i < N; i++) {
            DistroUtils.stringHash(client1);
        }
        System.out.printf("Distro Verify Revision Performance: %.2f ivk/ns\n", ((double) System.nanoTime() - start) / N);
    }
    
    @Test
    void performanceTestOfHash() {
        long start = System.nanoTime();
        for (int i = 0; i < N; i++) {
            DistroUtils.hash(client1);
        }
        System.out.printf("Distro Verify Hash Performance: %.2f ivk/ns\n", ((double) System.nanoTime() - start) / N);
    }
    
}