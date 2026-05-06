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
import com.alibaba.nacos.istio.model.VirtualService;
import com.google.protobuf.Any;
import io.envoyproxy.envoy.config.core.v3.TrafficDirection;
import io.envoyproxy.envoy.config.route.v3.RedirectAction;
import io.envoyproxy.envoy.config.route.v3.Route;
import io.envoyproxy.envoy.config.route.v3.RouteAction;
import io.envoyproxy.envoy.config.route.v3.RouteConfiguration;
import io.envoyproxy.envoy.config.route.v3.RouteMatch;
import io.envoyproxy.envoy.config.route.v3.VirtualHost;
import io.envoyproxy.envoy.service.discovery.v3.Resource;
import io.envoyproxy.envoy.type.matcher.v3.RegexMatcher;
import org.yaml.snakeyaml.Yaml;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.alibaba.nacos.istio.api.ApiConstants.ROUTE_TYPE;
import static com.alibaba.nacos.istio.util.IstioCrdUtil.buildClusterName;

/**
 * Rds of Xds Generator.
 *
 * @author PoisonGravity
 */
public class RdsGenerator implements ApiGenerator<Any> {
    
    public static final String DEFAULT_ROUTE_CONFIGURATION = "default_route_configuration";
    
    public static final String CONFIG_REASON = "config";
    
    public static final String DOMAIN_SUFFIX = ".nacos";
    
    public static final String ROUTE_CONFIGURATION_SUFFIX = "_route_config";
    
    public static final String BOOTSTRAP_UPSTREAM_CLUSTER = "nacos_xds";
    
    private final Yaml yaml = new Yaml();
    
    private static volatile RdsGenerator singleton = null;
    
    public static RdsGenerator getInstance() {
        if (singleton == null) {
            synchronized (RdsGenerator.class) {
                if (singleton == null) {
                    singleton = new RdsGenerator();
                }
            }
        }
        return singleton;
    }
    
    @Override
    public List<Any> generate(PushRequest pushRequest) {
        
        List<Any> result = new ArrayList<>();
        Set<String> reasons = pushRequest.getReason();
        if (reasons.contains(DEFAULT_ROUTE_CONFIGURATION)) {
            reasons.stream().filter(reason -> !DEFAULT_ROUTE_CONFIGURATION.equals(reason)).forEach(reason -> {
                result.add(buildDefaultRouteConfiguration(reason));
            });
        } else if (reasons.contains(CONFIG_REASON)) {
            reasons.stream().filter(reason -> !CONFIG_REASON.equals(reason)).forEach(reason -> {
                VirtualService vs = parseContent(reason, VirtualService.class);
                result.add(generateRdsFromVirtualService(vs, pushRequest));
            });
        } else {
            reasons.forEach(reason -> {
                result.add(buildDefaultRouteConfiguration(reason));
            });
        }
        
        return result;
    }
    
    @Override
    public List<Resource> deltaGenerate(PushRequest pushRequest) {
        return null;
    }
    
    private static Any buildDefaultRouteConfiguration(String routeConfigurationName) {
        if (routeConfigurationName == null) {
            throw new IllegalArgumentException("routeConfigurationName cannot be null");
        }
        String virtualHostName = routeConfigurationName;
        if (routeConfigurationName.endsWith(ROUTE_CONFIGURATION_SUFFIX)) {
            virtualHostName = routeConfigurationName.substring(0,
                    routeConfigurationName.length() - ROUTE_CONFIGURATION_SUFFIX.length());
        }
        RouteConfiguration routeConfiguration = RouteConfiguration.newBuilder().setName(routeConfigurationName)
                .addVirtualHosts(VirtualHost.newBuilder().setName(virtualHostName).addDomains("*").addRoutes(
                        Route.newBuilder().setMatch(RouteMatch.newBuilder().setPrefix("/").build())
                                .setRoute(RouteAction.newBuilder().setCluster(BOOTSTRAP_UPSTREAM_CLUSTER).build())
                                .build()).build()).build();
        
        return Any.newBuilder().setValue(routeConfiguration.toByteString()).setTypeUrl(ROUTE_TYPE).build();
    }
    
    /***
     * <p> generate Rds From VirtualService.</p>
     * @param virtualService VirtualService Parsed
     * @param pushRequest PushRequest
     * @return
     */
    public static Any generateRdsFromVirtualService(VirtualService virtualService, PushRequest pushRequest) {
        List<VirtualService.Spec.Http> httpRoutes = virtualService.getSpec().getHttp();
        List<String> hosts = virtualService.getSpec().getHosts();
        Map<String, IstioService> istioServiceMap = pushRequest.getResourceSnapshot().getIstioResources()
                .getIstioServiceMap();
        List<String> hostnames = getMatchingHostnames(hosts, pushRequest);
        String virtualHostName = virtualService.getMetadata().getName();
        for (Map.Entry<String, IstioService> entry : istioServiceMap.entrySet()) {
            if (entry.getKey().contains(virtualHostName)) {
                virtualHostName = buildClusterName(TrafficDirection.OUTBOUND, "", entry.getKey() + DOMAIN_SUFFIX,
                        entry.getValue().getPort());
                Loggers.MAIN.info("Setting virtualHostName: {}", virtualHostName);
            }
        }
        VirtualHost.Builder virtualHostBuilder = VirtualHost.newBuilder().setName(virtualHostName)
                .addAllDomains(hostnames);
        
        for (VirtualService.Spec.Http httpRoute : httpRoutes) {
            processHttpRoute(httpRoute, virtualHostBuilder, pushRequest);
        }
        
        RouteConfiguration routeConfiguration = RouteConfiguration.newBuilder()
                .setName(virtualService.getMetadata().getName() + ROUTE_CONFIGURATION_SUFFIX)
                .addVirtualHosts(virtualHostBuilder).build();
        return Any.newBuilder().setValue(routeConfiguration.toByteString()).setTypeUrl(ROUTE_TYPE).build();
        
    }
    
    private <T> T parseContent(String content, Class<T> valueType) {
        return yaml.loadAs(content, valueType);
    }
    
    private static List<String> getMatchingHostnames(List<String> hosts, PushRequest pushRequest) {
        List<String> hostnames = new ArrayList<>();
        Map<String, IstioService> istioServiceMap = pushRequest.getResourceSnapshot().getIstioResources()
                .getIstioServiceMap();
        for (String host : hosts) {
            if ("*".equals(host)) {
                hostnames.add("*");
                break;
            }
            for (Map.Entry<String, IstioService> entry : istioServiceMap.entrySet()) {
                if (entry.getKey().contains(host)) {
                    String hostname = buildClusterName(TrafficDirection.OUTBOUND, "", host + DOMAIN_SUFFIX,
                            entry.getValue().getPort());
                    Loggers.MAIN.info("Matching hostname: {}", hostname);
                    hostnames.add(hostname);
                }
            }
        }
        return hostnames;
    }
    
    private static void processHttpRoute(VirtualService.Spec.Http httpRoute, VirtualHost.Builder virtualHostBuilder,
            PushRequest pushRequest) {
        Route.Builder routeBuilder = Route.newBuilder();
        
        if (httpRoute.getName() != null) {
            routeBuilder.setName(httpRoute.getName());
        }
        
        for (VirtualService.Spec.Http.Match match : httpRoute.getMatch()) {
            RouteMatch.Builder routeMatchBuilder = RouteMatch.newBuilder();
            if (match.getUri().getPrefix() != null) {
                routeMatchBuilder.setPrefix(match.getUri().getPrefix());
            } else if (match.getUri().getExact() != null) {
                routeMatchBuilder.setPath(match.getUri().getExact());
            } else if (match.getUri().getRegex() != null) {
                // 检查是否定义了正则表达式
                RegexMatcher regexMatcher = RegexMatcher.newBuilder().setRegex(match.getUri().getRegex()).build();
                routeMatchBuilder.setSafeRegex(regexMatcher);
            }
            routeBuilder.setMatch(routeMatchBuilder);
        }
        
        if (httpRoute.getRedirect() != null) {
            setRedirectAction(httpRoute.getRedirect(), routeBuilder);
        } else {
            setRouteAction(httpRoute, routeBuilder, pushRequest);
        }
        virtualHostBuilder.addRoutes(routeBuilder);
    }
    
    private static void setRouteAction(VirtualService.Spec.Http httpRoute, Route.Builder routeBuilder,
            PushRequest pushRequest) {
        Map<String, IstioService> istioServiceMap = pushRequest.getResourceSnapshot().getIstioResources()
                .getIstioServiceMap();
        RouteAction.Builder routeActionBuilder = RouteAction.newBuilder();
        String hostName = httpRoute.getRoute().get(0).getDestination().getHost();
        if (httpRoute.getRewrite() != null) {
            routeActionBuilder.setPrefixRewrite(httpRoute.getRewrite().getUri());
        }
        String destName = hostName;
        for (Map.Entry<String, IstioService> entry : istioServiceMap.entrySet()) {
            if (entry.getKey().contains(hostName)) {
                destName = buildClusterName(TrafficDirection.OUTBOUND, "", entry.getKey() + DOMAIN_SUFFIX,
                        entry.getValue().getPort());
                Loggers.MAIN.info("Setting route action to destination: {}", destName);
            }
        }
        routeBuilder.setRoute(routeActionBuilder.setCluster(destName));
    }
    
    private static void setRedirectAction(VirtualService.Spec.Http.Redirect redirect, Route.Builder routeBuilder) {
        RedirectAction.Builder redirectBuilder = RedirectAction.newBuilder();
        if (redirect.getUri() != null) {
            redirectBuilder.setPathRedirect(redirect.getUri());
        }
        if (redirect.getAuthority() != null) {
            redirectBuilder.setHostRedirect(redirect.getAuthority());
        }
        routeBuilder.setRedirect(redirectBuilder);
    }
}
