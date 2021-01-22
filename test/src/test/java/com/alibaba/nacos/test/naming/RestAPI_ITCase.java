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

import com.alibaba.nacos.Nacos;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.test.base.Params;
import com.fasterxml.jackson.databind.JsonNode;

import org.junit.*;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import java.net.URL;

/**
 * @author nkorange
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = Nacos.class, properties = {"server.servlet.context-path=/nacos"},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class RestAPI_ITCase extends NamingBase {

    @LocalServerPort
    private int port;

    @Before
    public void setUp() throws Exception {
        String url = String.format("http://localhost:%d/", port);
        this.base = new URL(url);
        //prepareData();
    }

    @After
    public void cleanup() throws Exception {
        //removeData();
    }

    @Test
    public void metrics() throws Exception {

        ResponseEntity<String> response = request("/nacos/v1/ns/operator/metrics",
            Params.newParams()
                .done(),
            String.class);

        Assert.assertTrue(response.getStatusCode().is2xxSuccessful());

        JsonNode json = JacksonUtils.toObj(response.getBody());
        Assert.assertTrue(json.get("serviceCount").asInt() > 0);
        Assert.assertTrue(json.get("instanceCount").asInt() > 0);
        Assert.assertTrue(json.get("responsibleServiceCount").asInt() > 0);
        Assert.assertTrue(json.get("responsibleInstanceCount").asInt() > 0);
    }

    /**
     * @TCDescription : 根据serviceName创建服务
     * @TestStep :
     * @ExpectResult :
     */
    @Test
    public void createService() throws Exception {
        String serviceName = NamingBase.randomDomainName();
        ResponseEntity<String> response = request(NamingBase.NAMING_CONTROLLER_PATH + "/service",
            Params.newParams()
                .appendParam("serviceName", serviceName)
                .appendParam("protectThreshold", "0.3")
                .done(),
            String.class,
            HttpMethod.POST);
        Assert.assertTrue(response.getStatusCode().is2xxSuccessful());
        Assert.assertEquals("ok", response.getBody());

        namingServiceDelete(serviceName);
    }

    /**
     * @TCDescription : 根据serviceName获取服务信息
     * @TestStep :
     * @ExpectResult :
     */
    @Test
    public void getService() throws Exception {
        String serviceName = NamingBase.randomDomainName();
        ResponseEntity<String> response = request(NamingBase.NAMING_CONTROLLER_PATH + "/service",
            Params.newParams()
                .appendParam("serviceName", serviceName)
                .appendParam("protectThreshold", "0.3")
                .done(),
            String.class,
            HttpMethod.POST);
        Assert.assertTrue(response.getStatusCode().is2xxSuccessful());
        Assert.assertEquals("ok", response.getBody());

        //get service
        response = request(NamingBase.NAMING_CONTROLLER_PATH + "/service",
            Params.newParams()
                .appendParam("serviceName", serviceName)
                .appendParam("protectThreshold", "0.3")
                .done(),
            String.class);

        Assert.assertTrue(response.getStatusCode().is2xxSuccessful());

        JsonNode json = JacksonUtils.toObj(response.getBody());
        Assert.assertEquals(serviceName, json.get("name").asText());

        namingServiceDelete(serviceName);
    }

    /**
     * @TCDescription : 获取服务list信息
     * @TestStep :
     * @ExpectResult :
     */
    @Test
    public void listService() throws Exception {
        String serviceName = NamingBase.randomDomainName();
        //get service
        ResponseEntity<String> response = request(NamingBase.NAMING_CONTROLLER_PATH + "/service/list",
            Params.newParams()
                .appendParam("serviceName", serviceName)
                .appendParam("pageNo", "1")
                .appendParam("pageSize", "15")
                .done(),
            String.class);

        Assert.assertTrue(response.getStatusCode().is2xxSuccessful());
        JsonNode json = JacksonUtils.toObj(response.getBody());
        int count = json.get("count").asInt();
        Assert.assertTrue(count >= 0);

        response = request(NamingBase.NAMING_CONTROLLER_PATH + "/service",
            Params.newParams()
                .appendParam("serviceName", serviceName)
                .appendParam("protectThreshold", "0.3")
                .done(),
            String.class,
            HttpMethod.POST);
        Assert.assertTrue(response.getStatusCode().is2xxSuccessful());
        Assert.assertEquals("ok", response.getBody());

        response = request(NamingBase.NAMING_CONTROLLER_PATH + "/service/list",
            Params.newParams()
                .appendParam("serviceName", serviceName)
                .appendParam("pageNo", "1")
                .appendParam("pageSize", "15")
                .done(),
            String.class);

        Assert.assertTrue(response.getStatusCode().is2xxSuccessful());
        json = JacksonUtils.toObj(response.getBody());
        Assert.assertEquals(count + 1, json.get("count").asInt());

        namingServiceDelete(serviceName);
    }

    /**
     * @TCDescription : 更新serviceName获取服务信息
     * @TestStep :
     * @ExpectResult :
     */
    @Test
    public void updateService() throws Exception {
        String serviceName = NamingBase.randomDomainName();
        ResponseEntity<String> response = request(NamingBase.NAMING_CONTROLLER_PATH + "/service",
            Params.newParams()
                .appendParam("serviceName", serviceName)
                .appendParam("protectThreshold", "0.6")
                .done(),
            String.class,
            HttpMethod.POST);
        Assert.assertTrue(response.getStatusCode().is2xxSuccessful());
        Assert.assertEquals("ok", response.getBody());

        //update service
        response = request(NamingBase.NAMING_CONTROLLER_PATH + "/service",
            Params.newParams()
                .appendParam("serviceName", serviceName)
                .appendParam("healthCheckMode", "server")
                .appendParam("protectThreshold", "0.3")
                .done(),
            String.class,
            HttpMethod.PUT);

        Assert.assertTrue(response.getStatusCode().is2xxSuccessful());
        Assert.assertEquals("ok", response.getBody());

        //get service
        response = request(NamingBase.NAMING_CONTROLLER_PATH + "/service",
            Params.newParams()
                .appendParam("serviceName", serviceName)
                .done(),
            String.class);

        Assert.assertTrue(response.getStatusCode().is2xxSuccessful());
        JsonNode json = JacksonUtils.toObj(response.getBody());
        System.out.println(json);
        Assert.assertEquals(0.3f, json.get("protectThreshold").floatValue(), 0.0f);

        namingServiceDelete(serviceName);
    }

    @Test
    @Ignore
    public void testInvalidNamespace() {

        String serviceName = NamingBase.randomDomainName();
        ResponseEntity<String> response = request(NamingBase.NAMING_CONTROLLER_PATH + "/service",
            Params.newParams()
                .appendParam("serviceName", serviceName)
                .appendParam("protectThreshold", "0.6")
                .appendParam("namespaceId", "..invalid-namespace")
                .done(),
            String.class,
            HttpMethod.POST);
        Assert.assertTrue(response.getStatusCode().is4xxClientError());

        response = request(NamingBase.NAMING_CONTROLLER_PATH + "/service",
            Params.newParams()
                .appendParam("serviceName", serviceName)
                .appendParam("protectThreshold", "0.6")
                .appendParam("namespaceId", "/invalid-namespace")
                .done(),
            String.class,
            HttpMethod.POST);
        Assert.assertTrue(response.getStatusCode().is4xxClientError());

    }

    private void namingServiceDelete(String serviceName) {
        //delete service
        ResponseEntity<String> response = request(NamingBase.NAMING_CONTROLLER_PATH + "/service",
            Params.newParams()
                .appendParam("serviceName", serviceName)
                .appendParam("protectThreshold", "0.3")
                .done(),
            String.class,
            HttpMethod.DELETE);

        Assert.assertTrue(response.getStatusCode().is2xxSuccessful());
        Assert.assertEquals("ok", response.getBody());
    }

}
