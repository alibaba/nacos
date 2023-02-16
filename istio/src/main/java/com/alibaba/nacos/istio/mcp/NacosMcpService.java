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

import com.alibaba.nacos.istio.api.ApiGenerator;
import com.alibaba.nacos.istio.api.ApiGeneratorFactory;
import com.alibaba.nacos.istio.common.AbstractConnection;
import com.alibaba.nacos.istio.common.Event;
import com.alibaba.nacos.istio.common.NacosResourceManager;
import com.alibaba.nacos.istio.common.ResourceSnapshot;
import com.alibaba.nacos.istio.common.WatchedStatus;
import com.alibaba.nacos.istio.misc.Loggers;
import com.alibaba.nacos.istio.util.NonceGenerator;
import io.grpc.stub.StreamObserver;
import istio.mcp.v1alpha1.Mcp;
import istio.mcp.v1alpha1.ResourceOuterClass.Resource;
import istio.mcp.v1alpha1.ResourceSourceGrpc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.alibaba.nacos.istio.api.ApiConstants.SERVICE_ENTRY_COLLECTION;

/**
 * nacos mcp service.
 *
 * @author nkorange
 * @since 1.1.4
 */
@Service
public class NacosMcpService extends ResourceSourceGrpc.ResourceSourceImplBase {
    
    private final Map<String, AbstractConnection<Mcp.Resources>> connections = new ConcurrentHashMap<>(16);

    @Autowired
    ApiGeneratorFactory apiGeneratorFactory;

    @Autowired
    NacosResourceManager resourceManager;

    public boolean hasClientConnection() {
        return connections.size() != 0;
    }

    @Override
    public StreamObserver<Mcp.RequestResources> establishResourceStream(StreamObserver<Mcp.Resources> responseObserver) {

        // TODO add authN

        // Init snapshot of nacos service info.
        resourceManager.initResourceSnapshot();
        AbstractConnection<Mcp.Resources> newConnection = new McpConnection(responseObserver);

        return new StreamObserver<Mcp.RequestResources>() {
            private boolean initRequest = true;

            @Override
            public void onNext(Mcp.RequestResources requestResources) {
                // init connection
                if (initRequest) {
                    newConnection.setConnectionId(requestResources.getSinkNode().getId());
                    connections.put(newConnection.getConnectionId(), newConnection);
                    initRequest = false;
                }

                process(requestResources, newConnection);
            }
            
            @Override
            public void onError(Throwable throwable) {
                Loggers.MAIN.error("mcp: {} stream error.", newConnection.getConnectionId(), throwable);
                clear();
            }
            
            @Override
            public void onCompleted() {
                responseObserver.onCompleted();
                clear();
            }

            private void clear() {
                connections.remove(newConnection.getConnectionId());
            }
        };
    }

    private void process(Mcp.RequestResources requestResources, AbstractConnection<Mcp.Resources> connection) {
        if (!shouldPush(requestResources, connection)) {
            return;
        }

        Mcp.Resources response = buildMcpResourcesResponse(requestResources.getCollection(), resourceManager.getResourceSnapshot());
        connection.push(response, connection.getWatchedStatusByType(requestResources.getCollection()));
    }

    private boolean shouldPush(Mcp.RequestResources requestResources, AbstractConnection<Mcp.Resources> connection) {
        String type = requestResources.getCollection();
        String connectionId = connection.getConnectionId();

        if (requestResources.getErrorDetail().getCode() != 0) {
            Loggers.MAIN.error("mcp: ACK error, connection-id: {}, code: {}, message: {}",
                    connectionId,
                    requestResources.getErrorDetail().getCode(),
                    requestResources.getErrorDetail().getMessage());
            return false;
        }

        WatchedStatus watchedStatus;
        if (requestResources.getResponseNonce().isEmpty()) {
            Loggers.MAIN.info("mcp: init request, type {}, connection-id {}, is incremental {}",
                    type, connectionId, requestResources.getIncremental());

            watchedStatus = new WatchedStatus();
            watchedStatus.setType(type);
            connection.addWatchedResource(type, watchedStatus);

            return true;
        }

        watchedStatus = connection.getWatchedStatusByType(type);
        if (watchedStatus == null) {
            Loggers.MAIN.info("mcp: reconnect, type {}, connection-id {}, is incremental {}",
                    type, connectionId, requestResources.getIncremental());
            watchedStatus = new WatchedStatus();
            watchedStatus.setType(type);
            connection.addWatchedResource(type, watchedStatus);
            return true;
        }

        if (!watchedStatus.getLatestNonce().equals(requestResources.getResponseNonce())) {
            Loggers.MAIN.warn("mcp: request dis match, type {}, connection-id {}", type, connectionId);
            return false;
        }

        // This request is ack, we should record nonce.
        watchedStatus.setAckedNonce(requestResources.getResponseNonce());
        Loggers.MAIN.info("mcp: ack, type {}, connection-id {}, nonce {}", type, connectionId,
                requestResources.getResponseNonce());
        return false;
    }

    public void handleEvent(ResourceSnapshot resourceSnapshot, Event event) {
        switch (event.getType()) {
            case Service:
                if (connections.size() == 0) {
                    return;
                }

                Loggers.MAIN.info("xds: event {} trigger push.", event.getType());

                Mcp.Resources serviceEntryMcpResponse = buildMcpResourcesResponse(SERVICE_ENTRY_COLLECTION, resourceSnapshot);

                for (AbstractConnection<Mcp.Resources> connection : connections.values()) {
                    WatchedStatus watchedStatus = connection.getWatchedStatusByType(SERVICE_ENTRY_COLLECTION);
                    if (watchedStatus != null) {
                        connection.push(serviceEntryMcpResponse, watchedStatus);
                    }
                }
                break;
            default:
                Loggers.MAIN.warn("Invalid event {}, ignore it.", event.getType());
        }
    }

    private Mcp.Resources buildMcpResourcesResponse(String type, ResourceSnapshot resourceSnapshot) {
        @SuppressWarnings("unchecked")
        ApiGenerator<Resource> serviceEntryGenerator = (ApiGenerator<Resource>) apiGeneratorFactory.getApiGenerator(type);
        List<Resource> rawResources = serviceEntryGenerator.generate(resourceSnapshot);

        String nonce = NonceGenerator.generateNonce();
        return Mcp.Resources.newBuilder()
                .setCollection(type)
                .addAllResources(rawResources)
                .setSystemVersionInfo(resourceSnapshot.getVersion())
                .setNonce(nonce).build();
    }
}
