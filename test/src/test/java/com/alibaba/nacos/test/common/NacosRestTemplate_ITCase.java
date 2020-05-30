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
import com.alibaba.nacos.common.http.HttpClientManager;
import com.alibaba.nacos.common.http.client.NacosRestTemplate;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.model.RestResult;
import org.junit.Assert;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.HashMap;
import java.util.Map;

/**
 *  NacosRestTemplate_ITCase
 *
 * @author mai.jh
 * @date 2020/5/30
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = Nacos.class, properties = {"server.servlet.context-path=/nacos"},
    webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@FixMethodOrder(MethodSorters.JVM)
public class NacosRestTemplate_ITCase {

    private NacosRestTemplate nacosRestTemplate = HttpClientManager.getNacosRestTemplate();


    private final String CONFIG_INSTANCE_PATH = "/nacos/v1/ns";
    private final String IP = "http://127.0.0.1:8848";

    @Test
    public void test_url_post_from() throws Exception{
        String url = IP + CONFIG_INSTANCE_PATH + "/instance";
        Map<String, String> param = new HashMap<>();
        param.put("serviceName", "app-test");
        param.put("port", "8080");
        param.put("ip", "11.11.11.11");
        RestResult<String> restResult = nacosRestTemplate.postFrom(url, Header.newInstance(), Query.newInstance(), param, String.class);
        Assert.assertTrue(restResult.ok());
        System.out.println(restResult.getData());
    }

    @Test
    public void test_url_put_from() throws Exception{
        String url = IP + CONFIG_INSTANCE_PATH + "/instance";
        Map<String, String> param = new HashMap<>();
        param.put("serviceName", "app-test-change");
        param.put("port", "8080");
        param.put("ip", "11.11.11.11");
        RestResult<String> restResult = nacosRestTemplate.putFrom(url, Header.newInstance(), Query.newInstance(), param, String.class);
        Assert.assertTrue(restResult.ok());
        System.out.println(restResult.getData());
    }

    @Test
    public void test_url_get() throws Exception {
        String url = IP + CONFIG_INSTANCE_PATH + "/instance/list";
        Query query = Query.newInstance().addParam("serviceName", "app-test");
        RestResult<Map> restResult = nacosRestTemplate.get(url, Header.newInstance(), query, Map.class);
        Assert.assertTrue(restResult.ok());
        Assert.assertEquals(restResult.getData().get("dom"), "app-test");
        System.out.println(restResult.getData());
    }

    @Test
    public void test_url_get_by_map() throws Exception {
        String url = IP + CONFIG_INSTANCE_PATH + "/instance/list";
        Map<String, String> param = new HashMap<>();
        param.put("serviceName", "app-test");
        RestResult<Map> restResult = nacosRestTemplate.get(url, Header.newInstance(), param, Map.class);
        Assert.assertTrue(restResult.ok());
        Assert.assertEquals(restResult.getData().get("dom"), "app-test");
        System.out.println(restResult.getData());
    }

    @Test
    public void test_url_delete() throws Exception{
        String url = IP + CONFIG_INSTANCE_PATH + "/instance";
        Query query = Query.newInstance()
            .addParam("ip", "11.11.11.11")
            .addParam("port", "8080")
            .addParam("serviceName", "app-test");
        RestResult<Object> restResult = nacosRestTemplate.delete(url, Header.newInstance(), query,  RestResult.class);
        Assert.assertTrue(restResult.ok());
        System.out.println(restResult.getData());
    }








}
