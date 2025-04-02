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
import com.alibaba.nacos.api.model.response.ServerLoaderMetrics;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.core.remote.Connection;
import com.alibaba.nacos.core.service.NacosServerLoaderService;
import com.alibaba.nacos.core.utils.WebUtils;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.ApiType;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

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
    
    private final NacosServerLoaderService serverLoaderService;
    
    public ServerLoaderControllerV3(NacosServerLoaderService serverLoaderService) {
        this.serverLoaderService = serverLoaderService;
    }
    
    /**
     * Get current clients.
     *
     * @return state json.
     */
    @GetMapping("/current")
    @Secured(resource = NACOS_ADMIN_CORE_CONTEXT_V3 + "/loader", action = ActionTypes.READ, apiType = ApiType.ADMIN_API)
    public Result<Map<String, Connection>> currentClients() {
        return Result.success(serverLoaderService.getAllClients());
    }
    
    /**
     * Rebalance the number of sdk connections on the current server.
     *
     * @return state json.
     */
    @Secured(resource = NACOS_ADMIN_CORE_CONTEXT_V3
            + "/loader", action = ActionTypes.WRITE, apiType = ApiType.ADMIN_API)
    @PostMapping("/reloadCurrent")
    public Result<String> reloadCount(@RequestParam Integer count,
            @RequestParam(value = "redirectAddress", required = false) String redirectAddress) {
        serverLoaderService.reloadCount(count, redirectAddress);
        return Result.success();
    }
    
    /**
     * According to the total number of sdk connections of all nodes in the nacos cluster, intelligently balance the
     * number of sdk connections of each node in the nacos cluster.
     *
     * @return state json.
     */
    @PostMapping("/smartReloadCluster")
    @Secured(resource = NACOS_ADMIN_CORE_CONTEXT_V3
            + "/loader", action = ActionTypes.WRITE, apiType = ApiType.ADMIN_API)
    public Result<String> smartReload(HttpServletRequest request,
            @RequestParam(value = "loaderFactor", defaultValue = "0.1f") String loaderFactorStr) {
        LOGGER.info("Smart reload request receive,requestIp={}", WebUtils.getRemoteIp(request));
        float loaderFactor = Float.parseFloat(loaderFactorStr);
        if (!serverLoaderService.smartReload(loaderFactor)) {
            return Result.failure(ErrorCode.SERVER_ERROR, "Smart reload failed, please try again later.");
        }
        return Result.success();
    }
    
    /**
     * Send a ConnectResetRequest to this connection according to the sdk connection ID.
     *
     * @return state json.
     */
    @PostMapping("/reloadClient")
    @Secured(resource = NACOS_ADMIN_CORE_CONTEXT_V3
            + "/loader", action = ActionTypes.WRITE, apiType = ApiType.ADMIN_API)
    public Result<String> reloadSingle(@RequestParam String connectionId,
            @RequestParam(value = "redirectAddress", required = false) String redirectAddress) {
        serverLoaderService.reloadClient(connectionId, redirectAddress);
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
        return Result.success(serverLoaderService.getServerLoaderMetrics());
    }
}
