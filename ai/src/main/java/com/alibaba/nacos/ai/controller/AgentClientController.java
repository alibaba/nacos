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

package com.alibaba.nacos.ai.controller;

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.form.agent.client.AgentDiscoveryForm;
import com.alibaba.nacos.ai.form.agent.client.AgentEndpointDeregistrationForm;
import com.alibaba.nacos.ai.form.agent.client.AgentEndpointRegistrationForm;
import com.alibaba.nacos.ai.form.agent.client.AgentPublishForm;
import com.alibaba.nacos.ai.form.agent.client.AgentSearchForm;
import com.alibaba.nacos.ai.form.agent.client.AgentWatchBatchForm;
import com.alibaba.nacos.ai.param.AgentClientHttpParamExtractor;
import com.alibaba.nacos.ai.service.agent.AgentDiscoveryApplicationService;
import com.alibaba.nacos.ai.service.agent.AgentPublishApplicationService;
import com.alibaba.nacos.ai.service.agent.runtime.AgentHttpClientLifecycleService;
import com.alibaba.nacos.ai.service.agent.watch.AgentHttpWatchService;
import com.alibaba.nacos.api.ai.model.agent.ClientLivenessInfo;
import com.alibaba.nacos.api.ai.model.agent.AgentPublishRequest;
import com.alibaba.nacos.api.ai.model.agent.AgentVersionDetail;
import com.alibaba.nacos.api.ai.model.rad.AgentCatalogEntry;
import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryRequest;
import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryResult;
import com.alibaba.nacos.api.ai.model.rad.AgentEndpointRegistrationBatch;
import com.alibaba.nacos.api.ai.model.rad.AgentSearchRequest;
import com.alibaba.nacos.api.ai.model.rad.AgentWatchBatchRequest;
import com.alibaba.nacos.api.ai.model.rad.AgentWatchBatchResponse;
import com.alibaba.nacos.api.annotation.NacosApi;
import com.alibaba.nacos.api.annotation.Since;
import com.alibaba.nacos.api.common.ApiType;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.common.constant.HttpHeaderConsts;
import com.alibaba.nacos.core.paramcheck.ExtractorManager;
import com.alibaba.nacos.naming.constants.ClientConstants;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.SignType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;

/**
 * RAD Agent Client HTTP API.
 *
 * @author Nacos
 */
@NacosApi
@RestController
@RequestMapping(Constants.Agent.CLIENT_PATH)
@ExtractorManager.Extractor(httpExtractor = AgentClientHttpParamExtractor.class)
public class AgentClientController {
    
    private final AgentDiscoveryApplicationService discoveryService;
    
    private final AgentHttpClientLifecycleService clientLifecycleService;
    
    private final AgentPublishApplicationService publishService;
    
    private final AgentHttpWatchService watchService;
    
    public AgentClientController(AgentDiscoveryApplicationService discoveryService,
        AgentHttpClientLifecycleService clientLifecycleService,
        AgentPublishApplicationService publishService, AgentHttpWatchService watchService) {
        this.discoveryService = discoveryService;
        this.clientLifecycleService = clientLifecycleService;
        this.publishService = publishService;
        this.watchService = watchService;
    }
    
    /**
     * Publish one exact Agent Version from application code.
     */
    @Since("3.3.0")
    @PostMapping
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.OPEN_API)
    public Result<AgentVersionDetail> publish(AgentPublishForm form) throws NacosException {
        AgentPublishRequest request = form.toRequest();
        return Result.success(publishService.publish(form.getNamespaceId(), request));
    }
    
    /**
     * Search visible Agent catalog entries.
     */
    @Since("3.3.0")
    @GetMapping("/search")
    @Secured(action = ActionTypes.READ, signType = SignType.AI, apiType = ApiType.OPEN_API)
    public Result<Page<AgentCatalogEntry>> search(AgentSearchForm form,
        @RequestHeader(name = ClientConstants.HTTP_CLIENT_ID_HEADER,
            required = false) String clientId)
        throws NacosException {
        AgentSearchRequest request = form.toRequest();
        clientLifecycleService.renewForQuery(clientId, request.getNamespaceId());
        return Result.success(discoveryService.search(request));
    }
    
    /**
     * Discover one exact Agent Version and its current Endpoint sets.
     */
    @Since("3.3.0")
    @GetMapping
    @Secured(action = ActionTypes.READ, signType = SignType.AI, apiType = ApiType.OPEN_API)
    public Result<AgentDiscoveryResult> discover(AgentDiscoveryForm form,
        @RequestHeader(name = ClientConstants.HTTP_CLIENT_ID_HEADER,
            required = false) String clientId)
        throws NacosException {
        AgentDiscoveryRequest request = form.toRequest();
        clientLifecycleService.renewForQuery(clientId, request.getNamespaceId());
        return Result.success(discoveryService.discover(request));
    }
    
    /**
     * Wait for any changed Agent discovery projection in one complete client generation.
     */
    @Since("3.3.0")
    @PostMapping("/watch")
    @Secured(action = ActionTypes.READ, signType = SignType.AI, apiType = ApiType.OPEN_API)
    public DeferredResult<Result<AgentWatchBatchResponse>> watch(AgentWatchBatchForm form,
        @RequestHeader(name = ClientConstants.HTTP_CLIENT_ID_HEADER,
            required = false) String clientId,
        @RequestHeader(name = HttpHeaderConsts.REQUEST_MODULE,
            required = false) String requestModule)
        throws NacosException {
        AgentWatchBatchRequest request = form.toRequest();
        return watchService.watch(clientId, requestModule, request,
            form.getWatchPayloadBytes());
    }
    
    /**
     * Replace one HTTP Publisher's complete Agent Endpoint batch.
     */
    @Since("3.3.0")
    @PostMapping("/endpoints")
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.OPEN_API)
    public Result<ClientLivenessInfo> registerEndpoints(AgentEndpointRegistrationForm form,
        @RequestHeader(name = ClientConstants.HTTP_CLIENT_ID_HEADER,
            required = false) String clientId,
        @RequestHeader(name = HttpHeaderConsts.REQUEST_MODULE,
            required = false) String requestModule)
        throws NacosException {
        AgentEndpointRegistrationBatch batch = form.toRequest();
        return Result.success(clientLifecycleService.register(clientId, requestModule, batch));
    }
    
    /**
     * Remove one HTTP Publisher's complete Agent Endpoint publication.
     */
    @Since("3.3.0")
    @DeleteMapping("/endpoints")
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.OPEN_API)
    public Result<Void> deregisterEndpoints(AgentEndpointDeregistrationForm form,
        @RequestHeader(name = ClientConstants.HTTP_CLIENT_ID_HEADER,
            required = false) String clientId,
        @RequestHeader(name = HttpHeaderConsts.REQUEST_MODULE,
            required = false) String requestModule)
        throws NacosException {
        form.validate();
        clientLifecycleService.deregister(clientId, requestModule, form.getNamespaceId(),
            form.getAgentName(), form.getProtocol());
        return Result.success();
    }
    
    /**
     * Refresh one HTTP Client and all Agent Endpoint publications it owns.
     */
    @Since("3.3.0")
    @PutMapping("/endpoints/heartbeat")
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.OPEN_API)
    public Result<ClientLivenessInfo> heartbeat(
        @RequestHeader(name = ClientConstants.HTTP_CLIENT_ID_HEADER,
            required = false) String clientId,
        @RequestHeader(name = HttpHeaderConsts.REQUEST_MODULE,
            required = false) String requestModule)
        throws NacosException {
        return Result.success(clientLifecycleService.heartbeat(clientId, requestModule));
    }
}
