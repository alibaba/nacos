package com.alibaba.nacos.istio.mcp;


import com.alibaba.fastjson.JSON;
import com.alibaba.nacos.istio.misc.Loggers;
import com.alibaba.nacos.istio.model.Port;
import com.alibaba.nacos.istio.model.mcp.*;
import com.alibaba.nacos.istio.model.naming.ServiceEntry;
import com.google.protobuf.Any;
import io.grpc.stub.StreamObserver;
import org.springframework.stereotype.Service;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author nkorange
 * @since 1.1.4
 */
@Service
public class NacosMcpService extends ResourceSourceGrpc.ResourceSourceImplBase {

    private Map<String, StreamObserver<Resources>> connnections = new ConcurrentHashMap<>();

    public NacosMcpService() {
        super();
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {

                for (; ; ) {
                    try {
                        Thread.sleep(5000L);
                        if (connnections.isEmpty()) {
                            continue;
                        }
                        for (StreamObserver<Resources> observer : connnections.values()) {
                            observer.onNext(generateResources());
                        }

                    } catch (Exception e) {

                    }
                }
            }
        });
        thread.start();
    }

    public Resources generateResources() {

        String serviceName = "java.mock.1";
        int endpointCount = 10;

        ServiceEntry.Builder serviceEntryBuilder = ServiceEntry.newBuilder()
            .setResolution(ServiceEntry.Resolution.STATIC)
            .setLocation(ServiceEntry.Location.MESH_INTERNAL)
            .setHosts(0, serviceName + ".nacos")
            .setPorts(0, Port.newBuilder().setNumber(8080).setName("http").setProtocol("HTTP").build());

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
            .setTypeUrl(ServiceEntry.class.getCanonicalName())
            .build();

        Metadata metadata = Metadata.newBuilder().setName("nacos/" + serviceName).build();
        metadata.getAnnotationsMap().put("virtual", "1");

        Resource resource = Resource.newBuilder()
            .setBody(any)
            .setMetadata(metadata)
            .build();

        Resources resources = Resources.newBuilder()
            .addResources(resource)
            .setCollection(CollectionTypes.SERVICE_ENTRY)
            .setNonce(String.valueOf(System.currentTimeMillis()))
            .build();

        Loggers.MAIN.info("generated resources: {}", JSON.toJSONString(resource));

        return resources;
    }

    public void addConnection(String id, StreamObserver<Resources> observer) {
        if (!connnections.containsKey(id)) {
            connnections.put(id, observer);
        }
    }

    @Override
    public StreamObserver<RequestResources> establishResourceStream(StreamObserver<Resources> responseObserver) {

        Loggers.MAIN.info("new connection incoming...");

        return new StreamObserver<RequestResources>() {

            @Override
            public void onNext(RequestResources value) {

                String nonce = value.getResponseNonce();
                List<Integer> list = new LinkedList<>();

                Loggers.MAIN.info("receiving request, sink: {}, type: {}", value.getSinkNode(), value.getCollection());

                // Return empty resources for other types:
                if (!CollectionTypes.SERVICE_ENTRY.equals(value.getCollection())) {

                    Resources resources = Resources.newBuilder()
                        .setCollection(value.getCollection())
                        .build();

                    responseObserver.onNext(resources);
                    return;
                }

                addConnection(value.getSinkNode().getId(), responseObserver);
            }

            @Override
            public void onError(Throwable t) {
                Loggers.MAIN.error("", t);
                responseObserver.onError(t);
            }

            @Override
            public void onCompleted() {
                responseObserver.onCompleted();
            }
        };
    }
}
