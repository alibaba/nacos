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

package com.alibaba.nacos.ai.service.search;

import com.alibaba.nacos.ai.config.ConditionalOnAiResourceSearchEnabled;
import com.alibaba.nacos.ai.constant.AiResourceConstants;
import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.model.search.AiResourceSearchDocument;
import com.alibaba.nacos.ai.service.mcp.McpOperationService;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.mcp.McpCapability;
import com.alibaba.nacos.api.ai.model.mcp.McpResourceSpecification;
import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpTool;
import com.alibaba.nacos.api.ai.model.mcp.McpToolSpecification;
import com.alibaba.nacos.api.ai.model.mcp.registry.ServerVersionDetail;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.common.utils.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Search type handler for canonical MCP server resources.
 *
 * <p>The canonical document identity is always the MCP name. Projection follows the complete
 * compatibility router so SYNCING reads the full historical view and managed mode reads lifecycle
 * rows; partially reconciled rows therefore never become a Search authority.</p>
 *
 * @author nacos
 */
@Service
@ConditionalOnAiResourceSearchEnabled
public class McpAiResourceSearchTypeHandler implements AiResourceSearchTypeHandler {
    
    private static final int DEFAULT_MAX_MCP_CONTENT_CHARS = 12000;
    
    private final McpOperationService mcpOperationService;
    
    private final AiResourceSearchDocumentBuilder documentBuilder =
        new AiResourceSearchDocumentBuilder();
    
    private final AiResourceIndexProjectionBuilder projectionBuilder =
        new AiResourceIndexProjectionBuilder();
    
    public McpAiResourceSearchTypeHandler(McpOperationService mcpOperationService) {
        this.mcpOperationService = mcpOperationService;
    }
    
    @Override
    public int projectionVersion() {
        return 2;
    }
    
    @Override
    public Collection<String> resourceTypes() {
        return Collections.singletonList(AiResourceConstants.RESOURCE_TYPE_MCP);
    }
    
    @Override
    public AiResourceIndexProjection project(String namespaceId, String resourceType,
        String resourceName, String version) throws NacosException {
        if (!AiResourceConstants.RESOURCE_TYPE_MCP.equals(resourceType)) {
            return null;
        }
        try {
            McpServerDetailInfo detail = mcpOperationService.getMcpServerDetail(namespaceId,
                null, resourceName, version);
            return projectServer(namespaceId, detail);
        } catch (NacosException e) {
            if (e.getErrCode() == NacosException.NOT_FOUND) {
                return null;
            }
            throw e;
        }
    }
    
    @Override
    public AiResourceIndexSourcePage scan(String namespaceId, String resourceType, int pageNo,
        int pageSize) throws NacosException {
        if (!AiResourceConstants.RESOURCE_TYPE_MCP.equals(resourceType)) {
            return new AiResourceIndexSourcePage(Collections.emptyList(), false);
        }
        Page<McpServerBasicInfo> page = mcpOperationService.listMcpServerWithPage(namespaceId,
            null, Constants.MCP_LIST_SEARCH_ACCURATE, pageNo, pageSize);
        List<McpServerBasicInfo> servers = page == null ? null : page.getPageItems();
        if (servers == null || servers.isEmpty()) {
            return new AiResourceIndexSourcePage(Collections.emptyList(), false);
        }
        List<AiResourceIndexSource> items = new ArrayList<>();
        for (McpServerBasicInfo server : servers) {
            String resourceName = server == null ? null : server.getName();
            try {
                items.add(AiResourceIndexSource.success(resourceName,
                    projectServer(namespaceId, server)));
            } catch (Exception e) {
                items.add(AiResourceIndexSource.failed(resourceName, e));
            }
        }
        return new AiResourceIndexSourcePage(items, servers.size() >= pageSize);
    }
    
    @Override
    public boolean isCurrent(AiResourceSearchDocument document) {
        if (document == null
            || !AiResourceConstants.RESOURCE_TYPE_MCP.equals(document.getResourceType())) {
            return false;
        }
        try {
            McpServerDetailInfo detail = mcpOperationService.getMcpServerDetail(
                document.getNamespaceId(), null, document.getResourceName(),
                document.getResourceVersion());
            ServerVersionDetail versionDetail = detail == null ? null : detail.getVersionDetail();
            return detail != null && document.getResourceName().equals(detail.getName())
                && detail.isEnabled()
                && AiConstants.Mcp.MCP_STATUS_ACTIVE.equalsIgnoreCase(detail.getStatus())
                && versionDetail != null && Boolean.TRUE.equals(versionDetail.getIs_latest());
        } catch (NacosException e) {
            return false;
        }
    }
    
    @Override
    public boolean exists(String namespaceId, String resourceType, String resourceName)
        throws NacosException {
        if (!AiResourceConstants.RESOURCE_TYPE_MCP.equals(resourceType)) {
            return false;
        }
        try {
            return mcpOperationService.getMcpServerDetail(namespaceId, null, resourceName,
                null) != null;
        } catch (NacosException e) {
            if (e.getErrCode() == NacosException.NOT_FOUND) {
                return false;
            }
            throw e;
        }
    }
    
    private AiResourceIndexProjection projectServer(String namespaceId,
        McpServerBasicInfo mcpServer) {
        String resourceName = mcpServer == null ? null : mcpServer.getName();
        String resourceVersion = mcpServer == null ? null : resolveMcpVersion(mcpServer);
        if (!isIndexable(mcpServer) || StringUtils.isBlank(resourceName)
            || StringUtils.isBlank(resourceVersion)) {
            return null;
        }
        AiResourceSearchDocument document =
            documentBuilder.fromMcpServer(namespaceId, mcpServer);
        List<AiResourceIndexEnhancementContent> contents = mcpContents(mcpServer);
        return projectionBuilder.build(document, contents,
            AiResourceSearchConstants.CHUNK_TYPE_MCP_CONTENT);
    }
    
    private List<AiResourceIndexEnhancementContent> mcpContents(
        McpServerBasicInfo mcpServer) {
        List<AiResourceIndexEnhancementContent> contents = new ArrayList<>();
        addMcpContent(contents, "mcp-server.json", mcpServerText(mcpServer));
        if (mcpServer instanceof McpServerDetailInfo detail) {
            addMcpContent(contents, "mcp-tools.json", mcpToolText(detail.getToolSpec()));
            addMcpContent(contents, "mcp-resources.json",
                mcpResourceText(detail.getResourceSpec()));
        }
        return contents;
    }
    
    private void addMcpContent(List<AiResourceIndexEnhancementContent> contents, String path,
        String text) {
        if (StringUtils.isNotBlank(text)) {
            contents.add(new AiResourceIndexEnhancementContent(path,
                limit(text, DEFAULT_MAX_MCP_CONTENT_CHARS)));
        }
    }
    
    private String mcpServerText(McpServerBasicInfo mcpServer) {
        StringBuilder text = new StringBuilder();
        appendLine(text, "# MCP server");
        appendField(text, "name", mcpServer.getName());
        appendField(text, "description", mcpServer.getDescription());
        appendField(text, "protocol", mcpServer.getProtocol());
        appendField(text, "front protocol", mcpServer.getFrontProtocol());
        appendField(text, "website", mcpServer.getWebsiteUrl());
        if (mcpServer.getCapabilities() != null && !mcpServer.getCapabilities().isEmpty()) {
            appendLine(text, "capabilities: " + mcpCapabilities(mcpServer.getCapabilities()));
        }
        return text.toString();
    }
    
    private String mcpToolText(McpToolSpecification toolSpec) {
        if (toolSpec == null || toolSpec.getTools() == null || toolSpec.getTools().isEmpty()) {
            return null;
        }
        StringBuilder text = new StringBuilder();
        appendLine(text, "# MCP tools");
        for (McpTool tool : toolSpec.getTools()) {
            if (tool == null) {
                continue;
            }
            appendLine(text, "## Tool " + tool.getName());
            appendLine(text, tool.getDescription());
            appendMap(text, "input schema", tool.getInputSchema());
            appendMap(text, "output schema", tool.getOutputSchema());
        }
        return text.toString();
    }
    
    private String mcpResourceText(McpResourceSpecification resourceSpec) {
        if (resourceSpec == null) {
            return null;
        }
        StringBuilder text = new StringBuilder();
        appendResourceMaps(text, "# MCP resources", "resource", resourceSpec.getResources());
        appendResourceMaps(text, "# MCP resource templates", "resource template",
            resourceSpec.getResourceTemplates());
        return text.toString();
    }
    
    private void appendResourceMaps(StringBuilder text, String heading, String label,
        List<Map<String, Object>> resources) {
        if (resources == null || resources.isEmpty()) {
            return;
        }
        appendLine(text, heading);
        for (Map<String, Object> resource : resources) {
            if (resource == null || resource.isEmpty()) {
                continue;
            }
            List<String> parts = new ArrayList<>();
            addMapValue(parts, resource, "name");
            addMapValue(parts, resource, "title");
            addMapValue(parts, resource, "description");
            addMapValue(parts, resource, "uri");
            addMapValue(parts, resource, "uriTemplate");
            if (!parts.isEmpty()) {
                appendLine(text, label + ": " + StringUtils.join(parts, " "));
            }
        }
    }
    
    private void addMapValue(List<String> parts, Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value != null && StringUtils.isNotBlank(String.valueOf(value))) {
            parts.add(key + ": " + value);
        }
    }
    
    private void appendMap(StringBuilder text, String label, Map<String, Object> map) {
        if (map != null && !map.isEmpty()) {
            appendLine(text, label + ": " + map);
        }
    }
    
    private String mcpCapabilities(Collection<McpCapability> capabilities) {
        List<String> values = new ArrayList<>();
        for (McpCapability capability : capabilities) {
            if (capability != null) {
                values.add(capability.name().toLowerCase(Locale.ROOT));
            }
        }
        return StringUtils.join(values, " ");
    }
    
    private void appendField(StringBuilder text, String key, String value) {
        if (StringUtils.isNotBlank(value)) {
            appendLine(text, key + ": " + value);
        }
    }
    
    private void appendLine(StringBuilder text, String line) {
        if (StringUtils.isBlank(line)) {
            return;
        }
        if (text.length() > 0) {
            text.append('\n');
        }
        text.append(line);
    }
    
    private boolean isIndexable(McpServerBasicInfo mcpServer) {
        ServerVersionDetail versionDetail = mcpServer == null ? null
            : mcpServer.getVersionDetail();
        return mcpServer != null && mcpServer.isEnabled()
            && AiConstants.Mcp.MCP_STATUS_ACTIVE.equalsIgnoreCase(mcpServer.getStatus())
            && versionDetail != null && Boolean.TRUE.equals(versionDetail.getIs_latest());
    }
    
    private String resolveMcpVersion(McpServerBasicInfo mcpServer) {
        if (mcpServer.getVersionDetail() != null
            && StringUtils.isNotBlank(mcpServer.getVersionDetail().getVersion())) {
            return mcpServer.getVersionDetail().getVersion();
        }
        return mcpServer.getVersion();
    }
    
    private String limit(String text, int maxLength) {
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }
}
