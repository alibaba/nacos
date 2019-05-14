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

import java.net.URL;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.nacos.naming.NamingApp;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * @author nkorange
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = NamingApp.class, properties = {"server.servlet.context-path=/nacos",
        "server.port=7001"},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class RestAPI_ITCase {

    @LocalServerPort
    private int port;

    private URL base;

    @Autowired
    private TestRestTemplate restTemplate;

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

        JSONObject json = JSON.parseObject(response.getBody());
        Assert.assertTrue(json.getIntValue("serviceCount") > 0);
        Assert.assertTrue(json.getIntValue("instanceCount") > 0);
        Assert.assertTrue(json.getIntValue("responsibleServiceCount") > 0);
        Assert.assertTrue(json.getIntValue("responsibleInstanceCount") > 0);
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

        JSONObject json = JSON.parseObject(response.getBody());
        Assert.assertEquals(serviceName, json.getString("name"));

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
        JSONObject json = JSON.parseObject(response.getBody());
        int count = json.getIntValue("count");
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
        json = JSON.parseObject(response.getBody());
        Assert.assertEquals(count+1, json.getIntValue("count"));

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
        JSONObject json = JSON.parseObject(response.getBody());
        System.out.println(json);
        Assert.assertEquals(0.3f, json.getFloatValue("protectThreshold"), 0.0f);

        namingServiceDelete(serviceName);
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

    private <T> ResponseEntity<T> request(String path, MultiValueMap<String, String> params, Class<T> clazz) {

        HttpHeaders headers = new HttpHeaders();

        HttpEntity<?> entity = new HttpEntity<T>(headers);

        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(this.base.toString() + path)
                .queryParams(params);

        return this.restTemplate.exchange(
                builder.toUriString(), HttpMethod.GET, entity, clazz);
    }

    private <T> ResponseEntity<T> request(String path, MultiValueMap<String, String> params, Class<T> clazz, HttpMethod httpMethod) {

        HttpHeaders headers = new HttpHeaders();

        HttpEntity<?> entity = new HttpEntity<T>(headers);

        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(this.base.toString() + path)
            .queryParams(params);

        return this.restTemplate.exchange(
            builder.toUriString(), httpMethod, entity, clazz);
    }

    private void prepareData() {

        ResponseEntity<String> responseEntity = request("/nacos/v1/ns/api/regDom",
                Params.newParams()
                        .appendParam("dom", NamingBase.TEST_DOM_1)
                        .appendParam("cktype", "TCP")
                        .appendParam("token", "abc")
                        .done(),
                String.class);

        if (responseEntity.getStatusCode().isError()) {
            throw new RuntimeException("before test: register domain failed!" + responseEntity.toString());
        }

        try {
            Thread.sleep(100L);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        responseEntity = request("/nacos/v1/ns/api/addIP4Dom",
                Params.newParams()
                        .appendParam("dom", NamingBase.TEST_DOM_1)
                        .appendParam("ipList", NamingBase.TEST_IP_4_DOM_1 + ":" + NamingBase.TEST_PORT_4_DOM_1)
                        .appendParam("token", NamingBase.TEST_TOKEN_4_DOM_1).done(),
                String.class);

        if (responseEntity.getStatusCode().isError()) {
            throw new RuntimeException("before test: add ip for domain failed!" + responseEntity.toString());
        }
    }

    private void removeData() {

        ResponseEntity<String> responseEntity = request("/nacos/v1/ns/api/remvDom",
                Params.newParams()
                        .appendParam("dom", NamingBase.TEST_DOM_1)
                        .appendParam("token", "abc")
                        .done(),
                String.class);

        if (responseEntity.getStatusCode().isError()) {
            throw new RuntimeException("before test: remove domain failed!" + responseEntity.toString());
        }
    }

}
