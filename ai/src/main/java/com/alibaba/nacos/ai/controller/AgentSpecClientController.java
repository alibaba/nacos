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

package com.alibaba.nacos.ai.controller;

import com.alibaba.nacos.ai.utils.HubSharedReadValidator;
import com.alibaba.nacos.api.annotation.Since;
import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.form.agentspecs.client.AgentSpecQueryForm;
import com.alibaba.nacos.ai.form.agentspecs.client.AgentSpecSearchForm;
import com.alibaba.nacos.ai.service.agentspecs.AgentSpecOperationService;
import com.alibaba.nacos.ai.service.agentspecs.AgentSpecQueryResult;
import com.alibaba.nacos.ai.service.search.AiResourceSearchApplicationService;
import com.alibaba.nacos.ai.utils.AgentSpecRequestUtil;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpec;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpecBasicInfo;
import com.alibaba.nacos.api.annotation.NacosApi;
import com.alibaba.nacos.api.common.ApiType;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.auth.parser.http.HubAwareAgentSpecReadHttpResourceParser;
import com.alibaba.nacos.auth.parser.http.HubAwareAiReadHttpResourceParser;
import com.alibaba.nacos.ai.service.hub.HubAgentSpecReadAdapter;
import com.alibaba.nacos.ai.utils.HubNamespaceUtils;
import org.springframework.beans.factory.annotation.Autowired;
import com.alibaba.nacos.core.model.form.PageForm;
import com.alibaba.nacos.core.paramcheck.ExtractorManager;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.SignType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.alibaba.nacos.api.ai.constant.AiConstants.Hub.NAMESPACE_ID;
import static com.alibaba.nacos.api.ai.constant.AiConstants.Hub.ENTRY_RESOURCE;
import static com.alibaba.nacos.plugin.auth.constant.Constants.Tag.ONLY_IDENTITY;
import static com.alibaba.nacos.plugin.auth.constant.Constants.Tag.ALLOW_ANONYMOUS;

/**
 * AgentSpec client controller for runtime read query.
 *
 * @author nacos
 */
@NacosApi
@RestController
@RequestMapping(Constants.AgentSpecs.CLIENT_PATH)
@ExtractorManager.Extractor(httpExtractor = ExtractorManager.DefaultHttpExtractor.class)
public class AgentSpecClientController {
    
    private final AgentSpecOperationService agentSpecOperationService;
    
    private final AiResourceSearchApplicationService searchService;
    
    public AgentSpecClientController(AgentSpecOperationService agentSpecOperationService,
        AiResourceSearchApplicationService searchService) {
        this.agentSpecOperationService = agentSpecOperationService;
        this.searchService = searchService;
    }
    
    private HubAgentSpecReadAdapter sharedAgentSpecs;
    
    /** Install reserved-namespace search without changing the existing Client constructor or URL. */
    @Autowired
    public void setSharedAgentSpecReadAdapter(HubAgentSpecReadAdapter adapter) {
        this.sharedAgentSpecs = adapter;
    }
    
    /**
     * Search enabled agentspecs for runtime usage.
     */
    @Since("3.2.0")
    @GetMapping("/search")
    @Secured(action = ActionTypes.READ, signType = SignType.AI, apiType = ApiType.OPEN_API,
        parser = HubAwareAiReadHttpResourceParser.class)
    public Result<Page<AgentSpecBasicInfo>> search(AgentSpecSearchForm form, PageForm pageForm)
        throws NacosException {
        form.validate();
        pageForm.validate();
        if (HubNamespaceUtils.isHubNamespace(form.getNamespaceId())) {
            if (sharedAgentSpecs == null) {
                throw new NacosException(NacosException.SERVER_ERROR,
                    "Shared AgentSpec reader is unavailable");
            }
            return Result.success(sharedAgentSpecs.search(form.getKeyword(), form.getTagsAll(),
                pageForm.getPageNo(), pageForm.getPageSize()));
        }
        return Result.success(searchService.searchAgentSpecs(form, pageForm.getPageNo(),
            pageForm.getPageSize()));
    }
    
    /**
     * Get an online agentspec version by label/version/latest.
     * Supports MD5-based conditional query: returns 304 when the client cache is fresh.
     */
    @Since("3.2.0")
    @GetMapping
    @Secured(action = ActionTypes.READ, signType = SignType.AI, apiType = ApiType.OPEN_API,
        parser = HubAwareAgentSpecReadHttpResourceParser.class, tags = {ALLOW_ANONYMOUS})
    public ResponseEntity<Result<AgentSpec>> get(AgentSpecQueryForm form)
        throws NacosException {
        form.validate();
        AgentSpecQueryResult result =
            agentSpecOperationService.queryAgentSpecForClient(
                form.getNamespaceId(), form.getName(), form.getVersion(),
                form.getLabel(), form.getMd5());
        if (result.isNotModified()) {
            return AgentSpecRequestUtil.buildAgentSpecNotModifiedResponse(result.getMd5());
        }
        return AgentSpecRequestUtil.buildAgentSpecResponse(result.getAgentSpec(),
            result.getMd5(), result.getResolvedVersion());
    }
    
    /** Shared read binding; ordinary Namespace authorization is unchanged. */
    @Since("3.3.0")
    @GetMapping(value = "/search", params = "namespaceId=" + NAMESPACE_ID)
    @Secured(action = ActionTypes.READ, signType = SignType.SPECIFIED,
        resource = ENTRY_RESOURCE,
        apiType = ApiType.OPEN_API, parser = HubAwareAiReadHttpResourceParser.class,
        tags = {ONLY_IDENTITY,
            ALLOW_ANONYMOUS})
    public Result<Page<AgentSpecBasicInfo>> searchShared(AgentSpecSearchForm form,
        PageForm pageForm)
        throws NacosException {
        HubSharedReadValidator.requireHubNamespace(form.getNamespaceId());
        return search(form, pageForm);
    }
    
    /** Shared read binding; ordinary Namespace authorization is unchanged. */
    @Since("3.3.0")
    @GetMapping(params = "namespaceId=" + NAMESPACE_ID)
    @Secured(action = ActionTypes.READ, signType = SignType.SPECIFIED,
        resource = ENTRY_RESOURCE,
        apiType = ApiType.OPEN_API, parser = HubAwareAiReadHttpResourceParser.class,
        tags = {ONLY_IDENTITY,
            ALLOW_ANONYMOUS})
    public ResponseEntity<Result<AgentSpec>> getShared(AgentSpecQueryForm form)
        throws NacosException {
        HubSharedReadValidator.requireHubNamespace(form.getNamespaceId());
        return get(form);
    }
}
