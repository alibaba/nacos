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

import com.alibaba.nacos.api.annotation.Since;
import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.form.mcp.admin.McpDetailForm;
import com.alibaba.nacos.ai.form.mcp.admin.McpForm;
import com.alibaba.nacos.ai.form.mcp.admin.McpImportForm;
import com.alibaba.nacos.ai.form.mcp.admin.McpListForm;
import com.alibaba.nacos.ai.form.mcp.admin.McpLifecycleDraftForm;
import com.alibaba.nacos.ai.form.mcp.admin.McpLifecycleLabelsForm;
import com.alibaba.nacos.ai.form.mcp.admin.McpLifecycleVersionForm;
import com.alibaba.nacos.ai.form.mcp.admin.McpLifecycleVersionListForm;
import com.alibaba.nacos.ai.form.mcp.admin.McpUpdateForm;
import com.alibaba.nacos.ai.param.McpHttpParamExtractor;
import com.alibaba.nacos.ai.utils.McpRequestUtil;
import com.alibaba.nacos.api.ai.model.mcp.McpEndpointSpec;
import com.alibaba.nacos.api.ai.model.mcp.McpLifecycleVersionDetail;
import com.alibaba.nacos.api.ai.model.mcp.McpLifecycleVersionSummary;
import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerImportRequest;
import com.alibaba.nacos.api.ai.model.mcp.McpServerImportResponse;
import com.alibaba.nacos.api.ai.model.mcp.McpServerImportValidationResult;
import com.alibaba.nacos.api.ai.model.mcp.McpToolSpecification;
import com.alibaba.nacos.api.annotation.NacosApi;
import com.alibaba.nacos.api.common.ApiType;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.api.utils.StringUtils;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.console.config.McpEndpointAccessValidator;
import com.alibaba.nacos.console.proxy.ai.McpProxy;
import com.alibaba.nacos.core.controller.compatibility.CompatibilityHelper;
import com.alibaba.nacos.core.model.form.PageForm;
import com.alibaba.nacos.core.paramcheck.ExtractorManager;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.SignType;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
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
import java.util.Map;

import static com.alibaba.nacos.api.ai.constant.AiConstants.Mcp.MCP_PROTOCOL_SSE;
import static com.alibaba.nacos.api.ai.constant.AiConstants.Mcp.MCP_PROTOCOL_STREAMABLE;

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
    
    private final McpEndpointAccessValidator mcpEndpointAccessValidator;
    
    public ConsoleMcpController(McpProxy mcpProxy,
        McpEndpointAccessValidator mcpEndpointAccessValidator) {
        this.mcpProxy = mcpProxy;
        this.mcpEndpointAccessValidator = mcpEndpointAccessValidator;
    }
    
    /**
     * List mcp server.
     *
     * @param mcpListForm list mcp servers request form
     * @param pageForm    page info
     * @return mcp server list wrapper with {@link Result}
     * @throws NacosApiException if request parameter is invalid or handle error
     */
    @Since("3.0.0")
    @GetMapping(value = "/list")
    @Secured(action = ActionTypes.READ, signType = SignType.AI, apiType = ApiType.CONSOLE_API)
    public Result<Page<McpServerBasicInfo>> listMcpServers(McpListForm mcpListForm,
        PageForm pageForm)
        throws NacosException {
        mcpListForm.validate();
        pageForm.validate();
        return Result.success(
            mcpProxy.listMcpServers(mcpListForm.getNamespaceId(), mcpListForm.getMcpName(),
                mcpListForm.getSearch(),
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
    @Since("3.0.3")
    @GetMapping("/importToolsFromMcp")
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.CONSOLE_API)
    public Result<List<McpSchema.Tool>> importToolsFromMcp(@RequestParam String transportType,
        @RequestParam String baseUrl, @RequestParam String endpoint,
        @RequestParam(required = false) String authToken) throws NacosException {
        if (!StringUtils.equals(transportType, MCP_PROTOCOL_SSE)
            && !StringUtils.equals(transportType, MCP_PROTOCOL_STREAMABLE)) {
            return Result.failure(ErrorCode.SERVER_ERROR.getCode(),
                "Unsupported transport type: " + transportType,
                null);
        }
        try {
            mcpEndpointAccessValidator.validate(baseUrl, endpoint);
        } catch (SecurityException e) {
            return Result.failure(ErrorCode.ACCESS_DENIED.getCode(), e.getMessage(), null);
        } catch (IllegalArgumentException e) {
            return Result.failure(ErrorCode.PARAMETER_VALIDATE_ERROR.getCode(), e.getMessage(),
                null);
        }
        McpClientTransport transport;
        if (StringUtils.equals(transportType, MCP_PROTOCOL_SSE)) {
            HttpClientSseClientTransport.Builder transportBuilder =
                HttpClientSseClientTransport.builder(baseUrl)
                    .sseEndpoint(endpoint)
                    .customizeClient(builder -> builder
                        .followRedirects(java.net.http.HttpClient.Redirect.NEVER));
            if (!StringUtils.isBlank(authToken)) {
                transportBuilder
                    .customizeRequest(req -> req.header("Authorization", "Bearer " + authToken));
            }
            transport = transportBuilder.build();
        } else {
            HttpClientStreamableHttpTransport.Builder transportBuilder =
                HttpClientStreamableHttpTransport.builder(
                    baseUrl).endpoint(endpoint)
                    .customizeClient(builder -> builder
                        .followRedirects(java.net.http.HttpClient.Redirect.NEVER));
            if (!StringUtils.isBlank(authToken)) {
                transportBuilder
                    .customizeRequest(req -> req.header("Authorization", "Bearer " + authToken));
            }
            transport = transportBuilder.build();
        }
        try (McpSyncClient client =
            McpClient.sync(transport).requestTimeout(Duration.ofSeconds(10)).build()) {
            client.initialize();
            McpSchema.ListToolsResult tools = client.listTools();
            return Result.success(tools.tools());
        } catch (Exception e) {
            // 可以记录日志或抛出 NacosException
            throw new NacosException(NacosException.SERVER_ERROR,
                "Failed to import tools from MCP server", e);
        }
    }
    
    /**
     * Get specified mcp server detail info.
     *
     * @param mcpForm get mcp server request form
     * @return detail info with {@link McpServerDetailInfo}
     * @throws NacosException any exception during handling
     */
    @Since("3.0.0")
    @GetMapping
    @Secured(action = ActionTypes.READ, signType = SignType.AI, apiType = ApiType.CONSOLE_API)
    public Result<McpServerDetailInfo> getMcpServer(McpForm mcpForm) throws NacosException {
        mcpForm.validate();
        return Result.success(mcpProxy.getMcpServer(mcpForm.getNamespaceId(), mcpForm.getMcpName(),
            mcpForm.getMcpId(),
            mcpForm.getVersion()));
    }
    
    /**
     * Create new mcp server.
     *
     * @param mcpForm create mcp server request form
     * @throws NacosException any exception during handling
     */
    @Since("3.0.0")
    @PostMapping
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.CONSOLE_API)
    public Result<String> createMcpServer(McpDetailForm mcpForm) throws NacosException {
        mcpForm.validate();
        McpServerBasicInfo basicInfo = McpRequestUtil.parseMcpServerBasicInfo(mcpForm);
        McpToolSpecification mcpTools = McpRequestUtil.parseMcpTools(mcpForm);
        McpEndpointSpec endpointSpec = McpRequestUtil.parseMcpEndpointSpec(basicInfo, mcpForm);
        String mcpId =
            mcpProxy.createMcpServer(mcpForm.getNamespaceId(), basicInfo, mcpTools, endpointSpec);
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
    @Since("3.0.0")
    @PutMapping
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.CONSOLE_API)
    public Result<String> updateMcpServer(McpUpdateForm mcpForm) throws NacosException {
        mcpForm.validate();
        McpServerBasicInfo basicInfo = McpRequestUtil.parseMcpServerBasicInfo(mcpForm);
        McpToolSpecification mcpTools = McpRequestUtil.parseMcpTools(mcpForm);
        McpEndpointSpec endpointSpec = McpRequestUtil.parseMcpEndpointSpec(basicInfo, mcpForm);
        mcpProxy.updateMcpServer(mcpForm.getNamespaceId(), mcpForm.getLatest(), basicInfo, mcpTools,
            endpointSpec,
            mcpForm.isOverrideExisting());
        return Result.success("ok");
    }
    
    /**
     * Delete existed mcp server.
     *
     * @param mcpForm delete mcp server request form
     * @throws NacosException any exception during handling
     */
    @Since("3.0.0")
    @DeleteMapping
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.CONSOLE_API)
    public Result<String> deleteMcpServer(McpForm mcpForm) throws NacosException {
        mcpForm.validate();
        mcpProxy.deleteMcpServer(mcpForm.getNamespaceId(), mcpForm.getMcpName(), mcpForm.getMcpId(),
            mcpForm.getVersion());
        return Result.success("ok");
    }
    
    /**
     * Page lifecycle metadata for the Versions of one MCP resource.
     */
    @Since("3.3.0")
    @GetMapping("/versions")
    @Secured(action = ActionTypes.READ, signType = SignType.AI, apiType = ApiType.CONSOLE_API)
    public Result<Page<McpLifecycleVersionSummary>> listLifecycleVersions(
        McpLifecycleVersionListForm form, PageForm pageForm) throws NacosException {
        form.validate();
        pageForm.validate();
        return Result.success(mcpProxy.listLifecycleVersions(form.getNamespaceId(),
            form.getMcpName(), form.getStatus(), pageForm.getPageNo(), pageForm.getPageSize()));
    }
    
    /**
     * Read one exact MCP lifecycle Version.
     */
    @Since("3.3.0")
    @GetMapping("/version")
    @Secured(action = ActionTypes.READ, signType = SignType.AI, apiType = ApiType.CONSOLE_API)
    public Result<McpLifecycleVersionDetail> getLifecycleVersion(
        McpLifecycleVersionForm form) throws NacosException {
        form.validate();
        return Result.success(mcpProxy.getLifecycleVersion(form.getNamespaceId(),
            form.getMcpName(), form.getVersion()));
    }
    
    /**
     * Create one new MCP draft Version.
     */
    @Since("3.3.0")
    @PostMapping("/draft")
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.CONSOLE_API)
    public Result<McpLifecycleVersionDetail> createLifecycleDraft(
        McpLifecycleDraftForm form) throws NacosException {
        form.validate();
        McpServerBasicInfo server = McpRequestUtil.parseMcpServerBasicInfo(form);
        return Result.success(mcpProxy.createLifecycleDraft(form.getNamespaceId(), server,
            McpRequestUtil.parseMcpTools(form), McpRequestUtil.parseMcpResources(form),
            McpRequestUtil.parseMcpEndpointSpec(server, form)));
    }
    
    /**
     * Replace one exact current MCP draft.
     */
    @Since("3.3.0")
    @PutMapping("/draft")
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.CONSOLE_API)
    public Result<McpLifecycleVersionDetail> updateLifecycleDraft(
        McpLifecycleDraftForm form) throws NacosException {
        form.validate();
        McpServerBasicInfo server = McpRequestUtil.parseMcpServerBasicInfo(form);
        return Result.success(mcpProxy.updateLifecycleDraft(form.getNamespaceId(), server,
            McpRequestUtil.parseMcpTools(form), McpRequestUtil.parseMcpResources(form),
            McpRequestUtil.parseMcpEndpointSpec(server, form)));
    }
    
    /**
     * Delete one exact current MCP draft.
     */
    @Since("3.3.0")
    @DeleteMapping("/draft")
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.CONSOLE_API)
    public Result<Void> deleteLifecycleDraft(McpLifecycleVersionForm form)
        throws NacosException {
        form.validate();
        mcpProxy.deleteLifecycleDraft(form.getNamespaceId(), form.getMcpName(),
            form.getVersion());
        return Result.success();
    }
    
    /**
     * Submit one exact MCP working Version.
     */
    @Since("3.3.0")
    @PostMapping("/submit")
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.CONSOLE_API)
    public Result<McpLifecycleVersionSummary> submitLifecycleVersion(
        McpLifecycleVersionForm form) throws NacosException {
        form.validate();
        return Result.success(mcpProxy.submitLifecycleVersion(form.getNamespaceId(),
            form.getMcpName(), form.getVersion()));
    }
    
    /**
     * Publish one exact reviewed MCP Version.
     */
    @Since("3.3.0")
    @PostMapping("/publish")
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.CONSOLE_API)
    public Result<McpLifecycleVersionSummary> publishLifecycleVersion(
        McpLifecycleVersionForm form) throws NacosException {
        form.validate();
        return Result.success(mcpProxy.publishLifecycleVersion(form.getNamespaceId(),
            form.getMcpName(), form.getVersion()));
    }
    
    /**
     * Force-publish one exact MCP working Version.
     */
    @Since("3.3.0")
    @PostMapping("/force-publish")
    @Secured(resource = Constants.MCP_CONSOLE_PATH + "/force-publish",
        action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.CONSOLE_API)
    public Result<McpLifecycleVersionSummary> forcePublishLifecycleVersion(
        McpLifecycleVersionForm form) throws NacosException {
        form.validate();
        return Result.success(mcpProxy.forcePublishLifecycleVersion(form.getNamespaceId(),
            form.getMcpName(), form.getVersion()));
    }
    
    /**
     * Return one exact reviewed MCP Version to draft.
     */
    @Since("3.3.0")
    @PostMapping("/redraft")
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.CONSOLE_API)
    public Result<McpLifecycleVersionSummary> redraftLifecycleVersion(
        McpLifecycleVersionForm form) throws NacosException {
        form.validate();
        return Result.success(mcpProxy.redraftLifecycleVersion(form.getNamespaceId(),
            form.getMcpName(), form.getVersion()));
    }
    
    /**
     * Bring one exact offline MCP Version online and make it latest.
     */
    @Since("3.3.0")
    @PostMapping("/online")
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.CONSOLE_API)
    public Result<McpLifecycleVersionSummary> onlineLifecycleVersion(
        McpLifecycleVersionForm form) throws NacosException {
        form.validate();
        return Result.success(mcpProxy.onlineLifecycleVersion(form.getNamespaceId(),
            form.getMcpName(), form.getVersion()));
    }
    
    /**
     * Take one exact online MCP Version offline.
     */
    @Since("3.3.0")
    @PostMapping("/offline")
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.CONSOLE_API)
    public Result<McpLifecycleVersionSummary> offlineLifecycleVersion(
        McpLifecycleVersionForm form) throws NacosException {
        form.validate();
        return Result.success(mcpProxy.offlineLifecycleVersion(form.getNamespaceId(),
            form.getMcpName(), form.getVersion()));
    }
    
    /**
     * Replace custom MCP labels while preserving the server-managed latest label.
     */
    @Since("3.3.0")
    @PutMapping("/labels")
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.CONSOLE_API)
    public Result<Map<String, String>> updateLifecycleLabels(McpLifecycleLabelsForm form)
        throws NacosException {
        form.validate();
        Map<String, String> labels = McpRequestUtil.parseLifecycleLabels(form.getLabels());
        return Result.success(mcpProxy.updateLifecycleLabels(form.getNamespaceId(),
            form.getMcpName(), labels));
    }
    
    /**
     * Validate MCP server import request.
     *
     * @param mcpImportForm import request form
     * @return validation result with details about potential issues
     * @throws NacosException any exception during validation
     * @deprecated use {@code POST /v3/console/ai/import/validate} instead. Planned for removal in
     *     Nacos 3.4.0.
     */
    @Deprecated
    @Since("3.1.0")
    @PostMapping("/import/validate")
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.CONSOLE_API)
    public Result<McpServerImportValidationResult> validateImport(McpImportForm mcpImportForm)
        throws NacosException {
        CompatibilityHelper.check("POST /v3/console/ai/import/validate");
        mcpImportForm.validate();
        McpServerImportRequest request = convertToImportRequest(mcpImportForm);
        McpServerImportValidationResult result =
            mcpProxy.validateImport(mcpImportForm.getNamespaceId(), request);
        return Result.success(result);
    }
    
    /**
     * Execute MCP server import operation.
     *
     * @param mcpImportForm import request form
     * @return import response with results and statistics
     * @throws NacosException any exception during import execution
     * @deprecated use {@code POST /v3/console/ai/import/execute} instead. Planned for removal in
     *     Nacos 3.4.0.
     */
    @Deprecated
    @Since("3.1.0")
    @PostMapping("/import/execute")
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.CONSOLE_API)
    public Result<McpServerImportResponse> executeImport(McpImportForm mcpImportForm)
        throws NacosException {
        CompatibilityHelper.check("POST /v3/console/ai/import/execute");
        mcpImportForm.validate();
        McpServerImportRequest request = convertToImportRequest(mcpImportForm);
        McpServerImportResponse response =
            mcpProxy.executeImport(mcpImportForm.getNamespaceId(), request);
        return Result.success(response);
    }
    
    /**
     * Convert McpImportForm to McpServerImportRequest.
     *
     * @param form the form from HTTP request
     * @return the import request for service layer
     * @deprecated part of the legacy MCP import endpoint bridge. Planned for removal in Nacos
     *     3.4.0.
     */
    @Deprecated
    private McpServerImportRequest convertToImportRequest(McpImportForm form) {
        McpServerImportRequest request = new McpServerImportRequest();
        request.setImportType(form.getImportType());
        request.setData(form.getData());
        request.setOverrideExisting(form.isOverrideExisting());
        request.setValidateOnly(form.isValidateOnly());
        request.setSkipInvalid(form.isSkipInvalid());
        request.setSelectedServers(form.getSelectedServers());
        // Optional URL pagination parameters
        request.setCursor(form.getCursor());
        request.setLimit(form.getLimit());
        // Optional registry search parameter
        request.setSearch(form.getSearch());
        return request;
    }
}
