/*
 * Copyright 1999-$toady.year Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.naming.controllers.v3;

import com.alibaba.nacos.api.annotation.NacosApi;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.core.paramcheck.ExtractorManager;
import com.alibaba.nacos.naming.core.ClientService;
import com.alibaba.nacos.naming.core.v2.client.manager.ClientManager;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import com.alibaba.nacos.naming.paramcheck.NamingDefaultHttpParamExtractor;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.ApiType;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Client controller.
 *
 * @author Nacos
 */

@NacosApi
@RestController
@RequestMapping(UtilsAndCommons.CLIENT_CONTROLLER_V3_ADMIN_PATH)
@ExtractorManager.Extractor(httpExtractor = NamingDefaultHttpParamExtractor.class)
public class ClientControllerV3 {
    
    private final ClientManager clientManager;
    
    private final ClientService clientServiceV2Impl;
    
    public ClientControllerV3(ClientManager clientManager, ClientService clientServiceV2Impl) {
        this.clientManager = clientManager;
        this.clientServiceV2Impl = clientServiceV2Impl;
    }
    
    /**
     * Query all clients.
     */
    @GetMapping("/list")
    @Secured(resource = UtilsAndCommons.CLIENT_CONTROLLER_V3_ADMIN_PATH, action = ActionTypes.READ, apiType = ApiType.ADMIN_API)
    public Result<List<String>> getClientList() {
        return Result.success(clientServiceV2Impl.getClientList());
    }
    
    /**
     * Query client by clientId.
     */
    @GetMapping()
    @Secured(resource = UtilsAndCommons.CLIENT_CONTROLLER_V3_ADMIN_PATH, action = ActionTypes.READ, apiType = ApiType.ADMIN_API)
    public Result<ObjectNode> getClientDetail(@RequestParam("clientId") String clientId) throws NacosApiException {
        checkClientId(clientId);
        return Result.success(clientServiceV2Impl.getClientDetail(clientId));
    }
    
    /**
     * Query the services registered by the specified client.
     */
    @GetMapping("/publish/list")
    @Secured(resource = UtilsAndCommons.CLIENT_CONTROLLER_V3_ADMIN_PATH, action = ActionTypes.READ, apiType = ApiType.ADMIN_API)
    public Result<List<ObjectNode>> getPublishedServiceList(@RequestParam("clientId") String clientId)
            throws NacosApiException {
        checkClientId(clientId);
        return Result.success(clientServiceV2Impl.getPublishedServiceList(clientId));
    }
    
    /**
     * Query the services to which the specified client subscribes.
     */
    @GetMapping("/subscribe/list")
    @Secured(resource = UtilsAndCommons.CLIENT_CONTROLLER_V3_ADMIN_PATH, action = ActionTypes.READ, apiType = ApiType.ADMIN_API)
    public Result<List<ObjectNode>> getSubscribeServiceList(@RequestParam("clientId") String clientId)
            throws NacosApiException {
        checkClientId(clientId);
        return Result.success(clientServiceV2Impl.getSubscribeServiceList(clientId));
    }
    
    /**
     * Query the clients that have registered the specified service.
     */
    @GetMapping("/service/publisher/list")
    @Secured(resource = UtilsAndCommons.CLIENT_CONTROLLER_V3_ADMIN_PATH, action = ActionTypes.READ, apiType = ApiType.ADMIN_API)
    public Result<List<ObjectNode>> getPublishedClientList(
            @RequestParam(value = "namespaceId", required = false, defaultValue = Constants.DEFAULT_NAMESPACE_ID) String namespaceId,
            @RequestParam(value = "groupName", required = false, defaultValue = Constants.DEFAULT_GROUP) String groupName,
            @RequestParam(value = "ephemeral", required = false, defaultValue = "true") Boolean ephemeral,
            @RequestParam("serviceName") String serviceName, @RequestParam(value = "ip", required = false) String ip,
            @RequestParam(value = "port", required = false) Integer port) {
        return Result.success(clientServiceV2Impl.getPublishedClientList(namespaceId, groupName, serviceName, ephemeral, ip, port));
    }
    
    /**
     * Query the clients that are subscribed to the specified service.
     */
    @GetMapping("/service/subscriber/list")
    @Secured(resource = UtilsAndCommons.CLIENT_CONTROLLER_V3_ADMIN_PATH, action = ActionTypes.READ, apiType = ApiType.ADMIN_API)
    public Result<List<ObjectNode>> getSubscribeClientList(
            @RequestParam(value = "namespaceId", required = false, defaultValue = Constants.DEFAULT_NAMESPACE_ID) String namespaceId,
            @RequestParam(value = "groupName", required = false, defaultValue = Constants.DEFAULT_GROUP) String groupName,
            @RequestParam(value = "ephemeral", required = false, defaultValue = "true") Boolean ephemeral,
            @RequestParam("serviceName") String serviceName, @RequestParam(value = "ip", required = false) String ip,
            @RequestParam(value = "port", required = false) Integer port) {
        return Result.success(clientServiceV2Impl.getSubscribeClientList(namespaceId, groupName, serviceName, ephemeral, ip, port));
    }
    
    /**
     * Query the responsible server for a given client based on its IP and port.
     */
    @GetMapping("/distro")
    @Secured(resource = UtilsAndCommons.CLIENT_CONTROLLER_V3_ADMIN_PATH, action = ActionTypes.READ, apiType = ApiType.ADMIN_API)
    public Result<ObjectNode> getResponsibleServer4Client(@RequestParam String ip, @RequestParam String port) {
        return Result.success(clientServiceV2Impl.getResponsibleServer4Client(ip, port));
    }
    
    private void checkClientId(String clientId) throws NacosApiException {
        if (!clientManager.contains(clientId)) {
            throw new NacosApiException(HttpStatus.NOT_FOUND.value(), ErrorCode.RESOURCE_NOT_FOUND,
                    "clientId [ " + clientId + " ] not exist");
        }
    }
}