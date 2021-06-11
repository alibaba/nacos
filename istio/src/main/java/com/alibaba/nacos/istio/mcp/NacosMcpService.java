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
import io.grpc.stub.StreamObserver;
import istio.mcp.v1alpha1.Mcp;
import istio.mcp.v1alpha1.ResourceOuterClass;
import istio.mcp.v1alpha1.ResourceSourceGrpc;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;
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
    
    private Map<String, ResourceOuterClass.Resource> resourceMapCache;
    
    /**
     * Send resources to connections.
     *
     * @param resourceMap all mcp resource
     */
    public void sendResources(Map<String, ResourceOuterClass.Resource> resourceMap) {
        resourceMapCache = resourceMap;
        Loggers.MAIN.info("send resources for mcp,count : {}", resourceMap.size());
        Mcp.Resources resources = generateResponse(resourceMap);
        if (Loggers.MAIN.isDebugEnabled()) {
            Loggers.MAIN.debug("mcp resources:{}", resources.toString());
        }
        for (StreamObserver<Mcp.Resources> observer : connnections.values()) {
            Loggers.MAIN.info("mcp send to:{}", observer.toString());
            observer.onNext(resources);
        }
        
    }
    
    /**
     * generate response by resource.
     *
     * @param resourceMap all mcp resource
     * @return mcp resources.
     */
    private Mcp.Resources generateResponse(Map<String, ResourceOuterClass.Resource> resourceMap) {
        return Mcp.Resources.newBuilder().addAllResources(resourceMap.values())
                .setCollection(CollectionTypes.SERVICE_ENTRY).setNonce(String.valueOf(System.currentTimeMillis()))
                .build();
    }
    
    @Override
    public StreamObserver<Mcp.RequestResources> establishResourceStream(
            StreamObserver<Mcp.Resources> responseObserver) {
        
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
                    return;
                }
                Mcp.Resources resources = generateResponse(resourceMapCache);
                responseObserver.onNext(resources);
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
