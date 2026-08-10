/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
 * Copyright (c) 2018, The SOFAStack Authors. All rights reserved.
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

package com.alibaba.nacos.core.distributed.raft.grpc;

import com.alibaba.nacos.core.distributed.raft.auth.NacosJRaftCallCredentials;
import com.alipay.sofa.jraft.ReplicatorGroup;
import com.alipay.sofa.jraft.entity.PeerId;
import com.alipay.sofa.jraft.error.InvokeTimeoutException;
import com.alipay.sofa.jraft.error.RemotingException;
import com.alipay.sofa.jraft.option.RpcOptions;
import com.alipay.sofa.jraft.rpc.InvokeCallback;
import com.alipay.sofa.jraft.rpc.InvokeContext;
import com.alipay.sofa.jraft.rpc.RpcClient;
import com.alipay.sofa.jraft.rpc.RpcUtils;
import com.alipay.sofa.jraft.rpc.impl.ManagedChannelHelper;
import com.alipay.sofa.jraft.rpc.impl.MarshallerRegistry;
import com.alipay.sofa.jraft.util.DirectExecutor;
import com.alipay.sofa.jraft.util.Endpoint;
import com.alipay.sofa.jraft.util.Requires;
import com.alipay.sofa.jraft.util.SystemPropertyUtil;
import com.google.protobuf.Message;
import io.grpc.CallCredentials;
import io.grpc.CallOptions;
import io.grpc.ConnectivityState;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.MethodDescriptor;
import io.grpc.protobuf.ProtoUtils;
import io.grpc.stub.ClientCalls;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Nacos JRaft gRPC client that attaches Nacos server identity credentials to every request.
 *
 * <p>This class follows the SOFA-JRaft 1.4.0 {@code GrpcClient} implementation because that
 * version does not expose the channel and call-option construction points needed by Nacos. It
 * should extend the upstream client after the required protected extension point is released.</p>
 *
 * @author xiweng.yy
 */
public class NacosGrpcClient implements RpcClient {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(NacosGrpcClient.class);
    
    private static final String FIXED_METHOD_NAME = "_call";
    
    private static final int RESET_CONN_THRESHOLD =
        SystemPropertyUtil.getInt("jraft.grpc.max.conn.failures.to_reset", 2);
    
    private static final int RPC_MAX_INBOUND_MESSAGE_SIZE = SystemPropertyUtil.getInt(
        "jraft.grpc.max_inbound_message_size.bytes", 4 * 1024 * 1024);
    
    private final Map<Endpoint, ManagedChannel> managedChannelPool = new ConcurrentHashMap<>();
    
    private final Map<Endpoint, AtomicInteger> transientFailures = new ConcurrentHashMap<>();
    
    private final Map<String, Message> parserClasses;
    
    private final MarshallerRegistry marshallerRegistry;
    
    private final CallCredentials callCredentials;
    
    private volatile ReplicatorGroup replicatorGroup;
    
    public NacosGrpcClient(Map<String, Message> parserClasses,
        MarshallerRegistry marshallerRegistry) {
        this(parserClasses, marshallerRegistry, new NacosJRaftCallCredentials());
    }
    
    NacosGrpcClient(Map<String, Message> parserClasses, MarshallerRegistry marshallerRegistry,
        CallCredentials callCredentials) {
        this.parserClasses = parserClasses;
        this.marshallerRegistry = marshallerRegistry;
        this.callCredentials = callCredentials;
    }
    
    @Override
    public boolean init(RpcOptions rpcOptions) {
        return true;
    }
    
    @Override
    public void shutdown() {
        closeAllChannels();
        transientFailures.clear();
    }
    
    @Override
    public boolean checkConnection(Endpoint endpoint) {
        return checkConnection(endpoint, false);
    }
    
    @Override
    public boolean checkConnection(Endpoint endpoint, boolean createIfAbsent) {
        Requires.requireNonNull(endpoint, "endpoint");
        return checkChannel(endpoint, createIfAbsent);
    }
    
    @Override
    public void closeConnection(Endpoint endpoint) {
        Requires.requireNonNull(endpoint, "endpoint");
        closeChannel(endpoint);
    }
    
    @Override
    public void registerConnectEventListener(ReplicatorGroup replicatorGroup) {
        this.replicatorGroup = replicatorGroup;
    }
    
    @Override
    public Object invokeSync(Endpoint endpoint, Object request, InvokeContext invokeContext,
        long timeoutMillis) throws RemotingException {
        CompletableFuture<Object> future = new CompletableFuture<>();
        invokeAsync(endpoint, request, invokeContext, (result, error) -> {
            if (error == null) {
                future.complete(result);
            } else {
                future.completeExceptionally(error);
            }
        }, timeoutMillis);
        try {
            return future.get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new InvokeTimeoutException(e);
        } catch (Throwable e) {
            future.cancel(true);
            throw new RemotingException(e);
        }
    }
    
    @Override
    public void invokeAsync(Endpoint endpoint, Object request, InvokeContext invokeContext,
        InvokeCallback callback, long timeoutMillis) {
        Requires.requireNonNull(endpoint, "endpoint");
        Requires.requireNonNull(request, "request");
        Executor executor = callback.executor() == null ? DirectExecutor.INSTANCE
            : callback.executor();
        ManagedChannel channel = getCheckedChannel(endpoint);
        if (channel == null) {
            executor.execute(() -> callback.complete(null,
                new RemotingException("Fail to connect: " + endpoint)));
            return;
        }
        MethodDescriptor<Message, Message> callMethod = getCallMethod(request);
        CallOptions callOptions = CallOptions.DEFAULT
            .withDeadlineAfter(timeoutMillis, TimeUnit.MILLISECONDS)
            .withCallCredentials(callCredentials);
        ClientCalls.asyncUnaryCall(channel.newCall(callMethod, callOptions), (Message) request,
            new StreamObserver<Message>() {
                
                @Override
                public void onNext(Message value) {
                    executor.execute(() -> callback.complete(value, null));
                }
                
                @Override
                public void onError(Throwable throwable) {
                    executor.execute(() -> callback.complete(null, throwable));
                }
                
                @Override
                public void onCompleted() {
                }
            });
    }
    
    private MethodDescriptor<Message, Message> getCallMethod(Object request) {
        String requestClassName = request.getClass().getName();
        Message requestInstance = Requires.requireNonNull(parserClasses.get(requestClassName),
            "null default instance: " + requestClassName);
        return MethodDescriptor.<Message, Message>newBuilder()
            .setType(MethodDescriptor.MethodType.UNARY)
            .setFullMethodName(MethodDescriptor.generateFullMethodName(requestClassName,
                FIXED_METHOD_NAME))
            .setRequestMarshaller(ProtoUtils.marshaller(requestInstance))
            .setResponseMarshaller(ProtoUtils.marshaller(
                marshallerRegistry.findResponseInstanceByRequest(requestClassName)))
            .build();
    }
    
    private ManagedChannel getCheckedChannel(Endpoint endpoint) {
        ManagedChannel channel = getChannel(endpoint, true);
        return checkConnectivity(endpoint, channel) ? channel : null;
    }
    
    private ManagedChannel getChannel(Endpoint endpoint, boolean createIfAbsent) {
        if (createIfAbsent) {
            return managedChannelPool.computeIfAbsent(endpoint, this::newChannel);
        }
        return managedChannelPool.get(endpoint);
    }
    
    private ManagedChannel newChannel(Endpoint endpoint) {
        ManagedChannel channel = ManagedChannelBuilder.forAddress(endpoint.getIp(),
            endpoint.getPort())
            .usePlaintext()
            .directExecutor()
            .maxInboundMessageSize(RPC_MAX_INBOUND_MESSAGE_SIZE)
            .build();
        LOGGER.info("Creating new channel to: {}.", endpoint);
        notifyWhenStateChanged(ConnectivityState.IDLE, endpoint, channel);
        return channel;
    }
    
    private ManagedChannel removeChannel(Endpoint endpoint) {
        return managedChannelPool.remove(endpoint);
    }
    
    private void notifyWhenStateChanged(ConnectivityState state, Endpoint endpoint,
        ManagedChannel channel) {
        channel.notifyWhenStateChanged(state, () -> onStateChanged(endpoint, channel));
    }
    
    private void onStateChanged(Endpoint endpoint, ManagedChannel channel) {
        ConnectivityState state = channel.getState(false);
        LOGGER.info("The channel {} is in state: {}.", endpoint, state);
        switch (state) {
            case READY:
                notifyReady(endpoint);
                notifyWhenStateChanged(ConnectivityState.READY, endpoint, channel);
                break;
            case TRANSIENT_FAILURE:
                notifyFailure(endpoint);
                notifyWhenStateChanged(ConnectivityState.TRANSIENT_FAILURE, endpoint, channel);
                break;
            case SHUTDOWN:
                notifyShutdown(endpoint);
                break;
            case CONNECTING:
                notifyWhenStateChanged(ConnectivityState.CONNECTING, endpoint, channel);
                break;
            case IDLE:
                notifyWhenStateChanged(ConnectivityState.IDLE, endpoint, channel);
                break;
            default:
                break;
        }
    }
    
    private void notifyReady(Endpoint endpoint) {
        LOGGER.info("The channel {} has successfully established.", endpoint);
        clearConnFailuresCount(endpoint);
        ReplicatorGroup currentReplicatorGroup = replicatorGroup;
        if (currentReplicatorGroup == null) {
            return;
        }
        try {
            RpcUtils.runInThread(() -> {
                PeerId peer = new PeerId();
                if (peer.parse(endpoint.toString())) {
                    LOGGER.info("Peer {} is connected.", peer);
                    currentReplicatorGroup.checkReplicator(peer, true);
                } else {
                    LOGGER.error("Fail to parse peer: {}.", endpoint);
                }
            });
        } catch (Throwable e) {
            LOGGER.error("Fail to check replicator {}.", endpoint, e);
        }
    }
    
    private void notifyFailure(Endpoint endpoint) {
        LOGGER.warn("There has been some transient failure on this channel {}.", endpoint);
    }
    
    private void notifyShutdown(Endpoint endpoint) {
        LOGGER.warn("This channel {} has started shutting down. Any new RPCs should fail "
            + "immediately.", endpoint);
    }
    
    private void closeAllChannels() {
        for (Map.Entry<Endpoint, ManagedChannel> entry : managedChannelPool.entrySet()) {
            ManagedChannel channel = entry.getValue();
            LOGGER.info("Shutdown managed channel: {}, {}.", entry.getKey(), channel);
            ManagedChannelHelper.shutdownAndAwaitTermination(channel);
        }
        managedChannelPool.clear();
    }
    
    private void closeChannel(Endpoint endpoint) {
        ManagedChannel channel = removeChannel(endpoint);
        LOGGER.info("Close connection: {}, {}.", endpoint, channel);
        if (channel != null) {
            ManagedChannelHelper.shutdownAndAwaitTermination(channel);
        }
    }
    
    private boolean checkChannel(Endpoint endpoint, boolean createIfAbsent) {
        ManagedChannel channel = getChannel(endpoint, createIfAbsent);
        return channel != null && checkConnectivity(endpoint, channel);
    }
    
    private int incConnFailuresCount(Endpoint endpoint) {
        return transientFailures.computeIfAbsent(endpoint, key -> new AtomicInteger())
            .incrementAndGet();
    }
    
    private void clearConnFailuresCount(Endpoint endpoint) {
        transientFailures.remove(endpoint);
    }
    
    private boolean checkConnectivity(Endpoint endpoint, ManagedChannel channel) {
        ConnectivityState state = channel.getState(false);
        if (state != ConnectivityState.TRANSIENT_FAILURE
            && state != ConnectivityState.SHUTDOWN) {
            return true;
        }
        int failures = incConnFailuresCount(endpoint);
        if (failures < RESET_CONN_THRESHOLD) {
            if (failures == RESET_CONN_THRESHOLD - 1) {
                channel.resetConnectBackoff();
            }
            return true;
        }
        clearConnFailuresCount(endpoint);
        ManagedChannel removedChannel = removeChannel(endpoint);
        if (removedChannel == null) {
            return false;
        }
        LOGGER.warn("Channel[{}] in [INACTIVE] state {} times, it has been removed from the "
            + "pool.", endpoint, failures);
        if (removedChannel != channel) {
            ManagedChannelHelper.shutdownAndAwaitTermination(removedChannel, 100L);
        }
        ManagedChannelHelper.shutdownAndAwaitTermination(channel, 100L);
        return false;
    }
}
