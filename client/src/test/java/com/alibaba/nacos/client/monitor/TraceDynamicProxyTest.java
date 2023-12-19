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

package com.alibaba.nacos.client.monitor;

import com.alibaba.nacos.api.config.listener.AbstractListener;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ListView;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.api.naming.utils.NamingUtils;
import com.alibaba.nacos.api.selector.AbstractSelector;
import com.alibaba.nacos.api.selector.ExpressionSelector;
import com.alibaba.nacos.client.config.filter.impl.ConfigFilterChainManager;
import com.alibaba.nacos.client.config.http.HttpAgent;
import com.alibaba.nacos.client.config.http.ServerHttpAgent;
import com.alibaba.nacos.client.config.impl.ClientWorker;
import com.alibaba.nacos.client.config.impl.ServerListManager;
import com.alibaba.nacos.client.env.NacosClientProperties;
import com.alibaba.nacos.client.config.proxy.ClientWorkerProxy;
import com.alibaba.nacos.client.monitor.naming.NamingGrpcRedoServiceTraceProxy;
import com.alibaba.nacos.client.naming.cache.ServiceInfoHolder;
import com.alibaba.nacos.client.naming.core.ServiceInfoUpdateService;
import com.alibaba.nacos.client.naming.event.InstancesChangeNotifier;
import com.alibaba.nacos.client.naming.remote.NamingClientProxy;
import com.alibaba.nacos.client.naming.remote.NamingClientProxyDelegate;
import com.alibaba.nacos.client.naming.remote.gprc.NamingGrpcClientProxy;
import com.alibaba.nacos.client.naming.remote.gprc.redo.NamingGrpcRedoService;
import com.alibaba.nacos.common.utils.ReflectUtils;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.junit.After;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;

import javax.annotation.ParametersAreNonnullByDefault;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ScheduledExecutorService;

public class TraceDynamicProxyTest {
    
    public static OpenTelemetry testOpenTelemetry = null;
    
    public static TraceDynamicProxyTest.TestExporter testExporter = null;
    
    @BeforeClass
    public static void init() {
        testExporter = new TraceDynamicProxyTest.TestExporter();
        testOpenTelemetry = OpenTelemetrySdk.builder().setTracerProvider(
                SdkTracerProvider.builder().addSpanProcessor(SimpleSpanProcessor.create(testExporter)).build()).build();
        TraceMonitor.setOpenTelemetry(testOpenTelemetry);
    }
    
    @After
    public void clear() {
        testExporter.exportedSpans.clear();
    }
    
    @Test
    public void testGetClientWorkerTraceProxyListener() throws NacosException {
        Properties prop = new Properties();
        ConfigFilterChainManager filter = new ConfigFilterChainManager(new Properties());
        ServerListManager serverListManager = Mockito.mock(ServerListManager.class);
        
        final NacosClientProperties nacosClientProperties = NacosClientProperties.PROTOTYPE.derive(prop);
        ClientWorker clientWorker = new ClientWorker(filter, serverListManager, nacosClientProperties);
        ClientWorkerProxy traceProxy = TraceDynamicProxy.getClientWorkerTraceProxy(clientWorker);
        
        Listener listener = new AbstractListener() {
            @Override
            public void receiveConfigInfo(String configInfo) {
            }
        };
        
        String dataId = "a";
        String group = "b";
        String content = "c";
        
        traceProxy.addTenantListenersWithContent(dataId, group, content, null, Collections.singletonList(listener));
        Assert.assertEquals("Nacos.client.config.worker / addCacheDataIfAbsent",
                testExporter.exportedSpans.get(0).getName());
        Assert.assertEquals("Nacos.client.config.service / addTenantListenersWithContent",
                testExporter.exportedSpans.get(1).getName());
        testExporter.exportedSpans.clear();
        
        traceProxy.addTenantListeners(dataId, group, Collections.singletonList(listener));
        Assert.assertEquals("Nacos.client.config.worker / addCacheDataIfAbsent",
                testExporter.exportedSpans.get(0).getName());
        Assert.assertEquals("Nacos.client.config.service / addTenantListeners",
                testExporter.exportedSpans.get(1).getName());
        testExporter.exportedSpans.clear();
        
        traceProxy.removeTenantListener(dataId, group, listener);
        Assert.assertEquals("Nacos.client.config.service / removeTenantListener",
                testExporter.exportedSpans.get(0).getName());
        testExporter.exportedSpans.clear();
        
        clientWorker.shutdown();
    }
    
    @Test
    public void testGetClientWorkerTraceProxyConfig() throws NacosException {
        Properties prop = new Properties();
        ConfigFilterChainManager filter = new ConfigFilterChainManager(new Properties());
        ServerListManager serverListManager = Mockito.mock(ServerListManager.class);
        
        final NacosClientProperties nacosClientProperties = NacosClientProperties.PROTOTYPE.derive(prop);
        ClientWorker clientWorker = new ClientWorker(filter, serverListManager, nacosClientProperties);
        ClientWorkerProxy traceProxy = TraceDynamicProxy.getClientWorkerTraceProxy(clientWorker);
        
        String dataId = "a";
        String group = "b";
        String tenant = "c";
        
        try {
            traceProxy.getServerConfig(dataId, group, tenant, 3000, false);
            Assert.fail();
        } catch (NacosException e) {
            Assert.assertEquals("Client not connected, current status:STARTING", e.getErrMsg());
            Assert.assertEquals(-401, e.getErrCode());
        }
        Assert.assertEquals("Nacos.client.config.service / getServerConfig",
                testExporter.exportedSpans.get(testExporter.exportedSpans.size() - 1).getName());
        testExporter.exportedSpans.clear();
        
        String tag = "tag";
        
        try {
            traceProxy.removeConfig(dataId, group, tenant, tag);
            Assert.fail();
        } catch (NacosException e) {
            Assert.assertEquals("Client not connected, current status:STARTING", e.getErrMsg());
            Assert.assertEquals(-401, e.getErrCode());
        }
        Assert.assertEquals("Nacos.client.config.service / removeConfig",
                testExporter.exportedSpans.get(testExporter.exportedSpans.size() - 1).getName());
        testExporter.exportedSpans.clear();
        
        String content = "d";
        String appName = "app";
        String betaIps = "1.1.1.1";
        String casMd5 = "1111";
        String type = "properties";
        
        boolean b = traceProxy.publishConfig(dataId, group, tenant, appName, tag, betaIps, content, null, casMd5, type);
        Assert.assertFalse(b);
        Assert.assertEquals("Nacos.client.config.service / publishConfig",
                testExporter.exportedSpans.get(testExporter.exportedSpans.size() - 1).getName());
        testExporter.exportedSpans.clear();
        
        clientWorker.shutdown();
    }
    
    @Test
    public void testGetClientWorkerTraceProxyIsHealthServer()
            throws NacosException, NoSuchFieldException, IllegalAccessException {
        Properties prop = new Properties();
        ConfigFilterChainManager filter = new ConfigFilterChainManager(new Properties());
        ServerListManager serverListManager = Mockito.mock(ServerListManager.class);
        
        final NacosClientProperties nacosClientProperties = NacosClientProperties.PROTOTYPE.derive(prop);
        ClientWorker clientWorker = new ClientWorker(filter, serverListManager, nacosClientProperties);
        ClientWorker.ConfigRpcTransportClient client = Mockito.mock(ClientWorker.ConfigRpcTransportClient.class);
        Mockito.when(client.isHealthServer()).thenReturn(Boolean.TRUE);
        
        Field declaredField = ClientWorker.class.getDeclaredField("agent");
        declaredField.setAccessible(true);
        declaredField.set(clientWorker, client);
        
        ClientWorkerProxy traceProxy = TraceDynamicProxy.getClientWorkerTraceProxy(clientWorker);
        
        Assert.assertTrue(traceProxy.isHealthServer());
        Assert.assertEquals("Nacos.client.config.service / isHealthServer",
                testExporter.exportedSpans.get(testExporter.exportedSpans.size() - 1).getName());
        
        clientWorker.shutdown();
    }
    
    @Test
    public void testGetConfigRpcTransportClientTraceProxy() throws NacosException {
        Properties prop = new Properties();
        ConfigFilterChainManager filter = new ConfigFilterChainManager(new Properties());
        ServerListManager serverListManager = Mockito.mock(ServerListManager.class);
        
        final NacosClientProperties nacosClientProperties = NacosClientProperties.PROTOTYPE.derive(prop);
        ClientWorker clientWorker = new ClientWorker(filter, serverListManager, nacosClientProperties);
        ClientWorkerProxy traceProxy = TraceDynamicProxy.getClientWorkerTraceProxy(clientWorker);
        
        String dataId = "a";
        String group = "b";
        String tenant = "c";
        
        try {
            traceProxy.getServerConfig(dataId, group, tenant, 3000, false);
            Assert.fail();
        } catch (NacosException e) {
            Assert.assertEquals("Client not connected, current status:STARTING", e.getErrMsg());
            Assert.assertEquals(-401, e.getErrCode());
        }
        Assert.assertEquals("Nacos.client.config.worker / queryConfig",
                testExporter.exportedSpans.get(testExporter.exportedSpans.size() - 2).getName());
        Assert.assertEquals("Nacos.client.config.rpc / GRPC",
                testExporter.exportedSpans.get(testExporter.exportedSpans.size() - 3).getName());
        testExporter.exportedSpans.clear();
        
        String tag = "tag";
        
        try {
            traceProxy.removeConfig(dataId, group, tenant, tag);
            Assert.fail();
        } catch (NacosException e) {
            Assert.assertEquals("Client not connected, current status:STARTING", e.getErrMsg());
            Assert.assertEquals(-401, e.getErrCode());
        }
        Assert.assertEquals("Nacos.client.config.worker / removeConfig",
                testExporter.exportedSpans.get(testExporter.exportedSpans.size() - 2).getName());
        Assert.assertEquals("Nacos.client.config.rpc / GRPC",
                testExporter.exportedSpans.get(testExporter.exportedSpans.size() - 3).getName());
        testExporter.exportedSpans.clear();
        
        String content = "d";
        String appName = "app";
        String betaIps = "1.1.1.1";
        String casMd5 = "1111";
        String type = "properties";
        
        boolean b = traceProxy.publishConfig(dataId, group, tenant, appName, tag, betaIps, content, null, casMd5, type);
        Assert.assertFalse(b);
        Assert.assertEquals("Nacos.client.config.worker / publishConfig",
                testExporter.exportedSpans.get(testExporter.exportedSpans.size() - 2).getName());
        Assert.assertEquals("Nacos.client.config.rpc / GRPC",
                testExporter.exportedSpans.get(testExporter.exportedSpans.size() - 3).getName());
        testExporter.exportedSpans.clear();
        
        clientWorker.shutdown();
    }
    
    @Test
    public void testGetHttpAgentTraceProxy() throws Exception {
        ServerListManager serverListManager = Mockito.mock(ServerListManager.class);
        HttpAgent httpAgent = new ServerHttpAgent(serverListManager);
        HttpAgent agent = TraceDynamicProxy.getHttpAgentTraceProxy(httpAgent);
        
        try {
            agent.httpGet("/aa", new HashMap<>(), new HashMap<>(), "UTF-8", 1L);
            Assert.fail();
        } catch (IllegalArgumentException e) {
            Assert.assertEquals("URI is not absolute", e.getMessage());
        }
        Assert.assertEquals("Nacos.client.config.http / GET",
                testExporter.exportedSpans.get(testExporter.exportedSpans.size() - 1).getName());
        
        try {
            agent.httpPost("/bb", new HashMap<>(), new HashMap<>(), "UTF-8", 1L);
            Assert.fail();
        } catch (IllegalArgumentException e) {
            Assert.assertEquals("URI is not absolute", e.getMessage());
        }
        Assert.assertEquals("Nacos.client.config.http / POST",
                testExporter.exportedSpans.get(testExporter.exportedSpans.size() - 1).getName());
        
        try {
            agent.httpDelete("/cc", new HashMap<>(), new HashMap<>(), "UTF-8", 1L);
            Assert.fail();
        } catch (IllegalArgumentException e) {
            Assert.assertEquals("URI is not absolute", e.getMessage());
        }
        Assert.assertEquals("Nacos.client.config.http / DELETE",
                testExporter.exportedSpans.get(testExporter.exportedSpans.size() - 1).getName());
        
        agent.shutdown();
    }
    
    @Test
    public void testGetNamingClientProxyTraceProxy()
            throws NacosException, IllegalAccessException, NoSuchFieldException {
        String ns = "ns1";
        ServiceInfoHolder holder = Mockito.mock(ServiceInfoHolder.class);
        Properties props = new Properties();
        props.setProperty("serverAddr", "localhost");
        final NacosClientProperties nacosClientProperties = NacosClientProperties.PROTOTYPE.derive(props);
        InstancesChangeNotifier notifier = new InstancesChangeNotifier();
        NamingClientProxyDelegate delegate = new NamingClientProxyDelegate(ns, holder, nacosClientProperties, notifier);
        
        NamingGrpcClientProxy mockGrpcClient = Mockito.mock(NamingGrpcClientProxy.class);
        Field grpcClientProxyField = NamingClientProxyDelegate.class.getDeclaredField("grpcClientProxy");
        grpcClientProxyField.setAccessible(true);
        grpcClientProxyField.set(delegate, mockGrpcClient);
        
        ServiceInfoUpdateService serviceInfoUpdateService = Mockito.mock(ServiceInfoUpdateService.class);
        Field serviceInfoUpdateServiceField = NamingClientProxyDelegate.class.getDeclaredField(
                "serviceInfoUpdateService");
        serviceInfoUpdateServiceField.setAccessible(true);
        serviceInfoUpdateServiceField.set(delegate, serviceInfoUpdateService);
        
        Instance instance = new Instance();
        String serviceName = "service1";
        String groupName = "group1";
        instance.setServiceName(serviceName);
        instance.setClusterName(groupName);
        instance.setIp("1.1.1.1");
        instance.setPort(1);
        instance.setEphemeral(true);
        
        NamingClientProxy proxy = TraceDynamicProxy.getNamingClientProxyTraceProxy(delegate);
        
        Mockito.doNothing().when(mockGrpcClient).registerService(serviceName, groupName, instance);
        proxy.registerService(serviceName, groupName, instance);
        Assert.assertEquals("Nacos.client.naming.service / registerService",
                testExporter.exportedSpans.get(testExporter.exportedSpans.size() - 1).getName());
        
        Mockito.doNothing().when(mockGrpcClient).deregisterService(serviceName, groupName, instance);
        proxy.deregisterService(serviceName, groupName, instance);
        Assert.assertEquals("Nacos.client.naming.service / deregisterService",
                testExporter.exportedSpans.get(testExporter.exportedSpans.size() - 1).getName());
        
        Mockito.doNothing().when(mockGrpcClient)
                .batchRegisterService(serviceName, groupName, Collections.singletonList(instance));
        proxy.batchRegisterService(serviceName, groupName, Collections.singletonList(instance));
        Assert.assertEquals("Nacos.client.naming.service / batchRegisterService",
                testExporter.exportedSpans.get(testExporter.exportedSpans.size() - 1).getName());
        
        Mockito.doNothing().when(mockGrpcClient)
                .batchDeregisterService(serviceName, groupName, Collections.singletonList(instance));
        proxy.batchDeregisterService(serviceName, groupName, Collections.singletonList(instance));
        Assert.assertEquals("Nacos.client.naming.service / batchDeregisterService",
                testExporter.exportedSpans.get(testExporter.exportedSpans.size() - 1).getName());
        
        String clusters = "cluster1";
        int udpPort = 1111;
        ServiceInfo serviceInfo = new ServiceInfo();
        
        Mockito.when(mockGrpcClient.queryInstancesOfService(serviceName, groupName, clusters, udpPort, false))
                .thenReturn(serviceInfo);
        ServiceInfo result = proxy.queryInstancesOfService(serviceName, groupName, clusters, udpPort, false);
        Assert.assertEquals(serviceInfo, result);
        Assert.assertEquals("Nacos.client.naming.service / queryInstancesOfService",
                testExporter.exportedSpans.get(testExporter.exportedSpans.size() - 1).getName());
        
        AbstractSelector selector = new ExpressionSelector();
        int pageNo = 1;
        int pageSize = 10;
        ListView<String> listView = new ListView<>();
        
        Mockito.when(mockGrpcClient.getServiceList(pageNo, pageSize, groupName, selector)).thenReturn(listView);
        ListView<String> resultList = proxy.getServiceList(pageNo, pageSize, groupName, selector);
        Assert.assertEquals(listView, resultList);
        Assert.assertEquals("Nacos.client.naming.service / getServiceList",
                testExporter.exportedSpans.get(testExporter.exportedSpans.size() - 1).getName());
        
        Mockito.when(holder.getServiceInfoMap()).thenReturn(new HashMap<>());
        Mockito.when(holder.processServiceInfo(serviceInfo)).thenReturn(serviceInfo);
        Mockito.doNothing().when(serviceInfoUpdateService).scheduleUpdateIfAbsent(serviceName, groupName, clusters);
        Mockito.when(mockGrpcClient.subscribe(serviceName, groupName, clusters)).thenReturn(serviceInfo);
        result = proxy.subscribe(serviceName, groupName, clusters);
        Assert.assertEquals(serviceInfo, result);
        Assert.assertEquals("Nacos.client.naming.service / subscribe",
                testExporter.exportedSpans.get(testExporter.exportedSpans.size() - 1).getName());
        
        Mockito.doNothing().when(serviceInfoUpdateService).stopUpdateIfContain(serviceName, groupName, clusters);
        Mockito.doNothing().when(mockGrpcClient).unsubscribe(serviceName, groupName, clusters);
        proxy.unsubscribe(serviceName, groupName, clusters);
        Assert.assertEquals("Nacos.client.naming.service / unsubscribe",
                testExporter.exportedSpans.get(testExporter.exportedSpans.size() - 1).getName());
        
        Mockito.when(mockGrpcClient.isSubscribed(serviceName, groupName, clusters)).thenReturn(Boolean.TRUE);
        boolean isSubscribed = proxy.isSubscribed(serviceName, groupName, clusters);
        Assert.assertTrue(isSubscribed);
        Assert.assertEquals("Nacos.client.naming.service / isSubscribed",
                testExporter.exportedSpans.get(testExporter.exportedSpans.size() - 1).getName());
        
        Mockito.when(mockGrpcClient.serverHealthy()).thenReturn(Boolean.TRUE);
        boolean serverHealthy = proxy.serverHealthy();
        Assert.assertTrue(serverHealthy);
        Assert.assertEquals("Nacos.client.naming.service / serverHealthy",
                testExporter.exportedSpans.get(testExporter.exportedSpans.size() - 1).getName());
        
        delegate.shutdown();
    }
    
    @Test
    public void testGetNamingGrpcRedoServiceTraceProxy() {
        NamingGrpcClientProxy clientProxy = Mockito.mock(NamingGrpcClientProxy.class);
        NamingGrpcRedoService redoService = new NamingGrpcRedoService(clientProxy);
        ScheduledExecutorService redoExecutor = (ScheduledExecutorService) ReflectUtils.getFieldValue(redoService,
                "redoExecutor");
        redoExecutor.shutdownNow();
        
        String service = "service";
        String group = "group";
        Instance instance = new Instance();
        
        NamingGrpcRedoServiceTraceProxy proxy = TraceDynamicProxy.getNamingGrpcRedoServiceTraceProxy(redoService);
        
        proxy.cacheInstanceForRedo(service, group, instance);
        Assert.assertEquals("Nacos.client.naming.worker / cacheInstanceForRedo",
                testExporter.exportedSpans.get(testExporter.exportedSpans.size() - 1).getName());
        
        proxy.instanceDeregistered(service, group);
        Assert.assertEquals("Nacos.client.naming.worker / instanceDeregistered",
                testExporter.exportedSpans.get(testExporter.exportedSpans.size() - 1).getName());
        
        proxy.instanceDeregister(service, group);
        Assert.assertEquals("Nacos.client.naming.worker / instanceDeregister",
                testExporter.exportedSpans.get(testExporter.exportedSpans.size() - 1).getName());
        
        proxy.instanceDeregistered(service, group);
        Assert.assertEquals("Nacos.client.naming.worker / instanceDeregistered",
                testExporter.exportedSpans.get(testExporter.exportedSpans.size() - 1).getName());
        
        String cluster = "cluster";
        
        proxy.cacheSubscriberForRedo(service, group, cluster);
        Assert.assertEquals("Nacos.client.naming.worker / cacheSubscriberForRedo",
                testExporter.exportedSpans.get(testExporter.exportedSpans.size() - 1).getName());
        
        proxy.subscriberRegistered(service, group, cluster);
        Assert.assertEquals("Nacos.client.naming.worker / subscriberRegistered",
                testExporter.exportedSpans.get(testExporter.exportedSpans.size() - 1).getName());
        
        proxy.subscriberDeregister(service, group, cluster);
        Assert.assertEquals("Nacos.client.naming.worker / subscriberDeregister",
                testExporter.exportedSpans.get(testExporter.exportedSpans.size() - 1).getName());
        
        proxy.isSubscriberRegistered(service, group, cluster);
        Assert.assertEquals("Nacos.client.naming.worker / isSubscriberRegistered",
                testExporter.exportedSpans.get(testExporter.exportedSpans.size() - 1).getName());
        
        proxy.removeSubscriberForRedo(service, group, cluster);
        Assert.assertEquals("Nacos.client.naming.worker / removeSubscriberForRedo",
                testExporter.exportedSpans.get(testExporter.exportedSpans.size() - 1).getName());
        
        String combineKey = NamingUtils.getGroupedName(service, group);
        proxy.getRegisteredInstancesByKey(combineKey);
        Assert.assertEquals("Nacos.client.naming.worker / getRegisteredInstancesByKey",
                testExporter.exportedSpans.get(testExporter.exportedSpans.size() - 1).getName());
        
        redoService.shutdown();
    }
    
    public static class TestExporter implements SpanExporter {
        
        public final List<SpanData> exportedSpans = Collections.synchronizedList(new ArrayList<>());
        
        @ParametersAreNonnullByDefault
        @Override
        public CompletableResultCode export(Collection<SpanData> collection) {
            exportedSpans.addAll(collection);
            return CompletableResultCode.ofSuccess();
        }
        
        @Override
        public CompletableResultCode flush() {
            return CompletableResultCode.ofSuccess();
        }
        
        @Override
        public CompletableResultCode shutdown() {
            return CompletableResultCode.ofSuccess();
        }
    }
}
