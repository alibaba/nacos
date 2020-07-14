/**
 * Alipay.com Inc.
 * Copyright (c) 2004-2020 All Rights Reserved.
 */
package com.alibaba.nacos.client.remote.grpc;

import java.net.UnknownHostException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.grpc.GrpcMetadata;
import com.alibaba.nacos.api.grpc.GrpcRequest;
import com.alibaba.nacos.api.grpc.GrpcResponse;
import com.alibaba.nacos.api.grpc.RequestGrpc;
import com.alibaba.nacos.api.grpc.RequestStreamGrpc;
import com.alibaba.nacos.api.remote.request.HeartBeatRequest;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.client.naming.utils.NetUtils;
import com.alibaba.nacos.client.remote.RpcClient;
import com.alibaba.nacos.client.remote.RpcClientStatus;
import com.alibaba.nacos.client.remote.ServerListFactory;
import com.alibaba.nacos.common.utils.JacksonUtils;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;

/**
 * @author liuzunfei
 * @version $Id: GrpcClient.java, v 0.1 2020年07月13日 9:16 PM liuzunfei Exp $
 */
public class GrpcClient extends RpcClient {


    protected ManagedChannel channel;

    protected RequestStreamGrpc.RequestStreamStub grpcStreamServiceStub;

    protected RequestGrpc.RequestBlockingStub grpcServiceStub;



    public GrpcClient(){
        super();
    }

    public GrpcClient(ServerListFactory serverListFactory) {
        super(serverListFactory);
        try {
            start();
        } catch (Exception e) {
            System.out.println("GrpcClient  start fail .....");

            e.printStackTrace();
        }
    }

    @Override
    public void start() throws NacosException {

        if (rpcClientStatus!=RpcClientStatus.INITED){
            return;
        }
    
        rpcClientStatus = RpcClientStatus.STARTING;

        buildClient();
        ScheduledExecutorService executorService = new ScheduledThreadPoolExecutor(2, new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r);
                t.setName("com.alibaba.nacos.client.config.grpc.worker");
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
    
        rpcClientStatus = RpcClientStatus.RUNNING;

    }


    public void sendBeat() {

        GrpcMetadata meta= GrpcMetadata.newBuilder().setConnectionId(connectionId).setClientIp(
            NetUtils.localIP()).build();
        HeartBeatRequest heartBeatRequest=new HeartBeatRequest();
        GrpcRequest streamRequest = GrpcRequest.newBuilder()
            .setMetadata(meta)
            .setType(heartBeatRequest.getType())
            .setBody(Any.newBuilder().setValue(ByteString.copyFromUtf8(JacksonUtils.toJson(heartBeatRequest))).build())
            .build();
        GrpcResponse response = grpcServiceStub.request(streamRequest);
        System.out.println("Send heart beat message,response :"+response);
    }
    
    private void buildClient() throws NacosException {

        String serverAddress =getServerListFactory().genNextServer();

        String serverIp="";
        int serverPort=0;

        if (serverAddress.contains("http")) {
            serverIp = serverAddress.split(":")[1].replaceAll("//","");
            serverPort=Integer.valueOf(serverAddress.split(":")[2].replaceAll("//",""));
        }else{
            serverIp = serverAddress.split(":")[0];
            serverPort=Integer.valueOf(serverAddress.split(":")[1]);
        }

        //Loggers.info("[GRPC ]init config listen stream.......,server list:"+server );

        this.channel = ManagedChannelBuilder.forAddress(serverIp, serverPort+1000)
            .usePlaintext(true)
            .build();

        grpcStreamServiceStub = RequestStreamGrpc.newStub(channel);

        grpcServiceStub = RequestGrpc.newBlockingStub(channel);

        GrpcMetadata meta= GrpcMetadata.newBuilder().setConnectionId(connectionId).setClientIp(
            NetUtils.localIP()).build();
        GrpcRequest streamRequest = GrpcRequest.newBuilder()
            .setMetadata(meta)
            .build();

        //LOGGER.info("[GRPC ]init config listen stream......." );

        System.out.println("GrpcClient  send stream....."+streamRequest);

        grpcStreamServiceStub.requestStream(streamRequest, new NacosStreamObserver());

        //relistenKeyIfNecessary();
    }

    private class NacosStreamObserver implements StreamObserver<GrpcResponse> {

        @Override
        public void onNext(GrpcResponse value) {
            //LOGGER.info("[GRPC] receive config data: " + value.toString());
            String message = value.getBody().getValue().toStringUtf8();
            System.out.println("Receive Stream Response："+message);
            //JSONObject json = JSON.parseObject(message.trim());
            //LOGGER.info("[GRPC] receive config data: " + json);
            //abstractStreamMessageHandler.onResponse(json);
        }


        @Override
        public void onError(Throwable t) {
            //LOGGER.error("[GRPC] config error", t);
            //rebuildClient();
        }

        @Override
        public void onCompleted() {
            //LOGGER.info("[GRPC] config connection closed.");
            //rebuildClient();
        }
    }


    @Override
    public void switchServer() {

    }

    @Override
    public <T extends Response> T request(Request request) {

        GrpcMetadata meta= GrpcMetadata.newBuilder().setConnectionId(connectionId).setClientIp(
            NetUtils.localIP()).build();
        GrpcRequest streamRequest = GrpcRequest.newBuilder()
            .setMetadata(meta)
            .setType(request.getType())
            .setBody(Any.newBuilder().setValue(ByteString.copyFromUtf8(JacksonUtils.toJson(request))))
            .build();
        GrpcResponse response =grpcServiceStub.request(streamRequest);

        return null;
    }
    
    
}
