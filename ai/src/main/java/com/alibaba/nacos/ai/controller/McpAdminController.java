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
import com.alibaba.nacos.ai.form.mcp.admin.McpDetailForm;
import com.alibaba.nacos.ai.form.mcp.admin.McpForm;
import com.alibaba.nacos.ai.form.mcp.admin.McpListForm;
import com.alibaba.nacos.ai.form.mcp.admin.McpUpdateForm;
import com.alibaba.nacos.ai.param.McpHttpParamExtractor;
import com.alibaba.nacos.ai.service.McpServerOperationService;
import com.alibaba.nacos.ai.utils.McpRequestUtil;
import com.alibaba.nacos.api.ai.model.mcp.McpEndpointSpec;
import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpToolSpecification;
import com.alibaba.nacos.api.annotation.NacosApi;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.core.model.form.PageForm;
import com.alibaba.nacos.core.paramcheck.ExtractorManager;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.ApiType;
import com.alibaba.nacos.plugin.auth.constant.SignType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
    
    private final McpServerOperationService mcpServerOperationService;
    
    public McpAdminController(McpServerOperationService mcpServerOperationService) {
        this.mcpServerOperationService = mcpServerOperationService;
    }
    
    /**
     * List mcp server.
     *
     * @param mcpListForm list mcp servers request form.
     * @param pageForm page info about the request.
     * @return mcp server list wrapper with {@link Result}
     * @throws NacosApiException if request parameter is invalid or handle error
     */
    @GetMapping(value = "/list")
    @Secured(action = ActionTypes.READ, signType = SignType.AI, apiType = ApiType.ADMIN_API)
    public Result<Page<McpServerBasicInfo>> listMcpServers(McpListForm mcpListForm, PageForm pageForm)
            throws NacosException {
        mcpListForm.validate();
        pageForm.validate();
        return Result.success(
                mcpServerOperationService.listMcpServerWithPage(mcpListForm.getNamespaceId(), mcpListForm.getMcpName(), mcpListForm.getSearch(),
                         pageForm.getPageNo(), pageForm.getPageSize()));
    }
    
    /**
     * Get specified mcp server detail info.
     *
     * @param mcpForm get mcp server request form
     * @return detail info with {@link McpServerDetailInfo}
     * @throws NacosException any exception during handling
     */
    @GetMapping
    @Secured(action = ActionTypes.READ, signType = SignType.AI, apiType = ApiType.ADMIN_API)
    public Result<McpServerDetailInfo> getMcpServer(McpForm mcpForm) throws NacosException {
        mcpForm.validate();
        return Result.success(mcpServerOperationService.getMcpServerDetail(mcpForm.getNamespaceId(), mcpForm.getMcpId(), 
                mcpForm.getMcpName(), mcpForm.getVersion()));
    }
    
    /**
     * Create new mcp server.
     *
     * @param mcpForm create mcp server request form
     * @throws NacosException any exception during handling
     */
    @PostMapping
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.ADMIN_API)
    public Result<String> createMcpServer(McpDetailForm mcpForm) throws NacosException {
        mcpForm.validate();
        McpServerBasicInfo basicInfo = McpRequestUtil.parseMcpServerBasicInfo(mcpForm);
        McpToolSpecification mcpTools = McpRequestUtil.parseMcpTools(mcpForm);
        McpEndpointSpec endpointSpec = McpRequestUtil.parseMcpEndpointSpec(basicInfo, mcpForm);
        String mcpId = mcpServerOperationService.createMcpServer(mcpForm.getNamespaceId(), basicInfo, mcpTools,
                endpointSpec);
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
    @PutMapping
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.ADMIN_API)
    public Result<String> updateMcpServer(McpUpdateForm mcpForm) throws NacosException {
        mcpForm.validate();
        McpServerBasicInfo basicInfo = McpRequestUtil.parseMcpServerBasicInfo(mcpForm);
        McpToolSpecification mcpTools = McpRequestUtil.parseMcpTools(mcpForm);
        McpEndpointSpec endpointSpec = McpRequestUtil.parseMcpEndpointSpec(basicInfo, mcpForm);
        mcpServerOperationService.updateMcpServer(mcpForm.getNamespaceId(), mcpForm.getLatest(), basicInfo, mcpTools,
                endpointSpec, mcpForm.isOverrideExisting());
        return Result.success("ok");
    }
    
    /**
     * Delete existed mcp server.
     *
     * @param mcpForm delete mcp server request form
     * @throws NacosException any exception during handling
     */
    @DeleteMapping
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.ADMIN_API)
    public Result<String> deleteMcpServer(McpForm mcpForm) throws NacosException {
        mcpForm.validate();
        mcpServerOperationService.deleteMcpServer(mcpForm.getNamespaceId(), mcpForm.getMcpName(), mcpForm.getMcpId(), mcpForm.getVersion());
        return Result.success("ok");
    }
}
