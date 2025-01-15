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

package com.alibaba.nacos.core.controller.v3;

import com.alibaba.nacos.api.annotation.NacosApi;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.api.remote.RequestCallBack;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.api.remote.request.ServerLoaderInfoRequest;
import com.alibaba.nacos.api.remote.request.ServerReloadRequest;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.api.remote.response.ServerLoaderInfoResponse;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.MemberUtil;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.core.cluster.remote.ClusterRpcClientProxy;
import com.alibaba.nacos.core.model.response.ServerLoaderMetric;
import com.alibaba.nacos.core.model.response.ServerLoaderMetrics;
import com.alibaba.nacos.core.remote.Connection;
import com.alibaba.nacos.core.remote.ConnectionManager;
import com.alibaba.nacos.core.remote.core.ServerLoaderInfoRequestHandler;
import com.alibaba.nacos.core.remote.core.ServerReloaderRequestHandler;
import com.alibaba.nacos.core.utils.WebUtils;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.ApiType;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.alibaba.nacos.core.utils.Commons.NACOS_ADMIN_CORE_CONTEXT_V3;

/**
 * controller to control server loader v3.
 *
 * @author yunye
 * @since 3.0.0
 */
@NacosApi
@RestController
@RequestMapping(NACOS_ADMIN_CORE_CONTEXT_V3 + "/loader")
@SuppressWarnings("PMD.MethodTooLongRule")
public class ServerLoaderControllerV3 {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ServerLoaderControllerV3.class);
    
    private final ConnectionManager connectionManager;
    
    private final ServerMemberManager serverMemberManager;
    
    private final ClusterRpcClientProxy clusterRpcClientProxy;
    
    private final ServerReloaderRequestHandler serverReloaderRequestHandler;
    
    private final ServerLoaderInfoRequestHandler serverLoaderInfoRequestHandler;
    
    public ServerLoaderControllerV3(ConnectionManager connectionManager, ServerMemberManager serverMemberManager,
            ClusterRpcClientProxy clusterRpcClientProxy, ServerReloaderRequestHandler serverReloaderRequestHandler,
            ServerLoaderInfoRequestHandler serverLoaderInfoRequestHandler) {
        this.connectionManager = connectionManager;
        this.serverMemberManager = serverMemberManager;
        this.clusterRpcClientProxy = clusterRpcClientProxy;
        this.serverReloaderRequestHandler = serverReloaderRequestHandler;
        this.serverLoaderInfoRequestHandler = serverLoaderInfoRequestHandler;
    }
    
    /**
     * Get current clients.
     *
     * @return state json.
     */
    @GetMapping("/current")
    @Secured(resource = NACOS_ADMIN_CORE_CONTEXT_V3 + "/loader", action = ActionTypes.READ, apiType = ApiType.ADMIN_API)
    public Result<Map<String, Connection>> currentClients() {
        Map<String, Connection> stringConnectionMap = connectionManager.currentClients();
        return Result.success(stringConnectionMap);
    }
    
    /**
     * Rebalance the number of sdk connections on the current server.
     *
     * @return state json.
     */
    @Secured(resource = NACOS_ADMIN_CORE_CONTEXT_V3
            + "/loader", action = ActionTypes.WRITE, apiType = ApiType.ADMIN_API)
    @GetMapping("/reloadCurrent")
    public Result<String> reloadCount(@RequestParam Integer count,
            @RequestParam(value = "redirectAddress", required = false) String redirectAddress) {
        connectionManager.loadCount(count, redirectAddress);
        return Result.success();
    }
    
    /**
     * According to the total number of sdk connections of all nodes in the nacos cluster, intelligently balance the
     * number of sdk connections of each node in the nacos cluster.
     *
     * @return state json.
     */
    @GetMapping("/smartReloadCluster")
    @Secured(resource = NACOS_ADMIN_CORE_CONTEXT_V3
            + "/loader", action = ActionTypes.WRITE, apiType = ApiType.ADMIN_API)
    public Result<String> smartReload(HttpServletRequest request,
            @RequestParam(value = "loaderFactor", defaultValue = "0.1f") String loaderFactorStr) {
        
        LOGGER.info("Smart reload request receive,requestIp={}", WebUtils.getRemoteIp(request));
        
        ServerLoaderMetrics serverLoadMetrics = getServerLoadMetrics();
        List<ServerLoaderMetric> details = serverLoadMetrics.getDetail();
        float loaderFactor = Float.parseFloat(loaderFactorStr);
        int overLimitCount = (int) (serverLoadMetrics.getAvg() * (1 + loaderFactor));
        int lowLimitCount = (int) (serverLoadMetrics.getAvg() * (1 - loaderFactor));
        
        List<ServerLoaderMetric> overLimitServer = new ArrayList<>();
        List<ServerLoaderMetric> lowLimitServer = new ArrayList<>();
        
        for (ServerLoaderMetric metric : details) {
            int sdkCount = metric.getSdkConCount();
            if (sdkCount > overLimitCount) {
                overLimitServer.add(metric);
            }
            if (sdkCount < lowLimitCount) {
                lowLimitServer.add(metric);
            }
        }
        
        // desc by sdkConCount
        overLimitServer.sort((o1, o2) -> {
            Integer sdkCount1 = o1.getSdkConCount();
            Integer sdkCount2 = o2.getSdkConCount();
            return sdkCount2.compareTo(sdkCount1);
        });
        
        LOGGER.info("Over load limit server list ={}", overLimitServer);
        
        //asc by sdkConCount
        lowLimitServer.sort((o1, o2) -> {
            Integer sdkCount1 = o1.getSdkConCount();
            Integer sdkCount2 = o2.getSdkConCount();
            return sdkCount1.compareTo(sdkCount2);
        });
        
        LOGGER.info("Low load limit server list ={}", lowLimitServer);
        AtomicBoolean result = new AtomicBoolean(true);
        
        for (int i = 0; i < overLimitServer.size() & i < lowLimitServer.size(); i++) {
            ServerReloadRequest serverLoaderInfoRequest = new ServerReloadRequest();
            serverLoaderInfoRequest.setReloadCount(overLimitCount);
            serverLoaderInfoRequest.setReloadServer(lowLimitServer.get(i).getAddress());
            Member member = serverMemberManager.find(overLimitServer.get(i).getAddress());
            
            LOGGER.info("Reload task submit ,fromServer ={},toServer={}, ", overLimitServer.get(i).getAddress(),
                    lowLimitServer.get(i).getAddress());
            
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
        
        return result.get() ? Result.success() : Result.failure(ErrorCode.SERVER_ERROR);
    }
    
    /**
     * Send a ConnectResetRequest to this connection according to the sdk connection ID.
     *
     * @return state json.
     */
    @GetMapping("/reloadClient")
    @Secured(resource = NACOS_ADMIN_CORE_CONTEXT_V3
            + "/loader", action = ActionTypes.WRITE, apiType = ApiType.ADMIN_API)
    public Result<String> reloadSingle(@RequestParam String connectionId,
            @RequestParam(value = "redirectAddress", required = false) String redirectAddress) {
        connectionManager.loadSingle(connectionId, redirectAddress);
        return Result.success();
    }
    
    /**
     * Get current clients.
     *
     * @return state json.
     */
    @GetMapping("/cluster")
    @Secured(resource = NACOS_ADMIN_CORE_CONTEXT_V3 + "/loader", action = ActionTypes.READ, apiType = ApiType.ADMIN_API)
    public Result<ServerLoaderMetrics> loaderMetrics() {
        return Result.success(getServerLoadMetrics());
    }
    
    private ServerLoaderMetrics getServerLoadMetrics() {
        
        List<ServerLoaderMetric> responseList = new CopyOnWriteArrayList<>();
        
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
                                ServerLoaderMetric.Builder builder = ServerLoaderMetric.Builder.newBuilder();
                                builder.withAddress(member.getAddress())
                                        .convertFromMap(((ServerLoaderInfoResponse) response).getLoaderMetrics());
                                responseList.add(builder.build());
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
            ServerLoaderInfoResponse handle = serverLoaderInfoRequestHandler.handle(new ServerLoaderInfoRequest(),
                    new RequestMeta());
            ServerLoaderMetric.Builder builder = ServerLoaderMetric.Builder.newBuilder();
            builder.withAddress(serverMemberManager.getSelf().getAddress()).convertFromMap(handle.getLoaderMetrics());
            responseList.add(builder.build());
        } catch (NacosException e) {
            LOGGER.error("Get self metrics fail", e);
        }
        
        try {
            countDownLatch.await(1000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            LOGGER.warn("Get  metrics timeout,metrics info may not complete.");
        }
        int max = Integer.MIN_VALUE;
        int min = Integer.MAX_VALUE;
        int total = 0;
        
        for (ServerLoaderMetric serverLoaderMetric : responseList) {
            int sdkConCount = serverLoaderMetric.getSdkConCount();
            
            if (max < sdkConCount) {
                max = sdkConCount;
            }
            if (min > sdkConCount) {
                min = sdkConCount;
            }
            total += sdkConCount;
        }
        
        responseList.sort(Comparator.comparing(ServerLoaderMetric::getAddress));
        ServerLoaderMetrics serverLoaderMetrics = new ServerLoaderMetrics();
        serverLoaderMetrics.setDetail(responseList);
        serverLoaderMetrics.setMemberCount(serverMemberManager.allMembers().size());
        serverLoaderMetrics.setMetricsCount(responseList.size());
        serverLoaderMetrics.setCompleted(responseList.size() == serverMemberManager.allMembers().size());
        serverLoaderMetrics.setMax(max);
        serverLoaderMetrics.setMin(min);
        serverLoaderMetrics.setAvg(total / responseList.size());
        serverLoaderMetrics.setThreshold(String.valueOf(serverLoaderMetrics.getAvg() * 1.1d));
        serverLoaderMetrics.setTotal(total);
        
        return serverLoaderMetrics;
    }
}
