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

import com.alibaba.nacos.api.annotation.Since;
import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.form.mcp.admin.McpDetailForm;
import com.alibaba.nacos.ai.form.mcp.admin.McpForm;
import com.alibaba.nacos.ai.form.mcp.admin.McpListForm;
import com.alibaba.nacos.ai.form.mcp.admin.McpServerDraftForm;
import com.alibaba.nacos.ai.form.mcp.admin.McpServerLabelsForm;
import com.alibaba.nacos.ai.form.mcp.admin.McpScopeForm;
import com.alibaba.nacos.ai.form.mcp.admin.McpServerVersionForm;
import com.alibaba.nacos.ai.form.mcp.admin.McpServerVersionListForm;
import com.alibaba.nacos.ai.form.mcp.admin.McpUpdateForm;
import com.alibaba.nacos.ai.param.McpHttpParamExtractor;
import com.alibaba.nacos.ai.service.mcp.McpCompatibilityOperationService;
import com.alibaba.nacos.ai.service.mcp.McpOperationService;
import com.alibaba.nacos.ai.utils.McpRequestUtil;
import com.alibaba.nacos.api.ai.model.mcp.McpEndpointSpec;
import com.alibaba.nacos.api.ai.model.mcp.McpServerVersionDetail;
import com.alibaba.nacos.api.ai.model.mcp.McpServerVersionSummary;
import com.alibaba.nacos.api.ai.model.mcp.McpResourceSpecification;
import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpToolSpecification;
import com.alibaba.nacos.api.annotation.NacosApi;
import com.alibaba.nacos.api.common.ApiType;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.core.model.form.PageForm;
import com.alibaba.nacos.core.paramcheck.ExtractorManager;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.SignType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Nacos AI MCP controller.
 *
 * @author xiweng.yy
 */
@NacosApi
@RestController
@RequestMapping(Constants.MCP_ADMIN_PATH)
@ExtractorManager.Extractor(httpExtractor = McpHttpParamExtractor.class)
public class McpAdminController {
    
    private final McpOperationService mcpServerOperationService;
    
    private final McpCompatibilityOperationService lifecycleOperationService;
    
    public McpAdminController(McpOperationService mcpServerOperationService,
        McpCompatibilityOperationService lifecycleOperationService) {
        this.mcpServerOperationService = mcpServerOperationService;
        this.lifecycleOperationService = lifecycleOperationService;
    }
    
    /**
     * List mcp server.
     *
     * @param mcpListForm list mcp servers request form.
     * @param pageForm    page info about the request.
     * @return mcp server list wrapper with {@link Result}
     * @throws NacosApiException if request parameter is invalid or handle error
     */
    @Since("3.0.1")
    @GetMapping(value = "/list")
    @Secured(action = ActionTypes.READ, signType = SignType.AI, apiType = ApiType.ADMIN_API)
    public Result<Page<McpServerBasicInfo>> listMcpServers(McpListForm mcpListForm,
        PageForm pageForm)
        throws NacosException {
        mcpListForm.validate();
        pageForm.validate();
        return Result.success(
            mcpServerOperationService.listMcpServerWithPage(mcpListForm.getNamespaceId(),
                mcpListForm.getMcpName(),
                mcpListForm.getSearch(), pageForm.getPageNo(), pageForm.getPageSize()));
    }
    
    /**
     * Get specified mcp server detail info.
     *
     * @param mcpForm get mcp server request form
     * @return detail info with {@link McpServerDetailInfo}
     * @throws NacosException any exception during handling
     */
    @Since("3.0.1")
    @GetMapping
    @Secured(action = ActionTypes.READ, signType = SignType.AI, apiType = ApiType.ADMIN_API)
    public Result<McpServerDetailInfo> getMcpServer(McpForm mcpForm) throws NacosException {
        mcpForm.validate();
        return Result.success(mcpServerOperationService.getMcpServerDetail(mcpForm.getNamespaceId(),
            mcpForm.getMcpId(),
            mcpForm.getMcpName(), mcpForm.getVersion()));
    }
    
    /**
     * Create new mcp server.
     *
     * @param mcpForm create mcp server request form
     * @throws NacosException any exception during handling
     */
    @Since("3.0.1")
    @PostMapping
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.ADMIN_API)
    public Result<String> createMcpServer(McpDetailForm mcpForm) throws NacosException {
        mcpForm.validate();
        McpServerBasicInfo basicInfo = McpRequestUtil.parseMcpServerBasicInfo(mcpForm);
        McpToolSpecification mcpTools = McpRequestUtil.parseMcpTools(mcpForm);
        McpResourceSpecification mcpResources = McpRequestUtil.parseMcpResources(mcpForm);
        McpEndpointSpec endpointSpec = McpRequestUtil.parseMcpEndpointSpec(basicInfo, mcpForm);
        String mcpId =
            mcpServerOperationService.createMcpServer(mcpForm.getNamespaceId(), basicInfo, mcpTools,
                mcpResources, endpointSpec);
        return Result.success(mcpId);
    }
    
    /**
     * Update existed mcp server.
     *
     * <p>
     * `namespaceId` and `mcpName` can't be changed.
     * </p>
     *
     * @param mcpForm update mcp servers request form
     * @throws NacosException any exception during handling
     */
    @Since("3.0.1")
    @PutMapping
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.ADMIN_API)
    public Result<String> updateMcpServer(McpUpdateForm mcpForm) throws NacosException {
        mcpForm.validate();
        McpServerBasicInfo basicInfo = McpRequestUtil.parseMcpServerBasicInfo(mcpForm);
        McpToolSpecification mcpTools = McpRequestUtil.parseMcpTools(mcpForm);
        McpResourceSpecification mcpResources = McpRequestUtil.parseMcpResources(mcpForm);
        McpEndpointSpec endpointSpec = McpRequestUtil.parseMcpEndpointSpec(basicInfo, mcpForm);
        mcpServerOperationService.updateMcpServer(mcpForm.getNamespaceId(), mcpForm.getLatest(),
            basicInfo, mcpTools,
            mcpResources, endpointSpec, mcpForm.isOverrideExisting());
        return Result.success("ok");
    }
    
    /**
     * Delete existed mcp server.
     *
     * @param mcpForm delete mcp server request form
     * @throws NacosException any exception during handling
     */
    @Since("3.0.1")
    @DeleteMapping
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.ADMIN_API)
    public Result<String> deleteMcpServer(McpForm mcpForm) throws NacosException {
        mcpForm.validate();
        mcpServerOperationService.deleteMcpServer(mcpForm.getNamespaceId(), mcpForm.getMcpName(),
            mcpForm.getMcpId(),
            mcpForm.getVersion());
        return Result.success("ok");
    }
    
    /**
     * Page management metadata for the Versions of one MCP resource.
     */
    @Since("3.3.0")
    @GetMapping("/versions")
    @Secured(action = ActionTypes.READ, signType = SignType.AI, apiType = ApiType.ADMIN_API)
    public Result<Page<McpServerVersionSummary>> listMcpServerVersions(
        McpServerVersionListForm form, PageForm pageForm) throws NacosException {
        form.validate();
        pageForm.validate();
        return Result.success(lifecycleOperationService.listMcpServerVersions(
            form.getNamespaceId(), form.getMcpName(), form.getStatus(), pageForm.getPageNo(),
            pageForm.getPageSize()));
    }
    
    /**
     * Read one exact MCP Version.
     */
    @Since("3.3.0")
    @GetMapping("/version")
    @Secured(action = ActionTypes.READ, signType = SignType.AI, apiType = ApiType.ADMIN_API)
    public Result<McpServerVersionDetail> getMcpServerVersion(
        McpServerVersionForm form) throws NacosException {
        form.validate();
        return Result.success(lifecycleOperationService.getMcpServerVersion(
            form.getNamespaceId(), form.getMcpName(), form.getVersion()));
    }
    
    /**
     * Create one new MCP draft Version.
     */
    @Since("3.3.0")
    @PostMapping("/draft")
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.ADMIN_API)
    public Result<McpServerVersionDetail> createMcpServerDraft(
        McpServerDraftForm form) throws NacosException {
        form.validate();
        McpServerBasicInfo server = McpRequestUtil.parseMcpServerBasicInfo(form);
        return Result.success(lifecycleOperationService.createMcpServerDraft(
            form.getNamespaceId(), server, McpRequestUtil.parseMcpTools(form),
            McpRequestUtil.parseMcpResources(form),
            McpRequestUtil.parseMcpEndpointSpec(server, form)));
    }
    
    /**
     * Replace one exact current MCP draft.
     */
    @Since("3.3.0")
    @PutMapping("/draft")
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.ADMIN_API)
    public Result<McpServerVersionDetail> updateMcpServerDraft(
        McpServerDraftForm form) throws NacosException {
        form.validate();
        McpServerBasicInfo server = McpRequestUtil.parseMcpServerBasicInfo(form);
        return Result.success(lifecycleOperationService.updateMcpServerDraft(
            form.getNamespaceId(), server, McpRequestUtil.parseMcpTools(form),
            McpRequestUtil.parseMcpResources(form),
            McpRequestUtil.parseMcpEndpointSpec(server, form)));
    }
    
    /**
     * Delete one exact current MCP draft.
     */
    @Since("3.3.0")
    @DeleteMapping("/draft")
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.ADMIN_API)
    public Result<Void> deleteMcpServerDraft(McpServerVersionForm form)
        throws NacosException {
        form.validate();
        lifecycleOperationService.deleteMcpServerDraft(form.getNamespaceId(), form.getMcpName(),
            form.getVersion());
        return Result.success();
    }
    
    /**
     * Submit one exact MCP working Version.
     */
    @Since("3.3.0")
    @PostMapping("/submit")
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.ADMIN_API)
    public Result<McpServerVersionSummary> submitMcpServerVersion(
        McpServerVersionForm form) throws NacosException {
        form.validate();
        return Result.success(lifecycleOperationService.submitMcpServerVersion(
            form.getNamespaceId(), form.getMcpName(), form.getVersion()));
    }
    
    /**
     * Publish one exact reviewed MCP Version.
     */
    @Since("3.3.0")
    @PostMapping("/publish")
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.ADMIN_API)
    public Result<McpServerVersionSummary> publishMcpServerVersion(
        McpServerVersionForm form) throws NacosException {
        form.validate();
        return Result.success(lifecycleOperationService.publishMcpServerVersion(
            form.getNamespaceId(), form.getMcpName(), form.getVersion()));
    }
    
    /**
     * Force-publish one exact MCP working Version.
     */
    @Since("3.3.0")
    @PostMapping("/force-publish")
    @Secured(resource = Constants.MCP_ADMIN_PATH + "/force-publish",
        action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.ADMIN_API)
    public Result<McpServerVersionSummary> forcePublishMcpServerVersion(
        McpServerVersionForm form) throws NacosException {
        form.validate();
        return Result.success(lifecycleOperationService.forcePublishMcpServerVersion(
            form.getNamespaceId(), form.getMcpName(), form.getVersion()));
    }
    
    /**
     * Return one exact reviewed MCP Version to draft.
     */
    @Since("3.3.0")
    @PostMapping("/redraft")
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.ADMIN_API)
    public Result<McpServerVersionSummary> redraftMcpServerVersion(
        McpServerVersionForm form) throws NacosException {
        form.validate();
        return Result.success(lifecycleOperationService.redraftMcpServerVersion(
            form.getNamespaceId(), form.getMcpName(), form.getVersion()));
    }
    
    /**
     * Bring one exact offline MCP Version online and make it latest.
     */
    @Since("3.3.0")
    @PostMapping("/online")
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.ADMIN_API)
    public Result<McpServerVersionSummary> onlineMcpServerVersion(
        McpServerVersionForm form) throws NacosException {
        form.validate();
        return Result.success(lifecycleOperationService.onlineMcpServerVersion(
            form.getNamespaceId(), form.getMcpName(), form.getVersion()));
    }
    
    /**
     * Take one exact online MCP Version offline.
     */
    @Since("3.3.0")
    @PostMapping("/offline")
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.ADMIN_API)
    public Result<McpServerVersionSummary> offlineMcpServerVersion(
        McpServerVersionForm form) throws NacosException {
        form.validate();
        return Result.success(lifecycleOperationService.offlineMcpServerVersion(
            form.getNamespaceId(), form.getMcpName(), form.getVersion()));
    }
    
    /**
     * Replace custom MCP labels while preserving the server-managed latest label.
     */
    @Since("3.3.0")
    @PutMapping("/labels")
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.ADMIN_API)
    public Result<Map<String, String>> updateMcpServerLabels(McpServerLabelsForm form)
        throws NacosException {
        form.validate();
        Map<String, String> labels = McpRequestUtil.parseMcpServerLabels(form.getLabels());
        return Result.success(lifecycleOperationService.updateMcpServerLabels(
            form.getNamespaceId(), form.getMcpName(), labels));
    }

    /**
     * Update MCP visibility scope (PUBLIC or PRIVATE).
     *
     * @param form scope update request
     * @return successful result
     * @throws NacosException if the MCP is not found, not writable, or the update fails
     */
    @Since("3.3.0")
    @PutMapping("/scope")
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.ADMIN_API)
    public Result<String> updateScope(McpScopeForm form) throws NacosException {
        form.validate();
        lifecycleOperationService.updateScope(form.getNamespaceId(), form.getMcpName(),
            form.getScope());
        return Result.success("ok");
    }
}
