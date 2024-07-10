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
import com.alibaba.nacos.common.http.client.HttpClientRequestInterceptor;
import com.alibaba.nacos.common.http.client.NacosRestTemplate;
import com.alibaba.nacos.common.http.client.response.HttpClientResponse;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.model.RequestHttpEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer.MethodName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * NacosRestTemplate_Interceptors_ITCase
 *
 * @author mai.jh
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = Nacos.class, properties = {
        "server.servlet.context-path=/nacos"}, webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@TestMethodOrder(MethodName.class)
class NacosRestTemplate_Interceptors_ITCase {
    
    private final String CONFIG_PATH = "/nacos/v1/cs";
    
    @LocalServerPort
    private int port;
    
    private NacosRestTemplate nacosRestTemplate = HttpClientBeanHolder.getNacosRestTemplate(
            LoggerFactory.getLogger(NacosRestTemplate_Interceptors_ITCase.class));
    
    private String IP = null;
    
    @BeforeEach
    void init() throws NacosException {
        nacosRestTemplate.setInterceptors(Arrays.asList(new TerminationInterceptor()));
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
        assertEquals(500, restResult.getCode());
        assertEquals("Stop request", restResult.getMessage());
        System.out.println(restResult.getData());
        System.out.println(restResult.getHeader());
    }
    
    private class TerminationInterceptor implements HttpClientRequestInterceptor {
        
        @Override
        public HttpClientResponse intercept() {
            return new HttpClientResponse() {
                @Override
                public Header getHeaders() {
                    return Header.EMPTY;
                }
                
                @Override
                public InputStream getBody() throws IOException {
                    return new ByteArrayInputStream("Stop request".getBytes());
                }
                
                @Override
                public int getStatusCode() {
                    return NacosException.SERVER_ERROR;
                }
                
                @Override
                public String getStatusText() {
                    return null;
                }
                
                @Override
                public void close() {
                
                }
            };
        }
        
        @Override
        public boolean isIntercept(URI uri, String httpMethod, RequestHttpEntity requestHttpEntity) {
            return true;
        }
        
    }
}
