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

import com.alibaba.nacos.naming.healthcheck.AbstractHealthCheckConfig;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * @author dungu.zpf
 */
public class ClusterTest {

    private Cluster cluster;

    @Before
    public void before() {

        VirtualClusterDomain domain = new VirtualClusterDomain();
        domain.setName("nacos.domain.1");

        cluster = new Cluster();
        cluster.setName("nacos-cluster-1");
        cluster.setDom(domain);
        cluster.setDefCkport(80);
        cluster.setDefIPPort(8080);
    }


    @Test
    public void updateCluster() {

        Cluster newCluster = new Cluster();
        newCluster.setDefCkport(8888);
        newCluster.setDefIPPort(9999);
        AbstractHealthCheckConfig.Http healthCheckConfig = new AbstractHealthCheckConfig.Http();
        healthCheckConfig.setPath("/nacos-path-1");
        healthCheckConfig.setExpectedResponseCode(500);
        healthCheckConfig.setHeaders("Client-Version:nacos-test-1");
        newCluster.setHealthChecker(healthCheckConfig);

        VirtualClusterDomain domain = new VirtualClusterDomain();
        domain.setName("nacos.domain.2");

        newCluster.setDom(domain);

        cluster.update(newCluster);

        Assert.assertEquals(8888, cluster.getDefCkport());
        Assert.assertEquals(9999, cluster.getDefIPPort());
        Assert.assertTrue(cluster.getHealthChecker() instanceof AbstractHealthCheckConfig.Http);
        AbstractHealthCheckConfig.Http httpHealthCheck = (AbstractHealthCheckConfig.Http)(cluster.getHealthChecker());
        Assert.assertEquals("/nacos-path-1", httpHealthCheck.getPath());
        Assert.assertEquals(500, httpHealthCheck.getExpectedResponseCode());
        Assert.assertEquals("Client-Version:nacos-test-1", httpHealthCheck.getHeaders());
    }

    @Test
    public void updateIps() {

        IpAddress ipAddress1 = new IpAddress();
        ipAddress1.setIp("1.1.1.1");
        ipAddress1.setPort(1234);

        IpAddress ipAddress2 = new IpAddress();
        ipAddress2.setIp("1.1.1.1");
        ipAddress2.setPort(2345);

        List<IpAddress> list = new ArrayList<IpAddress>();
        list.add(ipAddress1);
        list.add(ipAddress2);

        cluster.updateIPs(list, false);

        List<IpAddress> ips = cluster.allIPs();
        Assert.assertNotNull(ips);
        Assert.assertEquals(2, ips.size());
        Assert.assertEquals("1.1.1.1", ips.get(0).getIp());
        Assert.assertEquals(1234, ips.get(0).getPort());
        Assert.assertEquals("1.1.1.1", ips.get(1).getIp());
        Assert.assertEquals(2345, ips.get(1).getPort());
    }
}
