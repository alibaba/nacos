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
 * <p>W5 routes a negotiated gRPC Watch to {@link GrpcAgentWatchTransport} and otherwise uses
 * bounded polling. The later HTTP Watch stage adds its concrete transport without moving
 * canonical intent or listener state out of {@link AgentWatchManager}.</p>
 *
 * @author Nacos
 */
public final class AgentWatchTransportRouter
    implements AgentWatchTransport, GrpcAgentWatchTransport.WireLifecycleListener {
    
    private static final Logger LOGGER = LogUtils.logger(AgentWatchTransportRouter.class);
    
    private final AgentTransportMode mode;
    
    private final GrpcAgentWatchTransport grpcTransport;
    
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
        this(mode, new GrpcAgentWatchTransport(grpcClient),
            new ScheduledThreadPoolExecutor(1,
                new NameThreadFactory("com.alibaba.nacos.client.ai.agent.watch.polling")),
            pollingIntervalMillis);
    }
    
    AgentWatchTransportRouter(AgentTransportMode mode,
        GrpcAgentWatchTransport grpcTransport, ScheduledExecutorService pollingExecutor,
        long pollingIntervalMillis) {
        this(mode, grpcTransport,
            new PollingAgentWatchTransport(pollingExecutor, pollingIntervalMillis),
            pollingExecutor);
    }
    
    AgentWatchTransportRouter(AgentTransportMode mode,
        GrpcAgentWatchTransport grpcTransport, AgentWatchTransport pollingTransport,
        ScheduledExecutorService pollingExecutor) {
        this.mode = mode;
        this.grpcTransport = grpcTransport;
        this.pollingTransport = pollingTransport;
        this.pollingExecutor = pollingExecutor;
        grpcTransport.setLifecycleListener(this);
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
        AgentWatchTransport owner = selectOwner();
        try {
            owner.start(registration, callback);
        } catch (NacosException e) {
            if (owner != grpcTransport || !canFallback(e)) {
                removeRoute(route);
                throw e;
            }
            owner = pollingTransport;
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
                if (route.owner == pollingTransport) {
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
        try {
            pollingTransport.start(route.registration, route.callback);
            synchronized (this) {
                if (!closed && routes.get(clientWatchId) == route && route.owner == null) {
                    route.owner = pollingTransport;
                    return;
                }
            }
            pollingTransport.stop(clientWatchId);
        } catch (NacosException e) {
            route.callback.unavailable(e.getErrCode(), e.getErrMsg(), true);
        } catch (RuntimeException e) {
            route.callback.unavailable(NacosException.CLIENT_ERROR,
                "Failed to start fallback Agent Watch polling.", true);
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
        pollingTransport.shutdown();
        pollingExecutor.shutdownNow();
    }
    
    private void upgradeToGrpc(Route route) {
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
                && route.owner == pollingTransport;
            if (upgraded) {
                route.owner = grpcTransport;
            }
        }
        if (upgraded) {
            pollingTransport.stop(route.registration.getClientWatchId());
        } else {
            grpcTransport.stop(route.registration.getClientWatchId());
        }
    }
    
    private synchronized void updateOwner(Route route) {
        if (route.owner != null) {
            route.owner.update(route.registration);
        }
    }
    
    private AgentWatchTransport selectOwner() {
        return mode != AgentTransportMode.HTTP && grpcTransport.isAvailable()
            ? grpcTransport : pollingTransport;
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
