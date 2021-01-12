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

package com.alibaba.nacos.istio.mcp;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.naming.utils.NamingUtils;
import com.alibaba.nacos.istio.misc.IstioConfig;
import com.alibaba.nacos.istio.misc.Loggers;
import com.alibaba.nacos.naming.core.Instance;
import com.alibaba.nacos.naming.core.Service;
import com.alibaba.nacos.naming.core.ServiceManager;
import com.alibaba.nacos.naming.misc.GlobalExecutor;
import com.google.protobuf.Any;
import com.google.protobuf.Timestamp;
import io.envoyproxy.envoy.service.discovery.v3.DiscoveryResponse;
import io.grpc.stub.StreamObserver;
import istio.mcp.v1alpha1.Mcp;
import istio.mcp.v1alpha1.MetadataOuterClass;
import istio.mcp.v1alpha1.ResourceOuterClass;
import istio.mcp.v1alpha1.ResourceSourceGrpc;
import istio.networking.v1alpha3.GatewayOuterClass;
import istio.networking.v1alpha3.ServiceEntryOuterClass;
import istio.networking.v1alpha3.WorkloadEntryOuterClass;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * nacos mcp service.
 *
 * @author nkorange
 * @since 1.1.4
 */
@org.springframework.stereotype.Service
public class NacosMcpService extends ResourceSourceGrpc.ResourceSourceImplBase {
    
    private final AtomicInteger connectIdGenerator = new AtomicInteger(0);
    
    private final Map<Integer, StreamObserver<Mcp.Resources>> connnections = new ConcurrentHashMap<>(16);
    
    private final Map<String, ResourceOuterClass.Resource> resourceMap = new ConcurrentHashMap<>(16);
    
    private final Map<String, String> checksumMap = new ConcurrentHashMap<>(16);
    
    private static final String SERVICE_NAME_SPLITTER = "nacos";
    
    private static final String SERVICEENTY_TYPE = "networking.istio.io/v1alpha3/ServiceEntry";
    
    private static final String MESSAGE_TYPE_URL = "type.googleapis.com/istio.networking.v1alpha3.ServiceEntry";
    
    private static final String MCP_RESOURCES_URL = "type.googleapis.com/istio.mcp.v1alpha1.Resource";
    
    private static final long MCP_PUSH_PERIOD_MILLISECONDS = 10000L;
    
    @Autowired
    private ServiceManager serviceManager;
    
    @Autowired
    private IstioConfig istioConfig;
    
    @Autowired
    private NacosMcpOverXdsService nacosMcpOverXdsService;
    
    /**
     * start mcpPushTask{@link McpPushTask}.
     */
    @PostConstruct
    public void init() {
        boolean enabled = istioConfig.isMcpServerEnabled();
        if (!enabled) {
            return;
        }
        GlobalExecutor
                .scheduleMcpPushTask(new McpPushTask(), MCP_PUSH_PERIOD_MILLISECONDS * 2, MCP_PUSH_PERIOD_MILLISECONDS);
    }
    
    private class McpPushTask implements Runnable {
        
        @Override
        public void run() {
            
            boolean changed = false;
            
            // Query all services to see if any of them have changes:
            Set<String> namespaces = serviceManager.getAllNamespaces();
            
            for (String namespace : namespaces) {
                
                Map<String, Service> services = serviceManager.chooseServiceMap(namespace);
                
                if (services.isEmpty()) {
                    continue;
                }
                
                for (Service service : services.values()) {
                    
                    String convertedName = convertName(service);
                    
                    // Service not changed:
                    if (checksumMap.containsKey(convertedName) && checksumMap.get(convertedName)
                            .equals(service.getChecksum())) {
                        continue;
                    }
                    
                    if (service.allIPs().isEmpty()) {
                        resourceMap.remove(convertedName);
                        continue;
                    }
                    
                    // Update the resource:
                    changed = true;
                    resourceMap.put(convertedName, convertService(service));
                    checksumMap.put(convertedName, service.getChecksum());
                }
            }
            
            if (!changed) {
                // If no service changed, just return:
                return;
            }
            
            Mcp.Resources resources = Mcp.Resources.newBuilder().addAllResources(resourceMap.values())
                    .setCollection(CollectionTypes.SERVICE_ENTRY).setNonce(String.valueOf(System.currentTimeMillis()))
                    .build();
            Loggers.MAIN.info("MCP push, resource count is: {}", resourceMap.size());
            
            if (Loggers.MAIN.isDebugEnabled()) {
                Loggers.MAIN.debug("MCP push, sending resources: {}", resources);
            }
            List<Any> anies = new ArrayList<>();
            for (ResourceOuterClass.Resource resource:resourceMap.values()) {
                Any any = Any.newBuilder().setValue(resource.toByteString()).setTypeUrl(MCP_RESOURCES_URL).build();
                anies.add(any);
            }
            DiscoveryResponse discoveryResponse = DiscoveryResponse.newBuilder().addAllResources(anies)
                    .setNonce(String.valueOf(System.currentTimeMillis())).setTypeUrl(SERVICEENTY_TYPE)
                    .build();
            
            nacosMcpOverXdsService.sendResponse(discoveryResponse);
            if (connnections.isEmpty()) {
                return;
            }
            
            for (StreamObserver<Mcp.Resources> observer : connnections.values()) {
                observer.onNext(resources);
            }
        }
    }
    
    private String convertName(Service service) {
        
        String serviceName = NamingUtils.getServiceName(service.getName()) + ".sn";
        
        if (!Constants.DEFAULT_GROUP.equals(NamingUtils.getGroupName(service.getName()))) {
            serviceName = serviceName + NamingUtils.getGroupName(service.getName()) + ".gn";
        }
        
        if (!Constants.DEFAULT_NAMESPACE_ID.equals(service.getNamespaceId())) {
            serviceName = serviceName + service.getNamespaceId() + ".ns";
        }
        return serviceName;
    }
    
    private ResourceOuterClass.Resource convertService(Service service) {
        
        String serviceName = convertName(service);
        ServiceEntryOuterClass.ServiceEntry.Builder serviceEntryBuilder = ServiceEntryOuterClass.ServiceEntry
                .newBuilder().setResolution(ServiceEntryOuterClass.ServiceEntry.Resolution.STATIC)
                .setLocation(ServiceEntryOuterClass.ServiceEntry.Location.MESH_INTERNAL)
                .addHosts(serviceName + "." + SERVICE_NAME_SPLITTER).addPorts(
                        GatewayOuterClass.Port.newBuilder().setNumber(8848).setName("http").setProtocol("HTTP")
                                .build());
        
        for (Instance instance : service.allIPs()) {
            
            if (!instance.isHealthy() || !instance.isEnabled()) {
                continue;
            }
            
            WorkloadEntryOuterClass.WorkloadEntry workloadEntry = WorkloadEntryOuterClass.WorkloadEntry.newBuilder()
                    .setAddress(instance.getIp()).setWeight((int) instance.getWeight())
                    .putAllLabels(instance.getMetadata()).putPorts("http", instance.getPort()).build();
            
            serviceEntryBuilder.addEndpoints(workloadEntry);
        }
        
        ServiceEntryOuterClass.ServiceEntry serviceEntry = serviceEntryBuilder.build();
        
        Any any = Any.newBuilder().setValue(serviceEntry.toByteString()).setTypeUrl(MESSAGE_TYPE_URL).build();
        MetadataOuterClass.Metadata metadata = MetadataOuterClass.Metadata.newBuilder().setName(SERVICE_NAME_SPLITTER + "/" + serviceName)
                .putAllAnnotations(service.getMetadata()).putAnnotations("virtual", "1").setCreateTime(
                        Timestamp.newBuilder().setSeconds(System.currentTimeMillis()/1000).build()).setVersion(service.getChecksum()).build();
        
        ResourceOuterClass.Resource resource = ResourceOuterClass.Resource.newBuilder().setBody(any).setMetadata(metadata).build();
        
        return resource;
    }
    
    @Override
    public StreamObserver<Mcp.RequestResources> establishResourceStream(StreamObserver<Mcp.Resources> responseObserver) {
        
        int id = connectIdGenerator.incrementAndGet();
        connnections.put(id, responseObserver);
        
        return new StreamObserver<Mcp.RequestResources>() {
            
            private final int connectionId = id;
            
            @Override
            public void onNext(Mcp.RequestResources value) {
                
                Loggers.MAIN.info("receiving request, sink: {}, type: {}", value.getSinkNode(), value.getCollection());
                
                if (value.getErrorDetail() != null && value.getErrorDetail().getCode() != 0) {
                    
                    Loggers.MAIN.error("NACK error code: {}, message: {}", value.getErrorDetail().getCode(),
                            value.getErrorDetail().getMessage());
                    return;
                }
                
                if (StringUtils.isNotBlank(value.getResponseNonce())) {
                    // This is a response:
                    Loggers.MAIN.info("ACK nonce: {}, type: {}", value.getResponseNonce(), value.getCollection());
                    return;
                }
                
                if (!CollectionTypes.SERVICE_ENTRY.equals(value.getCollection())) {
                    // Return empty resources for other types:
                    Mcp.Resources resources = Mcp.Resources.newBuilder().setCollection(value.getCollection())
                            .setNonce(String.valueOf(System.currentTimeMillis())).build();
                    
                    responseObserver.onNext(resources);
                }
            }
            
            @Override
            public void onError(Throwable t) {
                Loggers.MAIN.error("stream error.", t);
                connnections.remove(connectionId);
            }
            
            @Override
            public void onCompleted() {
                responseObserver.onCompleted();
            }
        };
    }
}
