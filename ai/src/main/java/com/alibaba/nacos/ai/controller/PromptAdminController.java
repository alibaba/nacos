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

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.form.prompt.PromptForm;
import com.alibaba.nacos.ai.form.prompt.PromptHistoryForm;
import com.alibaba.nacos.ai.form.prompt.PromptListForm;
import com.alibaba.nacos.ai.form.prompt.PromptMetadataForm;
import com.alibaba.nacos.ai.form.prompt.PromptPublishForm;
import com.alibaba.nacos.api.ai.model.prompt.PromptBasicInfo;
import com.alibaba.nacos.api.ai.model.prompt.PromptDetail;
import com.alibaba.nacos.api.ai.model.prompt.PromptHistoryItem;
import com.alibaba.nacos.ai.param.PromptHttpParamExtractor;
import com.alibaba.nacos.ai.service.prompt.PromptOperationService;
import com.alibaba.nacos.api.annotation.NacosApi;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.core.paramcheck.ExtractorManager;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.ApiType;
import com.alibaba.nacos.plugin.auth.constant.SignType;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Prompt admin controller.
 *
 * <p>Provides REST APIs for prompt management operations.</p>
 *
 * @author nacos
 */
@NacosApi
@RestController
@RequestMapping(Constants.Prompt.ADMIN_PATH)
@ExtractorManager.Extractor(httpExtractor = PromptHttpParamExtractor.class)
public class PromptAdminController {
    
    private final PromptOperationService promptOperationService;
    
    public PromptAdminController(PromptOperationService promptOperationService) {
        this.promptOperationService = promptOperationService;
    }
    
    /**
     * Publish a new version of prompt.
     *
     * @param form    the prompt publish form
     * @param request HTTP request for getting client info
     * @return result of the publish operation
     * @throws NacosException if the prompt publish fails
     */
    @PostMapping
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.ADMIN_API)
    public Result<Boolean> publishPrompt(PromptPublishForm form, HttpServletRequest request) throws NacosException {
        form.validate();
        String srcUser = request.getRemoteUser();
        String srcIp = request.getRemoteAddr();
        boolean success = promptOperationService.publishPrompt(
                form.getNamespaceId(),
                form.getPromptKey(),
                form.getVersion(),
                form.getTemplate(),
                form.getCommitMsg(),
                form.getDescription(),
                form.getPromptTags(),
                srcUser,
                srcIp
        );
        return Result.success(success);
    }
    
    /**
     * Get prompt detail.
     *
     * @param form the prompt form
     * @return result of the get operation
     * @throws NacosException if the prompt get fails
     */
    @GetMapping
    @Secured(action = ActionTypes.READ, signType = SignType.AI, apiType = ApiType.ADMIN_API)
    public Result<PromptDetail> getPrompt(PromptForm form) throws NacosException {
        form.validate();
        PromptDetail detail = promptOperationService.getPromptDetail(form.getNamespaceId(), form.getPromptKey());
        return Result.success(detail);
    }
    
    /**
     * Delete prompt.
     *
     * @param form    the prompt form
     * @param request HTTP request for getting client info
     * @return result of the deletion operation
     * @throws NacosException if the prompt deletion fails
     */
    @DeleteMapping
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.ADMIN_API)
    public Result<Boolean> deletePrompt(PromptForm form, HttpServletRequest request) throws NacosException {
        form.validate();
        String srcUser = request.getRemoteUser();
        String srcIp = request.getRemoteAddr();
        boolean success = promptOperationService.deletePrompt(
                form.getNamespaceId(),
                form.getPromptKey(),
                srcUser,
                srcIp
        );
        return Result.success(success);
    }
    
    /**
     * List prompts with pagination.
     *
     * @param form the prompt list form
     * @return result of the list operation
     * @throws NacosException if the prompt list fails
     */
    @GetMapping("/list")
    @Secured(action = ActionTypes.READ, signType = SignType.AI, apiType = ApiType.ADMIN_API)
    public Result<Page<PromptBasicInfo>> listPrompts(PromptListForm form) throws NacosException {
        form.validate();
        Page<PromptBasicInfo> result = promptOperationService.listPrompts(
                form.getNamespaceId(),
                form.getPromptKey(),
                form.getSearch(),
                form.getPageNo(),
                form.getPageSize()
        );
        return Result.success(result);
    }
    
    /**
     * List prompt history versions.
     *
     * @param form the prompt history form
     * @return result of the history list operation
     * @throws NacosException if the history list fails
     */
    @GetMapping("/history")
    @Secured(action = ActionTypes.READ, signType = SignType.AI, apiType = ApiType.ADMIN_API)
    public Result<Page<PromptHistoryItem>> listPromptHistory(PromptHistoryForm form) throws NacosException {
        form.validate();
        Page<PromptHistoryItem> result = promptOperationService.listPromptHistory(
                form.getNamespaceId(),
                form.getPromptKey(),
                form.getPageNo(),
                form.getPageSize()
        );
        return Result.success(result);
    }
    
    /**
     * Get prompt history detail by history ID.
     *
     * @param form      the prompt form
     * @param historyId history record ID
     * @return result of the history detail operation
     * @throws NacosException if the history detail fails
     */
    @GetMapping("/history/detail")
    @Secured(action = ActionTypes.READ, signType = SignType.AI, apiType = ApiType.ADMIN_API)
    public Result<PromptDetail> getPromptHistoryDetail(PromptForm form, @RequestParam("historyId") Long historyId)
            throws NacosException {
        form.validate();
        PromptDetail detail = promptOperationService.getPromptHistoryDetail(
                form.getNamespaceId(),
                form.getPromptKey(),
                historyId
        );
        return Result.success(detail);
    }
    
    /**
     * Update prompt metadata (description only).
     *
     * @param form    the prompt metadata form
     * @param request HTTP request for getting client info
     * @return result of the update operation
     * @throws NacosException if the update fails
     */
    @PutMapping("/metadata")
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.ADMIN_API)
    public Result<Boolean> updatePromptMetadata(PromptMetadataForm form, HttpServletRequest request)
            throws NacosException {
        form.validate();
        String srcUser = request.getRemoteUser();
        String srcIp = request.getRemoteAddr();
        boolean success = promptOperationService.updatePromptMetadata(
                form.getNamespaceId(),
                form.getPromptKey(),
                form.getDescription(),
                form.getPromptTags(),
                srcUser,
                srcIp
        );
        return Result.success(success);
    }
}
