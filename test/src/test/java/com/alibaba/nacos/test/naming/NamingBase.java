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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.alibaba.nacos.api.naming.pojo.AbstractHealthChecker;
import com.alibaba.nacos.api.naming.pojo.Cluster;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.Service;

/**
 * @author dungu.zpf
 */
public class NamingBase {


    public static final String TEST_DOM_1 = "nacos.test.1";
    public static final String TEST_IP_4_DOM_1 = "127.0.0.1";
    public static final String TEST_PORT_4_DOM_1 = "8080";
    public static final String TEST_PORT2_4_DOM_1 = "8888";
    public static final String TEST_TOKEN_4_DOM_1 = "abc";
    public static final String TEST_NEW_CLUSTER_4_DOM_1 = "TEST1";

    public static final String TEST_DOM_2 = "nacos.test.2";
    public static final String TEST_IP_4_DOM_2 = "127.0.0.2";
    public static final String TEST_PORT_4_DOM_2 = "7070";
    public static final String TETS_TOKEN_4_DOM_2 = "xyz";

    public static final int TEST_PORT = 8080;

    public static String randomDomainName() {
        StringBuilder sb = new StringBuilder();
        sb.append("jinhan");
        for (int i = 0; i < 2; i++) {
            sb.append(RandomUtils.getStringWithNumAndCha(5));
            sb.append(".");
        }
        int i = RandomUtils.getIntegerBetween(0, 2);
        if (i == 0) {
            sb.append("com");
        } else {
            sb.append("net");
        }
        return sb.toString();
    }

    public static Instance getInstance(String serviceName) {
        Instance instance = new Instance();
        instance.setIp("127.0.0.1");
        instance.setPort(TEST_PORT);
        instance.setHealthy(true);
        instance.setWeight(2.0);
        Map<String, String> instanceMeta = new HashMap<String, String>();
        instanceMeta.put("site", "et2");
        instance.setMetadata(instanceMeta);

        Service service = new Service(serviceName);
        service.setApp("nacos-naming");
        service.setHealthCheckMode("server");
        service.setProtectThreshold(0.8F);
        service.setGroup("CNCF");
        Map<String, String> serviceMeta = new HashMap<String, String>();
        serviceMeta.put("symmetricCall", "true");
        service.setMetadata(serviceMeta);
        instance.setService(service);

        Cluster cluster = new Cluster();
        cluster.setName("c1");
        AbstractHealthChecker.Http healthChecker = new AbstractHealthChecker.Http();
        healthChecker.setExpectedResponseCode(400);
        healthChecker.setHeaders("Client-Version|Nacos");
        healthChecker.setPath("/xxx.html");
        cluster.setHealthChecker(healthChecker);
        Map<String, String> clusterMeta = new HashMap<String, String>();
        clusterMeta.put("xxx", "yyyy");
        cluster.setMetadata(clusterMeta);

        instance.setCluster(cluster);

        return instance;
    }

    public static boolean verifyInstance(Instance i1, Instance i2) {

        if (!i1.getIp().equals(i2.getIp()) || i1.getPort() != i2.getPort() ||
            i1.getWeight() != i2.getWeight() || i1.isHealthy() != i2.isHealthy() ||
            !i1.getMetadata().equals(i2.getMetadata())) {
            return false;
        }

        //Service service1 = i1.getService();
        //Service service2 = i2.getService();
        //
        //if (!service1.getApp().equals(service2.getApp()) || !service1.getGroup().equals(service2.getGroup()) ||
        //    !service1.getMetadata().equals(service2.getMetadata()) || !service1.getName().equals(service2.getName()) ||
        //    service1.getProtectThreshold() != service2.getProtectThreshold() ||
        //    service1.isEnableClientBeat() != service2.isEnableClientBeat() ||
        //    service1.isEnableHealthCheck() != service2.isEnableHealthCheck()) {
        //    return false;
        //}

        //Cluster cluster1 = i1.getCluster();
        //Cluster cluster2 = i2.getCluster();
        //
        //if (!cluster1.getName().equals(cluster2.getName()) ||
        //    cluster1.getDefaultCheckPort() != cluster2.getDefaultCheckPort() ||
        //    cluster1.getDefaultPort() != cluster2.getDefaultPort() ||
        //    !cluster1.getServiceName().equals(cluster2.getServiceName()) ||
        //    !cluster1.getMetadata().equals(cluster2.getMetadata())||
        //    cluster1.isUseIPPort4Check() != cluster2.isUseIPPort4Check()) {
        //    return false;
        //}
        //
        //HealthChecker healthChecker1 = cluster1.getHealthChecker();
        //HealthChecker healthChecker2 = cluster2.getHealthChecker();
        //
        //if (healthChecker1.getClass().getName() != healthChecker2.getClass().getName()) {
        //    return false;
        //}
        //
        //if (healthChecker1 instanceof HealthChecker.Http) {
        //    HealthChecker.Http h1 = (HealthChecker.Http) healthChecker1;
        //    HealthChecker.Http h2 = (HealthChecker.Http) healthChecker2;
        //
        //    if (h1.getExpectedResponseCode() != h2.getExpectedResponseCode() ||
        //        !h1.getHeaders().equals(h2.getHeaders()) ||
        //        !h1.getPath().equals(h2.getPath()) ||
        //        !h1.getCustomHeaders().equals(h2.getCustomHeaders())) {
        //        return false;
        //    }
        //}

        return true;

    }

    public static boolean verifyInstanceList(List<Instance> instanceList1, List<Instance> instanceList2) {
        Map<String, Instance> instanceMap = new HashMap<String, Instance>();
        for (Instance instance : instanceList1) {
            instanceMap.put(instance.getIp(), instance);
        }

        Map<String, Instance> instanceGetMap = new HashMap<String, Instance>();
        for (Instance instance : instanceList2) {
            instanceGetMap.put(instance.getIp(), instance);
        }

        for (String ip : instanceMap.keySet()) {
            if (!instanceGetMap.containsKey(ip)) {
                return false;
            }
            if (!verifyInstance(instanceMap.get(ip), instanceGetMap.get(ip))) {
                return false;
            }
        }
        return true;
    }
}
