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

package com.alibaba.nacos.istio.xds;

import com.alibaba.nacos.istio.api.ApiGenerator;
import com.alibaba.nacos.istio.api.ApiGeneratorFactory;
import com.alibaba.nacos.istio.common.*;
import com.alibaba.nacos.istio.misc.Loggers;
import com.alibaba.nacos.istio.util.NonceGenerator;
import com.google.protobuf.Any;
import io.envoyproxy.envoy.service.discovery.v3.DiscoveryRequest;
import io.envoyproxy.envoy.service.discovery.v3.DiscoveryResponse;
import io.envoyproxy.envoy.service.endpoint.v3.EndpointDiscoveryServiceGrpc;
import io.grpc.stub.StreamObserver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.alibaba.nacos.istio.api.ApiConstants.*;

/**
 * @author puzhuo
 */
@Service
public class NacosEdsService extends EndpointDiscoveryServiceGrpc.EndpointDiscoveryServiceImplBase {

    private final Map<String, AbstractConnection<DiscoveryResponse>> connections = new ConcurrentHashMap<>(16);

    public boolean hasClientConnection() {
        return connections.size() != 0;
    }

    @Autowired
    ApiGeneratorFactory apiGeneratorFactory;

    @Autowired
    NacosResourceManager resourceManager;

    @Override
    public StreamObserver<DiscoveryRequest> streamEndpoints(
            StreamObserver<DiscoveryResponse> responseObserver) {

        // Init snapshot of nacos service info.
        resourceManager.initResourceSnapshot();
        AbstractConnection<DiscoveryResponse> newConnection = new XdsConnection(responseObserver);

        return new StreamObserver<DiscoveryRequest>() {
            private boolean initRequest = true;

            @Override
            public void onNext(DiscoveryRequest discoveryRequest) {
                // init connection
                if (initRequest) {
                    newConnection.setConnectionId(discoveryRequest.getNode().getId());
                    connections.put(newConnection.getConnectionId(), newConnection);
                    initRequest = false;
                }

                process(discoveryRequest, newConnection);
            }

            @Override
            public void onError(Throwable throwable) {
                Loggers.MAIN.error("eds: {} stream error.", newConnection.getConnectionId(), throwable);
                clear();
            }

            @Override
            public void onCompleted() {
                Loggers.MAIN.info("eds: {} stream close.", newConnection.getConnectionId());
                responseObserver.onCompleted();
                clear();
            }

            private void clear() {
                connections.remove(newConnection.getConnectionId());
            }
        };

//        return createRequestHandler(responseObserver, false, Resources.V3.CLUSTER_TYPE_URL);
    }


    public void process(DiscoveryRequest discoveryRequest, AbstractConnection<DiscoveryResponse> connection) {
        if (!shouldPush(discoveryRequest, connection)) {
            return;
        }

        DiscoveryResponse response = buildDiscoveryResponse(discoveryRequest.getTypeUrl(), resourceManager.getResourceSnapshot());
        connection.push(response, connection.getWatchedStatusByType(ENDPOINT_TYPE));
    }

    private boolean shouldPush(DiscoveryRequest discoveryRequest, AbstractConnection<DiscoveryResponse> connection) {
        String type = discoveryRequest.getTypeUrl();
        String connectionId = connection.getConnectionId();

        // Suitable for bug of istio
        // See https://github.com/istio/istio/pull/34633
        if (type.equals(MESH_CONFIG_PROTO_PACKAGE)) {
            Loggers.MAIN.info("eds: type {} should be ignored.", type);
            return false;
        }

        if (discoveryRequest.getErrorDetail().getCode() != 0) {
            Loggers.MAIN.error("eds: ACK error, connection-id: {}, code: {}, message: {}",
                    connectionId,
                    discoveryRequest.getErrorDetail().getCode(),
                    discoveryRequest.getErrorDetail().getMessage());
            return false;
        }

        WatchedStatus watchedStatus;
        if (discoveryRequest.getResponseNonce().isEmpty()) {
            Loggers.MAIN.info("eds: init request, type {}, connection-id {}, version {}",
                    type, connectionId, discoveryRequest.getVersionInfo());
            watchedStatus = new WatchedStatus();
            watchedStatus.setType(discoveryRequest.getTypeUrl());
            connection.addWatchedResource(discoveryRequest.getTypeUrl(), watchedStatus);

            return true;
        }

        watchedStatus = connection.getWatchedStatusByType(discoveryRequest.getTypeUrl());
        if (watchedStatus == null) {
            Loggers.MAIN.info("eds: reconnect, type {}, connection-id {}, version {}, nonce {}.",
                    type, connectionId, discoveryRequest.getVersionInfo(), discoveryRequest.getResponseNonce());
            watchedStatus = new WatchedStatus();
            watchedStatus.setType(discoveryRequest.getTypeUrl());
            connection.addWatchedResource(discoveryRequest.getTypeUrl(), watchedStatus);

            return true;
        }

        if (!watchedStatus.getLatestNonce().equals(discoveryRequest.getResponseNonce())) {
            Loggers.MAIN.warn("eds: request dis match, type {}, connection-id {}",
                    discoveryRequest.getTypeUrl(),
                    connection.getConnectionId());
            return false;
        }

        // This request is ack, we should record version and nonce.
        watchedStatus.setAckedVersion(discoveryRequest.getVersionInfo());
        watchedStatus.setAckedNonce(discoveryRequest.getResponseNonce());
        Loggers.MAIN.info("eds: ack, type {}, connection-id {}, version {}, nonce {}", type, connectionId,
                discoveryRequest.getVersionInfo(), discoveryRequest.getResponseNonce());
        return false;
    }

    public void handleEvent(ResourceSnapshot resourceSnapshot, Event event) {
        switch (event.getType()) {
            case Service:
                if (connections.size() == 0) {
                    return;
                }

                Loggers.MAIN.info("eds: event {} trigger push.", event.getType());
    
                //TODO CDS discriminate and increment
                DiscoveryResponse edsResponse = buildDiscoveryResponse(ENDPOINT_TYPE, resourceSnapshot);
    
                for (AbstractConnection<DiscoveryResponse> connection : connections.values()) {
                    WatchedStatus watchedStatus = connection.getWatchedStatusByType(ENDPOINT_TYPE);
                    if (watchedStatus != null) {
                        connection.push(edsResponse, watchedStatus);
                    }
                }
                break;

            default:
                Loggers.MAIN.warn("Invalid event {}, ignore it.", event.getType());
        }
    }

    private DiscoveryResponse buildDiscoveryResponse(String type, ResourceSnapshot resourceSnapshot) {
        @SuppressWarnings("unchecked")
        ApiGenerator<Any> serviceEntryGenerator = (ApiGenerator<Any>) apiGeneratorFactory.getApiGenerator(ENDPOINT_TYPE);
        List<Any> rawResources = serviceEntryGenerator.generate(resourceSnapshot);

        String nonce = NonceGenerator.generateNonce();
        return DiscoveryResponse.newBuilder()
                .setTypeUrl(ENDPOINT_TYPE)
                .addAllResources(rawResources)
                .setVersionInfo(resourceSnapshot.getVersion())
                .setNonce(nonce).build();
    }


}
