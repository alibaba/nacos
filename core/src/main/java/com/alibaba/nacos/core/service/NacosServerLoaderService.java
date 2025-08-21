/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.core.service;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.response.ServerLoaderMetric;
import com.alibaba.nacos.api.model.response.ServerLoaderMetrics;
import com.alibaba.nacos.api.remote.RequestCallBack;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.api.remote.request.ServerLoaderInfoRequest;
import com.alibaba.nacos.api.remote.request.ServerReloadRequest;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.api.remote.response.ServerLoaderInfoResponse;
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.core.cluster.remote.ClusterRpcClientProxy;
import com.alibaba.nacos.core.remote.Connection;
import com.alibaba.nacos.core.remote.ConnectionManager;
import com.alibaba.nacos.core.remote.core.ServerLoaderInfoRequestHandler;
import com.alibaba.nacos.core.remote.core.ServerReloaderRequestHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Nacos Service loader service.
 *
 * @author xiweng.yy
 */
@Service
public class NacosServerLoaderService {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(NacosServerLoaderService.class);
    
    private final ConnectionManager connectionManager;
    
    private final ServerMemberManager serverMemberManager;
    
    private final ClusterRpcClientProxy clusterRpcClientProxy;
    
    private final ServerReloaderRequestHandler serverReloaderRequestHandler;
    
    private final ServerLoaderInfoRequestHandler serverLoaderInfoRequestHandler;
    
    public NacosServerLoaderService(ConnectionManager connectionManager, ServerMemberManager serverMemberManager,
            ClusterRpcClientProxy clusterRpcClientProxy, ServerReloaderRequestHandler serverReloaderRequestHandler,
            ServerLoaderInfoRequestHandler serverLoaderInfoRequestHandler) {
        this.connectionManager = connectionManager;
        this.serverMemberManager = serverMemberManager;
        this.clusterRpcClientProxy = clusterRpcClientProxy;
        this.serverReloaderRequestHandler = serverReloaderRequestHandler;
        this.serverLoaderInfoRequestHandler = serverLoaderInfoRequestHandler;
    }
    
    /**
     * Get all current clients upper 2.0 Client which connected by gRPC.
     *
     * @return all current clients
     */
    public Map<String, Connection> getAllClients() {
        return connectionManager.currentClients();
    }
    
    /**
     * Reload single client connection to other server nodes.
     *
     * @param connectionId    the client want to be reload
     * @param redirectAddress expected redirect address. optional, if no setting, will random redirect to other server
     */
    public void reloadClient(String connectionId, String redirectAddress) {
        connectionManager.loadSingle(connectionId, redirectAddress);
    }
    
    /**
     * Reload client connect to other server nodes by remain count.
     *
     * @param count           remain connection counts, server will try to reload ${current count} - ${count} clients to
     *                        other server
     * @param redirectAddress expected redirect address. optional, if no setting, will random redirect to other server
     */
    public void reloadCount(int count, String redirectAddress) {
        connectionManager.loadCount(count, redirectAddress);
    }
    
    /**
     * According to the total number of sdk connections of all nodes in the nacos cluster, intelligently balance the
     * number of sdk connections of each node in the nacos cluster.
     * <p>
     * Server will calculate a low limit and a upper limit of sdk connections by loaderFactor and avg connection in all
     * cluster. the low limit is avg connection * (1 - loaderFactor), the upper limit is avg connection * (1 +
     * loaderFactor) connection count is upper than upper limit, server will try to reload sdk connections to other
     * server nodes which connection lower than low limit.
     * </p>
     *
     * @param loaderFactor load factor, the default value is 0.1f, the value range is [0,1]
     * @return {@code true} smart reload success, {@code false} smart reload fail
     */
    public boolean smartReload(float loaderFactor) {
        ServerLoaderMetrics serverLoadMetrics = getServerLoaderMetrics();
        List<ServerLoaderMetric> details = serverLoadMetrics.getDetail();
        int overLimitCount = (int) (serverLoadMetrics.getAvg() * (1 + loaderFactor));
        int lowLimitCount = (int) (serverLoadMetrics.getAvg() * (1 - loaderFactor));
        List<ServerLoaderMetric> overLimitServer = details.stream()
                .filter(metric -> metric.getSdkConCount() > overLimitCount).collect(Collectors.toList());
        List<ServerLoaderMetric> lowLimitServer = details.stream()
                .filter(metric -> metric.getSdkConCount() < lowLimitCount).collect(Collectors.toList());
        overLimitServer.sort(Comparator.comparingInt(ServerLoaderMetric::getSdkConCount).reversed());
        LOGGER.info("Over load limit server list ={}", overLimitServer);
        lowLimitServer.sort(Comparator.comparingInt(ServerLoaderMetric::getSdkConCount));
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
        
        return result.get();
    }
    
    /**
     * Get server loader metrics.
     *
     * @return server loader metrics for nacos server cluster.
     */
    public ServerLoaderMetrics getServerLoaderMetrics() {
        List<ServerLoaderMetric> responseList = new CopyOnWriteArrayList<>();
        int memberSize = serverMemberManager.allMembersWithoutSelf().size();
        CountDownLatch countDownLatch = new CountDownLatch(memberSize);
        for (Member member : serverMemberManager.allMembersWithoutSelf()) {
            ServerLoaderInfoRequest serverLoaderInfoRequest = new ServerLoaderInfoRequest();
            ServerLoaderMetricCallBack callBack = new ServerLoaderMetricCallBack(member, responseList, countDownLatch);
            try {
                clusterRpcClientProxy.asyncRequest(member, serverLoaderInfoRequest, callBack);
            } catch (NacosException e) {
                LOGGER.error("Get metrics fail,member={}", member.getAddress(), e);
                countDownLatch.countDown();
            }
        }
        responseList.add(getSelfServerLoaderMetric());
        waitAsyncGetLoaderMetricFinish(countDownLatch);
        int max = responseList.stream().mapToInt(ServerLoaderMetric::getSdkConCount).max().orElse(0);
        int min = responseList.stream().mapToInt(ServerLoaderMetric::getSdkConCount).min().orElse(0);
        int total = responseList.stream().mapToInt(ServerLoaderMetric::getSdkConCount).sum();
        responseList.sort(Comparator.comparing(ServerLoaderMetric::getAddress));
        return buildMetrics(responseList, max, min, total);
    }
    
    private ServerLoaderMetric getSelfServerLoaderMetric() {
        ServerLoaderMetric.Builder builder = ServerLoaderMetric.Builder.newBuilder();
        builder.withAddress(serverMemberManager.getSelf().getAddress());
        try {
            ServerLoaderInfoResponse handle = serverLoaderInfoRequestHandler.handle(new ServerLoaderInfoRequest(),
                    new RequestMeta());
            builder.convertFromMap(handle.getLoaderMetrics());
        } catch (NacosException e) {
            LOGGER.error("Get self metrics fail", e);
        }
        return builder.build();
    }
    
    private void waitAsyncGetLoaderMetricFinish(CountDownLatch countDownLatch) {
        try {
            countDownLatch.await(1000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            LOGGER.warn("Get  metrics timeout,metrics info may not complete.");
        }
    }
    
    private ServerLoaderMetrics buildMetrics(List<ServerLoaderMetric> responseList, int max, int min, int total) {
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
    
    private static class ServerLoaderMetricCallBack implements RequestCallBack<Response> {
        
        private final Member member;
        
        private final List<ServerLoaderMetric> responseList;
        
        private final CountDownLatch countDownLatch;
        
        private ServerLoaderMetricCallBack(Member member, List<ServerLoaderMetric> responseList,
                CountDownLatch countDownLatch) {
            this.member = member;
            this.responseList = responseList;
            this.countDownLatch = countDownLatch;
        }
        
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
    }
}
