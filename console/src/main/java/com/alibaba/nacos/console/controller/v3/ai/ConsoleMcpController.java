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

package com.alibaba.nacos.console.controller.v3.ai;

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.form.mcp.admin.McpDetailForm;
import com.alibaba.nacos.ai.form.mcp.admin.McpForm;
import com.alibaba.nacos.ai.form.mcp.admin.McpImportForm;
import com.alibaba.nacos.ai.form.mcp.admin.McpListForm;
import com.alibaba.nacos.ai.form.mcp.admin.McpUpdateForm;
import com.alibaba.nacos.ai.param.McpHttpParamExtractor;
import com.alibaba.nacos.ai.utils.McpRequestUtil;
import com.alibaba.nacos.api.ai.model.mcp.McpEndpointSpec;
import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerImportRequest;
import com.alibaba.nacos.api.ai.model.mcp.McpServerImportResponse;
import com.alibaba.nacos.api.ai.model.mcp.McpServerImportValidationResult;
import com.alibaba.nacos.api.ai.model.mcp.McpToolSpecification;
import com.alibaba.nacos.api.annotation.NacosApi;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.api.utils.StringUtils;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.console.proxy.ai.McpProxy;
import com.alibaba.nacos.core.model.form.PageForm;
import com.alibaba.nacos.core.paramcheck.ExtractorManager;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.ApiType;
import com.alibaba.nacos.plugin.auth.constant.SignType;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;

import static com.alibaba.nacos.api.ai.constant.AiConstants.Mcp.MCP_PROTOCOL_SSE;

/**
 * Nacos Console AI MCP Server Constants.
 *
 * @author xiweng.yy
 */
@NacosApi
@RestController
@RequestMapping(Constants.MCP_CONSOLE_PATH)
@ExtractorManager.Extractor(httpExtractor = McpHttpParamExtractor.class)
public class ConsoleMcpController {
    
    private final McpProxy mcpProxy;
    
    public ConsoleMcpController(McpProxy mcpProxy) {
        this.mcpProxy = mcpProxy;
    }
    
    /**
     * List mcp server.
     *
     * @param mcpListForm list mcp servers request form
     * @param pageForm page info
     * @return mcp server list wrapper with {@link Result}
     * @throws NacosApiException if request parameter is invalid or handle error
     */
    @GetMapping(value = "/list")
    @Secured(action = ActionTypes.READ, signType = SignType.AI, apiType = ApiType.CONSOLE_API)
    public Result<Page<McpServerBasicInfo>> listMcpServers(McpListForm mcpListForm, PageForm pageForm)
            throws NacosException {
        mcpListForm.validate();
        pageForm.validate();
        return Result.success(
                mcpProxy.listMcpServers(mcpListForm.getNamespaceId(), mcpListForm.getMcpName(), mcpListForm.getSearch(),
                        pageForm.getPageNo(), pageForm.getPageSize()));
    }
    
    /**
     * Import tools from mcp result.
     *
     * @param transportType the transport type
     * @param baseUrl       the base url
     * @param endpoint      the endpoint
     * @return the result
     * @throws NacosException the nacos exception
     */
    @GetMapping("/importToolsFromMcp")
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.CONSOLE_API)
    public Result<List<McpSchema.Tool>> importToolsFromMcp(@RequestParam String transportType, @RequestParam String baseUrl,
            @RequestParam String endpoint, @RequestParam(required = false) String authToken) throws NacosException {
        McpClientTransport transport = null;
        if (StringUtils.equals(transportType, MCP_PROTOCOL_SSE)) {
            HttpClientSseClientTransport.Builder transportBuilder = HttpClientSseClientTransport.builder(baseUrl)
                    .sseEndpoint(endpoint);
            if (!StringUtils.isBlank(authToken)) {
                transportBuilder.customizeRequest(req -> req.header("Authorization", "Bearer " + authToken));
            }
            transport = transportBuilder.build();
        } else {
            return Result.failure(ErrorCode.SERVER_ERROR.getCode(), "Unsupported transport type: " + transportType,
                    null);
        }
        try (McpSyncClient client = McpClient.sync(transport)
                .requestTimeout(Duration.ofSeconds(10))
                .build()) {
            client.initialize();
            McpSchema.ListToolsResult tools = client.listTools();
            return Result.success(tools.tools());
        } catch (Exception e) {
            // 可以记录日志或抛出 NacosException
            throw new NacosException(NacosException.SERVER_ERROR, "Failed to import tools from MCP server", e);
        }
    }
    
    /**
     * Get specified mcp server detail info.
     *
     * @param mcpForm get mcp server request form
     * @return detail info with {@link McpServerDetailInfo}
     * @throws NacosException any exception during handling
     */
    @GetMapping
    @Secured(action = ActionTypes.READ, signType = SignType.AI, apiType = ApiType.CONSOLE_API)
    public Result<McpServerDetailInfo> getMcpServer(McpForm mcpForm) throws NacosException {
        mcpForm.validate();
        return Result.success(mcpProxy.getMcpServer(mcpForm.getNamespaceId(), mcpForm.getMcpName(), mcpForm.getMcpId(), mcpForm.getVersion()));
    }
    
    /**
     * Create new mcp server.
     *
     * @param mcpForm create mcp server request form
     * @throws NacosException any exception during handling
     */
    @PostMapping
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.CONSOLE_API)
    public Result<String> createMcpServer(McpDetailForm mcpForm) throws NacosException {
        mcpForm.validate();
        McpServerBasicInfo basicInfo = McpRequestUtil.parseMcpServerBasicInfo(mcpForm);
        McpToolSpecification mcpTools = McpRequestUtil.parseMcpTools(mcpForm);
        McpEndpointSpec endpointSpec = McpRequestUtil.parseMcpEndpointSpec(basicInfo, mcpForm);
        String mcpId = mcpProxy.createMcpServer(mcpForm.getNamespaceId(), basicInfo, mcpTools, endpointSpec);
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
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.CONSOLE_API)
    public Result<String> updateMcpServer(McpUpdateForm mcpForm) throws NacosException {
        mcpForm.validate();
        McpServerBasicInfo basicInfo = McpRequestUtil.parseMcpServerBasicInfo(mcpForm);
        McpToolSpecification mcpTools = McpRequestUtil.parseMcpTools(mcpForm);
        McpEndpointSpec endpointSpec = McpRequestUtil.parseMcpEndpointSpec(basicInfo, mcpForm);
        mcpProxy.updateMcpServer(mcpForm.getNamespaceId(), mcpForm.getLatest(), basicInfo, mcpTools, endpointSpec);
        return Result.success("ok");
    }
    
    /**
     * Delete existed mcp server.
     *
     * @param mcpForm delete mcp server request form
     * @throws NacosException any exception during handling
     */
    @DeleteMapping
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.CONSOLE_API)
    public Result<String> deleteMcpServer(McpForm mcpForm) throws NacosException {
        mcpForm.validate();
        mcpProxy.deleteMcpServer(mcpForm.getNamespaceId(), mcpForm.getMcpName(), mcpForm.getMcpId(), mcpForm.getVersion());
        return Result.success("ok");
    }
    
    /**
     * Validate MCP server import request.
     *
     * @param mcpImportForm import request form
     * @return validation result with details about potential issues
     * @throws NacosException any exception during validation
     */
    @PostMapping("/import/validate")
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.CONSOLE_API)
    public Result<McpServerImportValidationResult> validateImport(McpImportForm mcpImportForm) throws NacosException {
        mcpImportForm.validate();
        McpServerImportRequest request = convertToImportRequest(mcpImportForm);
        McpServerImportValidationResult result = mcpProxy.validateImport(mcpImportForm.getNamespaceId(), request);
        return Result.success(result);
    }
    
    /**
     * Execute MCP server import operation.
     *
     * @param mcpImportForm import request form
     * @return import response with results and statistics
     * @throws NacosException any exception during import execution
     */
    @PostMapping("/import/execute")
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.CONSOLE_API)
    public Result<McpServerImportResponse> executeImport(McpImportForm mcpImportForm) throws NacosException {
        mcpImportForm.validate();
        McpServerImportRequest request = convertToImportRequest(mcpImportForm);
        McpServerImportResponse response = mcpProxy.executeImport(mcpImportForm.getNamespaceId(), request);
        return Result.success(response);
    }
    
    /**
     * Convert McpImportForm to McpServerImportRequest.
     *
     * @param form the form from HTTP request
     * @return the import request for service layer
     */
    private McpServerImportRequest convertToImportRequest(McpImportForm form) {
        McpServerImportRequest request = new McpServerImportRequest();
        request.setImportType(form.getImportType());
        request.setData(form.getData());
        request.setOverrideExisting(form.isOverrideExisting());
        request.setValidateOnly(form.isValidateOnly());
        request.setSelectedServers(form.getSelectedServers());
        return request;
    }
    
}

