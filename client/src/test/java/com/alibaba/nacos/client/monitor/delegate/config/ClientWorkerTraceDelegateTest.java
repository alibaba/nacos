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

import com.alibaba.nacos.api.config.listener.AbstractListener;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.client.config.filter.impl.ConfigFilterChainManager;
import com.alibaba.nacos.client.config.impl.ClientWorker;
import com.alibaba.nacos.client.config.impl.ServerListManager;
import com.alibaba.nacos.client.env.NacosClientProperties;
import com.alibaba.nacos.client.monitor.delegate.OpenTelemetryBaseTest;
import com.alibaba.nacos.common.constant.NacosSemanticAttributes;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Properties;

public class ClientWorkerTraceDelegateTest extends OpenTelemetryBaseTest {
    
    private ClientWorker clientWorker;
    
    @Before
    public void initClientWorker() throws NacosException {
        Properties prop = new Properties();
        ConfigFilterChainManager filter = new ConfigFilterChainManager(new Properties());
        ServerListManager serverListManager = Mockito.mock(ServerListManager.class);
        
        final NacosClientProperties nacosClientProperties = NacosClientProperties.PROTOTYPE.derive(prop);
        clientWorker = new ClientWorkerTraceDelegate(filter, serverListManager, nacosClientProperties);
    }
    
    @After
    public void closeClientWorker() throws NacosException {
        clientWorker.shutdown();
    }
    
    @Test
    public void testAddListeners() throws NacosException {
        Listener testListener = new AbstractListener() {
            @Override
            public void receiveConfigInfo(String configInfo) {
            }
        };
        
        String dataId = "testDataId";
        String group = "testGroup";
        
        clientWorker.addListeners(dataId, group, Collections.singletonList(testListener));
        Assert.assertFalse(testExporter.exportedSpans.isEmpty());
        Assert.assertEquals("Nacos.client.config.worker / addCacheDataIfAbsent",
                testExporter.exportedSpans.get(0).getName());
        Assert.assertEquals("Nacos.client.config.service / addListeners", testExporter.exportedSpans.get(1).getName());
        
        Assert.assertEquals(dataId, testExporter.exportedSpans.get(1).getAttributes()
                .get(AttributeKey.stringKey(NacosSemanticAttributes.DATA_ID)));
        Assert.assertEquals(group, testExporter.exportedSpans.get(1).getAttributes()
                .get(AttributeKey.stringKey(NacosSemanticAttributes.GROUP)));
    }
    
    @Test
    public void addTenantListenersWithContent() throws NacosException {
        Listener testListener = new AbstractListener() {
            @Override
            public void receiveConfigInfo(String configInfo) {
            }
        };
        
        String dataId = "testDataId";
        String group = "testGroup";
        
        clientWorker.addTenantListeners(dataId, group, Collections.singletonList(testListener));
        Assert.assertFalse(testExporter.exportedSpans.isEmpty());
        Assert.assertEquals("Nacos.client.config.worker / addCacheDataIfAbsent",
                testExporter.exportedSpans.get(0).getName());
        Assert.assertEquals("Nacos.client.config.service / addTenantListeners",
                testExporter.exportedSpans.get(1).getName());
        
        Assert.assertEquals(dataId, testExporter.exportedSpans.get(1).getAttributes()
                .get(AttributeKey.stringKey(NacosSemanticAttributes.DATA_ID)));
        Assert.assertEquals(group, testExporter.exportedSpans.get(1).getAttributes()
                .get(AttributeKey.stringKey(NacosSemanticAttributes.GROUP)));
    }
    
    @Test
    public void testAddTenantListeners() throws NacosException {
        Listener testListener = new AbstractListener() {
            @Override
            public void receiveConfigInfo(String configInfo) {
            }
        };
        
        String dataId = "testDataId";
        String group = "testGroup";
        
        clientWorker.addTenantListeners(dataId, group, Collections.singletonList(testListener));
        Assert.assertFalse(testExporter.exportedSpans.isEmpty());
        Assert.assertEquals("Nacos.client.config.worker / addCacheDataIfAbsent",
                testExporter.exportedSpans.get(0).getName());
        Assert.assertEquals("Nacos.client.config.service / addTenantListeners",
                testExporter.exportedSpans.get(1).getName());
        
        Assert.assertEquals(dataId, testExporter.exportedSpans.get(1).getAttributes()
                .get(AttributeKey.stringKey(NacosSemanticAttributes.DATA_ID)));
        Assert.assertEquals(group, testExporter.exportedSpans.get(1).getAttributes()
                .get(AttributeKey.stringKey(NacosSemanticAttributes.GROUP)));
    }
    
    @Test
    public void removeTenantListener() {
        Listener testListener = new AbstractListener() {
            @Override
            public void receiveConfigInfo(String configInfo) {
            }
        };
        
        String dataId = "testDataId";
        String group = "testGroup";
        
        clientWorker.removeTenantListener(dataId, group, testListener);
        Assert.assertFalse(testExporter.exportedSpans.isEmpty());
        SpanData spanData = testExporter.exportedSpans.get(0);
        Assert.assertEquals("Nacos.client.config.service / removeTenantListener", spanData.getName());
        
        Assert.assertEquals(dataId,
                spanData.getAttributes().get(AttributeKey.stringKey(NacosSemanticAttributes.DATA_ID)));
        Assert.assertEquals(group, spanData.getAttributes().get(AttributeKey.stringKey(NacosSemanticAttributes.GROUP)));
    }
    
    @Test
    public void testGetServerConfig() {
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
        Assert.assertEquals("Nacos.client.config.service / getServerConfig", spanData.getName());
        
        Assert.assertEquals(dataId,
                spanData.getAttributes().get(AttributeKey.stringKey(NacosSemanticAttributes.DATA_ID)));
        Assert.assertEquals(group, spanData.getAttributes().get(AttributeKey.stringKey(NacosSemanticAttributes.GROUP)));
        Assert.assertEquals(tenant,
                spanData.getAttributes().get(AttributeKey.stringKey(NacosSemanticAttributes.TENANT)));
    }
    
    @Test
    public void testRemoveConfig() {
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
        Assert.assertEquals("Nacos.client.config.service / removeConfig", spanData.getName());
        
        Assert.assertEquals(dataId,
                spanData.getAttributes().get(AttributeKey.stringKey(NacosSemanticAttributes.DATA_ID)));
        Assert.assertEquals(group, spanData.getAttributes().get(AttributeKey.stringKey(NacosSemanticAttributes.GROUP)));
        Assert.assertEquals(tenant,
                spanData.getAttributes().get(AttributeKey.stringKey(NacosSemanticAttributes.TENANT)));
        Assert.assertEquals(tag, spanData.getAttributes().get(AttributeKey.stringKey(NacosSemanticAttributes.TAG)));
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
        Assert.assertEquals("Nacos.client.config.service / publishConfig", spanData.getName());
        
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
    public void testIsHealthServer() throws NoSuchFieldException, IllegalAccessException {
        ClientWorker.ConfigRpcTransportClient client = Mockito.mock(ClientWorker.ConfigRpcTransportClient.class);
        Mockito.when(client.isHealthServer()).thenReturn(Boolean.TRUE);
        
        Field declaredField = ClientWorker.class.getDeclaredField("agent");
        declaredField.setAccessible(true);
        declaredField.set(clientWorker, client);
        
        Assert.assertTrue(clientWorker.isHealthServer());
        Assert.assertEquals("Nacos.client.config.service / isHealthServer",
                testExporter.exportedSpans.get(testExporter.exportedSpans.size() - 1).getName());
    }
}
