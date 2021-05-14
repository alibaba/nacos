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
import com.alibaba.nacos.common.http.Callback;
import com.alibaba.nacos.common.http.HttpClientBeanHolder;
import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.http.client.NacosAsyncRestTemplate;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.model.RestResult;
import org.junit.Assert;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.HashMap;
import java.util.Map;

/**
 * NacosAsyncRestTemplate_ITCase.
 *
 * @author mai.jh
 */
@SuppressWarnings("all")
@FixMethodOrder(MethodSorters.JVM)
@RunWith(SpringRunner.class)
@SpringBootTest(classes = Nacos.class, properties = {
        "server.servlet.context-path=/nacos"}, webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
public class NacosAsyncRestTemplate_ITCase {
    
    @LocalServerPort
    private int port;
    
    private NacosAsyncRestTemplate nacosRestTemplate = HttpClientBeanHolder
            .getNacosAsyncRestTemplate(LoggerFactory.getLogger(NacosAsyncRestTemplate_ITCase.class));
    
    private final String CONFIG_INSTANCE_PATH = "/nacos/v1/ns";
    
    private String IP = null;
    
    @Before
    public void init() throws NacosException {
        IP = String.format("http://localhost:%d", port);
    }
    
    private class CallbackMap<T> implements Callback<T> {
        
        private HttpRestResult<T> restResult;
        
        private Throwable throwable;
        
        @Override
        public void onReceive(RestResult<T> result) {
            restResult = (HttpRestResult<T>) result;
        }
        
        @Override
        public void onError(Throwable throwable) {
            this.throwable = throwable;
        }
    
        @Override
        public void onCancel() {
        
        }
        
        public HttpRestResult<T> getRestResult() {
            return restResult;
        }
        
        public Throwable getThrowable() {
            return throwable;
        }
    }
    
    @Test
    public void test_url_post_form() throws Exception {
        String url = IP + CONFIG_INSTANCE_PATH + "/instance";
        Map<String, String> param = new HashMap<>();
        param.put("serviceName", "app-test");
        param.put("port", "8080");
        param.put("ip", "11.11.11.11");
        CallbackMap<String> callbackMap = new CallbackMap<>();
        nacosRestTemplate.postForm(url, Header.newInstance(), Query.newInstance(), param, String.class, callbackMap);
        Thread.sleep(2000);
        HttpRestResult<String> restResult = callbackMap.getRestResult();
        System.out.println(restResult.getData());
        System.out.println(restResult.getHeader());
        Assert.assertTrue(restResult.ok());
    }
    
    @Test
    public void test_url_put_form() throws Exception {
        String url = IP + CONFIG_INSTANCE_PATH + "/instance";
        Map<String, String> param = new HashMap<>();
        param.put("serviceName", "app-test-change");
        param.put("port", "8080");
        param.put("ip", "11.11.11.11");
        CallbackMap<String> callbackMap = new CallbackMap<>();
        nacosRestTemplate.postForm(url, Header.newInstance(), Query.newInstance(), param, String.class, callbackMap);
        Thread.sleep(2000);
        HttpRestResult<String> restResult = callbackMap.getRestResult();
        System.out.println(restResult.getData());
        System.out.println(restResult.getHeader());
        Assert.assertTrue(restResult.ok());
    }
    
    
    @Test
    public void test_url_get() throws Exception {
        String url = IP + CONFIG_INSTANCE_PATH + "/instance/list";
        Query query = Query.newInstance().addParam("serviceName", "app-test");
        CallbackMap<Map> callbackMap = new CallbackMap<>();
        nacosRestTemplate.get(url, Header.newInstance(), query, Map.class, callbackMap);
        Thread.sleep(2000);
        HttpRestResult<Map> restResult = callbackMap.getRestResult();
        System.out.println(restResult.getData());
        System.out.println(restResult.getHeader());
        Assert.assertTrue(restResult.ok());
        Assert.assertEquals(restResult.getData().get("name"), "DEFAULT_GROUP@@app-test");
    }
    
    @Test
    public void test_url_by_map() throws Exception {
        String url = IP + CONFIG_INSTANCE_PATH + "/instance/list";
        Map<String, String> param = new HashMap<>();
        param.put("serviceName", "app-test");
        CallbackMap<Map> callbackMap = new CallbackMap<>();
        nacosRestTemplate.get(url, Header.newInstance(), Query.newInstance().initParams(param), Map.class, callbackMap);
        Thread.sleep(2000);
        HttpRestResult<Map> restResult = callbackMap.getRestResult();
        System.out.println(restResult.getData());
        System.out.println(restResult.getHeader());
        Assert.assertTrue(restResult.ok());
        Assert.assertEquals(restResult.getData().get("name"), "DEFAULT_GROUP@@app-test");
    }
    
    @Test
    public void test_url_delete() throws Exception {
        String url = IP + CONFIG_INSTANCE_PATH + "/instance";
        Query query = Query.newInstance().addParam("ip", "11.11.11.11").addParam("port", "8080")
                .addParam("serviceName", "app-test");
        CallbackMap<String> callbackMap = new CallbackMap<>();
        nacosRestTemplate.delete(url, Header.newInstance(), query, String.class, callbackMap);
        Thread.sleep(2000);
        HttpRestResult<String> restResult = callbackMap.getRestResult();
        System.out.println(restResult.getData());
        System.out.println(restResult.getHeader());
        Assert.assertTrue(restResult.ok());
    }
    
}
