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
 * This class provides integration tests for NacosAsyncRestTemplate. These tests cover various HTTP methods such as
 * POST, GET, PUT, and DELETE to ensure the correct functioning of asynchronous HTTP requests in the context of Nacos.
 *
 * @author mai.jh
 */
@SuppressWarnings("checkstyle:AbbreviationAsWordInName")
@TestMethodOrder(MethodName.class)
@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = Nacos.class, properties = {
        "server.servlet.context-path=/nacos"}, webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
class NacosAsyncRestTemplateCoreITCase {
    
    private static final HttpClientFactory PROCESSOR_ASYNC_HTTP_CLIENT_FACTORY = new ProcessorHttpClientFactory();
    
    private static final String CONFIG_INSTANCE_PATH = "/nacos/v1/ns";
    
    private final NacosAsyncRestTemplate nacosRestTemplate = HttpClientBeanHolder.getNacosAsyncRestTemplate(
            LoggerFactory.getLogger(NacosAsyncRestTemplateCoreITCase.class));
    
    @SuppressWarnings("deprecation")
    @LocalServerPort
    private int port;
    
    private NacosAsyncRestTemplate processorRestTemplate = null;
    
    private String ip = null;
    
    @Autowired
    private Environment environment;
    
    @BeforeEach
    void init() throws NacosException {
        ip = String.format("http://localhost:%d", port);
        EnvUtil.setEnvironment((ConfigurableEnvironment) environment);
        processorRestTemplate = HttpClientBeanHolder.getNacosAsyncRestTemplate(PROCESSOR_ASYNC_HTTP_CLIENT_FACTORY);
    }
    
    @Test
    void testUrlPostForm() throws Exception {
        Map<String, String> param = new HashMap<>();
        param.put("serviceName", "app-test");
        param.put("port", "8080");
        param.put("ip", "11.11.11.11");
        CallbackMap<String> callbackMap = new CallbackMap<>();
        String url = ip + CONFIG_INSTANCE_PATH + "/instance";
        nacosRestTemplate.postForm(url, Header.newInstance(), Query.newInstance(), param, String.class, callbackMap);
        Thread.sleep(2000);
        HttpRestResult<String> restResult = callbackMap.getRestResult();
        System.out.println(restResult.getData());
        System.out.println(restResult.getHeader());
        assertTrue(restResult.ok());
    }
    
    @Test
    void testUrlPostFormByProcessor() throws Exception {
        Map<String, String> param = new HashMap<>();
        param.put("serviceName", "app-test2");
        param.put("port", "8080");
        param.put("ip", "11.11.11.11");
        CallbackMap<String> callbackMap = new CallbackMap<>();
        String url = ip + CONFIG_INSTANCE_PATH + "/instance";
        processorRestTemplate.postForm(url, Header.newInstance(), Query.newInstance(), param, String.class,
                callbackMap);
        Thread.sleep(2000);
        HttpRestResult<String> restResult = callbackMap.getRestResult();
        System.out.println(restResult.getData());
        System.out.println(restResult.getHeader());
        assertTrue(restResult.ok());
    }
    
    @Test
    void testUrlPutForm() throws Exception {
        Map<String, String> param = new HashMap<>();
        param.put("serviceName", "app-test-change");
        param.put("port", "8080");
        param.put("ip", "11.11.11.11");
        CallbackMap<String> callbackMap = new CallbackMap<>();
        String url = ip + CONFIG_INSTANCE_PATH + "/instance";
        nacosRestTemplate.postForm(url, Header.newInstance(), Query.newInstance(), param, String.class, callbackMap);
        Thread.sleep(2000);
        HttpRestResult<String> restResult = callbackMap.getRestResult();
        System.out.println(restResult.getData());
        System.out.println(restResult.getHeader());
        assertTrue(restResult.ok());
    }
    
    @Test
    void testUrlGet() throws Exception {
        String url = ip + CONFIG_INSTANCE_PATH + "/instance/list";
        Query query = Query.newInstance().addParam("serviceName", "app-test");
        CallbackMap<Map<String, String>> callbackMap = new CallbackMap<>();
        nacosRestTemplate.get(url, Header.newInstance(), query, Map.class, callbackMap);
        Thread.sleep(2000);
        HttpRestResult<Map<String, String>> restResult = callbackMap.getRestResult();
        System.out.println(restResult.getData());
        System.out.println(restResult.getHeader());
        assertTrue(restResult.ok());
        assertEquals("DEFAULT_GROUP@@app-test", restResult.getData().get("name"));
    }
    
    @Test
    void testUrlByMap() throws Exception {
        String url = ip + CONFIG_INSTANCE_PATH + "/instance/list";
        Map<String, String> param = new HashMap<>();
        param.put("serviceName", "app-test");
        CallbackMap<Map<String, String>> callbackMap = new CallbackMap<>();
        nacosRestTemplate.get(url, Header.newInstance(), Query.newInstance().initParams(param), Map.class, callbackMap);
        Thread.sleep(2000);
        HttpRestResult<Map<String, String>> restResult = callbackMap.getRestResult();
        System.out.println(restResult.getData());
        System.out.println(restResult.getHeader());
        assertTrue(restResult.ok());
        assertEquals("DEFAULT_GROUP@@app-test", restResult.getData().get("name"));
    }
    
    @Test
    void testUrlDelete() throws Exception {
        String url = ip + CONFIG_INSTANCE_PATH + "/instance";
        Query query = Query.newInstance().addParam("ip", "11.11.11.11").addParam("port", "8080")
                .addParam("serviceName", "app-test");
        CallbackMap<String> callbackMap = new CallbackMap<>();
        nacosRestTemplate.delete(url, Header.newInstance(), query, String.class, callbackMap);
        Thread.sleep(2000);
        HttpRestResult<String> restResult = callbackMap.getRestResult();
        System.out.println(restResult.getData());
        System.out.println(restResult.getHeader());
        assertTrue(restResult.ok());
    }
    
    private static class CallbackMap<T> implements Callback<T> {
        
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
