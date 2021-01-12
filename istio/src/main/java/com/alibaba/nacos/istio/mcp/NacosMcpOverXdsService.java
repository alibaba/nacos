package com.alibaba.nacos.istio.mcp;

import com.alibaba.nacos.istio.misc.Loggers;
import com.google.protobuf.Any;
import io.envoyproxy.envoy.service.discovery.v3.AggregatedDiscoveryServiceGrpc;
import io.envoyproxy.envoy.service.discovery.v3.DiscoveryRequest;
import io.envoyproxy.envoy.service.discovery.v3.DiscoveryResponse;
import io.grpc.stub.StreamObserver;
import istio.mcp.v1alpha1.Mcp;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@org.springframework.stereotype.Service
public class NacosMcpOverXdsService extends AggregatedDiscoveryServiceGrpc.AggregatedDiscoveryServiceImplBase {
    
    private final AtomicInteger connectIdGenerator = new AtomicInteger(0);
    
    private final Map<Integer, StreamObserver<DiscoveryResponse>> connnections = new ConcurrentHashMap<>(16);
    
    private static final String MCP_RESOURCES_URL = "type.googleapis.com/istio.mcp.v1alpha1.Resource";
    
    /**
     * whkh.
     *
     * @param discoveryResponse discoveryResponse
     */
    public void sendResponse(DiscoveryResponse discoveryResponse) {
        for (StreamObserver<DiscoveryResponse> observer : connnections.values()) {
            observer.onNext(discoveryResponse);
        }
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
                
                if (StringUtils.isNotBlank(discoveryRequest.getResponseNonce())) {
                    // This is a response:
                    Loggers.MAIN.info("ACK nonce: {}", discoveryRequest.getResponseNonce());
                    return;
                }
                DiscoveryResponse discoveryResponse = DiscoveryResponse.newBuilder()
                        .setNonce(String.valueOf(System.currentTimeMillis())).build();
                responseObserver.onNext(discoveryResponse);
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
