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

package com.alibaba.nacos.test.naming;

import com.alibaba.nacos.api.naming.NamingFactory;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.utils.NamingUtils;
import com.alibaba.nacos.naming.NamingApp;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.alibaba.nacos.test.naming.NamingBase.TEST_PORT;
import static com.alibaba.nacos.test.naming.NamingBase.randomDomainName;
import static com.alibaba.nacos.test.naming.NamingBase.verifyInstanceList;

/**
 * @author liaochuntao
 * @date 2019-07-28 11:25
 **/
@RunWith(SpringRunner.class)
@SpringBootTest(classes = NamingApp.class, properties = {"server.servlet.context-path=/nacos"},
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class NamingMultiGroup_ITCase {


    private String groupName_1 = "groupName_1";
    private String groupName_2 = "groupName_2";
    private String groupName_3 = "groupName_3";

    private String cluster_1 = "cluster1";
    private String cluster_2 = "cluster2";
    private String cluster_3 = "cluster3";

    private NamingService naming;
    @LocalServerPort
    private int port;

    @Before
    public void init() throws Exception {
        NamingBase.prepareServer(port);
        if (naming == null) {
            //TimeUnit.SECONDS.sleep(10);
            naming = NamingFactory.createNamingService("127.0.0.1" + ":" + port);
        }
        while (true) {
            if (!"UP".equals(naming.getServerStatus())) {
                Thread.sleep(1000L);
                continue;
            }
            break;
        }
    }

    @Test
    public void getAllInstancesMultiGroup() throws Exception {
        String serviceName = randomDomainName();
        naming.registerInstance(serviceName, groupName_1, "1.1.1.1", TEST_PORT, cluster_1);
        naming.registerInstance(serviceName, groupName_2, "2.2.2.2", TEST_PORT, cluster_2);
        naming.registerInstance(serviceName, groupName_3, "3.3.3.3", TEST_PORT, cluster_3);

        Map<String, List<String>> clusterMap = new HashMap<>();
        clusterMap.put(NamingUtils.getGroupedName(serviceName, groupName_1), new ArrayList<>(Arrays.asList(cluster_1)));
        clusterMap.put(NamingUtils.getGroupedName(serviceName, groupName_2), new ArrayList<>(Arrays.asList(cluster_2)));
        clusterMap.put(NamingUtils.getGroupedName(serviceName, groupName_3), new ArrayList<>(Arrays.asList(cluster_3)));

        TimeUnit.SECONDS.sleep(1);

        List<Instance> instances = naming.getAllInstancesMultiGroup(serviceName, new ArrayList<>(Arrays.asList(groupName_1, groupName_2, groupName_3)), clusterMap, true);

        Assert.assertEquals(3, instances.size());

    }

    @Test
    public void selectHealthyInstancesMultiGroup() throws Exception {
        String serviceName = randomDomainName();
        naming.registerInstance(serviceName, groupName_1, "1.1.1.1", TEST_PORT, cluster_1);
        naming.registerInstance(serviceName, groupName_2, "2.2.2.2", TEST_PORT, cluster_2);
        naming.registerInstance(serviceName, groupName_3, "3.3.3.3", TEST_PORT, cluster_3);

        Map<String, List<String>> clusterMap = new HashMap<>();
        clusterMap.put(NamingUtils.getGroupedName(serviceName, groupName_1), new ArrayList<>(Arrays.asList(cluster_1)));
        clusterMap.put(NamingUtils.getGroupedName(serviceName, groupName_2), new ArrayList<>(Arrays.asList(cluster_2)));
        clusterMap.put(NamingUtils.getGroupedName(serviceName, groupName_3), new ArrayList<>(Arrays.asList(cluster_3)));

        TimeUnit.SECONDS.sleep(1);

        List<Instance> instances = naming.selectInstancesMultiGroup(serviceName, new ArrayList<>(Arrays.asList(groupName_1, groupName_2, groupName_3)), clusterMap, true, true);

        Assert.assertEquals(3, instances.size());

        naming.deregisterInstance(serviceName, groupName_2, "2.2.2.2", TEST_PORT);

    }

    @Test
    public void selectOneHealthyInstancesMultiGroup() throws Exception {
        String serviceName = randomDomainName();
        naming.registerInstance(serviceName, groupName_1, "1.1.1.1", TEST_PORT, cluster_1);
        naming.registerInstance(serviceName, groupName_2, "2.2.2.2", TEST_PORT, cluster_2);
        naming.registerInstance(serviceName, groupName_3, "3.3.3.3", TEST_PORT, cluster_3);

        Map<String, List<String>> clusterMap = new HashMap<>();
        clusterMap.put(NamingUtils.getGroupedName(serviceName, groupName_1), new ArrayList<>(Arrays.asList(cluster_1)));
        clusterMap.put(NamingUtils.getGroupedName(serviceName, groupName_2), new ArrayList<>(Arrays.asList(cluster_2)));
        clusterMap.put(NamingUtils.getGroupedName(serviceName, groupName_3), new ArrayList<>(Arrays.asList(cluster_3)));

        TimeUnit.SECONDS.sleep(1);

        Instance instance = naming.selectOneHealthyInstanceMultiGroup(serviceName, new ArrayList<>(Arrays.asList(groupName_1, groupName_2, groupName_3)), clusterMap, true);

        Assert.assertNotNull(instance);

    }

}
