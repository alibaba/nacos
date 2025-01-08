/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.istio.xds;

import com.alibaba.nacos.istio.api.ApiGenerator;
import com.alibaba.nacos.istio.misc.Loggers;
import com.alibaba.nacos.istio.model.IstioService;
import com.alibaba.nacos.istio.model.PushRequest;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import io.envoyproxy.envoy.config.accesslog.v3.AccessLog;
import io.envoyproxy.envoy.config.core.v3.Address;
import io.envoyproxy.envoy.config.core.v3.AggregatedConfigSource;
import io.envoyproxy.envoy.config.core.v3.ConfigSource;
import io.envoyproxy.envoy.config.core.v3.SocketAddress;
import io.envoyproxy.envoy.config.listener.v3.Filter;
import io.envoyproxy.envoy.config.listener.v3.FilterChain;
import io.envoyproxy.envoy.config.listener.v3.Listener;
import io.envoyproxy.envoy.config.route.v3.Route;
import io.envoyproxy.envoy.config.route.v3.RouteAction;
import io.envoyproxy.envoy.config.route.v3.RouteConfiguration;
import io.envoyproxy.envoy.config.route.v3.RouteMatch;
import io.envoyproxy.envoy.config.route.v3.VirtualHost;
import io.envoyproxy.envoy.extensions.filters.http.router.v3.Router;
import io.envoyproxy.envoy.extensions.filters.network.http_connection_manager.v3.HttpConnectionManager;
import io.envoyproxy.envoy.extensions.filters.network.http_connection_manager.v3.HttpFilter;
import io.envoyproxy.envoy.extensions.filters.network.http_connection_manager.v3.Rds;
import io.envoyproxy.envoy.service.discovery.v3.Resource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.alibaba.nacos.istio.api.ApiConstants.LISTENER_TYPE;
import static io.envoyproxy.envoy.config.core.v3.ApiVersion.V2_VALUE;

/**
 * LDS of XDS protocol generator.
 *
 * @author PoisonGravity
 */
public class LdsGenerator implements ApiGenerator<Any> {
    
    public static final String INIT_LISTENER = "bootstrap_listener";
    
    private static final String INIT_LISTENER_NAME = "listener_0";
    
    private static final String INIT_LISTENER_ADDRESS = "0.0.0.0";
    
    private static final int INIT_LISTENER_PORT = 80;
    
    private static final String ACCESS_LOGGER_NAME = "envoy.access_loggers.stdout";
    
    private static final String TYPE_URL_ACCESS_LOG = "type.googleapis.com/envoy.extensions.access_loggers.stream.v3.StdoutAccessLog";
    
    private static final int DEFAULT_PORT_INCREMENT = 1;
    
    public static final String ROUTE_CONFIGURATION_SUFFIX = "_route_config";
    
    public static final String BOOTSTRAP_UPSTREAM_CLUSTER = "nacos_xds";
    
    public static final String DEFAULT_FILTER_CHAIN_NAME = "filter_chain_0";
    
    public static final String DEFAULT_FILTER_TYPE = "envoy.filters.network.http_connection_manager";
    
    public static final String DEFAULT_HTTPMANAGER_PREFIX = "ingress_http";
    
    public static final String DEFAULT_HTTP_ROUTER_TYPE = "envoy.filters.http.router";
    
    private static volatile LdsGenerator singleton = null;
    
    public static LdsGenerator getInstance() {
        if (singleton == null) {
            synchronized (LdsGenerator.class) {
                if (singleton == null) {
                    singleton = new LdsGenerator();
                }
            }
        }
        return singleton;
    }
    
    @Override
    public List<Any> generate(PushRequest pushRequest) {
        if (!pushRequest.isFull()) {
            return null;
        }
        Map<String, IstioService> istioServiceMap = pushRequest.getResourceSnapshot().getIstioResources()
                .getIstioServiceMap();
        List<Any> result = new ArrayList<>();
        result.add(buildBootstrapListener());
        
        if (pushRequest.isFull()) {
            for (Map.Entry<String, IstioService> entry : istioServiceMap.entrySet()) {
                IstioService istioService = entry.getValue();
                if (istioService != null) {
                    String rdsName = entry.getKey() + ROUTE_CONFIGURATION_SUFFIX;
                    result.add(buildDynamicListener(entry.getKey(), INIT_LISTENER_ADDRESS, istioService.getPort(),
                            rdsName));
                } else {
                    Loggers.MAIN.error("Attempt to create listener for non-existent service");
                }
            }
        }
        return result;
    }
    
    @Override
    public List<Resource> deltaGenerate(PushRequest pushRequest) {
        return null;
    }
    
    /**
     * Constructs a default Envoy listener configuration with specified parameters.
     * This method prepares the necessary configurations for both bootstrap and non-bootstrap scenarios.
     */
    private static Any buildDefaultListener(String listenerName, String listenerAddress, int listenerPort,
            String rdsName, boolean isBootstrap) {
        if (isValid(listenerName, listenerPort, rdsName, isBootstrap)) {
            Loggers.MAIN.error("Listener name, Listener port and RDS name cannot be null.");
            return null;
        }
        
        Listener.Builder listenerBuilder = Listener.newBuilder().setName(listenerName);
        
        listenerAddress = (listenerAddress == null) ? INIT_LISTENER_ADDRESS : listenerAddress;
        int portValue = isBootstrap ? INIT_LISTENER_PORT : listenerPort + DEFAULT_PORT_INCREMENT;
        listenerBuilder.setAddress(Address.newBuilder()
                .setSocketAddress(SocketAddress.newBuilder().setAddress(listenerAddress).setPortValue(portValue)));
        
        String routeConfigName =
                isBootstrap ? INIT_LISTENER + ROUTE_CONFIGURATION_SUFFIX : listenerName + ROUTE_CONFIGURATION_SUFFIX;
        String virtualHostName = isBootstrap ? INIT_LISTENER : listenerName;
        
        RouteConfiguration routeConfiguration = RouteConfiguration.newBuilder().setName(routeConfigName)
                .addVirtualHosts(VirtualHost.newBuilder().setName(virtualHostName).addDomains("*").addRoutes(
                        Route.newBuilder().setMatch(RouteMatch.newBuilder().setPrefix("/").build())
                                .setRoute(RouteAction.newBuilder().setCluster(BOOTSTRAP_UPSTREAM_CLUSTER).build())
                                .build()).build()).build();
        
        HttpConnectionManager httpConnectionManager = HttpConnectionManager.newBuilder()
                .setStatPrefix(DEFAULT_HTTPMANAGER_PREFIX).addAccessLog(buildAccessLog())
                .setCodecType(HttpConnectionManager.CodecType.AUTO).setRouteConfig(routeConfiguration)
                .addHttpFilters(createHttpFilter()).build();
        
        listenerBuilder.addFilterChains(createFilterChain(httpConnectionManager));
        
        Listener listener = listenerBuilder.build();
        
        return Any.newBuilder().setValue(listener.toByteString()).setTypeUrl(LISTENER_TYPE).build();
    }
    
    /**
     * Creates the initial bootstrap listener configuration.
     * This is used during the startup phase of the Envoy server to construct the base configuration.
     */
    private static Any buildBootstrapListener() {
        return buildDefaultListener(INIT_LISTENER_NAME, INIT_LISTENER_ADDRESS, INIT_LISTENER_PORT, null, true);
    }
    
    /**
     * Constructs a default listener configuration for static environments.
     * This method is designed to provide configurations for scenarios where dynamic discovery services might not be used.
     */
    private static Any buildDefaultStaticListener(String listenerName, String listenerAddress, int listenerPort,
            String rdsName) {
        if (INIT_LISTENER.equals(listenerName)) {
            return buildBootstrapListener();
        }
        return buildDefaultListener(listenerName, listenerAddress, listenerPort, rdsName, false);
    }
    
    /**
     * Constructs a listener configuration for environments using dynamic service discovery.
     * This method prepares the listener to utilize dynamic routing and service discovery services with rds.
     */
    private static Any buildDynamicListener(String listenerName, String listenerAddress, int listenerPort,
            String rdsName) {
        if (INIT_LISTENER.equals(listenerName)) {
            return buildBootstrapListener();
        }
        
        if ((listenerName == null) || (listenerPort == 0) || (rdsName == null)) {
            Loggers.MAIN.error("Listener name, Listener port and RDS name cannot be null.");
            return null;
        }
        
        listenerAddress = (listenerAddress == null) ? INIT_LISTENER_ADDRESS : listenerAddress;
        Listener.Builder listenerBuilder = Listener.newBuilder().setName(listenerName);
        
        listenerBuilder.setAddress(Address.newBuilder().setSocketAddress(
                SocketAddress.newBuilder().setAddress(listenerAddress)
                        .setPortValue(listenerPort + DEFAULT_PORT_INCREMENT)));
        ConfigSource configSource = createConfigSource();
        
        Rds rds = Rds.newBuilder().setConfigSource(configSource).setRouteConfigName(rdsName).build();
        
        HttpConnectionManager httpConnectionManager = HttpConnectionManager.newBuilder()
                .setStatPrefix(DEFAULT_HTTPMANAGER_PREFIX).addAccessLog(buildAccessLog())
                .setCodecType(HttpConnectionManager.CodecType.AUTO).setRds(rds).addHttpFilters(createHttpFilter())
                .build();
        
        listenerBuilder.addFilterChains(createFilterChain(httpConnectionManager));
        
        Listener listener = listenerBuilder.build();
        
        return Any.newBuilder().setValue(listener.toByteString()).setTypeUrl(LISTENER_TYPE).build();
        
    }
    
    private static AccessLog buildAccessLog() {
        return AccessLog.newBuilder().setName(ACCESS_LOGGER_NAME)
                .setTypedConfig(Any.newBuilder().setTypeUrl(TYPE_URL_ACCESS_LOG).setValue(ByteString.EMPTY).build())
                .build();
    }
    
    private static ConfigSource createConfigSource() {
        return ConfigSource.newBuilder().setAds(AggregatedConfigSource.newBuilder())
                .setResourceApiVersionValue(V2_VALUE).build();
    }
    
    private static HttpFilter createHttpFilter() {
        return HttpFilter.newBuilder().setName(DEFAULT_HTTP_ROUTER_TYPE)
                .setTypedConfig(Any.pack(Router.newBuilder().build())).build();
    }
    
    private static FilterChain createFilterChain(HttpConnectionManager httpConnectionManager) {
        return FilterChain.newBuilder().setName(DEFAULT_FILTER_CHAIN_NAME).addFilters(
                        Filter.newBuilder().setName(DEFAULT_FILTER_TYPE).setTypedConfig(Any.pack(httpConnectionManager)))
                .build();
    }
    
    private static boolean isValid(String listenerName, int listenerPort, String rdsName, boolean isBootstrap) {
        return !isBootstrap && ((listenerName == null) || (listenerPort == 0) || (rdsName == null));
    }
}
