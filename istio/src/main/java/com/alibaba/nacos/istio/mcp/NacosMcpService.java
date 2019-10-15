package com.alibaba.nacos.istio.mcp;


import com.alibaba.nacos.istio.misc.Loggers;
import com.alibaba.nacos.istio.model.Port;
import com.alibaba.nacos.istio.model.mcp.*;
import com.alibaba.nacos.istio.model.naming.ServiceEntry;
import com.google.protobuf.Any;
import io.grpc.stub.StreamObserver;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author nkorange
 * @since 1.1.4
 */
@Service
public class NacosMcpService extends ResourceSourceGrpc.ResourceSourceImplBase {

    private AtomicInteger connectIdGenerator = new AtomicInteger(0);

    private Map<Integer, StreamObserver<Resources>> connnections = new ConcurrentHashMap<>();

    public NacosMcpService() {

        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {

                    while (true) {
                        TimeUnit.SECONDS.sleep(10L);

                        if (connnections.isEmpty()) {
                            continue;
                        }
                        for (StreamObserver<Resources> observer : connnections.values()) {
                            observer.onNext(generateResources());
                        }
                    }

                } catch (Exception e) {

                }
            }
        });

        thread.start();
    }

    public Resources generateResources() {

        try {

            String serviceName = "java.mock.1";
            int endpointCount = 10;

            ServiceEntry.Builder serviceEntryBuilder = ServiceEntry.newBuilder()
                .setResolution(ServiceEntry.Resolution.STATIC)
                .setLocation(ServiceEntry.Location.MESH_INTERNAL)
                .addHosts(serviceName + ".nacos")
                .addPorts(Port.newBuilder().setNumber(8080).setName("http").setProtocol("HTTP").build());

            for (int i = 0; i < endpointCount; i++) {
                ServiceEntry.Endpoint endpoint =
                    ServiceEntry.Endpoint.newBuilder()
                        .setAddress("10.10.10." + i)
                        .setWeight(1)
                        .putPorts("http", 8080)
                        .putLabels("app", "nacos-istio")
                        .build();
                serviceEntryBuilder.addEndpoints(endpoint);
            }

            ServiceEntry serviceEntry = serviceEntryBuilder.build();

            Any any = Any.newBuilder()
                .setValue(serviceEntry.toByteString())
                .setTypeUrl("type.googleapis.com/istio.networking.v1alpha3.ServiceEntry")
                .build();

            Metadata metadata = Metadata.newBuilder()
                .setName("nacos/" + serviceName)
                .putAnnotations("virtual", "1")
                .build();

            Resource resource = Resource.newBuilder()
                .setBody(any)
                .setMetadata(metadata)
                .build();

            Resources resources = Resources.newBuilder()
                .addResources(resource)
                .setCollection(CollectionTypes.SERVICE_ENTRY)
                .setNonce(String.valueOf(System.currentTimeMillis()))
                .build();

            Loggers.MAIN.info("generated resources: {}", resources);

            return resources;
        } catch (Exception e) {

            Loggers.MAIN.error("", e);
            return null;
        }
    }

    @Override
    public StreamObserver<RequestResources> establishResourceStream(StreamObserver<Resources> responseObserver) {

        int id = connectIdGenerator.incrementAndGet();
        connnections.put(id, responseObserver);

        return new StreamObserver<RequestResources>() {

            private int connectionId = id;

            @Override
            public void onNext(RequestResources value) {

                Loggers.MAIN.info("receiving request, sink: {}, type: {}", value.getSinkNode(), value.getCollection());

                if (value.getErrorDetail() != null && value.getErrorDetail().getCode() != 0) {

                    Loggers.MAIN.error("NACK error code: {}, message: {}", value.getErrorDetail().getCode()
                        , value.getErrorDetail().getMessage());
                    return;
                }

                if (StringUtils.isNotBlank(value.getResponseNonce())) {
                    // This is a response:
                    Loggers.MAIN.info("ACK nonce: {}, type: {}", value.getResponseNonce(), value.getCollection());
                    return;
                }

                if (!CollectionTypes.SERVICE_ENTRY.equals(value.getCollection())) {
                    // Return empty resources for other types:
                    Resources resources = Resources.newBuilder()
                        .setCollection(value.getCollection())
                        .setNonce(String.valueOf(System.currentTimeMillis()))
                        .build();

                    responseObserver.onNext(resources);
                } else {
                    responseObserver.onNext(generateResources());
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
