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
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.api.naming.pojo.maintainer.ClientPublisherInfo;
import com.alibaba.nacos.api.naming.pojo.maintainer.ClientServiceInfo;
import com.alibaba.nacos.api.naming.pojo.maintainer.ClientSubscriberInfo;
import com.alibaba.nacos.api.naming.pojo.maintainer.ClientSummaryInfo;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.core.paramcheck.ExtractorManager;
import com.alibaba.nacos.naming.core.ClientService;
import com.alibaba.nacos.naming.core.v2.client.manager.ClientManager;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import com.alibaba.nacos.naming.model.form.ClientServiceForm;
import com.alibaba.nacos.naming.paramcheck.NamingDefaultHttpParamExtractor;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.ApiType;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
@Tag(name = "nacos.admin.naming.client.api.controller.name", description = "nacos.admin.naming.client.api.controller.description")
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
    @Operation(summary = "nacos.admin.naming.client.api.list.summary", description = "nacos.admin.naming.client.api.list.description",
            security = @SecurityRequirement(name = "nacos"))
    @ApiResponse(responseCode = "200", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(implementation = Result.class, example = "nacos.admin.naming.client.api.list.example")))
    public Result<List<String>> getClientList() {
        return Result.success(clientServiceV2Impl.getClientList());
    }
    
    /**
     * Query client by clientId.
     */
    @GetMapping()
    @Secured(resource = UtilsAndCommons.CLIENT_CONTROLLER_V3_ADMIN_PATH, action = ActionTypes.READ, apiType = ApiType.ADMIN_API)
    @Operation(summary = "nacos.admin.naming.client.api.get.summary", description = "nacos.admin.naming.client.api.get.description",
            security = @SecurityRequirement(name = "nacos"))
    @ApiResponse(responseCode = "200", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(implementation = Result.class, example = "nacos.admin.naming.client.api.get.example")))
    @Parameters(value = {@Parameter(name = "clientId", example = "public")})
    public Result<ClientSummaryInfo> getClientDetail(@RequestParam("clientId") String clientId)
            throws NacosApiException {
        checkClientId(clientId);
        return Result.success(clientServiceV2Impl.getClientDetail(clientId));
    }
    
    /**
     * Query the services registered by the specified client.
     */
    @GetMapping("/publish/list")
    @Secured(resource = UtilsAndCommons.CLIENT_CONTROLLER_V3_ADMIN_PATH, action = ActionTypes.READ, apiType = ApiType.ADMIN_API)
    @Operation(summary = "nacos.admin.naming.client.api.publish.list.summary", description = "nacos.admin.naming.client.api.publish.list.description",
            security = @SecurityRequirement(name = "nacos"))
    @ApiResponse(responseCode = "200", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(implementation = Result.class, example = "nacos.admin.naming.client.api.publish.list.example")))
    @Parameters(value = {@Parameter(name = "clientId", example = "public")})
    public Result<List<ClientServiceInfo>> getPublishedServiceList(@RequestParam("clientId") String clientId)
            throws NacosApiException {
        checkClientId(clientId);
        return Result.success(clientServiceV2Impl.getPublishedServiceList(clientId));
    }
    
    /**
     * Query the services to which the specified client subscribes.
     */
    @GetMapping("/subscribe/list")
    @Secured(resource = UtilsAndCommons.CLIENT_CONTROLLER_V3_ADMIN_PATH, action = ActionTypes.READ, apiType = ApiType.ADMIN_API)
    @Operation(summary = "nacos.admin.naming.client.api.subscribe.list.summary",
            description = "nacos.admin.naming.client.api.subscribe.list.description", security = @SecurityRequirement(name = "nacos"))
    @ApiResponse(responseCode = "200", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(implementation = Result.class, example = "nacos.admin.naming.client.api.subscribe.list.example")))
    @Parameters(value = {@Parameter(name = "clientId", example = "public")})
    public Result<List<ClientServiceInfo>> getSubscribeServiceList(@RequestParam("clientId") String clientId)
            throws NacosApiException {
        checkClientId(clientId);
        return Result.success(clientServiceV2Impl.getSubscribeServiceList(clientId));
    }
    
    /**
     * Query the clients that have registered the specified service.
     */
    @GetMapping("/service/publisher/list")
    @Secured(resource = UtilsAndCommons.CLIENT_CONTROLLER_V3_ADMIN_PATH, action = ActionTypes.READ, apiType = ApiType.ADMIN_API)
    @Operation(summary = "nacos.admin.naming.client.api.service.publisher.list.summary",
            description = "nacos.admin.naming.client.api.service.publisher.list.description", security = @SecurityRequirement(name = "nacos"))
    @ApiResponse(responseCode = "200", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(implementation = Result.class, example = "nacos.admin.naming.client.api.service.publisher.list.example")))
    @Parameters(value = {@Parameter(name = "namespaceId", example = "public"),
            @Parameter(name = "groupName", example = "DEFAULT_GROUP"),
            @Parameter(name = "serviceName", required = true, example = "nacos.test.1"), @Parameter(name = "ip"),
            @Parameter(name = "port"), @Parameter(name = "clientServiceForm", hidden = true)})
    public Result<List<ClientPublisherInfo>> getPublishedClientList(ClientServiceForm clientServiceForm)
            throws NacosApiException {
        clientServiceForm.validate();
        return Result.success(clientServiceV2Impl.getPublishedClientList(clientServiceForm.getNamespaceId(),
                clientServiceForm.getGroupName(), clientServiceForm.getServiceName(), clientServiceForm.getIp(),
                clientServiceForm.getPort()));
    }
    
    /**
     * Query the clients that are subscribed to the specified service.
     */
    @GetMapping("/service/subscriber/list")
    @Secured(resource = UtilsAndCommons.CLIENT_CONTROLLER_V3_ADMIN_PATH, action = ActionTypes.READ, apiType = ApiType.ADMIN_API)
    @Operation(summary = "nacos.admin.naming.client.api.service.subscriber.list.summary",
            description = "nacos.admin.naming.client.api.service.subscriber.list.description", security = @SecurityRequirement(name = "nacos"))
    @ApiResponse(responseCode = "200", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(implementation = Result.class, example = "nacos.admin.naming.client.api.service.subscriber.list.example")))
    @Parameters(value = {@Parameter(name = "namespaceId", example = "public"),
            @Parameter(name = "groupName", example = "DEFAULT_GROUP"),
            @Parameter(name = "serviceName", required = true, example = "nacos.test.1"), @Parameter(name = "ip"),
            @Parameter(name = "port"), @Parameter(name = "clientServiceForm", hidden = true)})
    public Result<List<ClientSubscriberInfo>> getSubscribeClientList(ClientServiceForm clientServiceForm)
            throws NacosApiException {
        clientServiceForm.validate();
        return Result.success(clientServiceV2Impl.getSubscribeClientList(clientServiceForm.getNamespaceId(),
                clientServiceForm.getGroupName(), clientServiceForm.getServiceName(), clientServiceForm.getIp(),
                clientServiceForm.getPort()));
    }
    
    /**
     * Query the responsible server for a given client based on its IP and port.
     */
    @GetMapping("/distro")
    @Secured(resource = UtilsAndCommons.CLIENT_CONTROLLER_V3_ADMIN_PATH, action = ActionTypes.READ, apiType = ApiType.ADMIN_API)
    @Operation(summary = "nacos.admin.naming.client.api.distro.summary", description = "nacos.admin.naming.client.api.distro.description",
            security = @SecurityRequirement(name = "nacos"))
    @ApiResponse(responseCode = "200", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(implementation = Result.class, example = "nacos.admin.naming.client.api.distro.example")))
    @Parameters(value = {@Parameter(name = "ip", required = true, example = "127.0.0.1"),
            @Parameter(name = "port", required = true, example = "8080")})
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