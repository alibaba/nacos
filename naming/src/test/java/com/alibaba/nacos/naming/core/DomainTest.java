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
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author dungu.zpf
 */
public class DomainTest {

    private VirtualClusterDomain domain;

    @Before
    public void before() {
        domain = new VirtualClusterDomain();
        domain.setName("nacos.domain.1");
        Cluster cluster = new Cluster();
        cluster.setName(UtilsAndCommons.DEFAULT_CLUSTER_NAME);
        cluster.setDom(domain);
        domain.addCluster(cluster);
    }

    @Test
    public void updateDomain() {

        VirtualClusterDomain newDomain = new VirtualClusterDomain();
        newDomain.setName("nacos.domain.1");
        newDomain.setEnableClientBeat(false);
        newDomain.setEnableHealthCheck(false);
        newDomain.setProtectThreshold(0.7f);
        Cluster cluster = new Cluster();
        cluster.setName(UtilsAndCommons.DEFAULT_CLUSTER_NAME);
        cluster.setDom(newDomain);
        newDomain.addCluster(cluster);

        domain.update(newDomain);

        Assert.assertEquals(false, domain.getEnableClientBeat());
        Assert.assertEquals(false, domain.getEnableHealthCheck());
        Assert.assertEquals(0.7f, domain.getProtectThreshold(), 0.0001f);
    }

    @Test
    public void addCluster() {
        Cluster cluster = new Cluster();
        cluster.setName("nacos-cluster-1");

        domain.addCluster(cluster);

        Map<String, Cluster> clusterMap = domain.getClusterMap();
        Assert.assertNotNull(clusterMap);
        Assert.assertEquals(2, clusterMap.size());
        Assert.assertTrue(clusterMap.containsKey("nacos-cluster-1"));
    }

    @Test
    public void updateIps() throws Exception {

        IpAddress ipAddress = new IpAddress();
        ipAddress.setIp("1.1.1.1");
        ipAddress.setPort(1234);
        List<IpAddress> list = new ArrayList<IpAddress>();
        list.add(ipAddress);

        domain.onChange("iplist", JSON.toJSONString(list));

        List<IpAddress> ips = domain.allIPs();

        Assert.assertNotNull(ips);
        Assert.assertEquals(1, ips.size());
        Assert.assertEquals("1.1.1.1", ips.get(0).getIp());
        Assert.assertEquals(1234, ips.get(0).getPort());
    }
}
