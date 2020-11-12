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

package com.alibaba.nacos.console.controller;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.api.remote.request.ServerLoaderInfoRequest;
import com.alibaba.nacos.api.remote.request.ServerReloadRequest;
import com.alibaba.nacos.api.remote.response.ServerLoaderInfoResponse;
import com.alibaba.nacos.api.remote.response.ServerReloadResponse;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.auth.common.ActionTypes;
import com.alibaba.nacos.common.executor.ExecutorFactory;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.config.server.utils.LogUtil;
import com.alibaba.nacos.console.security.nacos.NacosAuthConfig;
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.MemberUtils;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.core.cluster.remote.ClusterRpcClientProxy;
import com.alibaba.nacos.core.remote.Connection;
import com.alibaba.nacos.core.remote.ConnectionManager;
import com.alibaba.nacos.core.remote.core.ServerLoaderInfoRequestHandler;
import com.alibaba.nacos.core.utils.RemoteUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * controller to controll server loader.
 *
 * @author liuzunfei
 * @version $Id: ServerLoaderController.java, v 0.1 2020年07月22日 4:28 PM liuzunfei Exp $
 */
@RestController
@RequestMapping("/v1/console/loader")
public class ServerLoaderController {
    
    @Autowired
    private ConnectionManager connectionManager;
    
    @Autowired
    private ServerMemberManager serverMemberManager;
    
    @Autowired
    private ClusterRpcClientProxy clusterRpcClientProxy;
    
    @Autowired
    private ServerLoaderInfoRequestHandler serverLoaderInfoRequestHandler;
    
    static ScheduledExecutorService executorService = ExecutorFactory
            .newScheduledExecutorService(Runtime.getRuntime().availableProcessors(), new ThreadFactory() {
                @Override
                public Thread newThread(Runnable r) {
                    String threadName = "nacos.core.server.cluster.Thread";
                    Thread thread = new Thread(r, threadName);
                    thread.setDaemon(true);
                    return thread;
                }
            });
    
    /**
     * Get server state of current server.
     *
     * @return state json.
     */
    @Secured(resource = NacosAuthConfig.CONSOLE_RESOURCE_NAME_PREFIX + "loader", action = ActionTypes.WRITE)
    @GetMapping("/max")
    public ResponseEntity updateMaxClients(@RequestParam Integer count) {
        Map<String, String> responseMap = new HashMap<>(3);
        connectionManager.coordinateMaxClientsSmoth(count);
        return ResponseEntity.ok().body("success");
    }
    
    /**
     * Get server state of current server.
     *
     * @return state json.
     */
    @Secured(resource = NacosAuthConfig.CONSOLE_RESOURCE_NAME_PREFIX + "loader", action = ActionTypes.WRITE)
    @GetMapping("/reload")
    public ResponseEntity reloadCount(@RequestParam Integer count,
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
    @Secured(resource = NacosAuthConfig.CONSOLE_RESOURCE_NAME_PREFIX + "loader", action = ActionTypes.WRITE)
    @GetMapping("/reloadsingle")
    public ResponseEntity reloadSingle(@RequestParam String connectionId,
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
    @Secured(resource = NacosAuthConfig.CONSOLE_RESOURCE_NAME_PREFIX + "loader", action = ActionTypes.READ)
    @GetMapping("/current")
    public ResponseEntity currentClients() {
        Map<String, String> responseMap = new HashMap<>(3);
        Map<String, Connection> stringConnectionMap = connectionManager.currentClients();
        return ResponseEntity.ok().body(stringConnectionMap);
    }
    
    
    /**
     * Get current clients.
     *
     * @return state json.
     */
    @Secured(resource = NacosAuthConfig.CONSOLE_RESOURCE_NAME_PREFIX + "loader", action = ActionTypes.READ)
    @GetMapping("/reloadcluster")
    public ResponseEntity reloadCluster(@RequestParam Integer count) {
        String result = "ok";
        Map<String, Object> serverLoadMetrics = getServerLoadMetrics();
        Object avgString = (Object) serverLoadMetrics.get("avg");
        if (avgString != null && NumberUtils.isDigits(avgString.toString())) {
            int avg = Integer.valueOf(avgString.toString());
            if (count < avg * RemoteUtils.LOADER_FACTOR + 1) {
                result = " loader count must be 10% larger than avg,avg current is " + avg;
            } else {
                reloadClusterInner(count);
            }
        } else {
            result = "can't get cluster metric";
        }
        return ResponseEntity.ok().body(result);
    }
    
    private void reloadClusterInner(int reloadcount) {
        
        CompletionService<ServerReloadResponse> completionService = new ExecutorCompletionService<ServerReloadResponse>(
                executorService);
        ServerReloadRequest serverLoaderInfoRequest = new ServerReloadRequest();
        serverLoaderInfoRequest.setReloadCount(reloadcount);
        int count = 0;
        for (Member member : serverMemberManager.allMembersWithoutSelf()) {
            if (MemberUtils.isSupportedLongCon(member)) {
                count++;
                completionService.submit(new ServerReLoaderRpcTask(serverLoaderInfoRequest, member));
            }
        }
        
        List<ServerLoaderMetris> responseList = new LinkedList<ServerLoaderMetris>();
        
        try {
            ServerLoaderInfoResponse handle = serverLoaderInfoRequestHandler
                    .handle(new ServerLoaderInfoRequest(), new RequestMeta());
            ServerLoaderMetris metris = new ServerLoaderMetris();
            metris.setAddress(serverMemberManager.getSelf().getAddress());
            metris.setMetric(handle.getLoaderMetrics());
            responseList.add(metris);
        } catch (NacosException e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Get current clients.
     *
     * @return state json.
     */
    @Secured(resource = NacosAuthConfig.CONSOLE_RESOURCE_NAME_PREFIX + "loader", action = ActionTypes.READ)
    @GetMapping("/clustermetric")
    public ResponseEntity clusterLoader() {
    
        Map<String, Object> serverLoadMetrics = getServerLoadMetrics();
    
        return ResponseEntity.ok().body(serverLoadMetrics);
    }
    
    private Map<String, Object> getServerLoadMetrics() {
        
        CompletionService<ServerLoaderMetris> completionService = new ExecutorCompletionService<ServerLoaderMetris>(
                executorService);
        
        int count = 0;
        for (Member member : serverMemberManager.allMembersWithoutSelf()) {
            if (MemberUtils.isSupportedLongCon(member)) {
                count++;
                ServerLoaderInfoRequest serverLoaderInfoRequest = new ServerLoaderInfoRequest();
                completionService.submit(new ServerLoaderInfoRpcTask(serverLoaderInfoRequest, member));
            }
        }
        
        List<ServerLoaderMetris> responseList = new LinkedList<ServerLoaderMetris>();
        
        try {
            ServerLoaderInfoResponse handle = serverLoaderInfoRequestHandler
                    .handle(new ServerLoaderInfoRequest(), new RequestMeta());
            ServerLoaderMetris metris = new ServerLoaderMetris();
            metris.setAddress(serverMemberManager.getSelf().getAddress());
            metris.setMetric(handle.getLoaderMetrics());
            responseList.add(metris);
        } catch (NacosException e) {
            e.printStackTrace();
        }
        
        for (int i = 0; i < count; i++) {
            try {
                Future<ServerLoaderMetris> f = completionService.poll(1000, TimeUnit.MILLISECONDS);
                try {
                    if (f != null) {
                        ServerLoaderMetris response = f.get(500, TimeUnit.MILLISECONDS);
                        if (response != null) {
                            responseList.add(response);
                        }
                    }
                } catch (TimeoutException e) {
                    if (f != null) {
                        f.cancel(true);
                    }
                }
            } catch (InterruptedException e) {
                LogUtil.DEFAULT_LOG.warn("get task result with InterruptedException: {} ", e.getMessage());
            } catch (ExecutionException e) {
                LogUtil.DEFAULT_LOG.warn("get task result with ExecutionException: {} ", e.getMessage());
            }
        }
        
        Map<String, Object> responseMap = new HashMap<>(3);
        
        responseMap.put("detail", responseList);
        int max = 0;
        int min = -1;
        int total = 0;
        
        for (ServerLoaderMetris serverLoaderMetris : responseList) {
            String sdkConCountStr = serverLoaderMetris.getMetric().get("sdkConCount");
            
            if (StringUtils.isNotBlank(sdkConCountStr)) {
                int sdkConCount = Integer.valueOf(sdkConCountStr);
                if (max == 0 || max < sdkConCount) {
                    max = sdkConCount;
                }
                if (min == -1 || sdkConCount < min) {
                    min = sdkConCount;
                }
                total += sdkConCount;
            }
        }
        responseMap.put("max", max);
        responseMap.put("min", min);
        responseMap.put("avg", total / responseList.size());
        responseMap.put("threshold", total / responseList.size() * 1.1);
        responseMap.put("total", total);
        
        return responseMap;
        
    }
    
    class ServerLoaderInfoRpcTask implements Callable<ServerLoaderMetris> {
        
        ServerLoaderInfoRequest request;
        
        Member member;
        
        public ServerLoaderInfoRpcTask(ServerLoaderInfoRequest t, Member member) {
            this.request = t;
            this.member = member;
        }
        
        @Override
        public ServerLoaderMetris call() throws Exception {
            
            ServerLoaderInfoResponse response = (ServerLoaderInfoResponse) clusterRpcClientProxy
                    .sendRequest(this.member, this.request);
            ServerLoaderMetris metris = new ServerLoaderMetris();
            metris.setAddress(member.getAddress());
            metris.setMetric(response.getLoaderMetrics());
            return metris;
        }
    }
    
    class ServerLoaderMetris {
        
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
    
    class ServerReLoaderRpcTask implements Callable<ServerReloadResponse> {
        
        ServerReloadRequest request;
        
        Member member;
        
        public ServerReLoaderRpcTask(ServerReloadRequest t, Member member) {
            this.request = t;
            this.member = member;
        }
        
        @Override
        public ServerReloadResponse call() throws Exception {
            
            ServerReloadResponse response = (ServerReloadResponse) clusterRpcClientProxy
                    .sendRequest(this.member, this.request);
            return response;
        }
    }
}
