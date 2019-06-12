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

import com.alibaba.nacos.api.naming.pojo.AbstractHealthChecker;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * @author nkorange
 */
public class ClusterTest {

    private Cluster cluster;

    @Before
    public void before() {

        Service service = new Service();
        service.setName("nacos.service.1");

        cluster = new Cluster("nacos-cluster-1", service);
        cluster.setDefCkport(80);
        cluster.setDefIPPort(8080);
    }


    @Test
    public void updateCluster() {
        Service service = new Service();
        service.setName("nacos.service.2");

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
    }

    @Test
    public void updateIps() {

        Instance instance1 = new Instance();
        instance1.setIp("1.1.1.1");
        instance1.setPort(1234);

        Instance instance2 = new Instance();
        instance2.setIp("1.1.1.1");
        instance2.setPort(2345);

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

}
