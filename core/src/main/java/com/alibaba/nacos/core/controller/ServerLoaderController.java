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

package com.alibaba.nacos.core.controller;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.remote.RequestCallBack;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.api.remote.request.ServerLoaderInfoRequest;
import com.alibaba.nacos.api.remote.request.ServerReloadRequest;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.api.remote.response.ServerLoaderInfoResponse;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.auth.common.ActionTypes;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.MemberUtil;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.core.cluster.remote.ClusterRpcClientProxy;
import com.alibaba.nacos.core.remote.Connection;
import com.alibaba.nacos.core.remote.ConnectionManager;
import com.alibaba.nacos.core.remote.core.ServerLoaderInfoRequestHandler;
import com.alibaba.nacos.core.remote.core.ServerReloaderRequestHandler;
import com.alibaba.nacos.core.utils.Commons;
import com.alibaba.nacos.core.utils.RemoteUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * controller to control server loader.
 *
 * @author liuzunfei
 * @version $Id: ServerLoaderController.java, v 0.1 2020年07月22日 4:28 PM liuzunfei Exp $
 */
@RestController
@RequestMapping(Commons.NACOS_CORE_CONTEXT_V2 + "/loader")
public class ServerLoaderController {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ServerLoaderController.class);
    
    private static final String X_REAL_IP = "X-Real-IP";
    
    private static final String X_FORWARDED_FOR = "X-Forwarded-For";
    
    private static final String X_FORWARDED_FOR_SPLIT_SYMBOL = ",";
    
    private static final String SUCCESS_RESULT = "Ok";
    
    private static final String FAIL_RESULT = "Fail";
    
    @Autowired
    private ConnectionManager connectionManager;
    
    @Autowired
    private ServerMemberManager serverMemberManager;
    
    @Autowired
    private ClusterRpcClientProxy clusterRpcClientProxy;
    
    @Autowired
    private ServerReloaderRequestHandler serverReloaderRequestHandler;
    
    @Autowired
    private ServerLoaderInfoRequestHandler serverLoaderInfoRequestHandler;
    
    /**
     * Get current clients.
     *
     * @return state json.
     */
    @Secured(resource = Commons.NACOS_CORE_CONTEXT_V2 + "/loader", action = ActionTypes.READ)
    @GetMapping("/current")
    public ResponseEntity<Map<String, Connection>> currentClients() {
        Map<String, Connection> stringConnectionMap = connectionManager.currentClients();
        return ResponseEntity.ok().body(stringConnectionMap);
    }
    
    /**
     * Get server state of current server.
     *
     * @return state json.
     */
    @Secured(resource = Commons.NACOS_CORE_CONTEXT_V2 + "/loader", action = ActionTypes.WRITE)
    @GetMapping("/reloadCurrent")
    public ResponseEntity<String> reloadCount(@RequestParam Integer count,
            @RequestParam(value = "redirectAddress", required = false) String redirectAddress) {
        Map<String, String> responseMap = new HashMap<>(3);
        connectionManager.loadCount(count, redirectAddress);
        return ResponseEntity.ok().body("success");
    }
    
    /**
     * Get server state of current server.
     *
     * @return state json.
     */
    @Secured(resource = Commons.NACOS_CORE_CONTEXT_V2 + "/loader", action = ActionTypes.WRITE)
    @GetMapping("/smartReloadCluster")
    public ResponseEntity<String> smartReload(HttpServletRequest request,
            @RequestParam(value = "loaderFactor", required = false) String loaderFactorStr,
            @RequestParam(value = "force", required = false) String force) {
        
        LOGGER.info("Smart reload request receive,requestIp={}", getRemoteIp(request));
        
        Map<String, Object> serverLoadMetrics = getServerLoadMetrics();
        Object avgString = (Object) serverLoadMetrics.get("avg");
        List<ServerLoaderMetrics> details = (List<ServerLoaderMetrics>) serverLoadMetrics.get("detail");
        int avg = Integer.parseInt(avgString.toString());
        float loaderFactor =
                StringUtils.isBlank(loaderFactorStr) ? RemoteUtils.LOADER_FACTOR : Float.parseFloat(loaderFactorStr);
        int overLimitCount = (int) (avg * (1 + loaderFactor));
        int lowLimitCount = (int) (avg * (1 - loaderFactor));
        
        List<ServerLoaderMetrics> overLimitServer = new ArrayList<ServerLoaderMetrics>();
        List<ServerLoaderMetrics> lowLimitServer = new ArrayList<ServerLoaderMetrics>();
        
        for (ServerLoaderMetrics metrics : details) {
            int sdkCount = Integer.parseInt(metrics.getMetric().get("sdkConCount"));
            if (sdkCount > overLimitCount) {
                overLimitServer.add(metrics);
            }
            if (sdkCount < lowLimitCount) {
                lowLimitServer.add(metrics);
            }
        }
        
        // desc by sdkConCount
        overLimitServer.sort((o1, o2) -> {
            Integer sdkCount1 = Integer.valueOf(o1.getMetric().get("sdkConCount"));
            Integer sdkCount2 = Integer.valueOf(o2.getMetric().get("sdkConCount"));
            return sdkCount1.compareTo(sdkCount2) * -1;
        });
        
        LOGGER.info("Over load limit server list ={}", overLimitServer);
        
        //asc by sdkConCount
        lowLimitServer.sort((o1, o2) -> {
            Integer sdkCount1 = Integer.valueOf(o1.getMetric().get("sdkConCount"));
            Integer sdkCount2 = Integer.valueOf(o2.getMetric().get("sdkConCount"));
            return sdkCount1.compareTo(sdkCount2);
        });
        
        LOGGER.info("Low load limit server list ={}", lowLimitServer);
        AtomicBoolean result = new AtomicBoolean(true);
        
        for (int i = 0; i < overLimitServer.size() & i < lowLimitServer.size(); i++) {
            ServerReloadRequest serverLoaderInfoRequest = new ServerReloadRequest();
            serverLoaderInfoRequest.setReloadCount(overLimitCount);
            serverLoaderInfoRequest.setReloadServer(lowLimitServer.get(i).address);
            Member member = serverMemberManager.find(overLimitServer.get(i).address);
            
            LOGGER.info("Reload task submit ,fromServer ={},toServer={}, ", overLimitServer.get(i).address,
                    lowLimitServer.get(i).address);
            
            if (serverMemberManager.getSelf().equals(member)) {
                try {
                    serverReloaderRequestHandler.handle(serverLoaderInfoRequest, new RequestMeta());
                } catch (NacosException e) {
                    LOGGER.error("Fail to loader self server", e);
                    result.set(false);
                }
            } else {
                
                try {
                    clusterRpcClientProxy.asyncRequest(member, serverLoaderInfoRequest, new RequestCallBack() {
                        @Override
                        public Executor getExecutor() {
                            return null;
                        }
                        
                        @Override
                        public long getTimeout() {
                            return 100L;
                        }
                        
                        @Override
                        public void onResponse(Response response) {
                            if (response == null || !response.isSuccess()) {
                                LOGGER.error("Fail to loader member={},response={}", member.getAddress(), response);
                                result.set(false);
                                
                            }
                        }
                        
                        @Override
                        public void onException(Throwable e) {
                            LOGGER.error("Fail to loader member={}", member.getAddress(), e);
                            result.set(false);
                        }
                    });
                } catch (NacosException e) {
                    LOGGER.error("Fail to loader member={}", member.getAddress(), e);
                    result.set(false);
                }
            }
        }
        
        return ResponseEntity.ok().body(result.get() ? SUCCESS_RESULT : FAIL_RESULT);
    }
    
    
    /**
     * Get server state of current server.
     *
     * @return state json.
     */
    @Secured(resource = Commons.NACOS_CORE_CONTEXT_V2 + "/loader", action = ActionTypes.WRITE)
    @GetMapping("/reloadClient")
    public ResponseEntity<String> reloadSingle(@RequestParam String connectionId,
            @RequestParam(value = "redirectAddress", required = false) String redirectAddress) {
        Map<String, String> responseMap = new HashMap<>(3);
        connectionManager.loadSingle(connectionId, redirectAddress);
        return ResponseEntity.ok().body("success");
    }
    
    /**
     * Get current clients.
     *
     * @return state json.
     */
    @Secured(resource = Commons.NACOS_CORE_CONTEXT_V2 + "/loader", action = ActionTypes.READ)
    @GetMapping("/cluster")
    public ResponseEntity<Map<String, Object>> loaderMetrics() {
        
        Map<String, Object> serverLoadMetrics = getServerLoadMetrics();
        
        return ResponseEntity.ok().body(serverLoadMetrics);
    }
    
    private Map<String, Object> getServerLoadMetrics() {
        
        List<ServerLoaderMetrics> responseList = new LinkedList<ServerLoaderMetrics>();
        
        // default include self.
        int memberSize = serverMemberManager.allMembersWithoutSelf().size();
        CountDownLatch countDownLatch = new CountDownLatch(memberSize);
        for (Member member : serverMemberManager.allMembersWithoutSelf()) {
            if (MemberUtil.isSupportedLongCon(member)) {
                ServerLoaderInfoRequest serverLoaderInfoRequest = new ServerLoaderInfoRequest();
                
                try {
                    clusterRpcClientProxy.asyncRequest(member, serverLoaderInfoRequest, new RequestCallBack() {
                        @Override
                        public Executor getExecutor() {
                            return null;
                        }
                        
                        @Override
                        public long getTimeout() {
                            return 200L;
                        }
                        
                        @Override
                        public void onResponse(Response response) {
                            if (response instanceof ServerLoaderInfoResponse) {
                                ServerLoaderMetrics metrics = new ServerLoaderMetrics();
                                metrics.setAddress(member.getAddress());
                                metrics.setMetric(((ServerLoaderInfoResponse) response).getLoaderMetrics());
                                responseList.add(metrics);
                            }
                            countDownLatch.countDown();
                        }
                        
                        @Override
                        public void onException(Throwable e) {
                            LOGGER.error("Get metrics fail,member={}", member.getAddress(), e);
                            countDownLatch.countDown();
                        }
                    });
                } catch (NacosException e) {
                    LOGGER.error("Get metrics fail,member={}", member.getAddress(), e);
                    countDownLatch.countDown();
                }
            } else {
                countDownLatch.countDown();
            }
        }
        
        try {
            ServerLoaderInfoResponse handle = serverLoaderInfoRequestHandler
                    .handle(new ServerLoaderInfoRequest(), new RequestMeta());
            ServerLoaderMetrics metrics = new ServerLoaderMetrics();
            metrics.setAddress(serverMemberManager.getSelf().getAddress());
            metrics.setMetric(handle.getLoaderMetrics());
            responseList.add(metrics);
        } catch (NacosException e) {
            LOGGER.error("Get self metrics fail", e);
        }
        
        try {
            countDownLatch.await(1000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            LOGGER.warn("Get  metrics timeout,metrics info may not complete.");
        }
        int max = 0;
        int min = -1;
        int total = 0;
        
        for (ServerLoaderMetrics serverLoaderMetrics : responseList) {
            String sdkConCountStr = serverLoaderMetrics.getMetric().get("sdkConCount");
            
            if (StringUtils.isNotBlank(sdkConCountStr)) {
                int sdkConCount = Integer.parseInt(sdkConCountStr);
                if (max == 0 || max < sdkConCount) {
                    max = sdkConCount;
                }
                if (min == -1 || sdkConCount < min) {
                    min = sdkConCount;
                }
                total += sdkConCount;
            }
        }
        Map<String, Object> responseMap = new HashMap<>(3);
        responseList.sort(Comparator.comparing(ServerLoaderMetrics::getAddress));
        responseMap.put("detail", responseList);
        responseMap.put("memberCount", serverMemberManager.allMembers().size());
        responseMap.put("metricsCount", responseList.size());
        responseMap.put("completed", responseList.size() == serverMemberManager.allMembers().size());
        responseMap.put("max", max);
        responseMap.put("min", min);
        responseMap.put("avg", total / responseList.size());
        responseMap.put("threshold", total / responseList.size() * 1.1);
        responseMap.put("total", total);
        return responseMap;
        
    }
    
    class ServerLoaderMetrics {
        
        String address;
        
        Map<String, String> metric = new HashMap<>();
        
        /**
         * Getter method for property <tt>address</tt>.
         *
         * @return property value of address
         */
        public String getAddress() {
            return address;
        }
        
        /**
         * Setter method for property <tt>address</tt>.
         *
         * @param address value to be assigned to property address
         */
        public void setAddress(String address) {
            this.address = address;
        }
        
        /**
         * Getter method for property <tt>metric</tt>.
         *
         * @return property value of metric
         */
        public Map<String, String> getMetric() {
            return metric;
        }
        
        /**
         * Setter method for property <tt>metric</tt>.
         *
         * @param metric value to be assigned to property metric
         */
        public void setMetric(Map<String, String> metric) {
            this.metric = metric;
        }
    }
    
    private static String getRemoteIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader(X_FORWARDED_FOR);
        if (!org.apache.commons.lang3.StringUtils.isBlank(xForwardedFor)) {
            return xForwardedFor.split(X_FORWARDED_FOR_SPLIT_SYMBOL)[0].trim();
        }
        String nginxHeader = request.getHeader(X_REAL_IP);
        return org.apache.commons.lang3.StringUtils.isBlank(nginxHeader) ? request.getRemoteAddr() : nginxHeader;
    }
}
