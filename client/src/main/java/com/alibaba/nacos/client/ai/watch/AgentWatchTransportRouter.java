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

import com.alibaba.nacos.api.ai.AgentTransportMode;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.client.ai.remote.AiGrpcClient;
import com.alibaba.nacos.client.ai.remote.AgentHttpWatchClient;
import com.alibaba.nacos.client.utils.LogUtils;
import com.alibaba.nacos.common.executor.NameThreadFactory;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

/**
 * Selects one Wire owner for each Agent Watch without owning business cache state.
 *
 * <p>A negotiated gRPC Watch is preferred in AUTO mode. HTTP mode and AUTO mode without an
 * available gRPC Watch use one batch long-poll binding, while bounded discover polling remains
 * the compatibility fallback. Canonical intent and listener state stay in
 * {@link AgentWatchManager}.</p>
 *
 * @author Nacos
 */
public final class AgentWatchTransportRouter
    implements AgentWatchTransport, GrpcAgentWatchTransport.WireLifecycleListener,
    HttpAgentWatchTransport.WireLifecycleListener {
    
    private static final Logger LOGGER = LogUtils.logger(AgentWatchTransportRouter.class);
    
    private final AgentTransportMode mode;
    
    private final GrpcAgentWatchTransport grpcTransport;
    
    private final HttpAgentWatchTransport httpTransport;
    
    private final AgentWatchTransport pollingTransport;
    
    private final ScheduledExecutorService pollingExecutor;
    
    private final Map<String, Route> routes = new LinkedHashMap<String, Route>();
    
    private boolean closed;
    
    /**
     * Create a Watch transport router over the shared AI gRPC client.
     *
     * @param mode configured Agent transport mode
     * @param grpcClient shared AI gRPC client
     * @param pollingIntervalMillis fallback polling interval
     */
    public AgentWatchTransportRouter(AgentTransportMode mode, AiGrpcClient grpcClient,
        long pollingIntervalMillis) {
        this(mode, new GrpcAgentWatchTransport(grpcClient), null,
            new ScheduledThreadPoolExecutor(1,
                new NameThreadFactory("com.alibaba.nacos.client.ai.agent.watch.polling")),
            pollingIntervalMillis);
    }
    
    /**
     * Create a Watch transport router with gRPC, HTTP batch long poll, and polling fallback.
     *
     * @param mode configured Agent transport mode
     * @param grpcClient shared AI gRPC client
     * @param httpWatchClient Agent HTTP Watch binding
     * @param pollingIntervalMillis compatibility polling interval
     */
    public AgentWatchTransportRouter(AgentTransportMode mode, AiGrpcClient grpcClient,
        AgentHttpWatchClient httpWatchClient, long pollingIntervalMillis) {
        this(mode, new GrpcAgentWatchTransport(grpcClient),
            new HttpAgentWatchTransport(httpWatchClient),
            new ScheduledThreadPoolExecutor(1,
                new NameThreadFactory("com.alibaba.nacos.client.ai.agent.watch.polling")),
            pollingIntervalMillis);
    }
    
    AgentWatchTransportRouter(AgentTransportMode mode,
        GrpcAgentWatchTransport grpcTransport, ScheduledExecutorService pollingExecutor,
        long pollingIntervalMillis) {
        this(mode, grpcTransport, null,
            new PollingAgentWatchTransport(pollingExecutor, pollingIntervalMillis),
            pollingExecutor);
    }
    
    AgentWatchTransportRouter(AgentTransportMode mode,
        GrpcAgentWatchTransport grpcTransport, HttpAgentWatchTransport httpTransport,
        ScheduledExecutorService pollingExecutor, long pollingIntervalMillis) {
        this(mode, grpcTransport, httpTransport,
            new PollingAgentWatchTransport(pollingExecutor, pollingIntervalMillis),
            pollingExecutor);
    }
    
    AgentWatchTransportRouter(AgentTransportMode mode,
        GrpcAgentWatchTransport grpcTransport, AgentWatchTransport pollingTransport,
        ScheduledExecutorService pollingExecutor) {
        this(mode, grpcTransport, null, pollingTransport, pollingExecutor);
    }
    
    AgentWatchTransportRouter(AgentTransportMode mode,
        GrpcAgentWatchTransport grpcTransport, HttpAgentWatchTransport httpTransport,
        AgentWatchTransport pollingTransport, ScheduledExecutorService pollingExecutor) {
        this.mode = mode;
        this.grpcTransport = grpcTransport;
        this.httpTransport = httpTransport;
        this.pollingTransport = pollingTransport;
        this.pollingExecutor = pollingExecutor;
        grpcTransport.setLifecycleListener(this);
        if (httpTransport != null) {
            httpTransport.setLifecycleListener(this);
        }
    }
    
    @Override
    public void start(AgentWatchRegistration registration,
        AgentWatchTransportCallback callback) throws NacosException {
        Route route = new Route(registration, callback);
        synchronized (this) {
            ensureOpen();
            Route existing = routes.get(registration.getClientWatchId());
            if (existing != null) {
                existing.registration = registration;
                updateOwner(existing);
                return;
            }
            routes.put(registration.getClientWatchId(), route);
        }
        AgentWatchTransport owner = selectOwner(registration);
        try {
            owner.start(registration, callback);
        } catch (NacosException e) {
            AgentWatchTransport fallback = fallbackOwner(owner, registration, e);
            if (fallback == null) {
                removeRoute(route);
                throw e;
            }
            owner = fallback;
            try {
                owner.start(registration, callback);
            } catch (NacosException fallbackFailure) {
                removeRoute(route);
                throw fallbackFailure;
            } catch (RuntimeException fallbackFailure) {
                removeRoute(route);
                throw fallbackFailure;
            }
        } catch (RuntimeException e) {
            owner.stop(registration.getClientWatchId());
            removeRoute(route);
            throw e;
        }
        boolean retained;
        synchronized (this) {
            retained = !closed && routes.get(registration.getClientWatchId()) == route;
            if (retained) {
                route.owner = owner;
            }
        }
        if (!retained) {
            owner.stop(registration.getClientWatchId());
        }
    }
    
    @Override
    public void update(AgentWatchRegistration registration) {
        Route route;
        AgentWatchTransport owner;
        synchronized (this) {
            route = routes.get(registration.getClientWatchId());
            if (route == null) {
                return;
            }
            route.registration = registration;
            owner = route.owner;
        }
        if (owner != null) {
            owner.update(registration);
        }
    }
    
    @Override
    public void stop(String clientWatchId) {
        Route route;
        synchronized (this) {
            route = routes.remove(clientWatchId);
        }
        if (route == null || route.owner == null) {
            grpcTransport.stop(clientWatchId);
            if (httpTransport != null) {
                httpTransport.stop(clientWatchId);
            }
            pollingTransport.stop(clientWatchId);
            return;
        }
        route.owner.stop(clientWatchId);
    }
    
    @Override
    public void onWireAvailable() {
        if (mode == AgentTransportMode.HTTP) {
            return;
        }
        List<Route> candidates = new ArrayList<Route>();
        synchronized (this) {
            if (closed) {
                return;
            }
            for (Route route : routes.values()) {
                if (route.owner != grpcTransport) {
                    candidates.add(route);
                }
            }
        }
        for (Route route : candidates) {
            upgradeToGrpc(route);
        }
    }
    
    @Override
    public void onWireUnavailable(String clientWatchId, NacosException exception) {
        Route route;
        synchronized (this) {
            route = routes.get(clientWatchId);
            if (closed || route == null || route.owner != grpcTransport) {
                return;
            }
            route.owner = null;
        }
        if (isNotFound(exception)) {
            route.callback.unavailable(exception.getErrCode(), exception.getErrMsg(), false);
            return;
        }
        if (isTerminal(exception)) {
            route.callback.unavailable(exception.getErrCode(), exception.getErrMsg(), true);
            return;
        }
        AgentWatchTransport fallback = selectNonGrpcOwner(route.registration);
        try {
            fallback.start(route.registration, route.callback);
            synchronized (this) {
                if (!closed && routes.get(clientWatchId) == route && route.owner == null) {
                    route.owner = fallback;
                    return;
                }
            }
            fallback.stop(clientWatchId);
        } catch (NacosException e) {
            route.callback.unavailable(e.getErrCode(), e.getErrMsg(), true);
        } catch (RuntimeException e) {
            route.callback.unavailable(NacosException.CLIENT_ERROR,
                "Failed to start fallback Agent Watch polling.", true);
        }
    }
    
    @Override
    public void onWireUnavailable(NacosException exception) {
        if (httpTransport == null) {
            return;
        }
        List<Route> candidates = new ArrayList<Route>();
        synchronized (this) {
            if (closed) {
                return;
            }
            for (Route route : routes.values()) {
                if (route.owner == httpTransport) {
                    route.owner = null;
                    candidates.add(route);
                }
            }
        }
        for (Route route : candidates) {
            if (isHttpTerminal(exception)) {
                route.callback.unavailable(exception.getErrCode(), exception.getErrMsg(), true);
            } else {
                fallbackHttpRoute(route);
            }
        }
    }
    
    @Override
    public void shutdown() {
        List<Route> active;
        synchronized (this) {
            if (closed) {
                return;
            }
            closed = true;
            active = new ArrayList<Route>(routes.values());
            routes.clear();
        }
        for (Route route : active) {
            if (route.owner != null) {
                route.owner.stop(route.registration.getClientWatchId());
            }
        }
        grpcTransport.shutdown();
        if (httpTransport != null) {
            httpTransport.shutdown();
        }
        pollingTransport.shutdown();
        pollingExecutor.shutdownNow();
    }
    
    private void upgradeToGrpc(Route route) {
        AgentWatchTransport previous;
        synchronized (this) {
            previous = route.owner;
            if (closed || previous == null || previous == grpcTransport
                || routes.get(route.registration.getClientWatchId()) != route) {
                return;
            }
        }
        try {
            grpcTransport.start(route.registration, route.callback);
        } catch (NacosException e) {
            if (isNotFound(e)) {
                route.callback.unavailable(e.getErrCode(), e.getErrMsg(), false);
            } else if (isTerminal(e)) {
                route.callback.unavailable(e.getErrCode(), e.getErrMsg(), true);
            } else {
                LOGGER.debug("Agent Watch remains on polling after gRPC upgrade failure.", e);
            }
            return;
        } catch (RuntimeException e) {
            LOGGER.debug("Agent Watch remains on polling after gRPC upgrade failure.", e);
            return;
        }
        boolean upgraded;
        synchronized (this) {
            upgraded = !closed
                && routes.get(route.registration.getClientWatchId()) == route
                && route.owner == previous;
            if (upgraded) {
                route.owner = grpcTransport;
            }
        }
        if (upgraded) {
            previous.stop(route.registration.getClientWatchId());
        } else {
            grpcTransport.stop(route.registration.getClientWatchId());
        }
    }
    
    private synchronized void updateOwner(Route route) {
        if (route.owner != null) {
            route.owner.update(route.registration);
        }
    }
    
    private AgentWatchTransport selectOwner(AgentWatchRegistration registration) {
        if (mode != AgentTransportMode.HTTP && grpcTransport.isAvailable()) {
            return grpcTransport;
        }
        return selectNonGrpcOwner(registration);
    }
    
    private AgentWatchTransport selectNonGrpcOwner(AgentWatchRegistration registration) {
        if (mode != AgentTransportMode.GRPC && httpTransport != null
            && httpTransport.isAvailable()
            && registration.getMaterializedFingerprint() != null) {
            return httpTransport;
        }
        return pollingTransport;
    }
    
    private AgentWatchTransport fallbackOwner(AgentWatchTransport failed,
        AgentWatchRegistration registration, NacosException exception) {
        if (!canFallback(exception)) {
            return null;
        }
        if (failed == grpcTransport) {
            return selectNonGrpcOwner(registration);
        }
        if (failed == httpTransport) {
            return pollingTransport;
        }
        return null;
    }
    
    private void fallbackHttpRoute(Route route) {
        try {
            pollingTransport.start(route.registration, route.callback);
            boolean retained;
            synchronized (this) {
                retained = !closed && routes.get(route.registration.getClientWatchId()) == route
                    && route.owner == null;
                if (retained) {
                    route.owner = pollingTransport;
                }
            }
            httpTransport.stop(route.registration.getClientWatchId());
            if (!retained) {
                pollingTransport.stop(route.registration.getClientWatchId());
            }
        } catch (NacosException e) {
            route.callback.unavailable(e.getErrCode(), e.getErrMsg(), true);
        } catch (RuntimeException e) {
            route.callback.unavailable(NacosException.CLIENT_ERROR,
                "Failed to start fallback Agent Watch polling.", true);
        }
    }
    
    private boolean isHttpTerminal(NacosException exception) {
        int code = exception.getErrCode();
        return code == NacosException.OVER_THRESHOLD
            || code == NacosException.CLIENT_OVER_THRESHOLD
            || code == NacosException.INVALID_PARAM
            || code == NacosException.CLIENT_INVALID_PARAM || code == NacosException.CONFLICT;
    }
    
    private synchronized void removeRoute(Route route) {
        if (routes.get(route.registration.getClientWatchId()) == route) {
            routes.remove(route.registration.getClientWatchId());
        }
    }
    
    private void ensureOpen() throws NacosException {
        if (closed) {
            throw new NacosException(NacosException.CLIENT_DISCONNECT,
                "Agent Watch transport router has been shut down.");
        }
    }
    
    private boolean canFallback(NacosException exception) {
        return !isNotFound(exception) && !isTerminal(exception);
    }
    
    private boolean isNotFound(NacosException exception) {
        return exception.getErrCode() == NacosException.NOT_FOUND
            || exception.getErrCode() == NacosException.RESOURCE_NOT_FOUND;
    }
    
    private boolean isTerminal(NacosException exception) {
        int code = exception.getErrCode();
        return code == NacosException.NO_RIGHT || code == NacosException.OVER_THRESHOLD
            || code == NacosException.CLIENT_OVER_THRESHOLD
            || code == NacosException.INVALID_PARAM
            || code == NacosException.CLIENT_INVALID_PARAM || code == NacosException.CONFLICT;
    }
    
    private static final class Route {
        
        private AgentWatchRegistration registration;
        
        private final AgentWatchTransportCallback callback;
        
        private AgentWatchTransport owner;
        
        private Route(AgentWatchRegistration registration,
            AgentWatchTransportCallback callback) {
            this.registration = registration;
            this.callback = callback;
        }
    }
}
