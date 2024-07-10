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
import com.alibaba.nacos.common.http.HttpClientFactory;
import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.http.client.NacosAsyncRestTemplate;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.model.RestResult;
import com.alibaba.nacos.naming.misc.HttpClientManager.ProcessorHttpClientFactory;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer.MethodName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * NacosAsyncRestTemplate_ITCase.
 *
 * @author mai.jh
 */
@SuppressWarnings("all")
@TestMethodOrder(MethodName.class)
@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = Nacos.class, properties = {
        "server.servlet.context-path=/nacos"}, webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
class NacosAsyncRestTemplate_ITCase {
    
    private static final HttpClientFactory PROCESSOR_ASYNC_HTTP_CLIENT_FACTORY = new ProcessorHttpClientFactory();
    
    private final String CONFIG_INSTANCE_PATH = "/nacos/v1/ns";
    
    @LocalServerPort
    private int port;
    
    private NacosAsyncRestTemplate nacosRestTemplate = HttpClientBeanHolder.getNacosAsyncRestTemplate(
            LoggerFactory.getLogger(NacosAsyncRestTemplate_ITCase.class));
    
    private NacosAsyncRestTemplate processorRestTemplate = null;
    
    private String IP = null;
    
    @Autowired
    private Environment environment;
    
    @BeforeEach
    void init() throws NacosException {
        IP = String.format("http://localhost:%d", port);
        EnvUtil.setEnvironment((ConfigurableEnvironment) environment);
        processorRestTemplate = HttpClientBeanHolder.getNacosAsyncRestTemplate(PROCESSOR_ASYNC_HTTP_CLIENT_FACTORY);
    }
    
    @Test
    void test_url_post_form() throws Exception {
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
        assertTrue(restResult.ok());
    }
    
    @Test
    void test_url_post_form_by_processor() throws Exception {
        String url = IP + CONFIG_INSTANCE_PATH + "/instance";
        Map<String, String> param = new HashMap<>();
        param.put("serviceName", "app-test2");
        param.put("port", "8080");
        param.put("ip", "11.11.11.11");
        CallbackMap<String> callbackMap = new CallbackMap<>();
        processorRestTemplate.postForm(url, Header.newInstance(), Query.newInstance(), param, String.class, callbackMap);
        Thread.sleep(2000);
        HttpRestResult<String> restResult = callbackMap.getRestResult();
        System.out.println(restResult.getData());
        System.out.println(restResult.getHeader());
        assertTrue(restResult.ok());
    }
    
    @Test
    void test_url_put_form() throws Exception {
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
        assertTrue(restResult.ok());
    }
    
    @Test
    void test_url_get() throws Exception {
        String url = IP + CONFIG_INSTANCE_PATH + "/instance/list";
        Query query = Query.newInstance().addParam("serviceName", "app-test");
        CallbackMap<Map> callbackMap = new CallbackMap<>();
        nacosRestTemplate.get(url, Header.newInstance(), query, Map.class, callbackMap);
        Thread.sleep(2000);
        HttpRestResult<Map> restResult = callbackMap.getRestResult();
        System.out.println(restResult.getData());
        System.out.println(restResult.getHeader());
        assertTrue(restResult.ok());
        assertEquals("DEFAULT_GROUP@@app-test", restResult.getData().get("name"));
    }
    
    @Test
    void test_url_by_map() throws Exception {
        String url = IP + CONFIG_INSTANCE_PATH + "/instance/list";
        Map<String, String> param = new HashMap<>();
        param.put("serviceName", "app-test");
        CallbackMap<Map> callbackMap = new CallbackMap<>();
        nacosRestTemplate.get(url, Header.newInstance(), Query.newInstance().initParams(param), Map.class, callbackMap);
        Thread.sleep(2000);
        HttpRestResult<Map> restResult = callbackMap.getRestResult();
        System.out.println(restResult.getData());
        System.out.println(restResult.getHeader());
        assertTrue(restResult.ok());
        assertEquals("DEFAULT_GROUP@@app-test", restResult.getData().get("name"));
    }
    
    @Test
    void test_url_delete() throws Exception {
        String url = IP + CONFIG_INSTANCE_PATH + "/instance";
        Query query = Query.newInstance().addParam("ip", "11.11.11.11").addParam("port", "8080").addParam("serviceName", "app-test");
        CallbackMap<String> callbackMap = new CallbackMap<>();
        nacosRestTemplate.delete(url, Header.newInstance(), query, String.class, callbackMap);
        Thread.sleep(2000);
        HttpRestResult<String> restResult = callbackMap.getRestResult();
        System.out.println(restResult.getData());
        System.out.println(restResult.getHeader());
        assertTrue(restResult.ok());
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
    
}
