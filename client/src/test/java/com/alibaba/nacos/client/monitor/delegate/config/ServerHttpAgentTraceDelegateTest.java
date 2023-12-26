/*
 *
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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
 *
 */

package com.alibaba.nacos.client.monitor.delegate.config;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.client.config.http.HttpAgent;
import com.alibaba.nacos.client.config.impl.ServerListManager;
import com.alibaba.nacos.client.monitor.delegate.OpenTelemetryBaseTest;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.Map;

public class ServerHttpAgentTraceDelegateTest extends OpenTelemetryBaseTest {
    
    private HttpAgent agent;
    
    @Before
    public void initAgent() {
        ServerListManager serverListManager = Mockito.mock(ServerListManager.class);
        agent = new ServerHttpAgentTraceDelegate(serverListManager);
    }
    
    @After
    public void closeAgent() throws NacosException {
        agent.shutdown();
    }
    
    @Test
    public void testHttpGet() throws Exception {
        
        String path = "/aa";
        Map<String, String> headers = new HashMap<>();
        Map<String, String> paramValues = new HashMap<>();
        String encoding = "UTF-8";
        long readTimeoutMs = 1L;
        
        try {
            agent.httpGet(path, headers, paramValues, encoding, readTimeoutMs);
            Assert.fail();
        } catch (IllegalArgumentException e) {
            Assert.assertEquals("URI is not absolute", e.getMessage());
        }
        Assert.assertFalse(testExporter.exportedSpans.isEmpty());
        SpanData spanData = testExporter.exportedSpans.get(testExporter.exportedSpans.size() - 1);
        Assert.assertEquals("Nacos.client.config.http / GET", spanData.getName());
        Assert.assertEquals(path, spanData.getAttributes().get(SemanticAttributes.HTTP_URL));
    }
    
    @Test
    public void testHttpPost() throws Exception {
        
        String path = "/aa";
        Map<String, String> headers = new HashMap<>();
        Map<String, String> paramValues = new HashMap<>();
        String encoding = "UTF-8";
        long readTimeoutMs = 1L;
        
        try {
            agent.httpPost(path, headers, paramValues, encoding, readTimeoutMs);
            Assert.fail();
        } catch (IllegalArgumentException e) {
            Assert.assertEquals("URI is not absolute", e.getMessage());
        }
        Assert.assertFalse(testExporter.exportedSpans.isEmpty());
        SpanData spanData = testExporter.exportedSpans.get(testExporter.exportedSpans.size() - 1);
        Assert.assertEquals("Nacos.client.config.http / POST", spanData.getName());
        Assert.assertEquals(path, spanData.getAttributes().get(SemanticAttributes.HTTP_URL));
    }
    
    @Test
    public void testHttpDelete() throws Exception {
        
        String path = "/aa";
        Map<String, String> headers = new HashMap<>();
        Map<String, String> paramValues = new HashMap<>();
        String encoding = "UTF-8";
        long readTimeoutMs = 1L;
        
        try {
            agent.httpDelete(path, headers, paramValues, encoding, readTimeoutMs);
            Assert.fail();
        } catch (IllegalArgumentException e) {
            Assert.assertEquals("URI is not absolute", e.getMessage());
        }
        Assert.assertFalse(testExporter.exportedSpans.isEmpty());
        SpanData spanData = testExporter.exportedSpans.get(testExporter.exportedSpans.size() - 1);
        Assert.assertEquals("Nacos.client.config.http / DELETE", spanData.getName());
        Assert.assertEquals(path, spanData.getAttributes().get(SemanticAttributes.HTTP_URL));
    }
}
