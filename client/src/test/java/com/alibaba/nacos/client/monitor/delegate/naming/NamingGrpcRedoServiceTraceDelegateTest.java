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

package com.alibaba.nacos.client.monitor.delegate.naming;

import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.utils.NamingUtils;
import com.alibaba.nacos.client.monitor.delegate.OpenTelemetryBaseTest;
import com.alibaba.nacos.client.naming.remote.gprc.NamingGrpcClientProxy;
import com.alibaba.nacos.client.naming.remote.gprc.redo.NamingGrpcRedoService;
import com.alibaba.nacos.common.constant.NacosSemanticAttributes;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.concurrent.ScheduledExecutorService;

public class NamingGrpcRedoServiceTraceDelegateTest extends OpenTelemetryBaseTest {
    
    private final String serviceName = "testService";
    
    private final String groupName = "testGroup";
    
    private final String clusterName = "testCluster";
    
    private final Instance instance = new Instance();
    
    private NamingGrpcRedoServiceTraceDelegate delegate;
    
    @Before
    public void initService() throws NoSuchFieldException, IllegalAccessException {
        NamingGrpcClientProxy clientProxy = Mockito.mock(NamingGrpcClientProxy.class);
        delegate = new NamingGrpcRedoServiceTraceDelegate(clientProxy);
        
        Field field = NamingGrpcRedoService.class.getDeclaredField("redoExecutor");
        field.setAccessible(true);
        ScheduledExecutorService redoExecutor = (ScheduledExecutorService) field.get(delegate);
        
        redoExecutor.shutdownNow();
    }
    
    @After
    public void shutdownService() {
        delegate.shutdown();
    }
    
    @Test
    public void testCacheInstanceForRedo() {
        delegate.cacheInstanceForRedo(serviceName, groupName, instance);
        Assert.assertFalse(testExporter.exportedSpans.isEmpty());
        SpanData spanData = testExporter.exportedSpans.get(testExporter.exportedSpans.size() - 1);
        Assert.assertEquals("Nacos.client.naming.worker / cacheInstanceForRedo", spanData.getName());
        
        Assert.assertEquals(serviceName,
                spanData.getAttributes().get(AttributeKey.stringKey(NacosSemanticAttributes.SERVICE_NAME)));
        Assert.assertEquals(groupName,
                spanData.getAttributes().get(AttributeKey.stringKey(NacosSemanticAttributes.GROUP)));
        Assert.assertEquals(instance.toString(),
                spanData.getAttributes().get(AttributeKey.stringKey(NacosSemanticAttributes.INSTANCE)));
    }
    
    @Test
    public void testInstanceDeregistered() {
        delegate.instanceDeregistered(serviceName, groupName);
        Assert.assertFalse(testExporter.exportedSpans.isEmpty());
        SpanData spanData = testExporter.exportedSpans.get(testExporter.exportedSpans.size() - 1);
        Assert.assertEquals("Nacos.client.naming.worker / instanceDeregistered", spanData.getName());
        
        Assert.assertEquals(serviceName,
                spanData.getAttributes().get(AttributeKey.stringKey(NacosSemanticAttributes.SERVICE_NAME)));
        Assert.assertEquals(groupName,
                spanData.getAttributes().get(AttributeKey.stringKey(NacosSemanticAttributes.GROUP)));
    }
    
    @Test
    public void testInstanceDeregister() {
        delegate.instanceDeregister(serviceName, groupName);
        Assert.assertFalse(testExporter.exportedSpans.isEmpty());
        SpanData spanData = testExporter.exportedSpans.get(testExporter.exportedSpans.size() - 1);
        Assert.assertEquals("Nacos.client.naming.worker / instanceDeregister", spanData.getName());
        
        Assert.assertEquals(serviceName,
                spanData.getAttributes().get(AttributeKey.stringKey(NacosSemanticAttributes.SERVICE_NAME)));
        Assert.assertEquals(groupName,
                spanData.getAttributes().get(AttributeKey.stringKey(NacosSemanticAttributes.GROUP)));
    }
    
    @Test
    public void testCacheSubscriberForRedo() {
        delegate.cacheSubscriberForRedo(serviceName, groupName, clusterName);
        Assert.assertFalse(testExporter.exportedSpans.isEmpty());
        SpanData spanData = testExporter.exportedSpans.get(testExporter.exportedSpans.size() - 1);
        Assert.assertEquals("Nacos.client.naming.worker / cacheSubscriberForRedo", spanData.getName());
        
        Assert.assertEquals(serviceName,
                spanData.getAttributes().get(AttributeKey.stringKey(NacosSemanticAttributes.SERVICE_NAME)));
        Assert.assertEquals(groupName,
                spanData.getAttributes().get(AttributeKey.stringKey(NacosSemanticAttributes.GROUP)));
        Assert.assertEquals(clusterName,
                spanData.getAttributes().get(AttributeKey.stringKey(NacosSemanticAttributes.CLUSTER)));
    }
    
    @Test
    public void testSubscriberRegistered() {
        delegate.subscriberRegistered(serviceName, groupName, clusterName);
        Assert.assertFalse(testExporter.exportedSpans.isEmpty());
        SpanData spanData = testExporter.exportedSpans.get(testExporter.exportedSpans.size() - 1);
        Assert.assertEquals("Nacos.client.naming.worker / subscriberRegistered", spanData.getName());
        
        Assert.assertEquals(serviceName,
                spanData.getAttributes().get(AttributeKey.stringKey(NacosSemanticAttributes.SERVICE_NAME)));
        Assert.assertEquals(groupName,
                spanData.getAttributes().get(AttributeKey.stringKey(NacosSemanticAttributes.GROUP)));
        Assert.assertEquals(clusterName,
                spanData.getAttributes().get(AttributeKey.stringKey(NacosSemanticAttributes.CLUSTER)));
    }
    
    @Test
    public void testSubscriberDeregister() {
        delegate.subscriberDeregister(serviceName, groupName, clusterName);
        Assert.assertFalse(testExporter.exportedSpans.isEmpty());
        SpanData spanData = testExporter.exportedSpans.get(testExporter.exportedSpans.size() - 1);
        Assert.assertEquals("Nacos.client.naming.worker / subscriberDeregister", spanData.getName());
        
        Assert.assertEquals(serviceName,
                spanData.getAttributes().get(AttributeKey.stringKey(NacosSemanticAttributes.SERVICE_NAME)));
        Assert.assertEquals(groupName,
                spanData.getAttributes().get(AttributeKey.stringKey(NacosSemanticAttributes.GROUP)));
        Assert.assertEquals(clusterName,
                spanData.getAttributes().get(AttributeKey.stringKey(NacosSemanticAttributes.CLUSTER)));
    }
    
    @Test
    public void testIsSubscriberRegistered() {
        delegate.isSubscriberRegistered(serviceName, groupName, clusterName);
        Assert.assertFalse(testExporter.exportedSpans.isEmpty());
        SpanData spanData = testExporter.exportedSpans.get(testExporter.exportedSpans.size() - 1);
        Assert.assertEquals("Nacos.client.naming.worker / isSubscriberRegistered", spanData.getName());
        
        Assert.assertEquals(serviceName,
                spanData.getAttributes().get(AttributeKey.stringKey(NacosSemanticAttributes.SERVICE_NAME)));
        Assert.assertEquals(groupName,
                spanData.getAttributes().get(AttributeKey.stringKey(NacosSemanticAttributes.GROUP)));
        Assert.assertEquals(clusterName,
                spanData.getAttributes().get(AttributeKey.stringKey(NacosSemanticAttributes.CLUSTER)));
    }
    
    @Test
    public void testRemoveSubscriberForRedo() {
        delegate.removeSubscriberForRedo(serviceName, groupName, clusterName);
        Assert.assertFalse(testExporter.exportedSpans.isEmpty());
        SpanData spanData = testExporter.exportedSpans.get(testExporter.exportedSpans.size() - 1);
        Assert.assertEquals("Nacos.client.naming.worker / removeSubscriberForRedo", spanData.getName());
        
        Assert.assertEquals(serviceName,
                spanData.getAttributes().get(AttributeKey.stringKey(NacosSemanticAttributes.SERVICE_NAME)));
        Assert.assertEquals(groupName,
                spanData.getAttributes().get(AttributeKey.stringKey(NacosSemanticAttributes.GROUP)));
        Assert.assertEquals(clusterName,
                spanData.getAttributes().get(AttributeKey.stringKey(NacosSemanticAttributes.CLUSTER)));
    }
    
    @Test
    public void testGetRegisteredInstancesByKey() {
        String combineKey = NamingUtils.getGroupedName(serviceName, groupName);
        
        delegate.getRegisteredInstancesByKey(combineKey);
        Assert.assertFalse(testExporter.exportedSpans.isEmpty());
        SpanData spanData = testExporter.exportedSpans.get(testExporter.exportedSpans.size() - 1);
        Assert.assertEquals("Nacos.client.naming.worker / getRegisteredInstancesByKey", spanData.getName());
        
        Assert.assertEquals(combineKey,
                spanData.getAttributes().get(AttributeKey.stringKey(NacosSemanticAttributes.SERVICE_NAME)));
    }
}
