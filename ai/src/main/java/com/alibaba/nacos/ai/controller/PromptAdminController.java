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
import com.alibaba.nacos.ai.form.prompt.PromptLabelBindForm;
import com.alibaba.nacos.ai.form.prompt.PromptLabelForm;
import com.alibaba.nacos.ai.form.prompt.PromptListForm;
import com.alibaba.nacos.ai.form.prompt.PromptMetadataForm;
import com.alibaba.nacos.ai.form.prompt.PromptPublishForm;
import com.alibaba.nacos.ai.form.prompt.PromptQueryForm;
import com.alibaba.nacos.ai.param.PromptHttpParamExtractor;
import com.alibaba.nacos.ai.service.prompt.PromptAdminOperationService;
import com.alibaba.nacos.api.ai.model.prompt.PromptMetaInfo;
import com.alibaba.nacos.api.ai.model.prompt.PromptMetaSummary;
import com.alibaba.nacos.api.ai.model.prompt.PromptVariable;
import com.alibaba.nacos.api.ai.model.prompt.PromptVersionInfo;
import com.alibaba.nacos.api.ai.model.prompt.PromptVersionSummary;
import com.alibaba.nacos.api.annotation.NacosApi;
import com.alibaba.nacos.api.common.ApiType;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.core.paramcheck.ExtractorManager;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.SignType;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

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
    
    private final PromptAdminOperationService promptOperationService;
    
    public PromptAdminController(PromptAdminOperationService promptOperationService) {
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
        boolean success = promptOperationService.publishPromptVersion(form.getNamespaceId(), form.getPromptKey(),
                form.getVersion(), form.getTemplate(), form.getCommitMsg(), form.getDescription(),
                parseBizTags(form.getBizTags()), parseVariables(form.getVariables()), srcUser, srcIp);
        return Result.success(success);
    }
    
    /**
     * Get prompt metadata.
     */
    @GetMapping("/metadata")
    @Secured(action = ActionTypes.READ, signType = SignType.AI, apiType = ApiType.ADMIN_API)
    public Result<PromptMetaInfo> getPromptMetadata(PromptForm form) throws NacosException {
        form.validate();
        PromptMetaInfo detail = promptOperationService.getPromptMeta(form.getNamespaceId(), form.getPromptKey());
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
        boolean success = promptOperationService.deletePrompt(form.getNamespaceId(), form.getPromptKey(), srcUser,
                srcIp);
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
    public Result<Page<PromptMetaSummary>> listPrompts(PromptListForm form) throws NacosException {
        form.validate();
        Page<PromptMetaSummary> result = promptOperationService.listPrompts(form.getNamespaceId(), form.getPromptKey(),
                form.getSearch(), form.getBizTags(), form.getPageNo(), form.getPageSize());
        return Result.success(result);
    }
    
    /**
     * List prompt versions.
     */
    @GetMapping("/versions")
    @Secured(action = ActionTypes.READ, signType = SignType.AI, apiType = ApiType.ADMIN_API)
    public Result<Page<PromptVersionSummary>> listPromptVersions(PromptHistoryForm form) throws NacosException {
        form.validate();
        Page<PromptVersionSummary> result = promptOperationService.listPromptVersions(form.getNamespaceId(),
                form.getPromptKey(), form.getPageNo(), form.getPageSize());
        return Result.success(result);
    }
    
    /**
     * Get prompt detail by specified version, null for latest.
     */
    @GetMapping("/detail")
    @Secured(action = ActionTypes.READ, signType = SignType.AI, apiType = ApiType.ADMIN_API)
    public Result<PromptVersionInfo> queryPromptDetail(PromptQueryForm form) throws NacosException {
        form.validate();
        PromptVersionInfo detail = promptOperationService.queryPromptDetail(form.getNamespaceId(), form.getPromptKey(),
                form.getVersion(), form.getLabel());
        return Result.success(detail);
    }
    
    /**
     * Bind label to a specified prompt version.
     */
    @PutMapping("/label")
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.ADMIN_API)
    public Result<Boolean> bindLabel(PromptLabelBindForm form, HttpServletRequest request) throws NacosException {
        form.validate();
        String srcUser = request.getRemoteUser();
        String srcIp = request.getRemoteAddr();
        boolean success = promptOperationService.bindLabel(form.getNamespaceId(), form.getPromptKey(), form.getLabel(),
                form.getVersion(), srcUser, srcIp);
        return Result.success(success);
    }
    
    /**
     * Unbind label from prompt.
     */
    @DeleteMapping("/label")
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.ADMIN_API)
    public Result<Boolean> unbindLabel(PromptLabelForm form, HttpServletRequest request) throws NacosException {
        form.validate();
        String srcUser = request.getRemoteUser();
        String srcIp = request.getRemoteAddr();
        boolean success = promptOperationService.unbindLabel(form.getNamespaceId(), form.getPromptKey(),
                form.getLabel(), srcUser, srcIp);
        return Result.success(success);
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
        boolean success = promptOperationService.updatePromptMetadata(form.getNamespaceId(), form.getPromptKey(),
                form.getDescription(), parseBizTags(form.getBizTags()), srcUser, srcIp);
        return Result.success(success);
    }
    
    private List<String> parseBizTags(String bizTags) {
        if (bizTags == null) {
            return null;
        }
        if (bizTags.trim().isEmpty()) {
            return new ArrayList<>(0);
        }
        String[] split = bizTags.split(",");
        List<String> result = new ArrayList<>(split.length);
        for (String each : split) {
            if (each != null && !each.trim().isEmpty()) {
                result.add(each.trim());
            }
        }
        return result;
    }
    
    private List<PromptVariable> parseVariables(String variables) {
        if (StringUtils.isBlank(variables)) {
            return null;
        }
        return JacksonUtils.toObj(variables, new TypeReference<List<PromptVariable>>() {
        });
    }
}
