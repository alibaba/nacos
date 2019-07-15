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

import com.alibaba.fastjson.JSON;
import com.alibaba.nacos.api.naming.pojo.AbstractHealthChecker;
import com.alibaba.nacos.naming.BaseTest;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * @author nkorange
 * @author jifengnan 2019-07-13
 */
public class ClusterTest extends BaseTest {

    private Cluster cluster;

    @Before
    public void before() {
        super.before();

        Service service = new Service("nacos.service.1");

        cluster = new Cluster("nacos-cluster-1", service);
        cluster.setDefCkport(80);
        cluster.setDefIPPort(8080);
    }

    @Test
    public void testUpdate() {
        Service service = new Service("nacos.service.2");

        Cluster newCluster = new Cluster("nacos-cluster-1", service);
        newCluster.setDefCkport(8888);
        newCluster.setDefIPPort(9999);
        AbstractHealthChecker.Http healthCheckConfig = new AbstractHealthChecker.Http();
        healthCheckConfig.setPath("/nacos-path-1");
        healthCheckConfig.setExpectedResponseCode(500);
        healthCheckConfig.setHeaders("Client-Version:nacos-test-1");
        newCluster.setHealthChecker(healthCheckConfig);

        cluster.update(newCluster);

        Assert.assertEquals(8888, cluster.getDefCkport());
        Assert.assertEquals(9999, cluster.getDefIPPort());
        Assert.assertTrue(cluster.getHealthChecker() instanceof AbstractHealthChecker.Http);
        AbstractHealthChecker.Http httpHealthCheck = (AbstractHealthChecker.Http) (cluster.getHealthChecker());
        Assert.assertEquals("/nacos-path-1", httpHealthCheck.getPath());
        Assert.assertEquals(500, httpHealthCheck.getExpectedResponseCode());
        Assert.assertEquals("Client-Version:nacos-test-1", httpHealthCheck.getHeaders());
        Assert.assertEquals(newCluster, service.getClusterMap().get("nacos-cluster-1"));
    }

    @Test
    public void testUpdateIps() {

        Instance instance1 = new Instance("1.1.1.1", 1234, cluster);

        Instance instance2 = new Instance("1.1.1.1", 2345, cluster);

        List<Instance> list = new ArrayList<>();
        list.add(instance1);
        list.add(instance2);

        cluster.updateIPs(list, false);

        List<Instance> ips = cluster.allIPs();
        Assert.assertNotNull(ips);
        Assert.assertEquals(2, ips.size());
        Assert.assertEquals("1.1.1.1", ips.get(0).getIp());
        Assert.assertEquals(1234, ips.get(0).getPort());
        Assert.assertEquals("1.1.1.1", ips.get(1).getIp());
        Assert.assertEquals(2345, ips.get(1).getPort());
    }

    @Test
    public void testValidate() {
        Service service = new Service("nacos.service.2");
        cluster = new Cluster("nacos-cluster-1", service);
        cluster.validate();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testValidateClusterNameNull() {
        Service service = new Service("nacos.service.2");
        cluster = new Cluster(null, service);
        cluster.validate();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testValidateServiceNull() {
        cluster = new Cluster("nacos-cluster-1", null);
        cluster.validate();
    }

    @Test
    public void testCreateCluster() {
        Cluster result = JSON.parseObject(NEW_DATA, Cluster.class);
        Assert.assertEquals(180, result.getDefCkport());
        Assert.assertEquals(180, result.getDefIPPort());
        Assert.assertEquals(180, result.getDefaultCheckPort());
        Assert.assertEquals(180, result.getDefaultPort());
        Assert.assertEquals(0, result.getMetadata().size());
        Assert.assertEquals(TEST_CLUSTER_NAME, result.getName());
        Assert.assertEquals(new Service(TEST_SERVICE_NAME, TEST_NAMESPACE, TEST_GROUP_NAME), result.getService());
        Assert.assertEquals(TEST_SERVICE_NAME, result.getServiceName());
        Assert.assertTrue(result.isUseIPPort4Check());
    }

    @Test
    public void testCreateCluster_old() {
        Cluster result = JSON.parseObject(OLD_DATA, Cluster.class);
        Assert.assertEquals(180, result.getDefCkport());
        Assert.assertEquals(180, result.getDefIPPort());
        Assert.assertEquals(180, result.getDefaultCheckPort());
        Assert.assertEquals(180, result.getDefaultPort());
        Assert.assertEquals(1, result.getMetadata().size());
        Assert.assertEquals("value", result.getMetadata().get("key"));
        Assert.assertEquals(TEST_CLUSTER_NAME, result.getName());
        Assert.assertFalse(result.isUseIPPort4Check());
    }

    private static final String NEW_DATA = "{\"defCkport\":180,\"defIPPort\":180,\"defaultCheckPort\":180,\"defaultPort\":180,\"healthCheckTask\":{\"cancelled\":false,\"checkRTBest\":9223372036854775807,\"checkRTLast\":-1,\"checkRTLastLast\":-1,\"checkRTNormalized\":2812,\"checkRTWorst\":0,\"cluster\":{\"$ref\":\"..\"},\"startTime\":1563084156426},\"healthChecker\":{\"type\":\"TCP\"},\"metadata\":{},\"name\":\"test-cluster\",\"service\":{\"checksum\":\"b2f1d9484f316186e1b5f746a22ecdc3\",\"clusterMap\":{\"test-cluster\":{\"$ref\":\"$\"}},\"enabled\":true,\"groupName\":\"test-group-name\",\"ipDeleteTimeout\":30000,\"lastModifiedMillis\":0,\"metadata\":{},\"name\":\"test-service\",\"namespaceId\":\"test-namespace\",\"owners\":[],\"protectThreshold\":0.0,\"resetWeight\":false,\"selector\":{\"type\":\"none\"}},\"serviceName\":\"test-service\",\"sitegroup\":\"\",\"useIPPort4Check\":true}";
    private static final String OLD_DATA = "{\"defCkport\":180,\"defIPPort\":180,\"defaultCheckPort\":180,\"defaultPort\":180,\"healthChecker\":{\"type\":\"MYSQL\"},\"metadata\":{\"key\":\"value\"},\"name\":\"test-cluster\",\"sitegroup\":\"\",\"useIPPort4Check\":false}";
}
