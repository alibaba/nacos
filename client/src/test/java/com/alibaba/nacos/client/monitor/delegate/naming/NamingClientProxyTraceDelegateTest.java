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

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ListView;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.api.selector.AbstractSelector;
import com.alibaba.nacos.api.selector.ExpressionSelector;
import com.alibaba.nacos.client.env.NacosClientProperties;
import com.alibaba.nacos.client.monitor.delegate.OpenTelemetryBaseTest;
import com.alibaba.nacos.client.naming.cache.ServiceInfoHolder;
import com.alibaba.nacos.client.naming.core.ServiceInfoUpdateService;
import com.alibaba.nacos.client.naming.event.InstancesChangeNotifier;
import com.alibaba.nacos.client.naming.remote.NamingClientProxy;
import com.alibaba.nacos.client.naming.remote.NamingClientProxyDelegate;
import com.alibaba.nacos.client.naming.remote.gprc.NamingGrpcClientProxy;
import com.alibaba.nacos.common.constant.NacosSemanticAttributes;
import com.alibaba.nacos.common.utils.StringUtils;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;

public class NamingClientProxyTraceDelegateTest extends OpenTelemetryBaseTest {
    
    private NamingClientProxy proxy;
    
    private NamingClientProxyDelegate delegate;
    
    private NamingClientProxy mockGrpcClient;
    
    private ServiceInfoHolder mockHolder;
    
    //    private ServiceInfoUpdateService mockServiceInfoUpdateService;
    
    private final String serviceName = "testService";
    
    private final String groupName = "testGroup";
    
    @Before
    public void initProxy() throws NacosException, NoSuchFieldException, IllegalAccessException {
        String nameSpace = "testNameSpace";
        
        mockHolder = Mockito.mock(ServiceInfoHolder.class);
        
        Properties props = new Properties();
        props.setProperty("serverAddr", "localhost");
        final NacosClientProperties nacosClientProperties = NacosClientProperties.PROTOTYPE.derive(props);
        InstancesChangeNotifier notifier = new InstancesChangeNotifier();
        delegate = new NamingClientProxyDelegate(nameSpace, mockHolder, nacosClientProperties, notifier);
        
        mockGrpcClient = Mockito.mock(NamingGrpcClientProxy.class);
        Field grpcClientProxyField = NamingClientProxyDelegate.class.getDeclaredField("grpcClientProxy");
        grpcClientProxyField.setAccessible(true);
        grpcClientProxyField.set(delegate, mockGrpcClient);
        
        proxy = new NamingClientProxyTraceDelegate(delegate);
    }
    
    @After
    public void closeProxy() throws NacosException {
        delegate.shutdown();
        proxy.shutdown();
    }
    
    @Test
    public void testRegisterService() throws NacosException {
        
        Instance instance = new Instance();
        instance.setServiceName(serviceName);
        instance.setClusterName(groupName);
        instance.setIp("1.1.1.1");
        instance.setPort(1);
        instance.setEphemeral(true);
        
        Mockito.doNothing().when(mockGrpcClient).registerService(serviceName, groupName, instance);
        proxy.registerService(serviceName, groupName, instance);
        
        Assert.assertFalse(testExporter.exportedSpans.isEmpty());
        SpanData spanData = testExporter.exportedSpans.get(testExporter.exportedSpans.size() - 1);
        Assert.assertEquals("Nacos.client.naming.service / registerService", spanData.getName());
        
        Assert.assertEquals(serviceName,
                spanData.getAttributes().get(AttributeKey.stringKey(NacosSemanticAttributes.SERVICE_NAME)));
        Assert.assertEquals(groupName,
                spanData.getAttributes().get(AttributeKey.stringKey(NacosSemanticAttributes.GROUP)));
        Assert.assertEquals(instance.toString(),
                spanData.getAttributes().get(AttributeKey.stringKey(NacosSemanticAttributes.INSTANCE)));
    }
    
    @Test
    public void testBatchRegisterService() throws NacosException {
        
        Instance instance = new Instance();
        instance.setServiceName(serviceName);
        instance.setClusterName(groupName);
        instance.setIp("1.1.1.1");
        instance.setPort(1);
        instance.setEphemeral(true);
        
        List<Instance> instances = Collections.singletonList(instance);
        
        Mockito.doNothing().when(mockGrpcClient).batchRegisterService(serviceName, groupName, instances);
        proxy.batchRegisterService(serviceName, groupName, instances);
        
        Assert.assertFalse(testExporter.exportedSpans.isEmpty());
        SpanData spanData = testExporter.exportedSpans.get(testExporter.exportedSpans.size() - 1);
        Assert.assertEquals("Nacos.client.naming.service / batchRegisterService", spanData.getName());
        
        Assert.assertEquals(serviceName,
                spanData.getAttributes().get(AttributeKey.stringKey(NacosSemanticAttributes.SERVICE_NAME)));
        Assert.assertEquals(groupName,
                spanData.getAttributes().get(AttributeKey.stringKey(NacosSemanticAttributes.GROUP)));
        Assert.assertEquals(StringUtils.join(instances, ", "),
                spanData.getAttributes().get(AttributeKey.stringKey(NacosSemanticAttributes.INSTANCE)));
    }
    
    @Test
    public void testBatchDeregisterService() throws NacosException {
        
        Instance instance = new Instance();
        instance.setServiceName(serviceName);
        instance.setClusterName(groupName);
        instance.setIp("1.1.1.1");
        instance.setPort(1);
        instance.setEphemeral(true);
        
        List<Instance> instances = Collections.singletonList(instance);
        
        Mockito.doNothing().when(mockGrpcClient).batchDeregisterService(serviceName, groupName, instances);
        proxy.batchDeregisterService(serviceName, groupName, instances);
        
        Assert.assertFalse(testExporter.exportedSpans.isEmpty());
        SpanData spanData = testExporter.exportedSpans.get(testExporter.exportedSpans.size() - 1);
        Assert.assertEquals("Nacos.client.naming.service / batchDeregisterService", spanData.getName());
        
        Assert.assertEquals(serviceName,
                spanData.getAttributes().get(AttributeKey.stringKey(NacosSemanticAttributes.SERVICE_NAME)));
        Assert.assertEquals(groupName,
                spanData.getAttributes().get(AttributeKey.stringKey(NacosSemanticAttributes.GROUP)));
        Assert.assertEquals(StringUtils.join(instances, ", "),
                spanData.getAttributes().get(AttributeKey.stringKey(NacosSemanticAttributes.INSTANCE)));
    }
    
    @Test
    public void testDeregisterService() throws NacosException {
        
        Instance instance = new Instance();
        instance.setServiceName(serviceName);
        instance.setClusterName(groupName);
        instance.setIp("1.1.1.1");
        instance.setPort(1);
        instance.setEphemeral(true);
        
        Mockito.doNothing().when(mockGrpcClient).deregisterService(serviceName, groupName, instance);
        proxy.deregisterService(serviceName, groupName, instance);
        
        Assert.assertFalse(testExporter.exportedSpans.isEmpty());
        SpanData spanData = testExporter.exportedSpans.get(testExporter.exportedSpans.size() - 1);
        Assert.assertEquals("Nacos.client.naming.service / deregisterService", spanData.getName());
        
        Assert.assertEquals(serviceName,
                spanData.getAttributes().get(AttributeKey.stringKey(NacosSemanticAttributes.SERVICE_NAME)));
        Assert.assertEquals(groupName,
                spanData.getAttributes().get(AttributeKey.stringKey(NacosSemanticAttributes.GROUP)));
        Assert.assertEquals(instance.toString(),
                spanData.getAttributes().get(AttributeKey.stringKey(NacosSemanticAttributes.INSTANCE)));
    }
    
    @Test
    public void testQueryInstancesOfService() throws NacosException {
        
        String clusters = "testCluster";
        int udpPort = 1111;
        ServiceInfo serviceInfo = new ServiceInfo();
        
        Mockito.when(mockGrpcClient.queryInstancesOfService(serviceName, groupName, clusters, udpPort, false))
                .thenReturn(serviceInfo);
        ServiceInfo result = proxy.queryInstancesOfService(serviceName, groupName, clusters, udpPort, false);
        Assert.assertEquals(serviceInfo, result);
        
        Assert.assertFalse(testExporter.exportedSpans.isEmpty());
        SpanData spanData = testExporter.exportedSpans.get(testExporter.exportedSpans.size() - 1);
        Assert.assertEquals("Nacos.client.naming.service / queryInstancesOfService", spanData.getName());
        
        Assert.assertEquals(serviceName,
                spanData.getAttributes().get(AttributeKey.stringKey(NacosSemanticAttributes.SERVICE_NAME)));
        Assert.assertEquals(groupName,
                spanData.getAttributes().get(AttributeKey.stringKey(NacosSemanticAttributes.GROUP)));
        Assert.assertEquals(clusters,
                spanData.getAttributes().get(AttributeKey.stringKey(NacosSemanticAttributes.CLUSTER)));
    }
    
    @Test
    public void testGetServiceList() throws NacosException {
        
        AbstractSelector selector = new ExpressionSelector();
        int pageNo = 1;
        int pageSize = 10;
        ListView<String> listView = new ListView<>();
        
        Mockito.when(mockGrpcClient.getServiceList(pageNo, pageSize, groupName, selector)).thenReturn(listView);
        ListView<String> resultList = proxy.getServiceList(pageNo, pageSize, groupName, selector);
        Assert.assertEquals(listView, resultList);
        
        Assert.assertFalse(testExporter.exportedSpans.isEmpty());
        SpanData spanData = testExporter.exportedSpans.get(testExporter.exportedSpans.size() - 1);
        Assert.assertEquals("Nacos.client.naming.service / getServiceList", spanData.getName());
        
        Assert.assertEquals(groupName,
                spanData.getAttributes().get(AttributeKey.stringKey(NacosSemanticAttributes.GROUP)));
        Assert.assertEquals(Long.valueOf(pageNo),
                spanData.getAttributes().get(AttributeKey.longKey(NacosSemanticAttributes.PAGE_NO)));
    }
    
    @Test
    public void testSubscribe() throws IllegalAccessException, NoSuchFieldException, NacosException {
        
        ServiceInfoUpdateService mockServiceInfoUpdateService = Mockito.mock(ServiceInfoUpdateService.class);
        Field serviceInfoUpdateServiceField = NamingClientProxyDelegate.class.getDeclaredField(
                "serviceInfoUpdateService");
        serviceInfoUpdateServiceField.setAccessible(true);
        serviceInfoUpdateServiceField.set(delegate, mockServiceInfoUpdateService);
        
        String clusters = "testCluster";
        ServiceInfo serviceInfo = new ServiceInfo();
        
        Mockito.when(mockHolder.getServiceInfoMap()).thenReturn(new HashMap<>());
        Mockito.when(mockHolder.processServiceInfo(serviceInfo)).thenReturn(serviceInfo);
        Mockito.doNothing().when(mockServiceInfoUpdateService).scheduleUpdateIfAbsent(serviceName, groupName, clusters);
        Mockito.when(mockGrpcClient.subscribe(serviceName, groupName, clusters)).thenReturn(serviceInfo);
        ServiceInfo result = proxy.subscribe(serviceName, groupName, clusters);
        Assert.assertEquals(serviceInfo, result);
        
        Assert.assertFalse(testExporter.exportedSpans.isEmpty());
        SpanData spanData = testExporter.exportedSpans.get(testExporter.exportedSpans.size() - 1);
        Assert.assertEquals("Nacos.client.naming.service / subscribe", spanData.getName());
        
        Assert.assertEquals(serviceName,
                spanData.getAttributes().get(AttributeKey.stringKey(NacosSemanticAttributes.SERVICE_NAME)));
        Assert.assertEquals(groupName,
                spanData.getAttributes().get(AttributeKey.stringKey(NacosSemanticAttributes.GROUP)));
        Assert.assertEquals(clusters,
                spanData.getAttributes().get(AttributeKey.stringKey(NacosSemanticAttributes.CLUSTER)));
    }
    
    @Test
    public void testUnsubscribe() throws NacosException, IllegalAccessException, NoSuchFieldException {
        
        ServiceInfoUpdateService mockServiceInfoUpdateService = Mockito.mock(ServiceInfoUpdateService.class);
        Field serviceInfoUpdateServiceField = NamingClientProxyDelegate.class.getDeclaredField(
                "serviceInfoUpdateService");
        serviceInfoUpdateServiceField.setAccessible(true);
        serviceInfoUpdateServiceField.set(delegate, mockServiceInfoUpdateService);
        
        String clusters = "testCluster";
        
        Mockito.doNothing().when(mockServiceInfoUpdateService).stopUpdateIfContain(serviceName, groupName, clusters);
        Mockito.doNothing().when(mockGrpcClient).unsubscribe(serviceName, groupName, clusters);
        proxy.unsubscribe(serviceName, groupName, clusters);
        
        Assert.assertFalse(testExporter.exportedSpans.isEmpty());
        SpanData spanData = testExporter.exportedSpans.get(testExporter.exportedSpans.size() - 1);
        Assert.assertEquals("Nacos.client.naming.service / unsubscribe", spanData.getName());
        
        Assert.assertEquals(serviceName,
                spanData.getAttributes().get(AttributeKey.stringKey(NacosSemanticAttributes.SERVICE_NAME)));
        Assert.assertEquals(groupName,
                spanData.getAttributes().get(AttributeKey.stringKey(NacosSemanticAttributes.GROUP)));
        Assert.assertEquals(clusters,
                spanData.getAttributes().get(AttributeKey.stringKey(NacosSemanticAttributes.CLUSTER)));
    }
    
    @Test
    public void testIsSubscribed() throws NacosException {
        
        String clusters = "testCluster";
        
        Mockito.when(mockGrpcClient.isSubscribed(serviceName, groupName, clusters)).thenReturn(Boolean.TRUE);
        boolean result = proxy.isSubscribed(serviceName, groupName, clusters);
        Assert.assertTrue(result);
        
        Mockito.when(mockGrpcClient.isSubscribed(serviceName, groupName, clusters)).thenReturn(Boolean.FALSE);
        result = proxy.isSubscribed(serviceName, groupName, clusters);
        Assert.assertFalse(result);
        
        Assert.assertFalse(testExporter.exportedSpans.isEmpty());
        SpanData spanData = testExporter.exportedSpans.get(testExporter.exportedSpans.size() - 2);
        Assert.assertEquals("Nacos.client.naming.service / isSubscribed", spanData.getName());
        
        Assert.assertEquals(serviceName,
                spanData.getAttributes().get(AttributeKey.stringKey(NacosSemanticAttributes.SERVICE_NAME)));
        Assert.assertEquals(groupName,
                spanData.getAttributes().get(AttributeKey.stringKey(NacosSemanticAttributes.GROUP)));
        Assert.assertEquals(clusters,
                spanData.getAttributes().get(AttributeKey.stringKey(NacosSemanticAttributes.CLUSTER)));
        
        spanData = testExporter.exportedSpans.get(testExporter.exportedSpans.size() - 1);
        Assert.assertEquals("Nacos.client.naming.service / isSubscribed", spanData.getName());
        
        Assert.assertEquals(serviceName,
                spanData.getAttributes().get(AttributeKey.stringKey(NacosSemanticAttributes.SERVICE_NAME)));
        Assert.assertEquals(groupName,
                spanData.getAttributes().get(AttributeKey.stringKey(NacosSemanticAttributes.GROUP)));
        Assert.assertEquals(clusters,
                spanData.getAttributes().get(AttributeKey.stringKey(NacosSemanticAttributes.CLUSTER)));
    }
    
    @Test
    public void testServerHealthy() {
        Mockito.when(mockGrpcClient.serverHealthy()).thenReturn(Boolean.TRUE);
        boolean result = proxy.serverHealthy();
        Assert.assertTrue(result);
        
        SpanData spanData = testExporter.exportedSpans.get(testExporter.exportedSpans.size() - 1);
        Assert.assertEquals("Nacos.client.naming.service / serverHealthy", spanData.getName());
        
        Mockito.when(mockGrpcClient.serverHealthy()).thenReturn(Boolean.FALSE);
        result = proxy.serverHealthy();
        Assert.assertFalse(result);
        
        spanData = testExporter.exportedSpans.get(testExporter.exportedSpans.size() - 1);
        Assert.assertEquals("Nacos.client.naming.service / serverHealthy", spanData.getName());
    }
}
