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
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.form.mcp.admin.McpDetailForm;
import com.alibaba.nacos.ai.form.mcp.admin.McpForm;
import com.alibaba.nacos.ai.form.mcp.admin.McpImportForm;
import com.alibaba.nacos.ai.form.mcp.admin.McpListForm;
import com.alibaba.nacos.ai.form.mcp.admin.McpServerDraftForm;
import com.alibaba.nacos.ai.form.mcp.admin.McpServerLabelsForm;
import com.alibaba.nacos.ai.form.mcp.admin.McpServerVersionForm;
import com.alibaba.nacos.ai.form.mcp.admin.McpServerVersionListForm;
import com.alibaba.nacos.ai.form.mcp.admin.McpUpdateForm;
import com.alibaba.nacos.ai.param.McpHttpParamExtractor;
import com.alibaba.nacos.ai.utils.McpRequestUtil;
import com.alibaba.nacos.api.ai.model.mcp.McpEndpointSpec;
import com.alibaba.nacos.api.ai.model.mcp.McpEndpointInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerCloneItem;
import com.alibaba.nacos.api.ai.model.mcp.McpServerRemoteServiceConfig;
import com.alibaba.nacos.api.ai.model.mcp.McpServiceRef;
import com.alibaba.nacos.api.ai.model.mcp.McpServerVersionDetail;
import com.alibaba.nacos.api.ai.model.mcp.McpServerVersionSummary;
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
import com.alibaba.nacos.api.naming.CommonParams;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.console.config.McpEndpointAccessValidator;
import com.alibaba.nacos.console.proxy.ai.McpProxy;
import com.alibaba.nacos.core.controller.compatibility.CompatibilityHelper;
import com.alibaba.nacos.core.model.form.PageForm;
import com.alibaba.nacos.core.paramcheck.ExtractorManager;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.SignType;
import com.alibaba.nacos.api.config.model.SameConfigPolicy;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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
     * Page management metadata for the Versions of one MCP resource.
     */
    @Since("3.3.0")
    @GetMapping("/versions")
    @Secured(action = ActionTypes.READ, signType = SignType.AI, apiType = ApiType.CONSOLE_API)
    public Result<Page<McpServerVersionSummary>> listMcpServerVersions(
        McpServerVersionListForm form, PageForm pageForm) throws NacosException {
        form.validate();
        pageForm.validate();
        return Result.success(mcpProxy.listMcpServerVersions(form.getNamespaceId(),
            form.getMcpName(), form.getStatus(), pageForm.getPageNo(), pageForm.getPageSize()));
    }
    
    /**
     * Read one exact MCP Version.
     */
    @Since("3.3.0")
    @GetMapping("/version")
    @Secured(action = ActionTypes.READ, signType = SignType.AI, apiType = ApiType.CONSOLE_API)
    public Result<McpServerVersionDetail> getMcpServerVersion(
        McpServerVersionForm form) throws NacosException {
        form.validate();
        return Result.success(mcpProxy.getMcpServerVersion(form.getNamespaceId(),
            form.getMcpName(), form.getVersion()));
    }
    
    /**
     * Create one new MCP draft Version.
     */
    @Since("3.3.0")
    @PostMapping("/draft")
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.CONSOLE_API)
    public Result<McpServerVersionDetail> createMcpServerDraft(
        McpServerDraftForm form) throws NacosException {
        form.validate();
        McpServerBasicInfo server = McpRequestUtil.parseMcpServerBasicInfo(form);
        return Result.success(mcpProxy.createMcpServerDraft(form.getNamespaceId(), server,
            McpRequestUtil.parseMcpTools(form), McpRequestUtil.parseMcpResources(form),
            McpRequestUtil.parseMcpEndpointSpec(server, form)));
    }
    
    /**
     * Replace one exact current MCP draft.
     */
    @Since("3.3.0")
    @PutMapping("/draft")
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.CONSOLE_API)
    public Result<McpServerVersionDetail> updateMcpServerDraft(
        McpServerDraftForm form) throws NacosException {
        form.validate();
        McpServerBasicInfo server = McpRequestUtil.parseMcpServerBasicInfo(form);
        return Result.success(mcpProxy.updateMcpServerDraft(form.getNamespaceId(), server,
            McpRequestUtil.parseMcpTools(form), McpRequestUtil.parseMcpResources(form),
            McpRequestUtil.parseMcpEndpointSpec(server, form)));
    }
    
    /**
     * Delete one exact current MCP draft.
     */
    @Since("3.3.0")
    @DeleteMapping("/draft")
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.CONSOLE_API)
    public Result<Void> deleteMcpServerDraft(McpServerVersionForm form)
        throws NacosException {
        form.validate();
        mcpProxy.deleteMcpServerDraft(form.getNamespaceId(), form.getMcpName(),
            form.getVersion());
        return Result.success();
    }
    
    /**
     * Submit one exact MCP working Version.
     */
    @Since("3.3.0")
    @PostMapping("/submit")
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.CONSOLE_API)
    public Result<McpServerVersionSummary> submitMcpServerVersion(
        McpServerVersionForm form) throws NacosException {
        form.validate();
        return Result.success(mcpProxy.submitMcpServerVersion(form.getNamespaceId(),
            form.getMcpName(), form.getVersion()));
    }
    
    /**
     * Publish one exact reviewed MCP Version.
     */
    @Since("3.3.0")
    @PostMapping("/publish")
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.CONSOLE_API)
    public Result<McpServerVersionSummary> publishMcpServerVersion(
        McpServerVersionForm form) throws NacosException {
        form.validate();
        return Result.success(mcpProxy.publishMcpServerVersion(form.getNamespaceId(),
            form.getMcpName(), form.getVersion()));
    }
    
    /**
     * Force-publish one exact MCP working Version.
     */
    @Since("3.3.0")
    @PostMapping("/force-publish")
    @Secured(resource = Constants.MCP_CONSOLE_PATH + "/force-publish",
        action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.CONSOLE_API)
    public Result<McpServerVersionSummary> forcePublishMcpServerVersion(
        McpServerVersionForm form) throws NacosException {
        form.validate();
        return Result.success(mcpProxy.forcePublishMcpServerVersion(form.getNamespaceId(),
            form.getMcpName(), form.getVersion()));
    }
    
    /**
     * Return one exact reviewed MCP Version to draft.
     */
    @Since("3.3.0")
    @PostMapping("/redraft")
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.CONSOLE_API)
    public Result<McpServerVersionSummary> redraftMcpServerVersion(
        McpServerVersionForm form) throws NacosException {
        form.validate();
        return Result.success(mcpProxy.redraftMcpServerVersion(form.getNamespaceId(),
            form.getMcpName(), form.getVersion()));
    }
    
    /**
     * Bring one exact offline MCP Version online and make it latest.
     */
    @Since("3.3.0")
    @PostMapping("/online")
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.CONSOLE_API)
    public Result<McpServerVersionSummary> onlineMcpServerVersion(
        McpServerVersionForm form) throws NacosException {
        form.validate();
        return Result.success(mcpProxy.onlineMcpServerVersion(form.getNamespaceId(),
            form.getMcpName(), form.getVersion()));
    }
    
    /**
     * Take one exact online MCP Version offline.
     */
    @Since("3.3.0")
    @PostMapping("/offline")
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.CONSOLE_API)
    public Result<McpServerVersionSummary> offlineMcpServerVersion(
        McpServerVersionForm form) throws NacosException {
        form.validate();
        return Result.success(mcpProxy.offlineMcpServerVersion(form.getNamespaceId(),
            form.getMcpName(), form.getVersion()));
    }
    
    /**
     * Replace custom MCP labels while preserving the server-managed latest label.
     */
    @Since("3.3.0")
    @PutMapping("/labels")
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.CONSOLE_API)
    public Result<Map<String, String>> updateMcpServerLabels(McpServerLabelsForm form)
        throws NacosException {
        form.validate();
        Map<String, String> labels = McpRequestUtil.parseMcpServerLabels(form.getLabels());
        return Result.success(mcpProxy.updateMcpServerLabels(form.getNamespaceId(),
            form.getMcpName(), labels));
    }

    /**
     * Export the selected MCP servers as a JSON array of the legacy serving projection.
     *
     * <p>The projection is assembled by the lifecycle-backed operation service, so the exported
     * file contains the selected current Version, optional tools/resources and endpoint data.</p>
     *
     * @param namespaceId namespace containing the MCP servers
     * @param mcpNames selected server names, supplied repeatedly or as a comma-separated value
     * @return downloadable JSON document
     * @throws NacosException if a selected server cannot be read
     */
    @Since("3.3.0")
    @GetMapping("/export")
    @Secured(action = ActionTypes.READ, signType = SignType.AI, apiType = ApiType.CONSOLE_API)
    public ResponseEntity<byte[]> exportMcpServers(
        @RequestParam(value = "namespaceId", required = false) String namespaceId,
        @RequestParam List<String> mcpNames)
        throws NacosException {
        namespaceId = StringUtils.isBlank(namespaceId) ? AiConstants.Mcp.MCP_DEFAULT_NAMESPACE
            : namespaceId;
        List<String> names = normalizeNames(mcpNames);
        if (names.isEmpty()) {
            throw invalidParameter("mcpNames");
        }
        List<McpServerDetailInfo> servers = new ArrayList<>(names.size());
        for (String name : names) {
            servers.add(requireSourceServer(namespaceId, name));
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setContentDisposition(ContentDisposition.attachment()
            .filename("mcp-servers.json").build());
        return new ResponseEntity<>(JacksonUtils.toJsonBytes(servers), headers, HttpStatus.OK);
    }

    /**
     * Clone selected MCP servers using the lifecycle-backed draft and publish operations.
     *
     * <p>Clones use the current serving Version only. The default conflict policy is {@code
     * ABORT}; {@code SKIP} and {@code OVERWRITE} are also supported. A missing target name keeps
     * the source name, which matches the configuration clone API and allows callers to choose
     * names explicitly when cloning within one namespace.</p>
     *
     * @param sourceNamespaceId source namespace
     * @param targetNamespaceId target namespace, defaults to the source namespace
     * @param cloneItems selected source and optional target names
     * @param policy conflict policy
     * @return clone counts and per-item status
     * @throws NacosException if validation fails or the ABORT policy finds a conflict
     */
    @Since("3.3.0")
    @PostMapping("/clone")
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.CONSOLE_API)
    public Result<Map<String, Object>> cloneMcpServers(
        @RequestParam(value = "namespaceId", required = false) String sourceNamespaceId,
        @RequestParam(value = "targetNamespaceId", required = false) String targetNamespaceId,
        @RequestParam(value = "policy", defaultValue = "ABORT") SameConfigPolicy policy,
        @RequestBody List<McpServerCloneItem> cloneItems) throws NacosException {
        String sourceNamespace = StringUtils.isBlank(sourceNamespaceId)
            ? AiConstants.Mcp.MCP_DEFAULT_NAMESPACE : sourceNamespaceId;
        String targetNamespace = StringUtils.isBlank(targetNamespaceId)
            ? sourceNamespace : targetNamespaceId;
        List<McpServerCloneItem> items = cloneItems == null ? Collections.emptyList() : cloneItems;
        SameConfigPolicy resolvedPolicy = policy == null ? SameConfigPolicy.ABORT : policy;
        validateCloneItems(items);
        Map<String, McpServerDetailInfo> sourceServers = new LinkedHashMap<>();
        if (SameConfigPolicy.ABORT == resolvedPolicy) {
            validateNoCloneConflicts(targetNamespace, items);
            for (McpServerCloneItem item : items) {
                String sourceName = item.getSourceName().trim();
                if (!sourceServers.containsKey(sourceName)) {
                    sourceServers.put(sourceName, requireSourceServer(sourceNamespace, sourceName));
                }
            }
        }

        Map<String, Object> response = new LinkedHashMap<>();
        List<Map<String, String>> results = new ArrayList<>(items.size());
        int successCount = 0;
        int skippedCount = 0;
        int failedCount = 0;
        for (McpServerCloneItem item : items) {
            String sourceName = item.getSourceName().trim();
            String targetName = resolveTargetName(item);
            Map<String, String> itemResult = new LinkedHashMap<>();
            itemResult.put("sourceName", sourceName);
            itemResult.put("targetName", targetName);
            try {
                boolean targetExists = targetExists(targetNamespace, targetName);
                if (targetExists
                    && SameConfigPolicy.ABORT == resolvedPolicy) {
                    throw new NacosException(NacosException.CONFLICT,
                        "Target MCP server already exists: " + targetName);
                }
                if (targetExists
                    && SameConfigPolicy.SKIP == resolvedPolicy) {
                    itemResult.put("status", "SKIPPED");
                    itemResult.put("message", "Target MCP server already exists");
                    skippedCount++;
                    results.add(itemResult);
                    continue;
                }
                McpServerDetailInfo source = sourceServers.get(sourceName);
                if (source == null) {
                    source = requireSourceServer(sourceNamespace, sourceName);
                }
                McpServerVersionDetail sourceVersion = mcpProxy.getMcpServerVersion(sourceNamespace,
                    sourceName, source.getVersion());
                if (targetExists) {
                    mcpProxy.deleteMcpServer(targetNamespace, targetName, null, null);
                }
                McpServerBasicInfo server = copyServerSpecification(source, targetNamespace,
                    targetName);
                McpServerVersionDetail created = mcpProxy.createMcpServerDraft(targetNamespace,
                    server, source.getToolSpec(), source.getResourceSpec(),
                    toEndpointSpecification(source));
                mcpProxy.forcePublishMcpServerVersion(targetNamespace, targetName,
                    created.getVersion());
                if (sourceVersion != null && sourceVersion.getLabels() != null
                    && !sourceVersion.getLabels().isEmpty()) {
                    mcpProxy.updateMcpServerLabels(targetNamespace, targetName,
                        sourceVersion.getLabels());
                }
                if (sourceVersion != null && !StringUtils.isBlank(sourceVersion.getScope())) {
                    mcpProxy.updateMcpServerScope(targetNamespace, targetName,
                        sourceVersion.getScope());
                }
                itemResult.put("status", "SUCCESS");
                successCount++;
            } catch (NacosException e) {
                if (SameConfigPolicy.ABORT == resolvedPolicy) {
                    throw e;
                }
                itemResult.put("status", "FAILED");
                itemResult.put("message", e.getErrMsg());
                failedCount++;
            }
            results.add(itemResult);
        }
        response.put("success", failedCount == 0);
        response.put("totalCount", items.size());
        response.put("successCount", successCount);
        response.put("skippedCount", skippedCount);
        response.put("failedCount", failedCount);
        response.put("results", results);
        return Result.success(response);
    }

    private List<String> normalizeNames(List<String> rawNames) {
        List<String> result = new ArrayList<>();
        if (rawNames == null) {
            return result;
        }
        for (String rawName : rawNames) {
            if (StringUtils.isBlank(rawName)) {
                continue;
            }
            result.addAll(Arrays.stream(rawName.split(","))
                .map(String::trim)
                .filter(value -> !StringUtils.isBlank(value))
                .distinct()
                .toList());
        }
        return result.stream().distinct().toList();
    }

    private void validateCloneItems(List<McpServerCloneItem> cloneItems)
        throws NacosApiException {
        if (cloneItems.isEmpty()) {
            throw invalidParameter("cloneItems");
        }
        for (McpServerCloneItem item : cloneItems) {
            if (item == null || StringUtils.isBlank(item.getSourceName())) {
                throw invalidParameter("cloneItems.sourceName");
            }
            if (item.getTargetName() != null && item.getTargetName().trim().isEmpty()) {
                item.setTargetName(null);
            }
        }
    }

    private void validateNoCloneConflicts(String targetNamespace, List<McpServerCloneItem> items)
        throws NacosException {
        Set<String> targetNames = new HashSet<>();
        for (McpServerCloneItem item : items) {
            String targetName = resolveTargetName(item);
            if (!targetNames.add(targetName)) {
                throw new NacosException(NacosException.CONFLICT,
                    "Duplicate target MCP server: " + targetName);
            }
            if (targetExists(targetNamespace, targetName)) {
                throw new NacosException(NacosException.CONFLICT,
                    "Target MCP server already exists: " + targetName);
            }
        }
    }

    private boolean targetExists(String namespaceId, String mcpName) throws NacosException {
        try {
            return mcpProxy.getMcpServer(namespaceId, mcpName, null, null) != null;
        } catch (NacosException e) {
            if (e.getErrCode() == NacosException.NOT_FOUND) {
                return false;
            }
            throw e;
        }
    }

    private McpServerDetailInfo requireSourceServer(String namespaceId, String mcpName)
        throws NacosException {
        McpServerDetailInfo source = mcpProxy.getMcpServer(namespaceId, mcpName, null, null);
        if (source == null) {
            throw new NacosException(NacosException.NOT_FOUND,
                "Source MCP server not found: " + mcpName);
        }
        return source;
    }

    private String resolveTargetName(McpServerCloneItem item) {
        return StringUtils.isBlank(item.getTargetName()) ? item.getSourceName().trim()
            : item.getTargetName().trim();
    }

    private McpServerBasicInfo copyServerSpecification(McpServerDetailInfo source,
        String namespaceId, String targetName) {
        McpServerBasicInfo result = new McpServerBasicInfo();
        BeanUtils.copyProperties(source, result);
        result.setId(null);
        result.setName(targetName);
        result.setNamespaceId(namespaceId);
        return result;
    }

    private McpEndpointSpec toEndpointSpecification(McpServerDetailInfo source)
        throws NacosException {
        if (AiConstants.Mcp.MCP_PROTOCOL_STDIO.equalsIgnoreCase(source.getProtocol())) {
            return null;
        }
        McpServerRemoteServiceConfig remote = source.getRemoteServerConfig();
        if (remote == null || remote.getServiceRef() == null) {
            throw new NacosException(NacosException.INVALID_PARAM,
                "MCP server has no backend endpoint specification: " + source.getName());
        }
        McpServiceRef serviceRef = remote.getServiceRef();
        if (source.getVersion() != null
            && Objects.equals(Constants.MCP_SERVER_ENDPOINT_GROUP, serviceRef.getGroupName())
            && Objects.equals(source.getName() + "::" + source.getVersion(),
                serviceRef.getServiceName())) {
            List<McpEndpointInfo> endpoints = source.getBackendEndpoints();
            if (endpoints == null || endpoints.isEmpty()) {
                throw new NacosException(NacosException.INVALID_PARAM,
                    "MCP direct endpoint has no registered instance: " + source.getName());
            }
            McpEndpointInfo endpoint = endpoints.get(0);
            McpEndpointSpec result = new McpEndpointSpec();
            result.setType(AiConstants.Mcp.MCP_ENDPOINT_TYPE_DIRECT);
            result.getData().put(Constants.MCP_SERVER_ENDPOINT_ADDRESS, endpoint.getAddress());
            result.getData().put(Constants.MCP_SERVER_ENDPOINT_PORT,
                String.valueOf(endpoint.getPort()));
            result.getData().put(Constants.MCP_BACKEND_INSTANCE_PROTOCOL_KEY,
                StringUtils.isBlank(serviceRef.getTransportProtocol()) ? endpoint.getProtocol()
                    : serviceRef.getTransportProtocol());
            return result;
        }
        McpEndpointSpec result = new McpEndpointSpec();
        result.setType(AiConstants.Mcp.MCP_ENDPOINT_TYPE_REF);
        result.getData().put(CommonParams.NAMESPACE_ID, serviceRef.getNamespaceId());
        result.getData().put(CommonParams.GROUP_NAME, serviceRef.getGroupName());
        result.getData().put(CommonParams.SERVICE_NAME, serviceRef.getServiceName());
        if (!StringUtils.isBlank(serviceRef.getTransportProtocol())) {
            result.getData().put(Constants.MCP_BACKEND_INSTANCE_PROTOCOL_KEY,
                serviceRef.getTransportProtocol());
        }
        return result;
    }

    private NacosApiException invalidParameter(String parameter) {
        return new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
            "Required parameter '" + parameter + "' is not present");
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
