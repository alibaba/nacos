package com.alibaba.nacos.naming.grpc;

import com.alibaba.fastjson.JSON;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.common.ResponseCode;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.CommonParams;
import com.alibaba.nacos.api.naming.utils.NamingUtils;
import com.alibaba.nacos.common.grpc.GrpcRequest;
import com.alibaba.nacos.common.grpc.GrpcResponse;
import com.alibaba.nacos.core.remoting.grpc.impl.GrpcRequestHandler;
import com.alibaba.nacos.core.remoting.grpc.impl.RequestServiceGrpcImpl;
import com.alibaba.nacos.naming.core.Instance;
import com.alibaba.nacos.naming.core.ServiceManager;
import com.alibaba.nacos.naming.push.DataSource;
import com.alibaba.nacos.naming.push.NamingPushService;
import com.alibaba.nacos.naming.push.PushClient;
import com.google.common.base.Charsets;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.List;

@Service
public class NamingGrpcRequestHandler implements GrpcRequestHandler {

    @Autowired
    private ServiceManager serviceManager;

    @Autowired
    private RequestServiceGrpcImpl requestServiceGrpc;

    @Autowired
    private NamingPushService pushService;

    @PostConstruct
    public void init() {
        requestServiceGrpc.registerHandler("naming", this);
    }

    public GrpcResponse handle(GrpcRequest request) throws NacosException {

        String action = request.getAction();

        switch (action) {
            case NamingGrpcActions.SUBSCRIBE_SERVICE:
                return handleSubscribeService(request);
            case NamingGrpcActions.UNSUBSCRIBE_SERVICE:
                return handleUnsubscribeService(request);
            case NamingGrpcActions.REGISTER_INSTANCE:
                return handleRegisterInstance(request);
            case NamingGrpcActions.DEREGISTER_INSTANCE:
                return handleDeregisterInstance(request);
            case NamingGrpcActions.QUERY_LIST:
                return handleQueryList(request);
            default:
                return GrpcResponse.newBuilder().setCode(ResponseCode.OK).build();
        }
    }

    private PushClient.GrpcPushClient buildPushClient(GrpcRequest request) {

        String namespaceId = request.getParamsOrDefault(CommonParams.NAMESPACE_ID, Constants.DEFAULT_NAMESPACE_ID);
        String serviceName = request.getParamsOrThrow(CommonParams.SERVICE_NAME);
        String groupName = request.getParamsOrDefault(CommonParams.GROUP_NAME, Constants.DEFAULT_GROUP);
        String clientId = request.getParamsOrThrow(CommonParams.CLIENT_ID);
        String clusters = request.getParamsOrDefault("clusters", Constants.DEFAULT_CLUSTER_NAME);
        String clientIp = request.getSource();

        return new PushClient.GrpcPushClient(namespaceId, NamingUtils.getGroupedName(serviceName, groupName), clusters, clientId, new DataSource() {

            @Override
            public String getData(PushClient client) throws Exception {
                List<Instance> instances =
                    serviceManager.getInstances(
                        namespaceId,
                        NamingUtils.getGroupedName(serviceName, groupName),
                        clusters,
                        clientIp,
                        true);
                return JSON.toJSONString(instances);
            }
        });
    }

    public GrpcResponse handleSubscribeService(GrpcRequest request) throws NacosException {
        pushService.addGrpcClient(buildPushClient(request));
        return GrpcResponse.newBuilder().setCode(ResponseCode.OK).build();
    }

    public GrpcResponse handleUnsubscribeService(GrpcRequest request) {
        pushService.removeGrpcClient(buildPushClient(request));
        return GrpcResponse.newBuilder().setCode(ResponseCode.OK).build();
    }

    public GrpcResponse handleRegisterInstance(GrpcRequest request) throws NacosException {

        String namespaceId = request.getParamsOrDefault(CommonParams.NAMESPACE_ID, Constants.DEFAULT_NAMESPACE_ID);
        String serviceName = request.getParamsOrThrow(CommonParams.SERVICE_NAME);
        String groupName = request.getParamsOrDefault(CommonParams.GROUP_NAME, Constants.DEFAULT_GROUP);

        Instance instance = JSON.parseObject(request.getBody().getValue().toString(), Instance.class);

        serviceManager.registerInstance(namespaceId, NamingUtils.getGroupedName(serviceName, groupName), instance);

        return GrpcResponse.newBuilder().setCode(ResponseCode.OK).build();
    }

    public GrpcResponse handleDeregisterInstance(GrpcRequest request) throws NacosException {

        String namespaceId = request.getParamsOrDefault(CommonParams.NAMESPACE_ID, Constants.DEFAULT_NAMESPACE_ID);
        String serviceName = request.getParamsOrThrow(CommonParams.SERVICE_NAME);
        String groupName = request.getParamsOrDefault(CommonParams.GROUP_NAME, Constants.DEFAULT_GROUP);

        Instance instance = JSON.parseObject(request.getBody().getValue().toString(), Instance.class);

        serviceManager.removeInstance(namespaceId, NamingUtils.getGroupedName(serviceName, groupName), instance.isEphemeral(), instance);

        return GrpcResponse.newBuilder().setCode(ResponseCode.OK).build();
    }

    public GrpcResponse handleQueryList(GrpcRequest request) throws NacosException {

        String namespaceId = request.getParamsOrDefault(CommonParams.NAMESPACE_ID, Constants.DEFAULT_NAMESPACE_ID);
        String serviceName = request.getParamsOrThrow(CommonParams.SERVICE_NAME);
        String groupName = request.getParamsOrDefault(CommonParams.GROUP_NAME, Constants.DEFAULT_GROUP);
        String clusters = request.getParamsOrDefault("clusters", Constants.DEFAULT_CLUSTER_NAME);
        boolean healthyOnly = Boolean.parseBoolean(request.getParamsOrDefault("healthyOnly", "false"));
        String clientIp = request.getSource();

        List<Instance> instances = serviceManager.getInstances(namespaceId, NamingUtils.getGroupedName(serviceName, groupName),
            clusters, clientIp, healthyOnly);

        GrpcResponse response = GrpcResponse.newBuilder()
            .setMessage(Any.newBuilder().setValue(ByteString.copyFrom(JSON.toJSONString(instances), Charsets.UTF_8)))
            .setCode(ResponseCode.OK)
            .build();

        return response;
    }

}
