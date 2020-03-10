package com.alibaba.nacos.client.naming.net;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;
import com.alibaba.nacos.api.common.ResponseCode;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.CommonParams;
import com.alibaba.nacos.api.naming.SubscribeInfo;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ListView;
import com.alibaba.nacos.api.naming.pojo.Service;
import com.alibaba.nacos.api.selector.AbstractSelector;
import com.alibaba.nacos.api.selector.NoneSelector;
import com.alibaba.nacos.client.connection.ServerListManager;
import com.alibaba.nacos.client.connection.grpc.BaseGrpcClient;
import com.alibaba.nacos.client.naming.beat.BeatInfo;
import com.alibaba.nacos.client.naming.utils.NetUtils;
import com.alibaba.nacos.client.security.SecurityProxy;
import com.alibaba.nacos.common.grpc.*;
import com.alibaba.nacos.common.utils.UuidUtils;
import com.google.common.base.Charsets;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import static com.alibaba.nacos.client.utils.LogUtils.NAMING_LOGGER;

public class NamingGrpcClient extends BaseGrpcClient implements NamingClient {

    private int port = 18849;

    private ServerListManager serverListManager;

    private SecurityProxy securityProxy;

    public NamingGrpcClient(ServerListManager serverListManager, SecurityProxy securityProxy) {

        super(UuidUtils.generateUuid());

        this.serverListManager = serverListManager;
        this.securityProxy = securityProxy;

        NAMING_LOGGER.info("[NAMING-GRPC-CLIENT] init connection id:" + connectionId
            + ", servers:" + serverListManager.getServerList());

        buildClient();

        ScheduledExecutorService executorService = new ScheduledThreadPoolExecutor(2, new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r);
                t.setName("com.alibaba.nacos.client.naming.updater");
                t.setDaemon(true);
                return t;
            }
        });

        executorService.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                sendBeat();
            }
        }, 5000, 5000, TimeUnit.MILLISECONDS);
    }

    private class NamingStreamServer implements StreamObserver<GrpcResponse> {

        @Override
        public void onNext(GrpcResponse value) {
            if (NAMING_LOGGER.isDebugEnabled()) {
                NAMING_LOGGER.debug("[GRPC] receive data: " + value.toString());
            }
        }

        @Override
        public void onError(Throwable t) {
            NAMING_LOGGER.error("[GRPC] error", t);
            rebuildClient();
        }

        @Override
        public void onCompleted() {
            NAMING_LOGGER.info("[GRPC] connection closed.");
            rebuildClient();
        }
    }

    private String nextServer() {
        return serverListManager.getNextServer(serverListManager.getServerList());
    }

    private void rebuildClient() {
        // TODO disconnect first:
        buildClient();
    }


    private void buildClient() {

        this.channel = ManagedChannelBuilder.forAddress(nextServer(), this.port)
            .usePlaintext(true)
            .build();

        GrpcMetadata grpcMetadata = GrpcMetadata.newBuilder().putLabels("type", "naming").build();

        grpcStreamServiceStub = GrpcStreamServiceGrpc.newStub(channel);

        grpcServiceBlockingStub = GrpcServiceGrpc.newBlockingStub(channel);

        GrpcRequest request = GrpcRequest.newBuilder()
            .setModule("naming")
            .setClientId(connectionId)
            .setRequestId(buildRequestId(connectionId))
            .setSource(NetUtils.localIP())
            .setAction("registerClient")
            .setMetadata(grpcMetadata)
            .build();

        grpcStreamServiceStub.streamRequest(request, new NamingStreamServer());
    }

    @Override
    public void subscribeService(String namespaceId, String serviceName, String groupName, String clusters) throws NacosException {

    }

    @Override
    public void unsubscribeService(String namespaceId, String serviceName, String groupName, String clusters) throws NacosException {

    }

    @Override
    public void registerInstance(String namespaceId, String serviceName, String groupName, Instance instance) throws NacosException {

        Any any = Any.newBuilder()
            .setValue(ByteString.copyFrom(JSON.toJSONString(instance), Charsets.UTF_8))
            .setTypeUrl("naming/instance")
            .build();

        GrpcRequest request = GrpcRequest.newBuilder()
            .setClientId(connectionId)
            .setRequestId(buildRequestId(connectionId))
            .setSource(NetUtils.localIP())
            .setAction("registerInstance")
            .putParams(CommonParams.NAMESPACE_ID, namespaceId)
            .putParams(CommonParams.SERVICE_NAME, serviceName)
            .putParams(CommonParams.GROUP_NAME, groupName)
            .setBody(any)
            .build();

        GrpcResponse response = grpcServiceBlockingStub.request(request);

        if (response.getCode() != ResponseCode.OK) {
            throw new NacosException(response.getCode(), response.getMessage().getValue().toString());
        }
    }

    @Override
    public void deregisterInstance(String namespaceId, String serviceName, String groupName, Instance instance) throws NacosException {

        Any any = Any.newBuilder()
            .setValue(ByteString.copyFrom(JSON.toJSONString(instance), Charsets.UTF_8))
            .setTypeUrl("naming/instance")
            .build();

        GrpcRequest request = GrpcRequest.newBuilder()
            .setClientId(connectionId)
            .setRequestId(buildRequestId(connectionId))
            .setSource(NetUtils.localIP())
            .setAction("deregisterInstance")
            .putParams(CommonParams.NAMESPACE_ID, namespaceId)
            .putParams(CommonParams.SERVICE_NAME, serviceName)
            .putParams(CommonParams.GROUP_NAME, groupName)
            .setBody(any)
            .build();

        GrpcResponse response = grpcServiceBlockingStub.request(request);

        if (response.getCode() != ResponseCode.OK) {
            throw new NacosException(response.getCode(), response.getMessage().getValue().toString());
        }
    }

    @Override
    public void updateInstance(String namespaceId, String serviceName, String groupName, Instance instance) throws NacosException {

        Any any = Any.newBuilder()
            .setValue(ByteString.copyFrom(JSON.toJSONString(instance), Charsets.UTF_8))
            .setTypeUrl("naming/instance")
            .build();

        GrpcRequest request = GrpcRequest.newBuilder()
            .setClientId(connectionId)
            .setRequestId(buildRequestId(connectionId))
            .setSource(NetUtils.localIP())
            .setAction("updateInstance")
            .putParams(CommonParams.NAMESPACE_ID, namespaceId)
            .putParams(CommonParams.SERVICE_NAME, serviceName)
            .putParams(CommonParams.GROUP_NAME, groupName)
            .setBody(any)
            .build();

        GrpcResponse response = grpcServiceBlockingStub.request(request);

        if (response.getCode() != ResponseCode.OK) {
            throw new NacosException(response.getCode(), response.getMessage().getValue().toString());
        }
    }

    @Override
    public Service queryService(String namespaceId, String serviceName, String groupName) throws NacosException {

        GrpcRequest request = GrpcRequest.newBuilder()
            .setClientId(connectionId)
            .setRequestId(buildRequestId(connectionId))
            .setSource(NetUtils.localIP())
            .setAction("queryService")
            .putParams(CommonParams.NAMESPACE_ID, namespaceId)
            .putParams(CommonParams.SERVICE_NAME, serviceName)
            .putParams(CommonParams.GROUP_NAME, groupName)
            .build();

        GrpcResponse response = grpcServiceBlockingStub.request(request);

        if (response.getCode() != ResponseCode.OK) {
            throw new NacosException(response.getCode(), response.getMessage().getValue().toString());
        }

        return JSON.parseObject(response.getMessage().getValue().toString(), Service.class);
    }

    @Override
    public void createService(String namespaceId, Service service, AbstractSelector selector) throws NacosException {

        Any any = Any.newBuilder()
            .setValue(ByteString.copyFrom(JSON.toJSONString(service), Charsets.UTF_8))
            .setTypeUrl("naming/service")
            .build();

        GrpcRequest request = GrpcRequest.newBuilder()
            .setClientId(connectionId)
            .setRequestId(buildRequestId(connectionId))
            .setSource(NetUtils.localIP())
            .setAction("createService")
            .putParams(CommonParams.NAMESPACE_ID, namespaceId)
            .putParams("selector", JSON.toJSONString(selector))
            .setBody(any)
            .build();

        GrpcResponse response = grpcServiceBlockingStub.request(request);

        if (response.getCode() != ResponseCode.OK) {
            throw new NacosException(response.getCode(), response.getMessage().getValue().toString());
        }
    }

    @Override
    public boolean deleteService(String namespaceId, String serviceName, String groupName) throws NacosException {

        GrpcRequest request = GrpcRequest.newBuilder()
            .setClientId(connectionId)
            .setRequestId(buildRequestId(connectionId))
            .setSource(NetUtils.localIP())
            .setAction("deleteService")
            .putParams(CommonParams.NAMESPACE_ID, namespaceId)
            .putParams(CommonParams.SERVICE_NAME, serviceName)
            .putParams(CommonParams.GROUP_NAME, groupName)
            .build();

        GrpcResponse response = grpcServiceBlockingStub.request(request);

        return response.getCode() == ResponseCode.OK;
    }

    @Override
    public void updateService(String namespaceId, Service service, AbstractSelector selector) throws NacosException {

        Any any = Any.newBuilder()
            .setValue(ByteString.copyFrom(JSON.toJSONString(service), Charsets.UTF_8))
            .setTypeUrl("naming/service")
            .build();

        GrpcRequest request = GrpcRequest.newBuilder()
            .setClientId(connectionId)
            .setRequestId(buildRequestId(connectionId))
            .setSource(NetUtils.localIP())
            .setAction("updateService")
            .putParams(CommonParams.NAMESPACE_ID, namespaceId)
            .putParams("selector", JSON.toJSONString(selector))
            .setBody(any)
            .build();

        GrpcResponse response = grpcServiceBlockingStub.request(request);

        if (response.getCode() != ResponseCode.OK) {
            throw new NacosException(response.getCode(), response.getMessage().getValue().toString());
        }
    }

    @Override
    public String queryList(String namespaceId, String serviceName, String groupName, String clusters,
                            SubscribeInfo subscribeInfo, boolean healthyOnly) throws NacosException {

        if ("UDP".equals(subscribeInfo.getType())) {
            NAMING_LOGGER.warn("udp subscribe type is not supported in grpc client.");
        }

        Any any = Any.newBuilder()
            .setValue(ByteString.copyFrom(JSON.toJSONString(subscribeInfo), Charsets.UTF_8))
            .setTypeUrl("naming/subscribeInfo")
            .build();

        GrpcRequest request = GrpcRequest.newBuilder()
            .setClientId(connectionId)
            .setRequestId(buildRequestId(connectionId))
            .setSource(NetUtils.localIP())
            .setAction("queryList")
            .putParams(CommonParams.NAMESPACE_ID, namespaceId)
            .putParams(CommonParams.SERVICE_NAME, serviceName)
            .putParams(CommonParams.GROUP_NAME, groupName)
            .putParams("clusters", clusters)
            .putParams("healthOnly", String.valueOf(healthyOnly))
            .setBody(any)
            .build();

        GrpcResponse response = grpcServiceBlockingStub.request(request);

        if (response.getCode() != ResponseCode.OK) {
            throw new NacosException(response.getCode(), response.getMessage().getValue().toString());
        }

        // if subscribe flag is set, also subscribe the service:


        return response.getMessage().getValue().toString();
    }

    @Override
    public JSONObject sendBeat(String namespaceId, BeatInfo beatInfo, boolean lightBeatEnabled) throws NacosException {
        return sendBeat();
    }

    @Override
    public boolean serverHealthy() {
        GrpcRequest request = GrpcRequest.newBuilder()
            .setClientId(connectionId)
            .setRequestId(buildRequestId(connectionId))
            .setSource(NetUtils.localIP())
            .setAction("serverHealthy")
            .build();

        GrpcResponse response = grpcServiceBlockingStub.request(request);

        return response.getCode() == ResponseCode.OK;
    }

    @Override
    public ListView<String> getServiceList(String namespaceId, int pageNo, int pageSize, String groupName) throws NacosException {
        return getServiceList(namespaceId, pageNo, pageSize, groupName, new NoneSelector());
    }

    @Override
    public ListView<String> getServiceList(String namespaceId, int pageNo, int pageSize, String groupName, AbstractSelector selector) throws NacosException {

        Any any = Any.newBuilder()
            .setValue(ByteString.copyFrom(JSON.toJSONString(selector), Charsets.UTF_8))
            .setTypeUrl("naming/selector")
            .build();

        GrpcRequest request = GrpcRequest.newBuilder()
            .setClientId(connectionId)
            .setRequestId(buildRequestId(connectionId))
            .setSource(NetUtils.localIP())
            .setAction("getServiceList")
            .putParams(CommonParams.NAMESPACE_ID, namespaceId)
            .putParams(CommonParams.GROUP_NAME, groupName)
            .putParams("pageNo", String.valueOf(pageNo))
            .putParams("pageSize", String.valueOf(pageSize))
            .setBody(any)
            .build();

        GrpcResponse response = grpcServiceBlockingStub.request(request);

        if (response.getCode() != ResponseCode.OK) {
            throw new NacosException(response.getCode(), response.getMessage().getValue().toString());
        }

        return JSON.parseObject(response.getMessage().getValue().toString(), new TypeReference<ListView<String>>(){});
    }
}
