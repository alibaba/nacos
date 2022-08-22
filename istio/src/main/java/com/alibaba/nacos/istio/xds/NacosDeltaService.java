/*
 * Copyright 1999-2022 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.istio.xds;

import com.alibaba.nacos.istio.api.ApiGenerator;
import com.alibaba.nacos.istio.api.ApiGeneratorFactory;
import com.alibaba.nacos.istio.common.AbstractConnection;
import com.alibaba.nacos.istio.common.Event;
import com.alibaba.nacos.istio.common.NacosResourceManager;
import com.alibaba.nacos.istio.common.ResourceSnapshot;
import com.alibaba.nacos.istio.common.WatchedStatus;
import com.alibaba.nacos.istio.misc.Loggers;
import com.alibaba.nacos.istio.model.PushContext;
import com.alibaba.nacos.istio.util.NonceGenerator;
import io.envoyproxy.envoy.service.discovery.v3.AggregatedDiscoveryServiceGrpc;
import io.envoyproxy.envoy.service.discovery.v3.DeltaDiscoveryRequest;
import io.envoyproxy.envoy.service.discovery.v3.DeltaDiscoveryResponse;
import io.envoyproxy.envoy.service.discovery.v3.Resource;
import io.grpc.stub.StreamObserver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static com.alibaba.nacos.istio.api.ApiConstants.MESH_CONFIG_PROTO_PACKAGE;

/**
 * @author RocketEngine26
 * @date 2022/8/20 下午10:45
 */
@Service
public class NacosDeltaService extends AggregatedDiscoveryServiceGrpc.AggregatedDiscoveryServiceImplBase {
    
    private final Map<String, AbstractConnection<DeltaDiscoveryResponse>> connections = new ConcurrentHashMap<>(16);
    
    public boolean hasClientConnection() {
        return connections.size() != 0;
    }
    
    @Autowired
    ApiGeneratorFactory apiGeneratorFactory;
    
    @Autowired
    NacosResourceManager resourceManager;
    
    @Override
    public StreamObserver<DeltaDiscoveryRequest> deltaAggregatedResources(StreamObserver<DeltaDiscoveryResponse> responseObserver) {
        // Init snapshot of nacos service info.
        resourceManager.initResourceSnapshot();
        AbstractConnection<DeltaDiscoveryResponse> newConnection = new DeltaConnection(responseObserver);
        
        return new StreamObserver<DeltaDiscoveryRequest>() {
            private boolean initRequest = true;
            
            @Override
            public void onNext(DeltaDiscoveryRequest deltaDiscoveryRequest) {
                // init connection
                if (initRequest) {
                    newConnection.setConnectionId(deltaDiscoveryRequest.getNode().getId());
                    connections.put(newConnection.getConnectionId(), newConnection);
                    initRequest = false;
                }
                
                process(deltaDiscoveryRequest, newConnection);
            }
            
            @Override
            public void onError(Throwable throwable) {
                Loggers.MAIN.error("delta: {} stream error.", newConnection.getConnectionId(), throwable);
                clear();
            }
            
            @Override
            public void onCompleted() {
                Loggers.MAIN.info("delta: {} stream close.", newConnection.getConnectionId());
                responseObserver.onCompleted();
                clear();
            }
            
            private void clear() {
                connections.remove(newConnection.getConnectionId());
            }
        };
    }
    
    public void process(DeltaDiscoveryRequest deltaDiscoveryRequest, AbstractConnection<DeltaDiscoveryResponse> connection) {
        if (!shouldPush(deltaDiscoveryRequest, connection)) {
            return;
        }
        
        ResourceSnapshot resourceSnapshot = resourceManager.getResourceSnapshot();
        PushContext pushContext = new PushContext(resourceSnapshot, true,
                deltaDiscoveryRequest.getResourceNamesSubscribeList(),
                deltaDiscoveryRequest.getResourceNamesUnsubscribeList());
        
        connection.getWatchedStatusByType(deltaDiscoveryRequest.getTypeUrl())
                .setLastSubscribe(deltaDiscoveryRequest.getResourceNamesSubscribeList());
        connection.getWatchedStatusByType(deltaDiscoveryRequest.getTypeUrl())
                .setLastUnSubscribe(deltaDiscoveryRequest.getResourceNamesUnsubscribeList());
        
        DeltaDiscoveryResponse response = buildDeltaDiscoveryResponse(deltaDiscoveryRequest.getTypeUrl(), pushContext);
        connection.push(response, connection.getWatchedStatusByType(deltaDiscoveryRequest.getTypeUrl()));
    }
    
    private boolean shouldPush(DeltaDiscoveryRequest deltaDiscoveryRequest, AbstractConnection<DeltaDiscoveryResponse> connection) {
        String type = deltaDiscoveryRequest.getTypeUrl();
        String connectionId = connection.getConnectionId();
        
        // Suitable for bug of istio
        // See https://github.com/istio/istio/pull/34633
        if (type.equals(MESH_CONFIG_PROTO_PACKAGE)) {
            Loggers.MAIN.info("delta: type {} should be ignored.", type);
            return false;
        }
        
        WatchedStatus watchedStatus;
        if (deltaDiscoveryRequest.getResponseNonce().isEmpty()) {
            Loggers.MAIN.info("delta: init request, type {}, connection-id {}",
                    type, connectionId);
            watchedStatus = new WatchedStatus();
            watchedStatus.setType(deltaDiscoveryRequest.getTypeUrl());
            connection.addWatchedResource(deltaDiscoveryRequest.getTypeUrl(), watchedStatus);
            
            return true;
        }
        
        watchedStatus = connection.getWatchedStatusByType(deltaDiscoveryRequest.getTypeUrl());
        if (watchedStatus == null) {
            Loggers.MAIN.info("delta: reconnect, type {}, connection-id {}, nonce {}.",
                    type, connectionId, deltaDiscoveryRequest.getResponseNonce());
            watchedStatus = new WatchedStatus();
            watchedStatus.setType(deltaDiscoveryRequest.getTypeUrl());
            connection.addWatchedResource(deltaDiscoveryRequest.getTypeUrl(), watchedStatus);
            
            return true;
        }
    
        if (deltaDiscoveryRequest.getErrorDetail().getCode() != 0) {
            Loggers.MAIN.error("delta: ACK error, connection-id: {}, code: {}, message: {}",
                    connectionId,
                    deltaDiscoveryRequest.getErrorDetail().getCode(),
                    deltaDiscoveryRequest.getErrorDetail().getMessage());
            watchedStatus.setLastAckOrNack(true);
            return false;
        }
        
        if (!watchedStatus.getLatestNonce().equals(deltaDiscoveryRequest.getResponseNonce())) {
            Loggers.MAIN.warn("delta: request dis match, type {}, connection-id {}",
                    deltaDiscoveryRequest.getTypeUrl(),
                    connection.getConnectionId());
            return false;
        }
        
        // This request is ack, we should record version and nonce.
        //TODO: setAckedVersion
        watchedStatus.setAckedNonce(deltaDiscoveryRequest.getResponseNonce());
        Loggers.MAIN.info("delta: ack, type {}, connection-id {}, nonce {}", type, connectionId, deltaDiscoveryRequest.getResponseNonce());
        return false;
    }
    
    public void handleEvent(ResourceSnapshot resourceSnapshot, Event event) {
        if (connections.size() == 0) {
            return;
        }
        boolean full = resourceSnapshot.getIstioConfig().isFullEnabled();
        
        switch (event.getType()) {
            case Service:
                Loggers.MAIN.info("delta: event {} trigger push.", event.getType());
                
                for (AbstractConnection<DeltaDiscoveryResponse> connection : connections.values()) {
                    //mcp
                    WatchedStatus watchedStatus = connection.getWatchedStatusByType("Delta_SERVICE_ENTRY_PROTO_PACKAGE");
                    if (watchedStatus != null && watchedStatus.isLastAckOrNack()) {
                        //TODO:incremental true or false
                        PushContext pushContext = new PushContext(resourceSnapshot, full,
                                watchedStatus.getLastSubscribe(), watchedStatus.getLastUnSubscribe());
                        DeltaDiscoveryResponse serviceEntryResponse = buildDeltaDiscoveryResponse("Delta_SERVICE_ENTRY_PROTO_PACKAGE", pushContext);
                        connection.push(serviceEntryResponse, watchedStatus);
                    }
                }
                break;
            case Endpoint:
                Loggers.MAIN.info("delta: event {} trigger push.", event.getType());
                
                for (AbstractConnection<DeltaDiscoveryResponse> connection : connections.values()) {
                    //EDS
                    WatchedStatus edsWatchedStatus = connection.getWatchedStatusByType("Delta_ENDPOINT_TYPE");
                    if (edsWatchedStatus != null && edsWatchedStatus.isLastAckOrNack()) {
                        //TODO:incremental true or false
                        PushContext pushContext = new PushContext(resourceSnapshot, full,
                                edsWatchedStatus.getLastSubscribe(), edsWatchedStatus.getLastUnSubscribe());
                        DeltaDiscoveryResponse edsResponse = buildDeltaDiscoveryResponse("Delta_ENDPOINT_TYPE", pushContext);
                        connection.push(edsResponse, edsWatchedStatus);
                    }
                }
                break;
            default:
                Loggers.MAIN.warn("Invalid event {}, ignore it.", event.getType());
        }
    }
    
    private DeltaDiscoveryResponse buildDeltaDiscoveryResponse(String type, PushContext pushContext) {
        @SuppressWarnings("unchecked")
        ApiGenerator<Resource> generator = (ApiGenerator<Resource>) apiGeneratorFactory.getApiGenerator(type);
        Set<String> removed = new HashSet<>();
        List<Resource> rawResources = generator.deltaGenerate(pushContext, removed);
        
        String nonce = NonceGenerator.generateNonce();
        return DeltaDiscoveryResponse.newBuilder()
                .setTypeUrl(type)
                .addAllResources(rawResources)
                .addAllRemovedResources(removed)
                .setSystemVersionInfo(pushContext.getVersion())
                .setNonce(nonce).build();
    }
}
