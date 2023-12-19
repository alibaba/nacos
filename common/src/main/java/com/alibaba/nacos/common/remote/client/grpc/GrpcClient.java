/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.common.remote.client.grpc;

import com.alibaba.nacos.api.ability.constant.AbilityMode;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.grpc.auto.BiRequestStreamGrpc;
import com.alibaba.nacos.api.grpc.auto.Payload;
import com.alibaba.nacos.api.grpc.auto.RequestGrpc;
import com.alibaba.nacos.api.remote.request.ConnectResetRequest;
import com.alibaba.nacos.api.remote.request.ConnectionSetupRequest;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.request.ServerCheckRequest;
import com.alibaba.nacos.api.remote.request.SetupAckRequest;
import com.alibaba.nacos.api.remote.response.ErrorResponse;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.api.remote.response.ServerCheckResponse;
import com.alibaba.nacos.api.remote.response.SetupAckResponse;
import com.alibaba.nacos.common.ability.discover.NacosAbilityManagerHolder;
import com.alibaba.nacos.common.packagescan.resource.Resource;
import com.alibaba.nacos.common.remote.ConnectionType;
import com.alibaba.nacos.common.remote.client.Connection;
import com.alibaba.nacos.common.remote.client.RpcClient;
import com.alibaba.nacos.common.remote.client.RpcClientStatus;
import com.alibaba.nacos.common.remote.client.RpcClientTlsConfig;
import com.alibaba.nacos.common.remote.client.ServerListFactory;
import com.alibaba.nacos.common.remote.client.ServerRequestHandler;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.LoggerUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.common.utils.ThreadFactoryBuilder;
import com.alibaba.nacos.common.utils.TlsTypeResolve;
import com.alibaba.nacos.common.utils.VersionUtils;
import com.google.common.util.concurrent.ListenableFuture;
import io.grpc.CompressorRegistry;
import io.grpc.DecompressorRegistry;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.grpc.netty.NegotiationType;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContext;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContextBuilder;
import io.grpc.netty.shaded.io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * gRPC Client.
 *
 * @author liuzunfei
 * @version $Id: GrpcClient.java, v 0.1 2020年07月13日 9:16 PM liuzunfei Exp $
 */
@SuppressWarnings("PMD.AbstractClassShouldStartWithAbstractNamingRule")
public abstract class GrpcClient extends RpcClient {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(GrpcClient.class);
    
    private final GrpcClientConfig clientConfig;
    
    private ThreadPoolExecutor grpcExecutor;
    
    /**
     * Block to wait setup success response.
     */
    private final RecAbilityContext recAbilityContext = new RecAbilityContext(null);
    
    /**
     * for receiving server abilities.
     */
    private SetupRequestHandler setupRequestHandler;
    
    @Override
    public ConnectionType getConnectionType() {
        return ConnectionType.GRPC;
    }
    
    /**
     * constructor.
     *
     * @param name .
     */
    public GrpcClient(String name) {
        this(DefaultGrpcClientConfig.newBuilder().setName(name).build());
    }
    
    /**
     * constructor.
     *
     * @param properties .
     */
    public GrpcClient(Properties properties) {
        this(DefaultGrpcClientConfig.newBuilder().fromProperties(properties).build());
    }
    
    /**
     * constructor.
     *
     * @param clientConfig .
     */
    public GrpcClient(GrpcClientConfig clientConfig) {
        super(clientConfig);
        this.clientConfig = clientConfig;
        initSetupHandler();
    }
    
    /**
     * constructor.
     *
     * @param clientConfig      .
     * @param serverListFactory .
     */
    public GrpcClient(GrpcClientConfig clientConfig, ServerListFactory serverListFactory) {
        super(clientConfig, serverListFactory);
        this.clientConfig = clientConfig;
        initSetupHandler();
    }
    
    /**
     * setup handler.
     */
    private void initSetupHandler() {
        // register to handler setup request
        setupRequestHandler = new SetupRequestHandler(this.recAbilityContext);
    }
    
    /**
     * constructor.
     *
     * @param name               .
     * @param threadPoolCoreSize .
     * @param threadPoolMaxSize  .
     * @param labels             .
     */
    public GrpcClient(String name, Integer threadPoolCoreSize, Integer threadPoolMaxSize, Map<String, String> labels) {
        this(DefaultGrpcClientConfig.newBuilder().setName(name).setThreadPoolCoreSize(threadPoolCoreSize)
                .setThreadPoolMaxSize(threadPoolMaxSize).setLabels(labels).build());
    }
    
    public GrpcClient(String name, Integer threadPoolCoreSize, Integer threadPoolMaxSize, Map<String, String> labels,
            RpcClientTlsConfig tlsConfig) {
        this(DefaultGrpcClientConfig.newBuilder().setName(name).setThreadPoolCoreSize(threadPoolCoreSize)
                .setTlsConfig(tlsConfig).setThreadPoolMaxSize(threadPoolMaxSize).setLabels(labels).build());
    }
    
    protected ThreadPoolExecutor createGrpcExecutor(String serverIp) {
        // Thread name will use String.format, ipv6 maybe contain special word %, so handle it first.
        serverIp = serverIp.replaceAll("%", "-");
        ThreadPoolExecutor grpcExecutor = new ThreadPoolExecutor(clientConfig.threadPoolCoreSize(),
                clientConfig.threadPoolMaxSize(), clientConfig.threadPoolKeepAlive(), TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(clientConfig.threadPoolQueueSize()),
                new ThreadFactoryBuilder().daemon(true).nameFormat("nacos-grpc-client-executor-" + serverIp + "-%d")
                        .build());
        grpcExecutor.allowCoreThreadTimeOut(true);
        return grpcExecutor;
    }
    
    @Override
    public void shutdown() throws NacosException {
        super.shutdown();
        if (grpcExecutor != null) {
            LOGGER.info("Shutdown grpc executor " + grpcExecutor);
            grpcExecutor.shutdown();
        }
    }
    
    /**
     * Create a stub using a channel.
     *
     * @param managedChannelTemp channel.
     * @return if server check success,return a non-null stub.
     */
    protected RequestGrpc.RequestFutureStub createNewChannelStub(ManagedChannel managedChannelTemp) {
        return RequestGrpc.newFutureStub(managedChannelTemp);
    }
    
    /**
     * create a new channel with specific server address.
     *
     * @param serverIp   serverIp.
     * @param serverPort serverPort.
     * @return if server check success,return a non-null channel.
     */
    private ManagedChannel createNewManagedChannel(String serverIp, int serverPort) {
        LOGGER.info("grpc client connection server:{} ip,serverPort:{},grpcTslConfig:{}", serverIp, serverPort,
                JacksonUtils.toJson(clientConfig.tlsConfig()));
        ManagedChannelBuilder<?> managedChannelBuilder = buildChannel(serverIp, serverPort, buildSslContext())
                .executor(grpcExecutor).compressorRegistry(CompressorRegistry.getDefaultInstance())
                .decompressorRegistry(DecompressorRegistry.getDefaultInstance())
                .maxInboundMessageSize(clientConfig.maxInboundMessageSize())
                .keepAliveTime(clientConfig.channelKeepAlive(), TimeUnit.MILLISECONDS)
                .keepAliveTimeout(clientConfig.channelKeepAliveTimeout(), TimeUnit.MILLISECONDS);
        return managedChannelBuilder.build();
    }
    
    /**
     * shutdown a  channel.
     *
     * @param managedChannel channel to be shutdown.
     */
    private void shuntDownChannel(ManagedChannel managedChannel) {
        if (managedChannel != null && !managedChannel.isShutdown()) {
            managedChannel.shutdownNow();
        }
    }
    
    /**
     * check server if success.
     *
     * @param requestBlockingStub requestBlockingStub used to check server.
     * @return success or not
     */
    private Response serverCheck(String ip, int port, RequestGrpc.RequestFutureStub requestBlockingStub) {
        try {
            ServerCheckRequest serverCheckRequest = new ServerCheckRequest();
            Payload grpcRequest = GrpcUtils.convert(serverCheckRequest);
            ListenableFuture<Payload> responseFuture = requestBlockingStub.request(grpcRequest);
            Payload response = responseFuture.get(clientConfig.serverCheckTimeOut(), TimeUnit.MILLISECONDS);
            //receive connection unregister response here,not check response is success.
            return (Response) GrpcUtils.parse(response);
        } catch (Exception e) {
            LoggerUtils.printIfErrorEnabled(LOGGER,
                    "Server check fail, please check server {} ,port {} is available , error ={}", ip, port, e);
            if (this.clientConfig != null && this.clientConfig.tlsConfig() != null && this.clientConfig.tlsConfig()
                    .getEnableTls()) {
                LoggerUtils.printIfErrorEnabled(LOGGER,
                        "current client is require tls encrypted ,server must support tls ,please check");
            }
            return null;
        }
    }
    
    private StreamObserver<Payload> bindRequestStream(final BiRequestStreamGrpc.BiRequestStreamStub streamStub,
            final GrpcConnection grpcConn) {
        
        return streamStub.requestBiStream(new StreamObserver<Payload>() {
            
            @Override
            public void onNext(Payload payload) {
                
                LoggerUtils.printIfDebugEnabled(LOGGER, "[{}]Stream server request receive, original info: {}",
                        grpcConn.getConnectionId(), payload.toString());
                try {
                    Object parseBody = GrpcUtils.parse(payload);
                    final Request request = (Request) parseBody;
                    if (request != null) {
                        
                        try {
                            if (request instanceof SetupAckRequest) {
                                // there is no connection ready this time
                                setupRequestHandler.requestReply(request, null);
                                return;
                            }
                            Response response = handleServerRequest(request);
                            if (response != null) {
                                response.setRequestId(request.getRequestId());
                                sendResponse(response);
                            } else {
                                LOGGER.warn("[{}]Fail to process server request, ackId->{}", grpcConn.getConnectionId(),
                                        request.getRequestId());
                            }
                            
                        } catch (Exception e) {
                            LoggerUtils.printIfErrorEnabled(LOGGER, "[{}]Handle server request exception: {}",
                                    grpcConn.getConnectionId(), payload.toString(), e.getMessage());
                            Response errResponse = ErrorResponse
                                    .build(NacosException.CLIENT_ERROR, "Handle server request error");
                            errResponse.setRequestId(request.getRequestId());
                            sendResponse(errResponse);
                        }
                        
                    }
                    
                } catch (Exception e) {
                    
                    LoggerUtils.printIfErrorEnabled(LOGGER, "[{}]Error to process server push response: {}",
                            grpcConn.getConnectionId(), payload.getBody().getValue().toStringUtf8());
                    // remove and notify
                    recAbilityContext.release(null);
                }
            }
            
            @Override
            public void onError(Throwable throwable) {
                boolean isRunning = isRunning();
                boolean isAbandon = grpcConn.isAbandon();
                if (isRunning && !isAbandon) {
                    LoggerUtils.printIfErrorEnabled(LOGGER, "[{}]Request stream error, switch server,error={}",
                            grpcConn.getConnectionId(), throwable);
                    if (rpcClientStatus.compareAndSet(RpcClientStatus.RUNNING, RpcClientStatus.UNHEALTHY)) {
                        switchServerAsync();
                    }
                    
                } else {
                    LoggerUtils.printIfWarnEnabled(LOGGER, "[{}]Ignore error event,isRunning:{},isAbandon={}",
                            grpcConn.getConnectionId(), isRunning, isAbandon);
                }
                
            }
            
            @Override
            public void onCompleted() {
                boolean isRunning = isRunning();
                boolean isAbandon = grpcConn.isAbandon();
                if (isRunning && !isAbandon) {
                    LoggerUtils.printIfErrorEnabled(LOGGER, "[{}]Request stream onCompleted, switch server",
                            grpcConn.getConnectionId());
                    if (rpcClientStatus.compareAndSet(RpcClientStatus.RUNNING, RpcClientStatus.UNHEALTHY)) {
                        switchServerAsync();
                    }
                    
                } else {
                    LoggerUtils.printIfInfoEnabled(LOGGER, "[{}]Ignore complete event,isRunning:{},isAbandon={}",
                            grpcConn.getConnectionId(), isRunning, isAbandon);
                }
                
            }
        });
    }
    
    private void sendResponse(Response response) {
        try {
            ((GrpcConnection) this.currentConnection).sendResponse(response);
        } catch (Exception e) {
            LOGGER.error("[{}]Error to send ack response, ackId->{}", this.currentConnection.getConnectionId(),
                    response.getRequestId());
        }
    }
    
    @Override
    public Connection connectToServer(ServerInfo serverInfo) {
        // the newest connection id
        String connectionId = "";
        try {
            if (grpcExecutor == null) {
                this.grpcExecutor = createGrpcExecutor(serverInfo.getServerIp());
            }
            int port = serverInfo.getServerPort() + rpcPortOffset();
            ManagedChannel managedChannel = createNewManagedChannel(serverInfo.getServerIp(), port);
            RequestGrpc.RequestFutureStub newChannelStubTemp = createNewChannelStub(managedChannel);
            
            Response response = serverCheck(serverInfo.getServerIp(), port, newChannelStubTemp);
            if (!(response instanceof ServerCheckResponse)) {
                shuntDownChannel(managedChannel);
                return null;
            }
            // submit ability table as soon as possible
            // ability table will be null if server doesn't support ability table
            ServerCheckResponse serverCheckResponse = (ServerCheckResponse) response;
            connectionId = serverCheckResponse.getConnectionId();
            
            BiRequestStreamGrpc.BiRequestStreamStub biRequestStreamStub = BiRequestStreamGrpc
                    .newStub(newChannelStubTemp.getChannel());
            GrpcConnection grpcConn = new GrpcConnection(serverInfo, grpcExecutor);
            grpcConn.setConnectionId(connectionId);
            // if not supported, it will be false
            if (serverCheckResponse.isSupportAbilityNegotiation()) {
                // mark
                this.recAbilityContext.reset(grpcConn);
                // promise null if no abilities receive
                grpcConn.setAbilityTable(null);
            }
            
            //create stream request and bind connection event to this connection.
            StreamObserver<Payload> payloadStreamObserver = bindRequestStream(biRequestStreamStub, grpcConn);
            
            // stream observer to send response to server
            grpcConn.setPayloadStreamObserver(payloadStreamObserver);
            grpcConn.setGrpcFutureServiceStub(newChannelStubTemp);
            grpcConn.setChannel(managedChannel);
            //send a  setup request.
            ConnectionSetupRequest conSetupRequest = new ConnectionSetupRequest();
            conSetupRequest.setClientVersion(VersionUtils.getFullClientVersion());
            conSetupRequest.setLabels(super.getLabels());
            // set ability table
            conSetupRequest
                    .setAbilityTable(NacosAbilityManagerHolder.getInstance().getCurrentNodeAbilities(abilityMode()));
            conSetupRequest.setTenant(super.getTenant());
            grpcConn.sendRequest(conSetupRequest);
            // wait for response
            if (recAbilityContext.isNeedToSync()) {
                // try to wait for notify response
                recAbilityContext.await(this.clientConfig.capabilityNegotiationTimeout(), TimeUnit.MILLISECONDS);
                // if no server abilities receiving, then reconnect
                if (!recAbilityContext.check(grpcConn)) {
                    return null;
                }
            } else {
                // leave for adapting old version server
                // registration is considered successful by default after 100ms
                // wait to register connection setup
                Thread.sleep(100L);
            }
            return grpcConn;
        } catch (Exception e) {
            LOGGER.error("[{}]Fail to connect to server!,error={}", GrpcClient.this.getName(), e);
            // remove and notify
            recAbilityContext.release(null);
        }
        return null;
    }
    
    /**
     * ability mode: sdk client or cluster client.
     *
     * @return mode
     */
    protected abstract AbilityMode abilityMode();
    
    @Override
    protected void afterReset(ConnectResetRequest request) {
        recAbilityContext.release(null);
    }
    
    /**
     * This is for receiving server abilities.
     */
    static class RecAbilityContext {
        
        /**
         * connection waiting for server abilities.
         */
        private volatile Connection connection;
        
        /**
         * way to block client.
         */
        private volatile CountDownLatch blocker;
        
        private volatile boolean needToSync = false;
        
        public RecAbilityContext(Connection connection) {
            this.connection = connection;
            this.blocker = new CountDownLatch(1);
        }
        
        /**
         * whether to sync for ability table.
         *
         * @return whether to sync for ability table.
         */
        public boolean isNeedToSync() {
            return this.needToSync;
        }
        
        /**
         * reset with new connection which is waiting for ability table.
         *
         * @param connection new connection which is waiting for ability table.
         */
        public void reset(Connection connection) {
            this.connection = connection;
            this.blocker = new CountDownLatch(1);
            this.needToSync = true;
        }
        
        /**
         * notify sync by abilities.
         *
         * @param abilities abilities.
         */
        public void release(Map<String, Boolean> abilities) {
            if (this.connection != null) {
                this.connection.setAbilityTable(abilities);
                // avoid repeat setting
                this.connection = null;
            }
            if (this.blocker != null) {
                blocker.countDown();
            }
            this.needToSync = false;
        }
        
        /**
         * await for abilities.
         *
         * @param timeout timeout.
         * @param unit    unit.
         * @throws InterruptedException by blocker.
         */
        public void await(long timeout, TimeUnit unit) throws InterruptedException {
            if (this.blocker != null) {
                this.blocker.await(timeout, unit);
            }
            this.needToSync = false;
        }
        
        /**
         * check whether receive abilities.
         *
         * @param connection conn.
         * @return whether receive abilities.
         */
        public boolean check(Connection connection) {
            if (!connection.isAbilitiesSet()) {
                LOGGER.error(
                        "Client don't receive server abilities table even empty table but server supports ability negotiation."
                                + " You can check if it is need to adjust the timeout of ability negotiation by property: {}"
                                + " if always fail to connect.",
                        GrpcConstants.GRPC_CHANNEL_CAPABILITY_NEGOTIATION_TIMEOUT);
                connection.setAbandon(true);
                connection.close();
                return false;
            }
            return true;
        }
    }
    
    /**
     * Setup response handler.
     */
    class SetupRequestHandler implements ServerRequestHandler {
        
        private final RecAbilityContext abilityContext;
        
        public SetupRequestHandler(RecAbilityContext abilityContext) {
            this.abilityContext = abilityContext;
        }
        
        @Override
        public Response requestReply(Request request, Connection connection) {
            // if finish setup
            if (request instanceof SetupAckRequest) {
                SetupAckRequest setupAckRequest = (SetupAckRequest) request;
                // remove and count down
                recAbilityContext
                        .release(Optional.ofNullable(setupAckRequest.getAbilityTable()).orElse(new HashMap<>(0)));
                return new SetupAckResponse();
            }
            return null;
        }
    }
    
    private ManagedChannelBuilder buildChannel(String serverIp, int port, Optional<SslContext> sslContext) {
        if (sslContext.isPresent()) {
            return NettyChannelBuilder.forAddress(serverIp, port).negotiationType(NegotiationType.TLS)
                    .sslContext(sslContext.get());
            
        } else {
            return ManagedChannelBuilder.forAddress(serverIp, port).usePlaintext();
        }
    }
    
    private Optional<SslContext> buildSslContext() {
        
        RpcClientTlsConfig tlsConfig = clientConfig.tlsConfig();
        if (!tlsConfig.getEnableTls()) {
            return Optional.empty();
        }
        try {
            SslContextBuilder builder = GrpcSslContexts.forClient();
            if (StringUtils.isNotBlank(tlsConfig.getSslProvider())) {
                builder.sslProvider(TlsTypeResolve.getSslProvider(tlsConfig.getSslProvider()));
            }
            
            if (StringUtils.isNotBlank(tlsConfig.getProtocols())) {
                builder.protocols(tlsConfig.getProtocols().split(","));
            }
            if (StringUtils.isNotBlank(tlsConfig.getCiphers())) {
                builder.ciphers(Arrays.asList(tlsConfig.getCiphers().split(",")));
            }
            if (tlsConfig.getTrustAll()) {
                builder.trustManager(InsecureTrustManagerFactory.INSTANCE);
            } else {
                if (StringUtils.isBlank(tlsConfig.getTrustCollectionCertFile())) {
                    throw new IllegalArgumentException("trustCollectionCertFile must be not null");
                }
                Resource resource = resourceLoader.getResource(tlsConfig.getTrustCollectionCertFile());
                builder.trustManager(resource.getInputStream());
            }
            
            if (tlsConfig.getMutualAuthEnable()) {
                if (StringUtils.isBlank(tlsConfig.getCertChainFile()) || StringUtils
                        .isBlank(tlsConfig.getCertPrivateKey())) {
                    throw new IllegalArgumentException("client certChainFile or certPrivateKey must be not null");
                }
                Resource certChainFile = resourceLoader.getResource(tlsConfig.getCertChainFile());
                Resource privateKey = resourceLoader.getResource(tlsConfig.getCertPrivateKey());
                builder.keyManager(certChainFile.getInputStream(), privateKey.getInputStream(),
                        tlsConfig.getCertPrivateKeyPassword());
            }
            return Optional.of(builder.build());
        } catch (Exception e) {
            throw new RuntimeException("Unable to build SslContext", e);
        }
    }
}



