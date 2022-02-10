package com.alibaba.nacos.naming.utils;

import com.alibaba.nacos.naming.constants.Constants;
import com.alibaba.nacos.naming.core.v2.client.Client;
import com.alibaba.nacos.naming.core.v2.client.impl.IpPortBasedClient;
import com.alibaba.nacos.naming.core.v2.pojo.InstancePublishInfo;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;

import static com.alibaba.nacos.api.common.Constants.DEFAULT_CLUSTER_NAME;
import static org.junit.Assert.assertEquals;

/**
 * Tests for {@link DistroUtils}.
 *
 * @author Pixy Yuan
 * on 2021/10/9
 */
public class DistroUtilsTest {
    
    private static final String NAMESPACE = "testNamespace-";
    
    private static final String GROUP = "testGroup-";
    
    private static final String SERVICE = "testName-";
    
    private static final int n = 100000;
    
    private IpPortBasedClient client0;
    
    private IpPortBasedClient client1;
    
    @Before
    public void setUp() throws Exception {
        client0 = buildClient("127.0.0.1", 8848, false, true, DEFAULT_CLUSTER_NAME,
                null);
        HashMap<String, Object> metadata = new HashMap<>();
        metadata.put(Constants.PUBLISH_INSTANCE_WEIGHT, 2L);
        metadata.put(Constants.PUBLISH_INSTANCE_ENABLE, false);
        metadata.put("Custom.metadataId1", "abc");
        metadata.put("Custom.metadataId2", 123);
        metadata.put("Custom.metadataId3", null);
        client1 = buildClient("127.0.0.2", 8848, true, true, "cluster1",
                metadata, 20);
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
            client.putServiceInstance(Service.newService(DistroUtilsTest.NAMESPACE + i,
                            DistroUtilsTest.GROUP + i, DistroUtilsTest.SERVICE + i, ephemeral),
                    instance);
        }
        return client;
    }
    
    @Test
    public void testRevision0() {
        assertEquals(-1402727264L, DistroUtils.revision(client0));
    }
    
    @Test
    public void testChecksum0() {
        for (int i = 0; i < 3; i++) {
            assertEquals("33bbc8d74e6220e3e2f12bc5292cf8e1", DistroUtils.checksum(client0));
        }
    }
    
    @Test
    public void testBuildUniqueString0() {
        assertEquals("127.0.0.1:8848#false|testNamespace-1##testGroup-1@@testName-1##false_127.0.0.1:8848_1.0_true_true_DEFAULT_,||",
                DistroUtils.buildUniqueString(client0));
    }
    
    @Test
    public void testBuildUniqueString1() {
        HashMap<String, Object> metadata = new HashMap<>();
        metadata.put(Constants.PUBLISH_INSTANCE_WEIGHT, 2L);
        metadata.put(Constants.PUBLISH_INSTANCE_ENABLE, false);
        metadata.put("Custom.metadataId1", "abc");
        metadata.put("Custom.metadataId2", 123);
        metadata.put("Custom.metadataId3", null);
        Client client = buildClient("128.0.0.1", 8848, false, false, DEFAULT_CLUSTER_NAME,
                metadata);
        assertEquals("128.0.0.1:8848#false|"
                        + "testNamespace-1##testGroup-1@@testName-1##false_128.0.0.1:8848_2.0_false_false_DEFAULT_"
                        + "Custom.metadataId1:abc,Custom.metadataId2:123,Custom.metadataId3:null,"
                        + "publishInstanceEnable:false,publishInstanceWeight:2,,||",
                DistroUtils.buildUniqueString(client));
        assertEquals(1307283503L, DistroUtils.revision(client));
        assertEquals("6e19890de2ec1b9177ad814a8f5df2fd", DistroUtils.checksum(client));
    }
    
    @Test
    public void testBuildUniqueString2() {
        HashMap<String, Object> metadata = new HashMap<>();
        metadata.put(Constants.PUBLISH_INSTANCE_WEIGHT, 2L);
        metadata.put(Constants.PUBLISH_INSTANCE_ENABLE, true);
        metadata.put("Custom.metadataId1", "abc");
        Client client = buildClient("128.0.0.2", 7001, true, false, "cluster1",
                metadata);
        assertEquals("128.0.0.2:7001#true|"
                        + "testNamespace-1##testGroup-1@@testName-1##true_128.0.0.2:7001_2.0_false_true_cluster1_"
                        + "Custom.metadataId1:abc,publishInstanceEnable:true,publishInstanceWeight:2,,||",
                DistroUtils.buildUniqueString(client));
        assertEquals(2084494023L, DistroUtils.revision(client));
        assertEquals("259842c60964805917e7a17129f0ffab", DistroUtils.checksum(client));
    }
    
    @Test
    public void performanceTestOfChecksum() {
        long start = System.nanoTime();
        for (int i = 0; i < n; i++) {
            DistroUtils.checksum(client1);
        }
        System.out.printf("Distro Verify Checksum Performance: %.2f ivk/ns\n", ((double) System.nanoTime() - start) / n);
    }
    
    @Test
    public void performanceTestOfRevision() {
        long start = System.nanoTime();
        for (int i = 0; i < n; i++) {
            DistroUtils.revision(client1);
        }
        System.out.printf("Distro Verify Revision Performance: %.2f ivk/ns\n", ((double) System.nanoTime() - start) / n);
    }
    
    @Test
    public void performanceTestOfHashCode() {
        long start = System.nanoTime();
        for (int i = 0; i < n; i++) {
            DistroUtils.hash(client1);
        }
        System.out.printf("Distro Verify Hash Performance: %.2f ivk/ns\n", ((double) System.nanoTime() - start) / n);
    }
    
}