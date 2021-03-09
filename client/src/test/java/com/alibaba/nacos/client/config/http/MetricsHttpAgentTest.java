/*
 *   Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package com.alibaba.nacos.client.config.http;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.http.param.Header;
import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class MetricsHttpAgentTest {
    
    private static class MockHttpAgent implements HttpAgent {
        
        private String name;
        
        private String encode;
        
        private String tenant;
        
        private String namespace;
        
        private boolean start = false;
        
        private boolean shutdown = false;
        
        public MockHttpAgent(String name, String encode, String tenant, String namespace) {
            this.name = name;
            this.encode = encode;
            this.tenant = tenant;
            this.namespace = namespace;
        }
        
        @Override
        public void start() throws NacosException {
            start = true;
        }
        
        @Override
        public HttpRestResult<String> httpGet(String path, Map<String, String> headers, Map<String, String> paramValues,
                String encoding, long readTimeoutMs) throws Exception {
            return new HttpRestResult<String>(Header.newInstance(), 200, "get", "get " + path);
        }
        
        @Override
        public HttpRestResult<String> httpPost(String path, Map<String, String> headers,
                Map<String, String> paramValues, String encoding, long readTimeoutMs) throws Exception {
            return new HttpRestResult<String>(Header.newInstance(), 200, "post", "post " + path);
        }
        
        @Override
        public HttpRestResult<String> httpDelete(String path, Map<String, String> headers,
                Map<String, String> paramValues, String encoding, long readTimeoutMs) throws Exception {
            return new HttpRestResult<String>(Header.newInstance(), 200, "delete", "delete " + path);
        }
        
        @Override
        public String getName() {
            return name;
        }
        
        @Override
        public String getNamespace() {
            return namespace;
        }
        
        @Override
        public String getTenant() {
            return tenant;
        }
        
        @Override
        public String getEncode() {
            return encode;
        }
        
        @Override
        public void shutdown() throws NacosException {
            shutdown = true;
        }
        
        public boolean isStart() {
            return start;
        }
        
        public boolean isShutdown() {
            return shutdown;
        }
    }
    
    @Test
    public void testGetter() {
        String name = "name";
        String encode = "UTF-8";
        String tenant = "aaa";
        String namespace = "aaa";
        final HttpAgent mockHttpAgent = new MockHttpAgent(name, encode, tenant, namespace);
        final MetricsHttpAgent metricsHttpAgent = new MetricsHttpAgent(mockHttpAgent);
        
        Assert.assertEquals(name, metricsHttpAgent.getName());
        Assert.assertEquals(encode, metricsHttpAgent.getEncode());
        Assert.assertEquals(tenant, metricsHttpAgent.getTenant());
        Assert.assertEquals(namespace, metricsHttpAgent.getNamespace());
    }
    
    @Test
    public void testLifeCycle() throws NacosException {
        String name = "name";
        String encode = "UTF-8";
        String tenant = "aaa";
        String namespace = "aaa";
        final MockHttpAgent mockHttpAgent = new MockHttpAgent(name, encode, tenant, namespace);
        final MetricsHttpAgent metricsHttpAgent = new MetricsHttpAgent(mockHttpAgent);
        
        metricsHttpAgent.start();
        Assert.assertTrue(mockHttpAgent.isStart());
        
        metricsHttpAgent.shutdown();
        Assert.assertTrue(mockHttpAgent.isShutdown());
    }
    
    @Test
    public void testHttpMethod() throws Exception {
        String name = "name";
        String encode = "UTF-8";
        String tenant = "aaa";
        String namespace = "aaa";
        final MockHttpAgent mockHttpAgent = new MockHttpAgent(name, encode, tenant, namespace);
        final MetricsHttpAgent metricsHttpAgent = new MetricsHttpAgent(mockHttpAgent);
        
        final HttpRestResult<String> result1 = metricsHttpAgent
                .httpGet("/aa", new HashMap<String, String>(), new HashMap<String, String>(), "UTF-8", 1L);
        Assert.assertEquals("get /aa", result1.getMessage());
        final HttpRestResult<String> result2 = metricsHttpAgent
                .httpPost("/aa", new HashMap<String, String>(), new HashMap<String, String>(), "UTF-8", 1L);
        Assert.assertEquals("post /aa", result2.getMessage());
        
        final HttpRestResult<String> result3 = metricsHttpAgent
                .httpDelete("/aa", new HashMap<String, String>(), new HashMap<String, String>(), "UTF-8", 1L);
        Assert.assertEquals("delete /aa", result3.getMessage());
    }
}