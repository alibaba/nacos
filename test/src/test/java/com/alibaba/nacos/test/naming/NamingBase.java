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

import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.client.naming.remote.http.NamingHttpClientManager;
import com.alibaba.nacos.common.constant.HttpHeaderConsts;
import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.http.client.NacosRestTemplate;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.test.base.HttpClient4Test;
import org.apache.http.HttpStatus;
import org.junit.Assert;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author nkorange
 */
public class NamingBase extends HttpClient4Test {

    private static final NacosRestTemplate nacosRestTemplate = NamingHttpClientManager.getInstance().getNacosRestTemplate();

    public static final String TEST_DOM_1 = "nacos.test.1";
    public static final String TEST_IP_4_DOM_1 = "127.0.0.1";
    public static final String TEST_PORT_4_DOM_1 = "8080";
    public static final String TEST_PORT2_4_DOM_1 = "8888";
    public static final String TEST_PORT3_4_DOM_1 = "80";
    public static final String TEST_TOKEN_4_DOM_1 = "abc";
    public static final String TEST_NEW_CLUSTER_4_DOM_1 = "TEST1";

    public static final String TEST_DOM_2 = "nacos.test.2";
    public static final String TEST_IP_4_DOM_2 = "127.0.0.2";
    public static final String TEST_PORT_4_DOM_2 = "7070";
    public static final String TETS_TOKEN_4_DOM_2 = "xyz";
    public static final String TEST_SERVER_STATUS = "UP";

    public static final String TEST_GROUP = "group";
    public static final String TEST_GROUP_1 = "group1";
    public static final String TEST_GROUP_2 = "group2";

    public static final String TEST_NAMESPACE_1 = "namespace-1";
    public static final String TEST_NAMESPACE_2 = "namespace-2";

    static final String NAMING_CONTROLLER_PATH = "/nacos/v1/ns";

    public static final int TEST_PORT = 8080;

    public static final int TIME_OUT = 3000;

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

        instance.setServiceName(serviceName);
        instance.setClusterName("c1");

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

    public static void prepareServer(int localPort) throws Exception{
        prepareServer(localPort, "UP", "/nacos");
    }
    
    public static void prepareServer(int localPort,String contextPath) throws Exception{
        prepareServer(localPort, "UP", contextPath);
    }

    public static void prepareServer(int localPort, String status,String contextPath) throws Exception {
        String url = "http://127.0.0.1:" + localPort + normalizeContextPath(contextPath) + "/v1/ns/operator/switches?entry=overriddenServerStatus&value=" + status;
        Header header = Header.newInstance();
        header.addParam(HttpHeaderConsts.USER_AGENT_HEADER, "Nacos-Server");
        HttpRestResult<String> result = nacosRestTemplate.putForm(url, header, new HashMap<>(), String.class);
        System.out.println(result);
        Assert.assertEquals(HttpStatus.SC_OK, result.getCode());

        url = "http://127.0.0.1:" + localPort + normalizeContextPath(contextPath) + "/v1/ns/operator/switches?entry=autoChangeHealthCheckEnabled&value=" + false;

        result = nacosRestTemplate.putForm(url, header, new HashMap<>(), String.class);
        System.out.println(result);
        Assert.assertEquals(HttpStatus.SC_OK, result.getCode());
    }
    
    public static void destoryServer(int localPort) throws Exception{
        destoryServer(localPort, "/nacos");
    }
    
    public static void destoryServer(int localPort, String contextPath) throws Exception{
        String url = "http://127.0.0.1:" + localPort + normalizeContextPath(contextPath) + "/v1/ns/operator/switches?entry=autoChangeHealthCheckEnabled&value=" + true;
        Header header = Header.newInstance();
        header.addParam(HttpHeaderConsts.USER_AGENT_HEADER, "Nacos-Server");

        HttpRestResult<String> result = nacosRestTemplate.putForm(url, header, new HashMap<>(), String.class);
        System.out.println(result);
        Assert.assertEquals(HttpStatus.SC_OK, result.getCode());
    }
    
    public static String normalizeContextPath(String contextPath) {
        if (StringUtils.isBlank(contextPath) || "/".equals(contextPath)) {
            return StringUtils.EMPTY;
        }
        return contextPath.startsWith("/") ? contextPath : "/" + contextPath;
    }
}
