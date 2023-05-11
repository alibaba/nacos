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

package com.alibaba.nacos.common.remote.client;

import com.alibaba.nacos.api.ability.ClientAbilities;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.remote.RequestCallBack;
import com.alibaba.nacos.api.remote.RequestFuture;
import com.alibaba.nacos.api.remote.request.ClientDetectionRequest;
import com.alibaba.nacos.api.remote.request.ConnectResetRequest;
import com.alibaba.nacos.api.remote.request.HealthCheckRequest;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.response.ClientDetectionResponse;
import com.alibaba.nacos.api.remote.response.ConnectResetResponse;
import com.alibaba.nacos.api.remote.response.ErrorResponse;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.common.lifecycle.Closeable;
import com.alibaba.nacos.common.packagescan.resource.DefaultResourceLoader;
import com.alibaba.nacos.common.packagescan.resource.ResourceLoader;
import com.alibaba.nacos.common.remote.ConnectionType;
import com.alibaba.nacos.common.remote.PayloadRegistry;
import com.alibaba.nacos.common.utils.CollectionUtils;
import com.alibaba.nacos.common.utils.InternetAddressUtil;
import com.alibaba.nacos.common.utils.LoggerUtils;
import com.alibaba.nacos.common.utils.NumberUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.alibaba.nacos.api.exception.NacosException.SERVER_ERROR;

/**
 * abstract remote client to connect to server.
 *
 * @author liuzunfei
 * @version $Id: RpcClient.java, v 0.1 2020年07月13日 9:15 PM liuzunfei Exp $
 */
@SuppressWarnings("PMD.AbstractClassShouldStartWithAbstractNamingRule")
public abstract class RpcClient implements Closeable {
    
    private static final Logger LOGGER = LoggerFactory.getLogger("com.alibaba.nacos.common.remote.client");
    
    private ServerListFactory serverListFactory;
    
    protected BlockingQueue<ConnectionEvent> eventLinkedBlockingQueue = new LinkedBlockingQueue<>();
    
    protected volatile AtomicReference<RpcClientStatus> rpcClientStatus = new AtomicReference<>(
            RpcClientStatus.WAIT_INIT);
    
    protected ScheduledExecutorService clientEventExecutor;
    
    private final BlockingQueue<ReconnectContext> reconnectionSignal = new ArrayBlockingQueue<>(1);
    
    protected volatile Connection currentConnection;
    
    private String tenant;
    
    protected ClientAbilities clientAbilities;
    
    private long lastActiveTimeStamp = System.currentTimeMillis();
    
    /**
     * listener called where connection's status changed.
     */
    protected List<ConnectionEventListener> connectionEventListeners = new ArrayList<>();
    
    /**
     * handlers to process server push request.
     */
    protected List<ServerRequestHandler> serverRequestHandlers = new ArrayList<>();
    
    private static final Pattern EXCLUDE_PROTOCOL_PATTERN = Pattern.compile("(?<=\\w{1,5}://)(.*)");
    
    protected RpcClientConfig rpcClientConfig;

    protected final ResourceLoader resourceLoader = new DefaultResourceLoader();

    static {
        PayloadRegistry.init();
    }
    
    public RpcClient(RpcClientConfig rpcClientConfig) {
        this(rpcClientConfig, null);
    }
    
    public RpcClient(RpcClientConfig rpcClientConfig, ServerListFactory serverListFactory) {
        this.rpcClientConfig = rpcClientConfig;
        this.serverListFactory = serverListFactory;
        init();
    }
    
    protected void init() {
        if (this.serverListFactory != null) {
            rpcClientStatus.compareAndSet(RpcClientStatus.WAIT_INIT, RpcClientStatus.INITIALIZED);
            LoggerUtils.printIfInfoEnabled(LOGGER, "RpcClient init in constructor, ServerListFactory = {}",
                    serverListFactory.getClass().getName());
        }
    }
    
    public Map<String, String> labels() {
        return Collections.unmodifiableMap(rpcClientConfig.labels());
    }
    
    /**
     * init client abilities.
     *
     * @param clientAbilities clientAbilities.
     */
    public RpcClient clientAbilities(ClientAbilities clientAbilities) {
        this.clientAbilities = clientAbilities;
        return this;
    }
    
    /**
     * init server list factory. only can init once.
     *
     * @param serverListFactory serverListFactory
     */
    public RpcClient serverListFactory(ServerListFactory serverListFactory) {
        if (!isWaitInitiated()) {
            return this;
        }
        this.serverListFactory = serverListFactory;
        rpcClientStatus.compareAndSet(RpcClientStatus.WAIT_INIT, RpcClientStatus.INITIALIZED);
        
        LoggerUtils.printIfInfoEnabled(LOGGER, "[{}] RpcClient init, ServerListFactory = {}", rpcClientConfig.name(),
                serverListFactory.getClass().getName());
        return this;
    }
    
    /**
     * Notify when client disconnected.
     */
    protected void notifyDisConnected() {
        if (connectionEventListeners.isEmpty()) {
            return;
        }
        LoggerUtils.printIfInfoEnabled(LOGGER, "[{}] Notify disconnected event to listeners", rpcClientConfig.name());
        for (ConnectionEventListener connectionEventListener : connectionEventListeners) {
            try {
                connectionEventListener.onDisConnect();
            } catch (Throwable throwable) {
                LoggerUtils.printIfErrorEnabled(LOGGER, "[{}] Notify disconnect listener error, listener = {}",
                        rpcClientConfig.name(), connectionEventListener.getClass().getName());
            }
        }
    }
    
    /**
     * Notify when client new connected.
     */
    protected void notifyConnected() {
        if (connectionEventListeners.isEmpty()) {
            return;
        }
        LoggerUtils.printIfInfoEnabled(LOGGER, "[{}] Notify connected event to listeners.", rpcClientConfig.name());
        for (ConnectionEventListener connectionEventListener : connectionEventListeners) {
            try {
                connectionEventListener.onConnected();
            } catch (Throwable throwable) {
                LoggerUtils.printIfErrorEnabled(LOGGER, "[{}] Notify connect listener error, listener = {}",
                        rpcClientConfig.name(), connectionEventListener.getClass().getName());
            }
        }
    }
    
    /**
     * check is this client is initiated.
     *
     * @return is wait initiated or not.
     */
    public boolean isWaitInitiated() {
        return this.rpcClientStatus.get() == RpcClientStatus.WAIT_INIT;
    }
    
    /**
     * check is this client is running.
     *
     * @return is running or not.
     */
    public boolean isRunning() {
        return this.rpcClientStatus.get() == RpcClientStatus.RUNNING;
    }
    
    /**
     * check is this client is shutdown.
     *
     * @return is shutdown or not.
     */
    public boolean isShutdown() {
        return this.rpcClientStatus.get() == RpcClientStatus.SHUTDOWN;
    }
    
    /**
     * check if current connected server is in server list, if not switch server.
     */
    public void onServerListChange() {
        if (currentConnection != null && currentConnection.serverInfo != null) {
            ServerInfo serverInfo = currentConnection.serverInfo;
            boolean found = false;
            for (String serverAddress : serverListFactory.getServerList()) {
                if (resolveServerInfo(serverAddress).getAddress().equalsIgnoreCase(serverInfo.getAddress())) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                LoggerUtils.printIfInfoEnabled(LOGGER,
                        "Current connected server {} is not in latest server list, switch switchServerAsync",
                        serverInfo.getAddress());
                switchServerAsync();
            }
            
        }
    }
    
    /**
     * Start this client.
     */
    public final void start() throws NacosException {
        
        boolean success = rpcClientStatus.compareAndSet(RpcClientStatus.INITIALIZED, RpcClientStatus.STARTING);
        if (!success) {
            return;
        }
        
        clientEventExecutor = new ScheduledThreadPoolExecutor(2, r -> {
            Thread t = new Thread(r);
            t.setName("com.alibaba.nacos.client.remote.worker");
            t.setDaemon(true);
            return t;
        });
        
        // connection event consumer.
        clientEventExecutor.submit(() -> {
            while (!clientEventExecutor.isTerminated() && !clientEventExecutor.isShutdown()) {
                ConnectionEvent take;
                try {
                    take = eventLinkedBlockingQueue.take();
                    if (take.isConnected()) {
                        notifyConnected();
                    } else if (take.isDisConnected()) {
                        notifyDisConnected();
                    }
                } catch (Throwable e) {
                    // Do nothing
                }
            }
        });
        
        clientEventExecutor.submit(() -> {
            while (true) {
                try {
                    if (isShutdown()) {
                        break;
                    }
                    ReconnectContext reconnectContext = reconnectionSignal
                            .poll(rpcClientConfig.connectionKeepAlive(), TimeUnit.MILLISECONDS);
                    if (reconnectContext == null) {
                        // check alive time.
                        if (System.currentTimeMillis() - lastActiveTimeStamp >= rpcClientConfig.connectionKeepAlive()) {
                            boolean isHealthy = healthCheck();
                            if (!isHealthy) {
                                if (currentConnection == null) {
                                    continue;
                                }
                                LoggerUtils.printIfInfoEnabled(LOGGER,
                                        "[{}] Server healthy check fail, currentConnection = {}",
                                        rpcClientConfig.name(), currentConnection.getConnectionId());
                                
                                RpcClientStatus rpcClientStatus = RpcClient.this.rpcClientStatus.get();
                                if (RpcClientStatus.SHUTDOWN.equals(rpcClientStatus)) {
                                    break;
                                }
                                
                                boolean statusFLowSuccess = RpcClient.this.rpcClientStatus
                                        .compareAndSet(rpcClientStatus, RpcClientStatus.UNHEALTHY);
                                if (statusFLowSuccess) {
                                    reconnectContext = new ReconnectContext(null, false);
                                } else {
                                    continue;
                                }
                                
                            } else {
                                lastActiveTimeStamp = System.currentTimeMillis();
                                continue;
                            }
                        } else {
                            continue;
                        }
                        
                    }
                    
                    if (reconnectContext.serverInfo != null) {
                        // clear recommend server if server is not in server list.
                        boolean serverExist = false;
                        for (String server : getServerListFactory().getServerList()) {
                            ServerInfo serverInfo = resolveServerInfo(server);
                            if (serverInfo.getServerIp().equals(reconnectContext.serverInfo.getServerIp())) {
                                serverExist = true;
                                reconnectContext.serverInfo.serverPort = serverInfo.serverPort;
                                break;
                            }
                        }
                        if (!serverExist) {
                            LoggerUtils.printIfInfoEnabled(LOGGER,
                                    "[{}] Recommend server is not in server list, ignore recommend server {}",
                                    rpcClientConfig.name(), reconnectContext.serverInfo.getAddress());
                            
                            reconnectContext.serverInfo = null;
                            
                        }
                    }
                    reconnect(reconnectContext.serverInfo, reconnectContext.onRequestFail);
                } catch (Throwable throwable) {
                    // Do nothing
                }
            }
        });
        
        // connect to server, try to connect to server sync retryTimes times, async starting if failed.
        Connection connectToServer = null;
        rpcClientStatus.set(RpcClientStatus.STARTING);
        
        int startUpRetryTimes = rpcClientConfig.retryTimes();
        while (startUpRetryTimes > 0 && connectToServer == null) {
            try {
                startUpRetryTimes--;
                ServerInfo serverInfo = nextRpcServer();
                
                LoggerUtils.printIfInfoEnabled(LOGGER, "[{}] Try to connect to server on start up, server: {}",
                        rpcClientConfig.name(), serverInfo);
                
                connectToServer = connectToServer(serverInfo);
            } catch (Throwable e) {
                LoggerUtils.printIfWarnEnabled(LOGGER,
                        "[{}] Fail to connect to server on start up, error message = {}, start up retry times left: {}",
                        rpcClientConfig.name(), e.getMessage(), startUpRetryTimes, e);
            }
            
        }
        
        if (connectToServer != null) {
            LoggerUtils
                    .printIfInfoEnabled(LOGGER, "[{}] Success to connect to server [{}] on start up, connectionId = {}",
                            rpcClientConfig.name(), connectToServer.serverInfo.getAddress(),
                            connectToServer.getConnectionId());
            this.currentConnection = connectToServer;
            rpcClientStatus.set(RpcClientStatus.RUNNING);
            eventLinkedBlockingQueue.offer(new ConnectionEvent(ConnectionEvent.CONNECTED));
        } else {
            switchServerAsync();
        }
        
        registerServerRequestHandler(new ConnectResetRequestHandler());
        
        // register client detection request.
        registerServerRequestHandler(request -> {
            if (request instanceof ClientDetectionRequest) {
                return new ClientDetectionResponse();
            }
            
            return null;
        });
        
    }
    
    class ConnectResetRequestHandler implements ServerRequestHandler {
        
        @Override
        public Response requestReply(Request request) {
            
            if (request instanceof ConnectResetRequest) {
                
                try {
                    synchronized (RpcClient.this) {
                        if (isRunning()) {
                            ConnectResetRequest connectResetRequest = (ConnectResetRequest) request;
                            if (StringUtils.isNotBlank(connectResetRequest.getServerIp())) {
                                ServerInfo serverInfo = resolveServerInfo(
                                        connectResetRequest.getServerIp() + Constants.COLON + connectResetRequest
                                                .getServerPort());
                                switchServerAsync(serverInfo, false);
                            } else {
                                switchServerAsync();
                            }
                        }
                    }
                } catch (Exception e) {
                    LoggerUtils.printIfErrorEnabled(LOGGER, "[{}] Switch server error, {}", rpcClientConfig.name(), e);
                }
                return new ConnectResetResponse();
            }
            return null;
        }
    }
    
    @Override
    public void shutdown() throws NacosException {
        LOGGER.info("Shutdown rpc client, set status to shutdown");
        rpcClientStatus.set(RpcClientStatus.SHUTDOWN);
        LOGGER.info("Shutdown client event executor " + clientEventExecutor);
        if (clientEventExecutor != null) {
            clientEventExecutor.shutdownNow();
        }
        closeConnection(currentConnection);
    }
    
    private boolean healthCheck() {
        HealthCheckRequest healthCheckRequest = new HealthCheckRequest();
        if (this.currentConnection == null) {
            return false;
        }
        int reTryTimes = rpcClientConfig.healthCheckRetryTimes();
        while (reTryTimes >= 0) {
            reTryTimes--;
            try {
                Response response = this.currentConnection
                        .request(healthCheckRequest, rpcClientConfig.healthCheckTimeOut());
                // not only check server is ok, also check connection is register.
                return response != null && response.isSuccess();
            } catch (NacosException e) {
                // ignore
            }
        }
        return false;
    }
    
    public void switchServerAsyncOnRequestFail() {
        switchServerAsync(null, true);
    }
    
    public void switchServerAsync() {
        switchServerAsync(null, false);
    }
    
    protected void switchServerAsync(final ServerInfo recommendServerInfo, boolean onRequestFail) {
        reconnectionSignal.offer(new ReconnectContext(recommendServerInfo, onRequestFail));
    }
    
    /**
     * switch server .
     */
    protected void reconnect(final ServerInfo recommendServerInfo, boolean onRequestFail) {
        
        try {
            
            AtomicReference<ServerInfo> recommendServer = new AtomicReference<>(recommendServerInfo);
            if (onRequestFail && healthCheck()) {
                LoggerUtils.printIfInfoEnabled(LOGGER, "[{}] Server check success, currentServer is {} ",
                        rpcClientConfig.name(), currentConnection.serverInfo.getAddress());
                rpcClientStatus.set(RpcClientStatus.RUNNING);
                return;
            }
            
            LoggerUtils.printIfInfoEnabled(LOGGER, "[{}] Try to reconnect to a new server, server is {}",
                    rpcClientConfig.name(), recommendServerInfo == null ? " not appointed, will choose a random server."
                            : (recommendServerInfo.getAddress() + ", will try it once."));
            
            // loop until start client success.
            boolean switchSuccess = false;
            
            int reConnectTimes = 0;
            int retryTurns = 0;
            Exception lastException;
            while (!switchSuccess && !isShutdown()) {
                
                // 1.get a new server
                ServerInfo serverInfo = null;
                try {
                    serverInfo = recommendServer.get() == null ? nextRpcServer() : recommendServer.get();
                    // 2.create a new channel to new server
                    Connection connectionNew = connectToServer(serverInfo);
                    if (connectionNew != null) {
                        LoggerUtils
                                .printIfInfoEnabled(LOGGER, "[{}] Success to connect a server [{}], connectionId = {}",
                                        rpcClientConfig.name(), serverInfo.getAddress(),
                                        connectionNew.getConnectionId());
                        // successfully create a new connect.
                        if (currentConnection != null) {
                            LoggerUtils.printIfInfoEnabled(LOGGER,
                                    "[{}] Abandon prev connection, server is {}, connectionId is {}",
                                    rpcClientConfig.name(), currentConnection.serverInfo.getAddress(),
                                    currentConnection.getConnectionId());
                            // set current connection to enable connection event.
                            currentConnection.setAbandon(true);
                            closeConnection(currentConnection);
                        }
                        currentConnection = connectionNew;
                        rpcClientStatus.set(RpcClientStatus.RUNNING);
                        switchSuccess = true;
                        eventLinkedBlockingQueue.add(new ConnectionEvent(ConnectionEvent.CONNECTED));
                        return;
                    }
                    
                    // close connection if client is already shutdown.
                    if (isShutdown()) {
                        closeConnection(currentConnection);
                    }
                    
                    lastException = null;
                    
                } catch (Exception e) {
                    lastException = e;
                } finally {
                    recommendServer.set(null);
                }
                
                if (CollectionUtils.isEmpty(RpcClient.this.serverListFactory.getServerList())) {
                    throw new Exception("server list is empty");
                }
                
                if (reConnectTimes > 0
                        && reConnectTimes % RpcClient.this.serverListFactory.getServerList().size() == 0) {
                    LoggerUtils.printIfInfoEnabled(LOGGER,
                            "[{}] Fail to connect server, after trying {} times, last try server is {}, error = {}",
                            rpcClientConfig.name(), reConnectTimes, serverInfo,
                            lastException == null ? "unknown" : lastException);
                    if (Integer.MAX_VALUE == retryTurns) {
                        retryTurns = 50;
                    } else {
                        retryTurns++;
                    }
                }
                
                reConnectTimes++;
                
                try {
                    // sleep x milliseconds to switch next server.
                    if (!isRunning()) {
                        // first round, try servers at a delay 100ms;second round, 200ms; max delays 5s. to be reconsidered.
                        Thread.sleep(Math.min(retryTurns + 1, 50) * 100L);
                    }
                } catch (InterruptedException e) {
                    // Do nothing.
                    // set the interrupted flag
                    Thread.currentThread().interrupt();
                }
            }
            
            if (isShutdown()) {
                LoggerUtils.printIfInfoEnabled(LOGGER, "[{}] Client is shutdown, stop reconnect to server",
                        rpcClientConfig.name());
            }
            
        } catch (Exception e) {
            LoggerUtils
                    .printIfWarnEnabled(LOGGER, "[{}] Fail to reconnect to server, error is {}", rpcClientConfig.name(),
                            e);
        }
    }
    
    private void closeConnection(Connection connection) {
        if (connection != null) {
            LOGGER.info("Close current connection " + connection.getConnectionId());
            connection.close();
            eventLinkedBlockingQueue.add(new ConnectionEvent(ConnectionEvent.DISCONNECTED));
        }
    }
    
    /**
     * get connection type of this client.
     *
     * @return ConnectionType.
     */
    public abstract ConnectionType getConnectionType();
    
    /**
     * increase offset of the nacos server port for the rpc server port.
     *
     * @return rpc port offset
     */
    public abstract int rpcPortOffset();
    
    /**
     * get current server.
     *
     * @return server info.
     */
    public ServerInfo getCurrentServer() {
        if (this.currentConnection != null) {
            return currentConnection.serverInfo;
        }
        return null;
    }
    
    /**
     * send request.
     *
     * @param request request.
     * @return response from server.
     */
    public Response request(Request request) throws NacosException {
        return request(request, rpcClientConfig.timeOutMills());
    }
    
    /**
     * send request.
     *
     * @param request request.
     * @return response from server.
     */
    public Response request(Request request, long timeoutMills) throws NacosException {
        int retryTimes = 0;
        Response response;
        Throwable exceptionThrow = null;
        long start = System.currentTimeMillis();
        while (retryTimes < rpcClientConfig.retryTimes() && System.currentTimeMillis() < timeoutMills + start) {
            boolean waitReconnect = false;
            try {
                if (this.currentConnection == null || !isRunning()) {
                    waitReconnect = true;
                    throw new NacosException(NacosException.CLIENT_DISCONNECT,
                            "Client not connected, current status:" + rpcClientStatus.get());
                }
                response = this.currentConnection.request(request, timeoutMills);
                if (response == null) {
                    throw new NacosException(SERVER_ERROR, "Unknown Exception.");
                }
                if (response instanceof ErrorResponse) {
                    if (response.getErrorCode() == NacosException.UN_REGISTER) {
                        synchronized (this) {
                            waitReconnect = true;
                            if (rpcClientStatus.compareAndSet(RpcClientStatus.RUNNING, RpcClientStatus.UNHEALTHY)) {
                                LoggerUtils.printIfErrorEnabled(LOGGER,
                                        "Connection is unregistered, switch server, connectionId = {}, request = {}",
                                        currentConnection.getConnectionId(), request.getClass().getSimpleName());
                                switchServerAsync();
                            }
                        }
                        
                    }
                    throw new NacosException(response.getErrorCode(), response.getMessage());
                }
                // return response.
                lastActiveTimeStamp = System.currentTimeMillis();
                return response;
                
            } catch (Throwable e) {
                if (waitReconnect) {
                    try {
                        // wait client to reconnect.
                        Thread.sleep(Math.min(100, timeoutMills / 3));
                    } catch (Exception exception) {
                        // Do nothing.
                    }
                }
                
                LoggerUtils.printIfErrorEnabled(LOGGER,
                        "Send request fail, request = {}, retryTimes = {}, errorMessage = {}", request, retryTimes,
                        e.getMessage());
                
                exceptionThrow = e;
                
            }
            retryTimes++;
            
        }
        
        if (rpcClientStatus.compareAndSet(RpcClientStatus.RUNNING, RpcClientStatus.UNHEALTHY)) {
            switchServerAsyncOnRequestFail();
        }
        
        if (exceptionThrow != null) {
            throw (exceptionThrow instanceof NacosException) ? (NacosException) exceptionThrow
                    : new NacosException(SERVER_ERROR, exceptionThrow);
        } else {
            throw new NacosException(SERVER_ERROR, "Request fail, unknown Error");
        }
    }
    
    /**
     * send async request.
     *
     * @param request request.
     */
    public void asyncRequest(Request request, RequestCallBack callback) throws NacosException {
        int retryTimes = 0;
    
        Throwable exceptionToThrow = null;
        long start = System.currentTimeMillis();
        while (retryTimes < rpcClientConfig.retryTimes() && System.currentTimeMillis() < start + callback
                .getTimeout()) {
            boolean waitReconnect = false;
            try {
                if (this.currentConnection == null || !isRunning()) {
                    waitReconnect = true;
                    throw new NacosException(NacosException.CLIENT_INVALID_PARAM, "Client not connected.");
                }
                this.currentConnection.asyncRequest(request, callback);
                return;
            } catch (Throwable e) {
                if (waitReconnect) {
                    try {
                        // wait client to reconnect.
                        Thread.sleep(Math.min(100, callback.getTimeout() / 3));
                    } catch (Exception exception) {
                        // Do nothing.
                    }
                }
                LoggerUtils.printIfErrorEnabled(LOGGER,
                        "[{}] Send request fail, request = {}, retryTimes = {}, errorMessage = {}",
                        rpcClientConfig.name(), request, retryTimes, e.getMessage());
                exceptionToThrow = e;
                
            }
            retryTimes++;
            
        }
        
        if (rpcClientStatus.compareAndSet(RpcClientStatus.RUNNING, RpcClientStatus.UNHEALTHY)) {
            switchServerAsyncOnRequestFail();
        }
        if (exceptionToThrow != null) {
            throw (exceptionToThrow instanceof NacosException) ? (NacosException) exceptionToThrow
                    : new NacosException(SERVER_ERROR, exceptionToThrow);
        } else {
            throw new NacosException(SERVER_ERROR, "AsyncRequest fail, unknown error");
        }
    }
    
    /**
     * send async request.
     *
     * @param request request.
     * @return request future.
     */
    public RequestFuture requestFuture(Request request) throws NacosException {
        int retryTimes = 0;
        long start = System.currentTimeMillis();
        Exception exceptionToThrow = null;
        while (retryTimes < rpcClientConfig.retryTimes() && System.currentTimeMillis() < start + rpcClientConfig
                .timeOutMills()) {
            boolean waitReconnect = false;
            try {
                if (this.currentConnection == null || !isRunning()) {
                    waitReconnect = true;
                    throw new NacosException(NacosException.CLIENT_INVALID_PARAM, "Client not connected.");
                }
                return this.currentConnection.requestFuture(request);
            } catch (Exception e) {
                if (waitReconnect) {
                    try {
                        // wait client to reconnect.
                        Thread.sleep(100L);
                    } catch (Exception exception) {
                        // Do nothing.
                    }
                }
                LoggerUtils.printIfErrorEnabled(LOGGER,
                        "[{}] Send request fail, request = {}, retryTimes = {}, errorMessage = {}",
                        rpcClientConfig.name(), request, retryTimes, e.getMessage());
                exceptionToThrow = e;
                
            }
            retryTimes++;
        }
        
        if (rpcClientStatus.compareAndSet(RpcClientStatus.RUNNING, RpcClientStatus.UNHEALTHY)) {
            switchServerAsyncOnRequestFail();
        }
        
        if (exceptionToThrow != null) {
            throw (exceptionToThrow instanceof NacosException) ? (NacosException) exceptionToThrow
                    : new NacosException(SERVER_ERROR, exceptionToThrow);
        } else {
            throw new NacosException(SERVER_ERROR, "Request future fail, unknown error");
        }
        
    }
    
    /**
     * connect to server.
     *
     * @param serverInfo server address to connect.
     * @return return connection when successfully connect to server, or null if failed.
     * @throws Exception exception when fail to connect to server.
     */
    public abstract Connection connectToServer(ServerInfo serverInfo) throws Exception;
    
    /**
     * handle server request.
     *
     * @param request request.
     * @return response.
     */
    protected Response handleServerRequest(final Request request) {
        
        LoggerUtils.printIfInfoEnabled(LOGGER, "[{}] Receive server push request, request = {}, requestId = {}",
                rpcClientConfig.name(), request.getClass().getSimpleName(), request.getRequestId());
        lastActiveTimeStamp = System.currentTimeMillis();
        for (ServerRequestHandler serverRequestHandler : serverRequestHandlers) {
            try {
                Response response = serverRequestHandler.requestReply(request);
                
                if (response != null) {
                    LoggerUtils.printIfInfoEnabled(LOGGER, "[{}] Ack server push request, request = {}, requestId = {}",
                            rpcClientConfig.name(), request.getClass().getSimpleName(), request.getRequestId());
                    return response;
                }
            } catch (Exception e) {
                LoggerUtils.printIfInfoEnabled(LOGGER, "[{}] HandleServerRequest:{}, errorMessage = {}",
                        rpcClientConfig.name(), serverRequestHandler.getClass().getName(), e.getMessage());
            }
            
        }
        return null;
    }
    
    /**
     * Register connection handler. Will be notified when inner connection's state changed.
     *
     * @param connectionEventListener connectionEventListener
     */
    public synchronized void registerConnectionListener(ConnectionEventListener connectionEventListener) {
        
        LoggerUtils.printIfInfoEnabled(LOGGER, "[{}] Registry connection listener to current client:{}",
                rpcClientConfig.name(), connectionEventListener.getClass().getName());
        this.connectionEventListeners.add(connectionEventListener);
    }
    
    /**
     * Register serverRequestHandler, the handler will handle the request from server side.
     *
     * @param serverRequestHandler serverRequestHandler
     */
    public synchronized void registerServerRequestHandler(ServerRequestHandler serverRequestHandler) {
        LoggerUtils.printIfInfoEnabled(LOGGER, "[{}] Register server push request handler:{}", rpcClientConfig.name(),
                serverRequestHandler.getClass().getName());
        
        this.serverRequestHandlers.add(serverRequestHandler);
    }
    
    /**
     * Getter method for property <tt>name</tt>.
     *
     * @return property value of name
     */
    public String getName() {
        return rpcClientConfig.name();
    }
    
    /**
     * Getter method for property <tt>serverListFactory</tt>.
     *
     * @return property value of serverListFactory
     */
    public ServerListFactory getServerListFactory() {
        return serverListFactory;
    }
    
    protected ServerInfo nextRpcServer() {
        String serverAddress = getServerListFactory().genNextServer();
        return resolveServerInfo(serverAddress);
    }
    
    protected ServerInfo currentRpcServer() {
        String serverAddress = getServerListFactory().getCurrentServer();
        return resolveServerInfo(serverAddress);
    }
    
    /**
     * resolve server info.
     *
     * @param serverAddress address.
     * @return
     */
    @SuppressWarnings("PMD.UndefineMagicConstantRule")
    private ServerInfo resolveServerInfo(String serverAddress) {
        Matcher matcher = EXCLUDE_PROTOCOL_PATTERN.matcher(serverAddress);
        if (matcher.find()) {
            serverAddress = matcher.group(1);
        }
        String[] ipPortTuple = InternetAddressUtil.splitIPPortStr(serverAddress);
        int defaultPort = Integer.parseInt(System.getProperty("nacos.server.port", "8848"));
        String serverPort = CollectionUtils.getOrDefault(ipPortTuple, 1, Integer.toString(defaultPort));
        
        return new ServerInfo(ipPortTuple[0], NumberUtils.toInt(serverPort, defaultPort));
    }
    
    public static class ServerInfo {
        
        protected String serverIp;
        
        protected int serverPort;
        
        public ServerInfo() {
        
        }
        
        public ServerInfo(String serverIp, int serverPort) {
            this.serverPort = serverPort;
            this.serverIp = serverIp;
        }
        
        /**
         * get address, ip:port.
         *
         * @return address.
         */
        public String getAddress() {
            return serverIp + Constants.COLON + serverPort;
        }
        
        /**
         * Setter method for property <tt>serverIp</tt>.
         *
         * @param serverIp value to be assigned to property serverIp
         */
        public void setServerIp(String serverIp) {
            this.serverIp = serverIp;
        }
        
        /**
         * Setter method for property <tt>serverPort</tt>.
         *
         * @param serverPort value to be assigned to property serverPort
         */
        public void setServerPort(int serverPort) {
            this.serverPort = serverPort;
        }
        
        /**
         * Getter method for property <tt>serverIp</tt>.
         *
         * @return property value of serverIp
         */
        public String getServerIp() {
            return serverIp;
        }
        
        /**
         * Getter method for property <tt>serverPort</tt>.
         *
         * @return property value of serverPort
         */
        public int getServerPort() {
            return serverPort;
        }
        
        @Override
        public String toString() {
            return "{serverIp = '" + serverIp + '\'' + ", server main port = " + serverPort + '}';
        }
    }
    
    public class ConnectionEvent {
        
        public static final int CONNECTED = 1;
        
        public static final int DISCONNECTED = 0;
        
        int eventType;
        
        public ConnectionEvent(int eventType) {
            this.eventType = eventType;
        }
        
        public boolean isConnected() {
            return eventType == CONNECTED;
        }
        
        public boolean isDisConnected() {
            return eventType == DISCONNECTED;
        }
    }
    
    /**
     * Getter method for property <tt>labels</tt>.
     *
     * @return property value of labels
     */
    public Map<String, String> getLabels() {
        return rpcClientConfig.labels();
    }
    
    class ReconnectContext {
        
        public ReconnectContext(ServerInfo serverInfo, boolean onRequestFail) {
            this.onRequestFail = onRequestFail;
            this.serverInfo = serverInfo;
        }
        
        boolean onRequestFail;
        
        ServerInfo serverInfo;
    }
    
    public String getTenant() {
        return tenant;
    }
    
    public void setTenant(String tenant) {
        this.tenant = tenant;
    }
}
