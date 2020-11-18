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
import com.alibaba.nacos.istio.model.Port;
import com.alibaba.nacos.istio.model.mcp.Metadata;
import com.alibaba.nacos.istio.model.mcp.RequestResources;
import com.alibaba.nacos.istio.model.mcp.Resource;
import com.alibaba.nacos.istio.model.mcp.ResourceSourceGrpc;
import com.alibaba.nacos.istio.model.mcp.Resources;
import com.alibaba.nacos.istio.model.naming.ServiceEntry;
import com.alibaba.nacos.naming.core.Instance;
import com.alibaba.nacos.naming.core.Service;
import com.alibaba.nacos.naming.core.ServiceManager;
import com.alibaba.nacos.naming.misc.GlobalExecutor;
import com.google.protobuf.Any;
import io.grpc.stub.StreamObserver;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;

import javax.annotation.PostConstruct;
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
    
    private final Map<Integer, StreamObserver<Resources>> connnections = new ConcurrentHashMap<>(16);
    
    private final Map<String, Resource> resourceMap = new ConcurrentHashMap<>(16);
    
    private final Map<String, String> checksumMap = new ConcurrentHashMap<>(16);
    
    private static final String SERVICE_NAME_SPLITTER = "nacos";
    
    private static final String MESSAGE_TYPE_URL = "type.googleapis.com/istio.networking.v1alpha3.ServiceEntry";
    
    private static final long MCP_PUSH_PERIOD_MILLISECONDS = 10000L;
    
    @Autowired
    private ServiceManager serviceManager;
    
    @Autowired
    private IstioConfig istioConfig;
    
    /**
     * start mcpPushTask{@link McpPushTask}.
     */
    @PostConstruct
    public void init() {
        if (!istioConfig.isMcpServerEnabled()) {
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
            
            Resources resources = Resources.newBuilder().addAllResources(resourceMap.values())
                    .setCollection(CollectionTypes.SERVICE_ENTRY).setNonce(String.valueOf(System.currentTimeMillis()))
                    .build();
            
            if (connnections.isEmpty()) {
                return;
            }
            
            Loggers.MAIN.info("MCP push, resource count is: {}", resourceMap.size());
            
            if (Loggers.MAIN.isDebugEnabled()) {
                Loggers.MAIN.debug("MCP push, sending resources: {}", resources);
            }
            
            for (StreamObserver<Resources> observer : connnections.values()) {
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
    
    private Resource convertService(Service service) {
        
        String serviceName = convertName(service);
        
        ServiceEntry.Builder serviceEntryBuilder = ServiceEntry.newBuilder()
                .setResolution(ServiceEntry.Resolution.STATIC).setLocation(ServiceEntry.Location.MESH_INTERNAL)
                .addHosts(serviceName + "." + SERVICE_NAME_SPLITTER)
                .addPorts(Port.newBuilder().setNumber(8848).setName("http").setProtocol("HTTP").build());
        
        for (Instance instance : service.allIPs()) {
            
            if (!instance.isHealthy() || !instance.isEnabled()) {
                continue;
            }
            
            ServiceEntry.Endpoint endpoint = ServiceEntry.Endpoint.newBuilder().setAddress(instance.getIp())
                    .setWeight((int) instance.getWeight()).putAllLabels(instance.getMetadata())
                    .putPorts("http", instance.getPort()).build();
            
            serviceEntryBuilder.addEndpoints(endpoint);
        }
        
        ServiceEntry serviceEntry = serviceEntryBuilder.build();
        
        Any any = Any.newBuilder().setValue(serviceEntry.toByteString()).setTypeUrl(MESSAGE_TYPE_URL).build();
        
        Metadata metadata = Metadata.newBuilder().setName(SERVICE_NAME_SPLITTER + "/" + serviceName)
                .putAllAnnotations(service.getMetadata()).putAnnotations("virtual", "1").build();
        
        Resource resource = Resource.newBuilder().setBody(any).setMetadata(metadata).build();
        
        return resource;
    }
    
    @Override
    public StreamObserver<RequestResources> establishResourceStream(StreamObserver<Resources> responseObserver) {
        
        int id = connectIdGenerator.incrementAndGet();
        connnections.put(id, responseObserver);
        
        return new StreamObserver<RequestResources>() {
            
            private final int connectionId = id;
            
            @Override
            public void onNext(RequestResources value) {
                
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
                    Resources resources = Resources.newBuilder().setCollection(value.getCollection())
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
