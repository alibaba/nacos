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

package com.alibaba.nacos.test.common;

import com.alibaba.nacos.Nacos;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.http.HttpClientBeanHolder;
import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.http.client.NacosRestTemplate;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.model.RestResult;
import com.alibaba.nacos.config.server.model.ConfigInfo4Beta;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.MethodOrderer.MethodName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * NacosRestTemplate_ITCase.
 *
 * @author mai.jh
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = Nacos.class, properties = {
        "server.servlet.context-path=/nacos"}, webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@TestMethodOrder(MethodName.class)
class NacosRestTemplate_ITCase {
    
    private final String INSTANCE_PATH = "/nacos/v1/ns";
    
    private final String CONFIG_PATH = "/nacos/v1/cs";
    
    @LocalServerPort
    private int port;
    
    private NacosRestTemplate nacosRestTemplate = HttpClientBeanHolder.getNacosRestTemplate(
            LoggerFactory.getLogger(NacosRestTemplate_ITCase.class));
    
    private String IP = null;
    
    @BeforeEach
    void init() throws NacosException {
        IP = String.format("http://localhost:%d", port);
    }
    
    @Test
    void test_url_post_config() throws Exception {
        String url = IP + CONFIG_PATH + "/configs";
        Map<String, String> param = new HashMap<>();
        param.put("dataId", "test-1");
        param.put("group", "DEFAULT_GROUP");
        param.put("content", "aaa=b");
        HttpRestResult<String> restResult = nacosRestTemplate.postForm(url, Header.newInstance(), param, String.class);
        assertTrue(restResult.ok());
        System.out.println(restResult.getData());
        System.out.println(restResult.getHeader());
    }
    
    @Test
    void test_url_get_return_restResult() throws Exception {
        String url = IP + CONFIG_PATH + "/configs";
        Query query = Query.newInstance().addParam("beta", true).addParam("dataId", "test-1").addParam("group", "DEFAULT_GROUP");
        HttpRestResult<ConfigInfo4Beta> restResult = nacosRestTemplate.get(url, Header.newInstance(), query,
                new TypeReference<RestResult<ConfigInfo4Beta>>() {
                }.getType());
        assertTrue(restResult.ok());
        System.out.println(restResult.getData());
        System.out.println(restResult.getHeader());
    }
    
    
    @Test
    void test_url_post_form() throws Exception {
        String url = IP + INSTANCE_PATH + "/instance";
        Map<String, String> param = new HashMap<>();
        param.put("serviceName", "app-test");
        param.put("port", "8080");
        param.put("ip", "11.11.11.11");
        HttpRestResult<String> restResult = nacosRestTemplate.postForm(url, Header.newInstance(), param, String.class);
        assertTrue(restResult.ok());
        System.out.println(restResult.getData());
    }
    
    @Test
    @Disabled("new version can't update instance when service and instance is not exist")
    void test_url_put_from() throws Exception {
        String url = IP + INSTANCE_PATH + "/instance";
        Map<String, String> param = new HashMap<>();
        param.put("serviceName", "app-test-change");
        param.put("port", "8080");
        param.put("ip", "11.11.11.11");
        HttpRestResult<String> restResult = nacosRestTemplate.putForm(url, Header.newInstance(), param, String.class);
        assertTrue(restResult.ok());
        System.out.println(restResult.getData());
    }
    
    @Test
    void test_url_get() throws Exception {
        String url = IP + INSTANCE_PATH + "/instance/list";
        Query query = Query.newInstance().addParam("serviceName", "app-test");
        HttpRestResult<Map> restResult = nacosRestTemplate.get(url, Header.newInstance(), query, Map.class);
        assertTrue(restResult.ok());
        assertEquals("DEFAULT_GROUP@@app-test", restResult.getData().get("name"));
        System.out.println(restResult.getData());
    }
    
    @Test
    void test_url_get_by_map() throws Exception {
        String url = IP + INSTANCE_PATH + "/instance/list";
        Map<String, String> param = new HashMap<>();
        param.put("serviceName", "app-test");
        HttpRestResult<Map> restResult = nacosRestTemplate.get(url, Header.newInstance(), Query.newInstance().initParams(param), Map.class);
        assertTrue(restResult.ok());
        assertEquals("DEFAULT_GROUP@@app-test", restResult.getData().get("name"));
        System.out.println(restResult.getData());
    }
    
    @Test
    void test_url_delete() throws Exception {
        String url = IP + INSTANCE_PATH + "/instance";
        Query query = Query.newInstance().addParam("ip", "11.11.11.11").addParam("port", "8080").addParam("serviceName", "app-test");
        HttpRestResult<String> restResult = nacosRestTemplate.delete(url, Header.newInstance(), query, String.class);
        assertTrue(restResult.ok());
        System.out.println(restResult);
    }
    
}
