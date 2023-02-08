/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
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


import com.alibaba.nacos.Nacos;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.naming.NamingFactory;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.prometheus.api.ApiConstants;
import com.alibaba.nacos.test.base.HttpClient4Test;
import com.alibaba.nacos.test.base.Params;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.Assert;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.alibaba.nacos.test.naming.NamingBase.randomDomainName;
import static com.alibaba.nacos.test.naming.PrometheusAPI_ITCase.CONTEXT_PATH;

/**
 * @author karsonto
 * @date 2023/02/08
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = Nacos.class, properties = {"server.servlet.context-path=" + CONTEXT_PATH,
        "nacos.prometheus.metrics.enabled=true"}, webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@FixMethodOrder(MethodSorters.JVM)
public class PrometheusAPI_ITCase extends HttpClient4Test {
    
    public static final String CONTEXT_PATH = "/nacos";
    
    private Instance instance;
    
    private String serviceName;
    
    private String INSTANCE_IP = "127.0.0.1";
    
    private int INSTANCE_PORT = 8081;
    
    @LocalServerPort
    private int port;
    
    private NamingService namingService;
    
    @Before
    public void init() throws Exception {
        String url = String.format("http://127.0.0.1:%d/", port);
        this.base = new URL(url);
        if (namingService == null) {
            TimeUnit.SECONDS.sleep(10);
            namingService = NamingFactory.createNamingService("127.0.0.1" + ":" + port);
        }
        instance = new Instance();
        instance.setIp(INSTANCE_IP);
        instance.setPort(INSTANCE_PORT);
        instance.setWeight(2);
        instance.setClusterName(Constants.DEFAULT_CLUSTER_NAME);
        Map<String, String> map = new HashMap<String, String>();
        map.put("netType", "external");
        map.put("version", "1.0");
        instance.setMetadata(map);
        serviceName = randomDomainName();
        namingService.registerInstance(serviceName, instance);
    }
    
    @Test
    public void test() throws Exception {
        metricTest();
    }
    
    public void metricTest() throws Exception {
        ResponseEntity<String> response = request(CONTEXT_PATH + ApiConstants.PROMETHEUS_CONTROLLER_PATH,
                Params.newParams().done(), String.class, HttpMethod.GET);
        Assert.assertTrue(response.getStatusCode().is2xxSuccessful());
        JsonNode jsonNode = JacksonUtils.toObj(response.getBody());
        String targets = jsonNode.get(0).get("targets").get(0).asText();
        Assert.assertEquals(targets, INSTANCE_IP + ":" + INSTANCE_PORT);
        
        
    }
    
    
}
