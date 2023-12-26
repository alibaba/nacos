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
import com.alibaba.nacos.client.config.filter.impl.ConfigFilterChainManager;
import com.alibaba.nacos.client.config.impl.ClientWorker;
import com.alibaba.nacos.client.config.impl.ServerListManager;
import com.alibaba.nacos.client.env.NacosClientProperties;
import com.alibaba.nacos.client.monitor.delegate.OpenTelemetryBaseTest;
import com.alibaba.nacos.common.constant.NacosSemanticAttributes;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Properties;

public class ConfigRpcTransportClientProxyTraceDelegateTest extends OpenTelemetryBaseTest {
    
    // ConfigRpcTransportClient is an anonymous inner class of ClientWorker.
    // So we must use ClientWorker to test ConfigRpcTransportClientProxyTraceDelegate.
    
    private ClientWorker clientWorker;
    
    @Before
    public void initClientWorker() throws NacosException {
        Properties prop = new Properties();
        ConfigFilterChainManager filter = new ConfigFilterChainManager(new Properties());
        ServerListManager serverListManager = Mockito.mock(ServerListManager.class);
        
        final NacosClientProperties nacosClientProperties = NacosClientProperties.PROTOTYPE.derive(prop);
        clientWorker = new ClientWorker(filter, serverListManager, nacosClientProperties);
    }
    
    @After
    public void closeClientWorker() throws NacosException {
        clientWorker.shutdown();
    }
    
    @Test
    public void testQueryConfig() {
        String dataId = "a";
        String group = "b";
        String tenant = "c";
        
        try {
            clientWorker.getServerConfig(dataId, group, tenant, 3000, false);
            Assert.fail();
        } catch (NacosException e) {
            Assert.assertEquals("Client not connected, current status:STARTING", e.getErrMsg());
            Assert.assertEquals(-401, e.getErrCode());
        }
        Assert.assertFalse(testExporter.exportedSpans.isEmpty());
        SpanData spanData = testExporter.exportedSpans.get(testExporter.exportedSpans.size() - 1);
        Assert.assertEquals("Nacos.client.config.worker / queryConfig", spanData.getName());
        
        Assert.assertEquals(dataId,
                spanData.getAttributes().get(AttributeKey.stringKey(NacosSemanticAttributes.DATA_ID)));
        Assert.assertEquals(group, spanData.getAttributes().get(AttributeKey.stringKey(NacosSemanticAttributes.GROUP)));
        Assert.assertEquals(tenant,
                spanData.getAttributes().get(AttributeKey.stringKey(NacosSemanticAttributes.TENANT)));
    }
    
    @Test
    public void testPublishConfig() throws NacosException {
        String dataId = "a";
        String group = "b";
        String tenant = "c";
        String tag = "tag";
        String content = "d";
        String appName = "app";
        String betaIps = "1.1.1.1";
        String casMd5 = "1111";
        String type = "properties";
        
        boolean b = clientWorker.publishConfig(dataId, group, tenant, appName, tag, betaIps, content, null, casMd5,
                type);
        Assert.assertFalse(b);
        Assert.assertFalse(testExporter.exportedSpans.isEmpty());
        SpanData spanData = testExporter.exportedSpans.get(testExporter.exportedSpans.size() - 1);
        Assert.assertEquals("Nacos.client.config.worker / publishConfig", spanData.getName());
        
        Assert.assertEquals(dataId,
                spanData.getAttributes().get(AttributeKey.stringKey(NacosSemanticAttributes.DATA_ID)));
        Assert.assertEquals(group, spanData.getAttributes().get(AttributeKey.stringKey(NacosSemanticAttributes.GROUP)));
        Assert.assertEquals(tenant,
                spanData.getAttributes().get(AttributeKey.stringKey(NacosSemanticAttributes.TENANT)));
        Assert.assertEquals(tag, spanData.getAttributes().get(AttributeKey.stringKey(NacosSemanticAttributes.TAG)));
        Assert.assertEquals(appName,
                spanData.getAttributes().get(AttributeKey.stringKey(NacosSemanticAttributes.APPLICATION_NAME)));
        Assert.assertEquals(content,
                spanData.getAttributes().get(AttributeKey.stringKey(NacosSemanticAttributes.CONTENT)));
        Assert.assertEquals(type,
                spanData.getAttributes().get(AttributeKey.stringKey(NacosSemanticAttributes.CONFIG_TYPE)));
    }
    
    @Test
    public void testRemoveConfig() throws NacosException {
        String dataId = "a";
        String group = "b";
        String tenant = "c";
        String tag = "tag";
        try {
            clientWorker.removeConfig(dataId, group, tenant, tag);
            Assert.fail();
        } catch (NacosException e) {
            Assert.assertEquals("Client not connected, current status:STARTING", e.getErrMsg());
            Assert.assertEquals(-401, e.getErrCode());
        }
        Assert.assertFalse(testExporter.exportedSpans.isEmpty());
        SpanData spanData = testExporter.exportedSpans.get(testExporter.exportedSpans.size() - 1);
        Assert.assertEquals("Nacos.client.config.worker / removeConfig", spanData.getName());
        
        Assert.assertEquals(dataId,
                spanData.getAttributes().get(AttributeKey.stringKey(NacosSemanticAttributes.DATA_ID)));
        Assert.assertEquals(group, spanData.getAttributes().get(AttributeKey.stringKey(NacosSemanticAttributes.GROUP)));
        Assert.assertEquals(tenant,
                spanData.getAttributes().get(AttributeKey.stringKey(NacosSemanticAttributes.TENANT)));
        Assert.assertEquals(tag, spanData.getAttributes().get(AttributeKey.stringKey(NacosSemanticAttributes.TAG)));
    }
    
    @Test
    public void testRequestProxy() throws NacosException {
        String dataId = "a";
        String group = "b";
        String tenant = "c";
        String tag = "tag";
        String content = "d";
        String appName = "app";
        String betaIps = "1.1.1.1";
        String casMd5 = "1111";
        String type = "properties";
        
        final String targetSpanName = "Nacos.client.config.rpc / GRPC";
        final String targetRpcSystem = "grpc";
        
        boolean b = clientWorker.publishConfig(dataId, group, tenant, appName, tag, betaIps, content, null, casMd5,
                type);
        Assert.assertFalse(b);
        Assert.assertFalse(testExporter.exportedSpans.isEmpty());
        SpanData spanData = testExporter.exportedSpans.get(testExporter.exportedSpans.size() - 2);
        Assert.assertEquals(targetSpanName, spanData.getName());
        Assert.assertEquals(targetRpcSystem, spanData.getAttributes().get(SemanticAttributes.RPC_SYSTEM));
        testExporter.exportedSpans.clear();
        
        try {
            clientWorker.getServerConfig(dataId, group, tenant, 3000, false);
            Assert.fail();
        } catch (NacosException e) {
            Assert.assertEquals("Client not connected, current status:STARTING", e.getErrMsg());
            Assert.assertEquals(-401, e.getErrCode());
        }
        Assert.assertFalse(testExporter.exportedSpans.isEmpty());
        spanData = testExporter.exportedSpans.get(testExporter.exportedSpans.size() - 2);
        Assert.assertEquals(targetSpanName, spanData.getName());
        Assert.assertEquals(targetRpcSystem, spanData.getAttributes().get(SemanticAttributes.RPC_SYSTEM));
        testExporter.exportedSpans.clear();
        
        try {
            clientWorker.removeConfig(dataId, group, tenant, tag);
            Assert.fail();
        } catch (NacosException e) {
            Assert.assertEquals("Client not connected, current status:STARTING", e.getErrMsg());
            Assert.assertEquals(-401, e.getErrCode());
        }
        Assert.assertFalse(testExporter.exportedSpans.isEmpty());
        spanData = testExporter.exportedSpans.get(testExporter.exportedSpans.size() - 2);
        Assert.assertEquals(targetSpanName, spanData.getName());
        Assert.assertEquals(targetRpcSystem, spanData.getAttributes().get(SemanticAttributes.RPC_SYSTEM));
    }
}
