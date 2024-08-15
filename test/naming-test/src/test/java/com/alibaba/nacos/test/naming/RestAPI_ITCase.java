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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.net.URL;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author nkorange
 */
@SpringBootTest(classes = Nacos.class, properties = {
        "server.servlet.context-path=/nacos"}, webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
class RestAPI_ITCase extends NamingBase {
    
    @LocalServerPort
    private int port;
    
    @BeforeEach
    void setUp() throws Exception {
        String url = String.format("http://localhost:%d/", port);
        this.base = new URL(url);
        isNamingServerReady();
        //prepareData();
    }
    
    @AfterEach
    void cleanup() throws Exception {
        //removeData();
    }
    
    @Test
    void metrics() throws Exception {
        
        ResponseEntity<String> response = request("/nacos/v1/ns/operator/metrics",
                Params.newParams().appendParam("onlyStatus", "false").done(), String.class);
        
        assertTrue(response.getStatusCode().is2xxSuccessful());
        
        JsonNode json = JacksonUtils.toObj(response.getBody());
        assertNotNull(json.get("serviceCount"));
        assertNotNull(json.get("instanceCount"));
        assertNotNull(json.get("responsibleInstanceCount"));
        assertNotNull(json.get("clientCount"));
        assertNotNull(json.get("connectionBasedClientCount"));
        assertNotNull(json.get("ephemeralIpPortClientCount"));
        assertNotNull(json.get("persistentIpPortClientCount"));
        assertNotNull(json.get("responsibleClientCount"));
    }
    
    /**
     * @TCDescription : 根据serviceName创建服务
     * @TestStep :
     * @ExpectResult :
     */
    @Test
    void createService() throws Exception {
        String serviceName = NamingBase.randomDomainName();
        ResponseEntity<String> response = request(NamingBase.NAMING_CONTROLLER_PATH + "/service",
                Params.newParams().appendParam("serviceName", serviceName).appendParam("protectThreshold", "0.3")
                        .done(), String.class, HttpMethod.POST);
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertEquals("ok", response.getBody());
        
        namingServiceDelete(serviceName);
    }
    
    /**
     * @TCDescription : 根据serviceName获取服务信息
     * @TestStep :
     * @ExpectResult :
     */
    @Test
    void getService() throws Exception {
        String serviceName = NamingBase.randomDomainName();
        ResponseEntity<String> response = request(NamingBase.NAMING_CONTROLLER_PATH + "/service",
                Params.newParams().appendParam("serviceName", serviceName).appendParam("protectThreshold", "0.3")
                        .done(), String.class, HttpMethod.POST);
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertEquals("ok", response.getBody());
        
        //get service
        response = request(NamingBase.NAMING_CONTROLLER_PATH + "/service",
                Params.newParams().appendParam("serviceName", serviceName).appendParam("protectThreshold", "0.3")
                        .done(), String.class);
        
        assertTrue(response.getStatusCode().is2xxSuccessful());
        
        JsonNode json = JacksonUtils.toObj(response.getBody());
        assertEquals(serviceName, json.get("name").asText());
        
        namingServiceDelete(serviceName);
    }
    
    /**
     * @TCDescription : 获取服务list信息
     * @TestStep :
     * @ExpectResult :
     */
    @Test
    void listService() throws Exception {
        String serviceName = NamingBase.randomDomainName();
        //get service
        ResponseEntity<String> response = request(NamingBase.NAMING_CONTROLLER_PATH + "/service/list",
                Params.newParams().appendParam("serviceName", serviceName).appendParam("pageNo", "1")
                        .appendParam("pageSize", "150").done(), String.class);
        
        assertTrue(response.getStatusCode().is2xxSuccessful());
        JsonNode json = JacksonUtils.toObj(response.getBody());
        int count = json.get("count").asInt();
        assertTrue(count >= 0);
        
        response = request(NamingBase.NAMING_CONTROLLER_PATH + "/service",
                Params.newParams().appendParam("serviceName", serviceName).appendParam("protectThreshold", "0.3")
                        .done(), String.class, HttpMethod.POST);
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertEquals("ok", response.getBody());
        
        response = request(NamingBase.NAMING_CONTROLLER_PATH + "/service/list",
                Params.newParams().appendParam("serviceName", serviceName).appendParam("pageNo", "1")
                        .appendParam("pageSize", "150").done(), String.class);
        
        assertTrue(response.getStatusCode().is2xxSuccessful());
        json = JacksonUtils.toObj(response.getBody());
        assertEquals(count + 1, json.get("count").asInt());
        
        namingServiceDelete(serviceName);
    }
    
    /**
     * @TCDescription : 更新serviceName获取服务信息
     * @TestStep :
     * @ExpectResult :
     */
    @Test
    void updateService() throws Exception {
        String serviceName = NamingBase.randomDomainName();
        ResponseEntity<String> response = request(NamingBase.NAMING_CONTROLLER_PATH + "/service",
                Params.newParams().appendParam("serviceName", serviceName).appendParam("protectThreshold", "0.6")
                        .done(), String.class, HttpMethod.POST);
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertEquals("ok", response.getBody());
        
        //update service
        response = request(NamingBase.NAMING_CONTROLLER_PATH + "/service",
                Params.newParams().appendParam("serviceName", serviceName).appendParam("healthCheckMode", "server")
                        .appendParam("protectThreshold", "0.3").done(), String.class, HttpMethod.PUT);
        
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertEquals("ok", response.getBody());
        
        //get service
        response = request(NamingBase.NAMING_CONTROLLER_PATH + "/service",
                Params.newParams().appendParam("serviceName", serviceName).done(), String.class);
        
        assertTrue(response.getStatusCode().is2xxSuccessful());
        JsonNode json = JacksonUtils.toObj(response.getBody());
        System.out.println(json);
        assertEquals(0.3f, json.get("protectThreshold").floatValue(), 0.0f);
        
        namingServiceDelete(serviceName);
    }
    
    @Test
    @Disabled
    void testInvalidNamespace() {
        
        String serviceName = NamingBase.randomDomainName();
        ResponseEntity<String> response = request(NamingBase.NAMING_CONTROLLER_PATH + "/service",
                Params.newParams().appendParam("serviceName", serviceName).appendParam("protectThreshold", "0.6")
                        .appendParam("namespaceId", "..invalid-namespace").done(), String.class, HttpMethod.POST);
        assertTrue(response.getStatusCode().is4xxClientError());
        
        response = request(NamingBase.NAMING_CONTROLLER_PATH + "/service",
                Params.newParams().appendParam("serviceName", serviceName).appendParam("protectThreshold", "0.6")
                        .appendParam("namespaceId", "/invalid-namespace").done(), String.class, HttpMethod.POST);
        assertTrue(response.getStatusCode().is4xxClientError());
        
    }
    
    private void namingServiceDelete(String serviceName) {
        //delete service
        ResponseEntity<String> response = request(NamingBase.NAMING_CONTROLLER_PATH + "/service",
                Params.newParams().appendParam("serviceName", serviceName).appendParam("protectThreshold", "0.3")
                        .done(), String.class, HttpMethod.DELETE);
        
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertEquals("ok", response.getBody());
    }
    
}
