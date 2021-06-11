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

import com.alibaba.nacos.istio.misc.Loggers;
import com.google.protobuf.Any;
import io.envoyproxy.envoy.service.discovery.v3.AggregatedDiscoveryServiceGrpc;
import io.envoyproxy.envoy.service.discovery.v3.DiscoveryRequest;
import io.envoyproxy.envoy.service.discovery.v3.DiscoveryResponse;
import io.grpc.stub.StreamObserver;
import istio.mcp.v1alpha1.ResourceOuterClass;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Nacos MCP server.
 *
 * <p>This MCP serves as a ResourceSource defined by Istio.
 *
 * @author huaicheng.lzp
 * @since 1.2.1
 */
@org.springframework.stereotype.Service
public class NacosMcpOverXdsService extends AggregatedDiscoveryServiceGrpc.AggregatedDiscoveryServiceImplBase {
    
    private final AtomicInteger connectIdGenerator = new AtomicInteger(0);
    
    private final Map<Integer, StreamObserver<DiscoveryResponse>> connnections = new ConcurrentHashMap<>(16);
    
    private final ConcurrentHashMap<Integer, Boolean> connectionInited = new ConcurrentHashMap<>();
    
    private static final String MCP_RESOURCES_URL = "type.googleapis.com/istio.mcp.v1alpha1.Resource";
    
    private static final String SERVICEENTY_TYPE = "networking.istio.io/v1alpha3/ServiceEntry";
    
    private Map<String, ResourceOuterClass.Resource> resourceMapCache;
    
    /**
     * Send resources to connections.
     *
     * @param resourceMap all mcp resource
     */
    public void sendResources(Map<String, ResourceOuterClass.Resource> resourceMap) {
        resourceMapCache = resourceMap;
        Loggers.MAIN.info("send resources for mcpOverXds,count : {}", resourceMap.size());
        DiscoveryResponse discoveryResponse = generateResponse(resourceMap);
        if (Loggers.MAIN.isDebugEnabled()) {
            Loggers.MAIN.debug("discoveryResponse:{}", discoveryResponse.toString());
        }
        for (StreamObserver<DiscoveryResponse> observer : connnections.values()) {
            Loggers.MAIN.info("mcpOverXds send to:{}", observer.toString());
            observer.onNext(discoveryResponse);
        }
    }
    
    /**
     * generate response by resource.
     *
     * @param resourceMap all mcp resource
     * @return discovery response.
     */
    private DiscoveryResponse generateResponse(Map<String, ResourceOuterClass.Resource> resourceMap) {
        List<Any> anies = new ArrayList<>();
        for (ResourceOuterClass.Resource resource : resourceMap.values()) {
            Any any = Any.newBuilder().setValue(resource.toByteString()).setTypeUrl(MCP_RESOURCES_URL).build();
            anies.add(any);
        }
        return DiscoveryResponse.newBuilder().addAllResources(anies)
                .setNonce(String.valueOf(System.currentTimeMillis())).setTypeUrl(SERVICEENTY_TYPE).build();
    }
    
    @Override
    public StreamObserver<DiscoveryRequest> streamAggregatedResources(
            StreamObserver<DiscoveryResponse> responseObserver) {
        
        int id = connectIdGenerator.incrementAndGet();
        connnections.put(id, responseObserver);
        
        return new StreamObserver<DiscoveryRequest>() {
            private final int connectionId = id;
            
            @Override
            public void onNext(DiscoveryRequest discoveryRequest) {
                Loggers.MAIN.info("receiving request,  {}", discoveryRequest.toString());
                
                if (discoveryRequest.getErrorDetail() != null && discoveryRequest.getErrorDetail().getCode() != 0) {
                    
                    Loggers.MAIN.error("NACK error code: {}, message: {}", discoveryRequest.getErrorDetail().getCode(),
                            discoveryRequest.getErrorDetail().getMessage());
                    return;
                }
                if (SERVICEENTY_TYPE.equals(discoveryRequest.getTypeUrl())) {
                    Boolean inited = connectionInited.get(id);
                    if (inited == null || !inited) {
                        connectionInited.put(id, true);
                        if (resourceMapCache != null) {
                            DiscoveryResponse discoveryResponse = generateResponse(resourceMapCache);
                            Loggers.MAIN.info("ACK for serviceEntry discoveryRequest {}", discoveryRequest.toString());
                            responseObserver.onNext(discoveryResponse);
                        }
                    }
                }
            }
            
            @Override
            public void onError(Throwable throwable) {
                Loggers.MAIN.error("stream error.", throwable);
                connnections.remove(connectionId);
            }
            
            @Override
            public void onCompleted() {
                responseObserver.onCompleted();
            }
        };
        
    }
}
