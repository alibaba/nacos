/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.client.ai.watch;

import com.alibaba.nacos.api.ability.constant.AbilityKey;
import com.alibaba.nacos.api.ability.constant.AbilityStatus;
import com.alibaba.nacos.api.ai.model.rad.AgentWatchEventType;
import com.alibaba.nacos.api.ai.remote.request.AgentDiscoveryNotifyRequest;
import com.alibaba.nacos.api.ai.remote.response.AgentDiscoveryNotifyResponse;
import com.alibaba.nacos.api.ai.remote.response.AgentSubscribeRpcResponse;
import com.alibaba.nacos.api.ai.utils.AgentWatchLogUtils;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.client.ai.remote.AiGrpcClient;
import com.alibaba.nacos.client.utils.LogUtils;
import com.alibaba.nacos.common.executor.NameThreadFactory;
import com.alibaba.nacos.common.remote.client.Connection;
import com.alibaba.nacos.common.remote.client.ConnectionEventListener;
import com.alibaba.nacos.common.remote.client.ServerRequestHandler;
import com.alibaba.nacos.common.utils.LogRateLimiter;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;

/**
 * Connection-scoped gRPC binding for Agent Watch hints.
 *
 * <p>This transport owns only Wire Watch keys and reconnect resubscription. Canonical intent,
 * complete discovery snapshots, fingerprints, and user listeners remain in
 * {@link AgentWatchManager}.</p>
 *
 * @author Nacos
 */
final class GrpcAgentWatchTransport
    implements AgentWatchTransport, ConnectionEventListener, ServerRequestHandler {
    
    private static final Logger LOGGER = LogUtils.logger(GrpcAgentWatchTransport.class);
    
    private static final LogRateLimiter WARN_LOG_LIMITER = new LogRateLimiter(60000L);
    
    private final AiGrpcClient client;
    
    private final ExecutorService reconnectExecutor;
    
    private final Map<String, WireWatch> watchesByClientId =
        new LinkedHashMap<String, WireWatch>();
    
    private final Map<String, WireWatch> watchesByWireKey =
        new HashMap<String, WireWatch>();
    
    private WireLifecycleListener lifecycleListener;
    
    private String connectionId;
    
    private boolean available;
    
    private boolean closed;
    
    GrpcAgentWatchTransport(AiGrpcClient client) {
        this(client, new ScheduledThreadPoolExecutor(1,
            new NameThreadFactory("com.alibaba.nacos.client.ai.agent.watch.grpc")));
    }
    
    GrpcAgentWatchTransport(AiGrpcClient client, ExecutorService reconnectExecutor) {
        this.client = client;
        this.reconnectExecutor = reconnectExecutor;
        client.registerConnectionListener(this);
        client.registerServerRequestHandler(this);
    }
    
    synchronized void setLifecycleListener(WireLifecycleListener lifecycleListener) {
        this.lifecycleListener = lifecycleListener;
    }
    
    synchronized boolean isAvailable() {
        return !closed && available && client.isAgentWatchAvailable()
            && connectionId != null && connectionId.equals(client.getCurrentConnectionId());
    }
    
    @Override
    public void start(AgentWatchRegistration registration,
        AgentWatchTransportCallback callback) throws NacosException {
        WireWatch watch;
        String expectedConnectionId;
        synchronized (this) {
            ensureAvailable();
            WireWatch existing = watchesByClientId.get(registration.getClientWatchId());
            if (existing != null) {
                existing.registration = registration;
                return;
            }
            watch = new WireWatch(registration, callback);
            watchesByClientId.put(registration.getClientWatchId(), watch);
            expectedConnectionId = connectionId;
        }
        try {
            subscribe(watch, expectedConnectionId);
        } catch (NacosException e) {
            removeWatchAndWire(watch);
            throw e;
        } catch (RuntimeException e) {
            removeWatchAndWire(watch);
            throw e;
        }
    }
    
    @Override
    public synchronized void update(AgentWatchRegistration registration) {
        WireWatch watch = watchesByClientId.get(registration.getClientWatchId());
        if (watch != null) {
            watch.registration = registration;
        }
    }
    
    @Override
    public void stop(String clientWatchId) {
        final String watchKey;
        synchronized (this) {
            WireWatch removed = watchesByClientId.remove(clientWatchId);
            if (removed == null) {
                return;
            }
            watchKey = removed.watchKey;
            removeWireKey(removed);
        }
        if (watchKey != null) {
            LOGGER.info("[RAD-WATCH] Client gRPC Watch stopping: clientWatchId={}, watchKey={}",
                AgentWatchLogUtils.token(clientWatchId), AgentWatchLogUtils.token(watchKey));
            executeBestEffortUnsubscribe(watchKey);
        }
    }
    
    @Override
    public void onConnected(Connection connection) {
        String currentConnectionId = client.getCurrentConnectionId();
        if (currentConnectionId == null
            || !currentConnectionId.equals(connection.getConnectionId())) {
            return;
        }
        final List<WireWatch> reconnectWatches;
        final boolean watchAvailable =
            connection
                .getConnectionAbility(AbilityKey.SERVER_RAD_WATCH_V1) == AbilityStatus.SUPPORTED
                && client.isAgentWatchAvailable();
        synchronized (this) {
            if (closed) {
                return;
            }
            connectionId = connection.getConnectionId();
            available = watchAvailable;
            reconnectWatches = new ArrayList<WireWatch>(watchesByClientId.values());
            for (WireWatch watch : reconnectWatches) {
                removeWireKey(watch);
            }
        }
        executeReconnect(connection.getConnectionId(), reconnectWatches, watchAvailable);
    }
    
    @Override
    public synchronized void onDisConnect(Connection connection) {
        if (closed) {
            return;
        }
        if (connectionId != null && connectionId.equals(connection.getConnectionId())) {
            available = false;
            connectionId = null;
        }
        for (WireWatch watch : watchesByClientId.values()) {
            if (connection.getConnectionId().equals(watch.connectionId)) {
                removeWireKey(watch);
            }
        }
    }
    
    @Override
    public Response requestReply(Request request, Connection connection) {
        if (!(request instanceof AgentDiscoveryNotifyRequest)) {
            return null;
        }
        AgentDiscoveryNotifyRequest notifyRequest = (AgentDiscoveryNotifyRequest) request;
        AgentDiscoveryNotifyResponse response = new AgentDiscoveryNotifyResponse();
        response.setWatchKey(notifyRequest.getWatchKey());
        WireWatch watch;
        boolean terminated = notifyRequest.getEventType() == AgentWatchEventType.TERMINATED;
        synchronized (this) {
            watch = watchesByWireKey.get(notifyRequest.getWatchKey());
            if (!isCurrent(watch, connection) || notifyRequest.getEventType() == null
                || terminated && notifyRequest.getErrorCode() == null) {
                response.setAccepted(false);
                LOGGER.warn("[RAD-WATCH] Client gRPC hint rejected: connectionId={}, watchKey={}, "
                    + "eventType={}, reason=STALE_OR_INVALID", connectionId(connection),
                    AgentWatchLogUtils.token(notifyRequest.getWatchKey()),
                    notifyRequest.getEventType());
                return response;
            }
            if (terminated) {
                watchesByClientId.remove(watch.registration.getClientWatchId());
                removeWireKey(watch);
            }
        }
        boolean accepted = false;
        switch (notifyRequest.getEventType()) {
            case INVALIDATE:
                accepted = watch.callback.invalidate(notifyRequest.getObservedFingerprint(),
                    false);
                break;
            case REVALIDATE:
                accepted = watch.callback.invalidate(null, true);
                break;
            case TERMINATED:
                int errorCode = notifyRequest.getErrorCode();
                watch.callback.unavailable(errorCode, "Agent Watch terminated by server.",
                    !isNotFound(errorCode));
                accepted = true;
                break;
            default:
                // No other event type is defined by this client version.
        }
        response.setAccepted(accepted);
        LOGGER.info("[RAD-WATCH] Client gRPC hint received: connectionId={}, clientWatchId={}, "
            + "watchKey={}, eventType={}, observedFingerprint={}, errorCode={}, accepted={}, "
            + "{}", connectionId(connection),
            AgentWatchLogUtils.token(watch.registration.getClientWatchId()),
            AgentWatchLogUtils.token(notifyRequest.getWatchKey()), notifyRequest.getEventType(),
            AgentWatchLogUtils.fingerprint(notifyRequest.getObservedFingerprint()),
            notifyRequest.getErrorCode(), accepted,
            AgentWatchLogUtils.describeRequest(watch.registration.getDiscoveryRequest()));
        return response;
    }
    
    @Override
    public void shutdown() {
        List<String> watchKeys = new ArrayList<String>();
        synchronized (this) {
            if (closed) {
                return;
            }
            closed = true;
            available = false;
            connectionId = null;
            for (WireWatch watch : watchesByClientId.values()) {
                if (watch.watchKey != null) {
                    watchKeys.add(watch.watchKey);
                }
            }
            watchesByClientId.clear();
            watchesByWireKey.clear();
        }
        for (String watchKey : watchKeys) {
            executeBestEffortUnsubscribe(watchKey);
        }
        reconnectExecutor.shutdown();
    }
    
    private void subscribe(WireWatch watch, String expectedConnectionId) throws NacosException {
        AgentWatchRegistration registration = watch.registration;
        AgentSubscribeRpcResponse response = client.subscribeAgentWatch(
            registration.getClientWatchId(), registration.getDiscoveryRequest(),
            registration.getMaterializedFingerprint());
        if (response == null || response.getWatchKey() == null) {
            throw new NacosException(NacosException.SERVER_ERROR,
                "Server returned an invalid Agent Watch subscription response.");
        }
        String responseConnectionId = client.getCurrentConnectionId();
        synchronized (this) {
            if (closed || watchesByClientId.get(registration.getClientWatchId()) != watch
                || responseConnectionId == null || !responseConnectionId.equals(connectionId)
                || expectedConnectionId != null && !expectedConnectionId.equals(
                    responseConnectionId)) {
                throw new NacosException(NacosException.CLIENT_DISCONNECT,
                    "Agent Watch connection changed during subscription.");
            }
            removeWireKey(watch);
            watch.connectionId = responseConnectionId;
            watch.watchKey = response.getWatchKey();
            watchesByWireKey.put(watch.watchKey, watch);
        }
        if (response.isRefreshRequired()
            && !watch.callback.invalidate(response.getObservedFingerprint(),
                response.getObservedFingerprint() == null)) {
            throw new NacosException(NacosException.CLIENT_ERROR,
                "Agent Watch refresh could not be scheduled after subscription.");
        }
        LOGGER.info("[RAD-WATCH] Client gRPC Watch subscribed: connectionId={}, clientWatchId={}, "
            + "watchKey={}, refreshRequired={}, observedFingerprint={}, {}",
            responseConnectionId, AgentWatchLogUtils.token(registration.getClientWatchId()),
            AgentWatchLogUtils.token(response.getWatchKey()), response.isRefreshRequired(),
            AgentWatchLogUtils.fingerprint(response.getObservedFingerprint()),
            AgentWatchLogUtils.describeRequest(registration.getDiscoveryRequest()));
    }
    
    private void executeReconnect(final String connectedId, final List<WireWatch> watches,
        final boolean watchAvailable) {
        try {
            reconnectExecutor.execute(new Runnable() {
                
                @Override
                public void run() {
                    reconnect(connectedId, watches, watchAvailable);
                }
            });
        } catch (RejectedExecutionException e) {
            if (WARN_LOG_LIMITER.tryAcquire()) {
                LOGGER.warn("Agent Watch reconnect scheduling was rejected: {}",
                    e.getClass().getSimpleName());
            }
            for (WireWatch watch : watches) {
                notifyWireUnavailable(watch, new NacosException(NacosException.CLIENT_ERROR,
                    "Agent Watch reconnect scheduling was rejected.", e));
            }
        }
    }
    
    private void reconnect(String connectedId, List<WireWatch> watches,
        boolean watchAvailable) {
        LOGGER.info("[RAD-WATCH] Client gRPC Watch reconnect started: connectionId={}, "
            + "watchAvailable={}, watchCount={}", connectedId, watchAvailable,
            watches.size());
        if (!watchAvailable) {
            for (WireWatch watch : watches) {
                removeWatch(watch);
                notifyWireUnavailable(watch,
                    new NacosException(NacosException.SERVER_NOT_IMPLEMENTED,
                        "Server does not support the RAD Watch hint binding."));
            }
            notifyWireAvailable();
            return;
        }
        for (WireWatch watch : watches) {
            if (!isCurrentRegistration(watch, connectedId)) {
                continue;
            }
            try {
                subscribe(watch, connectedId);
            } catch (NacosException e) {
                if (!isCurrentConnection(connectedId)) {
                    continue;
                }
                removeWatchAndWire(watch);
                notifyWireUnavailable(watch, e);
            } catch (RuntimeException e) {
                removeWatchAndWire(watch);
                notifyWireUnavailable(watch, new NacosException(NacosException.CLIENT_ERROR,
                    "Agent Watch resubscription failed.", e));
            }
        }
        LOGGER.info("[RAD-WATCH] Client gRPC Watch reconnect completed: connectionId={}, "
            + "watchAvailable={}, attemptedWatchCount={}", connectedId, watchAvailable,
            watches.size());
        notifyWireAvailable();
    }
    
    private void notifyWireAvailable() {
        WireLifecycleListener listener;
        synchronized (this) {
            listener = lifecycleListener;
        }
        if (listener != null && isAvailable()) {
            listener.onWireAvailable();
        }
    }
    
    private void notifyWireUnavailable(WireWatch watch, NacosException exception) {
        WireLifecycleListener listener;
        synchronized (this) {
            listener = lifecycleListener;
        }
        if (listener != null) {
            listener.onWireUnavailable(watch.registration.getClientWatchId(), exception);
        }
    }
    
    private void executeBestEffortUnsubscribe(final String watchKey) {
        try {
            reconnectExecutor.execute(new Runnable() {
                
                @Override
                public void run() {
                    try {
                        if (client.isAgentWatchAvailable()) {
                            client.unsubscribeAgentWatch(watchKey);
                        }
                    } catch (NacosException e) {
                        LOGGER.debug("Best-effort Agent Watch unsubscribe failed.", e);
                    }
                }
            });
        } catch (RejectedExecutionException e) {
            LOGGER.debug("Best-effort Agent Watch unsubscribe was skipped.", e);
        }
    }
    
    private synchronized void removeWatch(WireWatch watch) {
        if (watchesByClientId.get(watch.registration.getClientWatchId()) == watch) {
            watchesByClientId.remove(watch.registration.getClientWatchId());
        }
        removeWireKey(watch);
    }
    
    private void removeWatchAndWire(WireWatch watch) {
        String watchKey;
        synchronized (this) {
            watchKey = watch.watchKey;
            removeWatch(watch);
        }
        if (watchKey != null) {
            executeBestEffortUnsubscribe(watchKey);
        }
    }
    
    private void removeWireKey(WireWatch watch) {
        if (watch.watchKey != null && watchesByWireKey.get(watch.watchKey) == watch) {
            watchesByWireKey.remove(watch.watchKey);
        }
        watch.watchKey = null;
        watch.connectionId = null;
    }
    
    private synchronized boolean isCurrentRegistration(WireWatch watch,
        String expectedConnectionId) {
        return !closed && available && expectedConnectionId.equals(connectionId)
            && expectedConnectionId.equals(client.getCurrentConnectionId())
            && watchesByClientId.get(watch.registration.getClientWatchId()) == watch;
    }
    
    private synchronized boolean isCurrentConnection(String expectedConnectionId) {
        return !closed && available && expectedConnectionId.equals(connectionId)
            && expectedConnectionId.equals(client.getCurrentConnectionId());
    }
    
    private boolean isCurrent(WireWatch watch, Connection connection) {
        return !closed && watch != null && connection != null && watch.connectionId != null
            && watch.connectionId.equals(connection.getConnectionId())
            && watch.connectionId.equals(connectionId)
            && watch.connectionId.equals(client.getCurrentConnectionId())
            && watchesByClientId.get(watch.registration.getClientWatchId()) == watch;
    }
    
    private String connectionId(Connection connection) {
        return connection == null ? "-" : connection.getConnectionId();
    }
    
    private void ensureAvailable() throws NacosException {
        if (closed) {
            throw new NacosException(NacosException.CLIENT_DISCONNECT,
                "Agent gRPC Watch transport has been shut down.");
        }
        if (!isAvailable()) {
            throw new NacosException(NacosException.SERVER_NOT_IMPLEMENTED,
                "RAD Watch hint binding is not available on the current gRPC connection.");
        }
    }
    
    private boolean isNotFound(int errorCode) {
        return errorCode == NacosException.NOT_FOUND
            || errorCode == NacosException.RESOURCE_NOT_FOUND;
    }
    
    interface WireLifecycleListener {
        
        /** A current gRPC connection supports the Watch binding. */
        void onWireAvailable();
        
        /** One previously gRPC-owned Watch can no longer be retained on the Wire. */
        void onWireUnavailable(String clientWatchId, NacosException exception);
    }
    
    private static final class WireWatch {
        
        private AgentWatchRegistration registration;
        
        private final AgentWatchTransportCallback callback;
        
        private String connectionId;
        
        private String watchKey;
        
        private WireWatch(AgentWatchRegistration registration,
            AgentWatchTransportCallback callback) {
            this.registration = registration;
            this.callback = callback;
        }
    }
}
