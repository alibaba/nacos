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

import com.alibaba.nacos.ai.utils.HubSharedReadValidator;
import com.alibaba.nacos.api.annotation.Since;
import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.form.prompt.PromptQueryForm;
import com.alibaba.nacos.ai.form.search.client.AiResourcePageSearchForm;
import com.alibaba.nacos.ai.param.PromptHttpParamExtractor;
import com.alibaba.nacos.ai.service.prompt.PromptClientOperationService;
import com.alibaba.nacos.ai.service.search.AiResourceSearchApplicationService;
import com.alibaba.nacos.ai.utils.PromptConvertUtils;
import com.alibaba.nacos.ai.utils.HubNamespaceUtils;
import com.alibaba.nacos.ai.service.hub.HubPromptReadAdapter;
import org.springframework.beans.factory.annotation.Autowired;
import com.alibaba.nacos.api.ai.model.prompt.Prompt;
import com.alibaba.nacos.api.ai.model.prompt.PromptVersionInfo;
import com.alibaba.nacos.api.ai.model.prompt.PromptMetaSummary;
import com.alibaba.nacos.api.annotation.NacosApi;
import com.alibaba.nacos.api.common.ApiType;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.auth.parser.http.HubAwareAiReadHttpResourceParser;
import com.alibaba.nacos.core.paramcheck.ExtractorManager;
import com.alibaba.nacos.core.model.form.PageForm;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.SignType;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.alibaba.nacos.api.ai.constant.AiConstants.Hub.NAMESPACE_ID;
import static com.alibaba.nacos.api.ai.constant.AiConstants.Hub.ENTRY_RESOURCE;
import static com.alibaba.nacos.plugin.auth.constant.Constants.Tag.ONLY_IDENTITY;
import static com.alibaba.nacos.plugin.auth.constant.Constants.Tag.ALLOW_ANONYMOUS;

/**
 * Prompt client controller for prompt runtime read query.
 *
 * @author nacos
 */
@NacosApi
@RestController
@RequestMapping(Constants.Prompt.CLIENT_PATH)
@ExtractorManager.Extractor(httpExtractor = PromptHttpParamExtractor.class)
public class PromptClientController {
    
    private final PromptClientOperationService promptOperationService;
    
    private final AiResourceSearchApplicationService searchService;
    
    public PromptClientController(PromptClientOperationService promptOperationService,
        AiResourceSearchApplicationService searchService) {
        this.promptOperationService = promptOperationService;
        this.searchService = searchService;
    }
    
    private HubPromptReadAdapter sharedPrompts;
    
    /** Install reserved-namespace search on the existing Prompt business route. */
    @Autowired
    public void setSharedPromptReadAdapter(HubPromptReadAdapter adapter) {
        this.sharedPrompts = adapter;
    }
    
    /**
     * Search visible current Prompts.
     */
    @Since("3.3.0")
    @GetMapping("/search")
    @Secured(action = ActionTypes.READ, signType = SignType.AI, apiType = ApiType.OPEN_API,
        parser = HubAwareAiReadHttpResourceParser.class)
    public Result<Page<PromptMetaSummary>> search(AiResourcePageSearchForm form,
        PageForm pageForm) throws NacosException {
        form.validate();
        pageForm.validate();
        if (HubNamespaceUtils.isHubNamespace(form.getNamespaceId())) {
            if (sharedPrompts == null) {
                throw new NacosException(NacosException.SERVER_ERROR,
                    "Shared Prompt reader is unavailable");
            }
            return Result.success(sharedPrompts.search(form.getQuery(), form.getTagsAll(),
                pageForm.getPageNo(), pageForm.getPageSize()));
        }
        return Result.success(searchService.searchPrompts(form, pageForm.getPageNo(),
            pageForm.getPageSize()));
    }
    
    /**
     * Query prompt by version/label/latest with priority version > label > latest.
     */
    @Since("3.2.0")
    @GetMapping
    @Secured(action = ActionTypes.READ, signType = SignType.AI, apiType = ApiType.OPEN_API,
        parser = HubAwareAiReadHttpResourceParser.class)
    public Result<Prompt> queryPrompt(PromptQueryForm form, HttpServletResponse response)
        throws NacosException {
        form.validate();
        try {
            PromptVersionInfo result =
                promptOperationService.queryPrompt(form.getNamespaceId(), form.getPromptKey(),
                    form.getVersion(), form.getLabel(), form.getMd5());
            return Result.success(PromptConvertUtils.toClientPrompt(result));
        } catch (NacosException ex) {
            if (ex.getErrCode() == NacosException.NOT_MODIFIED) {
                response.setStatus(NacosException.NOT_MODIFIED);
                return Result.success(null);
            }
            throw ex;
        }
    }
    
    /** Shared read binding; ordinary Namespace authorization is unchanged. */
    @Since("3.3.0")
    @GetMapping(value = "/search", params = "namespaceId=" + NAMESPACE_ID)
    @Secured(action = ActionTypes.READ, signType = SignType.SPECIFIED,
        resource = ENTRY_RESOURCE,
        apiType = ApiType.OPEN_API, parser = HubAwareAiReadHttpResourceParser.class,
        tags = {ONLY_IDENTITY,
            ALLOW_ANONYMOUS})
    public Result<Page<PromptMetaSummary>> searchShared(AiResourcePageSearchForm form,
        PageForm pageForm) throws NacosException {
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
    public Result<Prompt> queryPromptShared(PromptQueryForm form, HttpServletResponse response)
        throws NacosException {
        HubSharedReadValidator.requireHubNamespace(form.getNamespaceId());
        return queryPrompt(form, response);
    }
}
