/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.istio.common;

import com.alibaba.nacos.istio.api.ApiGeneratorFactory;
import com.alibaba.nacos.istio.mcp.NacosMcpService;
import com.alibaba.nacos.istio.misc.IstioConfig;
import com.alibaba.nacos.istio.model.IstioEndpoint;
import com.alibaba.nacos.istio.model.IstioService;
import com.alibaba.nacos.istio.model.PushRequest;
import com.alibaba.nacos.istio.xds.ServiceEntryXdsGenerator;
import com.google.protobuf.Any;
import io.grpc.stub.StreamObserver;
import istio.mcp.v1alpha1.Mcp;
import istio.mcp.v1alpha1.ResourceOuterClass.Resource;
import istio.networking.v1alpha3.ServiceEntryOuterClass.ServiceEntry;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static com.alibaba.nacos.istio.api.ApiConstants.SERVICE_ENTRY_COLLECTION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NacosResourceManagerInitialSnapshotTest {
    
    @Test
    void initialAndReplacementSnapshotsUseInjectedConfig() {
        NacosServiceInfoResourceWatcher watcher = mock(NacosServiceInfoResourceWatcher.class);
        try (AnnotationConfigApplicationContext context = newContext(watcher)) {
            NacosResourceManager manager = context.getBean(NacosResourceManager.class);
            IstioConfig config = context.getBean(IstioConfig.class);
            assertSame(config, manager.getResourceSnapshot().getIstioConfig());
            assertSame(config, manager.createResourceSnapshot().getIstioConfig());
        }
    }
    
    @Test
    void firstStreamAndXdsUseConfiguredSuffix() throws Exception {
        IstioEndpoint endpoint = mock(IstioEndpoint.class);
        IstioService service = mock(IstioService.class);
        when(service.getHosts()).thenReturn(Collections.singletonList(endpoint));
        when(service.getNamespace()).thenReturn("public");
        when(service.getRevision()).thenReturn(1L);
        when(service.getCreateTimeStamp()).thenReturn(new Date(0));
        NacosServiceInfoResourceWatcher watcher = mock(NacosServiceInfoResourceWatcher.class);
        when(watcher.snapshot()).thenReturn(Collections.singletonMap("orders", service));
        
        try (AnnotationConfigApplicationContext context = newContext(watcher)) {
            NacosResourceManager manager = context.getBean(NacosResourceManager.class);
            CapturingObserver response = new CapturingObserver();
            StreamObserver<Mcp.RequestResources> requests =
                context.getBean(NacosMcpService.class).establishResourceStream(response);
            requests.onNext(Mcp.RequestResources.newBuilder()
                .setSinkNode(Mcp.SinkNode.newBuilder().setId("first-client"))
                .setCollection(SERVICE_ENTRY_COLLECTION).build());
            
            assertEquals(1, response.resources.getResourcesCount());
            ServiceEntry firstEntry = ServiceEntry.parseFrom(
                response.resources.getResources(0).getBody().getValue());
            assertEquals("orders.example.test", firstEntry.getHosts(0));
            
            List<Any> xds = ServiceEntryXdsGenerator.getInstance()
                .generate(new PushRequest(manager.getResourceSnapshot(), true));
            assertEquals(1, xds.size());
            Resource xdsResource = Resource.parseFrom(xds.get(0).getValue());
            assertEquals("orders.example.test",
                ServiceEntry.parseFrom(xdsResource.getBody().getValue()).getHosts(0));
            requests.onCompleted();
            assertTrue(response.completed);
        }
    }
    
    private AnnotationConfigApplicationContext newContext(NacosServiceInfoResourceWatcher watcher) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test",
            Map.of("nacos.istio.domain.suffix", ".example.test")));
        context.registerBean(IstioConfig.class);
        context.getBeanFactory().registerSingleton("serviceInfoResourceWatcher", watcher);
        context.registerBean(NacosResourceManager.class);
        context.registerBean(ApiGeneratorFactory.class);
        context.registerBean(NacosMcpService.class);
        context.refresh();
        return context;
    }
    
    private static class CapturingObserver implements StreamObserver<Mcp.Resources> {
        
        private Mcp.Resources resources;
        
        private boolean completed;
        
        @Override
        public void onNext(Mcp.Resources value) {
            resources = value;
        }
        
        @Override
        public void onError(Throwable throwable) {
            throw new AssertionError(throwable);
        }
        
        @Override
        public void onCompleted() {
            completed = true;
        }
    }
}
