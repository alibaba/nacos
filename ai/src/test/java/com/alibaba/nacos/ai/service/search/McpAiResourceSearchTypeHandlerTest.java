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

import com.alibaba.nacos.ai.constant.AiResourceConstants;
import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.model.search.AiResourceSearchDocument;
import com.alibaba.nacos.ai.service.mcp.McpLifecycleOperationService;
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
import com.alibaba.nacos.common.utils.JacksonUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link McpAiResourceSearchTypeHandler}.
 *
 * @author nacos
 */
@ExtendWith(MockitoExtension.class)
class McpAiResourceSearchTypeHandlerTest {
    
    @Mock
    private McpLifecycleOperationService mcpServerOperationService;
    
    @Test
    void projectShouldBuildMcpDocumentChunksAndFacets() throws Exception {
        McpServerDetailInfo detail = detail(true);
        detail.setFrontProtocol("stdio");
        detail.setWebsiteUrl("https://example.com");
        detail.setCapabilities(Arrays.asList(McpCapability.TOOL, null, McpCapability.RESOURCE));
        McpTool tool = new McpTool();
        tool.setName("research");
        tool.setDescription("Search academic papers");
        tool.setInputSchema(Map.of("query", "string"));
        tool.setOutputSchema(Map.of("citations", "array"));
        McpToolSpecification toolSpec = new McpToolSpecification();
        toolSpec.setTools(Arrays.asList(null, tool));
        detail.setToolSpec(toolSpec);
        McpResourceSpecification resourceSpec = new McpResourceSpecification();
        resourceSpec.setResources(Arrays.asList(null, Collections.emptyMap(),
            Map.of("name", "papers", "description", "Academic papers",
                "uri", "papers://all")));
        resourceSpec.setResourceTemplates(List.of(
            Map.of("title", "Paper", "uriTemplate", "papers://{id}")));
        detail.setResourceSpec(resourceSpec);
        when(mcpServerOperationService.getMcpServerDetail("public", null, "mcp-research", null))
            .thenReturn(detail);
        
        AiResourceIndexProjection projection = handler().project("public",
            AiResourceConstants.RESOURCE_TYPE_MCP, "mcp-research", null);
        
        assertEquals("mcp-research", projection.getDocument().getResourceName());
        assertEquals("legacy-id", projection.getFacets().get("mcpServerId"));
        assertTrue(projection.getChunks().stream().anyMatch(
            chunk -> AiResourceSearchConstants.CHUNK_TYPE_MCP_CONTENT.equals(chunk.getChunkType())
                && chunk.getChunkText().contains("academic papers")));
        assertEquals(3, projection.getEnhancementContents().size());
    }
    
    @Test
    void projectShouldReturnNullForUnsupportedMissingOrInvalidMcp() throws Exception {
        McpAiResourceSearchTypeHandler handler = handler();
        assertNull(handler.project("public", "skill", "mcp", null));
        when(mcpServerOperationService.getMcpServerDetail("public", null, "missing", null))
            .thenThrow(new NacosException(NacosException.NOT_FOUND, "missing"));
        assertNull(handler.project("public", AiResourceConstants.RESOURCE_TYPE_MCP,
            "missing", null));
        when(mcpServerOperationService.getMcpServerDetail("public", null, "failed", null))
            .thenThrow(new NacosException(NacosException.SERVER_ERROR, "failed"));
        assertThrows(NacosException.class, () -> handler.project("public",
            AiResourceConstants.RESOURCE_TYPE_MCP, "failed", null));
        
        McpServerDetailInfo disabled = detail(true);
        disabled.setEnabled(false);
        when(mcpServerOperationService.getMcpServerDetail("public", null, "disabled", null))
            .thenReturn(disabled);
        assertNull(handler.project("public", AiResourceConstants.RESOURCE_TYPE_MCP,
            "disabled", null));
        
        McpServerDetailInfo nonLatest = detail(false);
        when(mcpServerOperationService.getMcpServerDetail("public", null, "offline", null))
            .thenReturn(nonLatest);
        assertNull(handler.project("public", AiResourceConstants.RESOURCE_TYPE_MCP,
            "offline", null));
    }
    
    @Test
    void projectShouldHandleEmptyMcpDetailsAndBlankToolFields() throws Exception {
        McpServerDetailInfo emptyDetail = detail(true);
        McpResourceSpecification emptyResources = new McpResourceSpecification();
        emptyResources.setResources(Collections.emptyList());
        emptyResources.setResourceTemplates(Collections.emptyList());
        emptyDetail.setResourceSpec(emptyResources);
        when(mcpServerOperationService.getMcpServerDetail("public", null, "empty", null))
            .thenReturn(emptyDetail);
        
        AiResourceIndexProjection emptyProjection = handler().project("public",
            AiResourceConstants.RESOURCE_TYPE_MCP, "empty", null);
        
        assertEquals(1, emptyProjection.getEnhancementContents().size());
        
        McpServerDetailInfo blankToolDetail = detail(true);
        McpTool blankTool = new McpTool();
        blankTool.setName("blank");
        McpToolSpecification toolSpec = new McpToolSpecification();
        toolSpec.setTools(List.of(blankTool));
        blankToolDetail.setToolSpec(toolSpec);
        when(mcpServerOperationService.getMcpServerDetail("public", null, "blank-tool", null))
            .thenReturn(blankToolDetail);
        
        AiResourceIndexProjection blankToolProjection = handler().project("public",
            AiResourceConstants.RESOURCE_TYPE_MCP, "blank-tool", null);
        
        assertEquals(2, blankToolProjection.getEnhancementContents().size());
    }
    
    @Test
    void projectShouldHandleEveryOptionalCollectionAndVersionFallback() throws Exception {
        McpServerDetailInfo optional = detail(true);
        optional.setCapabilities(Collections.emptyList());
        ServerVersionDetail blankVersion = new ServerVersionDetail();
        blankVersion.setVersion(" ");
        blankVersion.setIs_latest(true);
        optional.setVersionDetail(blankVersion);
        optional.setVersion("fallback-version");
        McpTool blankTool = new McpTool();
        blankTool.setInputSchema(Collections.emptyMap());
        blankTool.setOutputSchema(Collections.emptyMap());
        McpToolSpecification optionalTools = new McpToolSpecification();
        optionalTools.setTools(List.of(blankTool));
        optional.setToolSpec(optionalTools);
        Map<String, Object> blankResource = new LinkedHashMap<>();
        blankResource.put("name", " ");
        blankResource.put("description", null);
        McpResourceSpecification optionalResources = new McpResourceSpecification();
        optionalResources.setResources(List.of(blankResource));
        optional.setResourceSpec(optionalResources);
        when(mcpServerOperationService.getMcpServerDetail("public", null, "optional", null))
            .thenReturn(optional);
        
        AiResourceIndexProjection projection = handler().project("public",
            AiResourceConstants.RESOURCE_TYPE_MCP, "optional", null);
        
        assertEquals("fallback-version", projection.getDocument().getResourceVersion());
        assertEquals(3, projection.getEnhancementContents().size());
        
        McpServerDetailInfo nullLists = detail(true);
        McpToolSpecification nullTools = new McpToolSpecification();
        nullTools.setTools(null);
        nullLists.setToolSpec(nullTools);
        McpResourceSpecification nullResources = new McpResourceSpecification();
        nullResources.setResources(null);
        nullResources.setResourceTemplates(null);
        nullLists.setResourceSpec(nullResources);
        when(mcpServerOperationService.getMcpServerDetail("public", null, "null-lists", null))
            .thenReturn(nullLists);
        assertEquals(1, handler().project("public", AiResourceConstants.RESOURCE_TYPE_MCP,
            "null-lists", null).getEnhancementContents().size());
        
        McpServerDetailInfo emptyTools = detail(true);
        McpToolSpecification emptyToolSpec = new McpToolSpecification();
        emptyToolSpec.setTools(Collections.emptyList());
        emptyTools.setToolSpec(emptyToolSpec);
        when(mcpServerOperationService.getMcpServerDetail("public", null, "empty-tools", null))
            .thenReturn(emptyTools);
        assertEquals(1, handler().project("public", AiResourceConstants.RESOURCE_TYPE_MCP,
            "empty-tools", null).getEnhancementContents().size());
        
        McpServerDetailInfo inactive = detail(true);
        inactive.setStatus(AiConstants.Mcp.MCP_STATUS_DEPRECATED);
        when(mcpServerOperationService.getMcpServerDetail("public", null, "inactive", null))
            .thenReturn(inactive);
        assertNull(handler().project("public", AiResourceConstants.RESOURCE_TYPE_MCP,
            "inactive", null));
    }
    
    @Test
    void scanShouldReturnBoundedMcpSources() throws Exception {
        McpServerBasicInfo valid = basic("mcp", "MCP", "1.0.0");
        when(mcpServerOperationService.listMcpServerWithPage("public", null,
            Constants.MCP_LIST_SEARCH_ACCURATE, 1, 2))
            .thenReturn(page(Arrays.asList(null, valid)));
        
        AiResourceIndexSourcePage result = handler().scan("public",
            AiResourceConstants.RESOURCE_TYPE_MCP, 1, 2);
        
        assertTrue(result.hasMore());
        assertEquals(2, result.getItems().size());
        assertNull(result.getItems().get(0).getProjection());
        assertEquals("MCP", result.getItems().get(1).getResourceName());
        assertTrue(result.getItems().get(1).getProjection() != null);
        assertFalse(handler().scan("public", "skill", 1, 2).hasMore());
        assertTrue(handler().scan("public", AiResourceConstants.RESOURCE_TYPE_MCP,
            2, 2).getItems().isEmpty());
    }
    
    @Test
    void scanShouldRetainInvalidCanonicalMcpAsUnindexableSource() throws Exception {
        McpServerBasicInfo withoutIdentity = basic(null, null, "1.0.0");
        McpServerBasicInfo withoutVersion = basic("without-version", "without-version", null);
        when(mcpServerOperationService.listMcpServerWithPage("public", null,
            Constants.MCP_LIST_SEARCH_ACCURATE, 1, 3))
            .thenReturn(page(List.of(withoutIdentity, withoutVersion)));
        
        AiResourceIndexSourcePage result = handler().scan("public",
            AiResourceConstants.RESOURCE_TYPE_MCP, 1, 3);
        
        assertFalse(result.hasMore());
        assertTrue(result.getItems().stream()
            .allMatch(source -> source.getProjection() == null));
    }
    
    @Test
    void scanShouldCaptureOneProjectionFailure() throws Exception {
        McpServerBasicInfo broken = new McpServerBasicInfo() {
            
            @Override
            public String getVersion() {
                throw new IllegalStateException("broken source");
            }
        };
        broken.setId("broken");
        broken.setName("Broken MCP");
        broken.setStatus(AiConstants.Mcp.MCP_STATUS_ACTIVE);
        broken.setEnabled(true);
        when(mcpServerOperationService.listMcpServerWithPage("public", null,
            Constants.MCP_LIST_SEARCH_ACCURATE, 1, 1)).thenReturn(page(List.of(broken)));
        
        AiResourceIndexSourcePage result = handler().scan("public",
            AiResourceConstants.RESOURCE_TYPE_MCP, 1, 1);
        
        assertEquals("broken source", result.getItems().get(0).getFailure().getMessage());
    }
    
    @Test
    void isCurrentShouldValidateActiveLatestMcpVersion() throws Exception {
        McpServerDetailInfo latest = detail(true);
        when(mcpServerOperationService.getMcpServerDetail("public", null, "mcp-research",
            "1.0.0")).thenReturn(latest);
        AiResourceSearchDocument document = document();
        
        assertTrue(handler().isCurrent(document));
        assertFalse(handler().isCurrent(null));
        document.setResourceType("skill");
        assertFalse(handler().isCurrent(document));
    }
    
    @Test
    void isCurrentShouldRejectNonLatestMissingAndFailedMcp() throws Exception {
        AiResourceSearchDocument document = document();
        McpServerDetailInfo old = detail(false);
        when(mcpServerOperationService.getMcpServerDetail("public", null, "mcp-research",
            "1.0.0")).thenReturn(old);
        assertFalse(handler().isCurrent(document));
        
        document.setMetadata("invalid");
        when(mcpServerOperationService.getMcpServerDetail("public", null, "mcp-research",
            "1.0.0")).thenThrow(new NacosException(NacosException.NOT_FOUND, "missing"));
        assertFalse(handler().isCurrent(document));
        
        document.setMetadata(null);
        assertFalse(handler().isCurrent(document));
    }
    
    @Test
    void isCurrentShouldRejectDisabledInactiveAndVersionlessMcp() throws Exception {
        AiResourceSearchDocument document = document();
        McpServerDetailInfo disabled = detail(true);
        disabled.setEnabled(false);
        McpServerDetailInfo inactive = detail(true);
        inactive.setStatus(AiConstants.Mcp.MCP_STATUS_DEPRECATED);
        McpServerDetailInfo versionless = detail(true);
        versionless.setVersionDetail(null);
        when(mcpServerOperationService.getMcpServerDetail("public", null, "mcp-research",
            "1.0.0")).thenReturn(disabled, inactive, versionless);
        
        assertFalse(handler().isCurrent(document));
        assertFalse(handler().isCurrent(document));
        assertFalse(handler().isCurrent(document));
    }
    
    @Test
    void existsShouldMapNotFoundAndPropagateUnexpectedFailure() throws Exception {
        when(mcpServerOperationService.getMcpServerDetail("public", null, "mcp", null))
            .thenReturn(detail(true));
        assertTrue(handler().exists("public", AiResourceConstants.RESOURCE_TYPE_MCP, "mcp"));
        assertFalse(handler().exists("public", "skill", "mcp"));
        
        when(mcpServerOperationService.getMcpServerDetail("public", null, "missing", null))
            .thenThrow(new NacosException(NacosException.NOT_FOUND, "missing"));
        assertFalse(handler().exists("public", AiResourceConstants.RESOURCE_TYPE_MCP, "missing"));
        when(mcpServerOperationService.getMcpServerDetail("public", null, "empty", null))
            .thenReturn(null);
        assertFalse(handler().exists("public", AiResourceConstants.RESOURCE_TYPE_MCP, "empty"));
        when(mcpServerOperationService.getMcpServerDetail("public", null, "failed", null))
            .thenThrow(new NacosException(NacosException.SERVER_ERROR, "failed"));
        assertThrows(NacosException.class, () -> handler().exists("public",
            AiResourceConstants.RESOURCE_TYPE_MCP, "failed"));
        assertEquals(List.of(AiResourceConstants.RESOURCE_TYPE_MCP),
            List.copyOf(handler().resourceTypes()));
        assertEquals(2, handler().projectionVersion());
    }
    
    @Test
    void isCurrentShouldHandleNullDetailAndJsonNullMetadata() throws Exception {
        AiResourceSearchDocument document = document();
        document.setMetadata("null");
        when(mcpServerOperationService.getMcpServerDetail("public", null, "mcp-research",
            "1.0.0")).thenReturn(null);
        assertFalse(handler().isCurrent(document));
    }
    
    @Test
    void projectShouldBoundLargeMcpContent() throws Exception {
        McpServerDetailInfo detail = detail(true);
        detail.setDescription(String.join("", Collections.nCopies(13000, "x")));
        when(mcpServerOperationService.getMcpServerDetail("public", null, "large", null))
            .thenReturn(detail);
        
        AiResourceIndexProjection projection = handler().project("public",
            AiResourceConstants.RESOURCE_TYPE_MCP, "large", null);
        
        assertEquals(12000, projection.getEnhancementContents().get(0).getText().length());
    }
    
    private McpAiResourceSearchTypeHandler handler() {
        return new McpAiResourceSearchTypeHandler(mcpServerOperationService);
    }
    
    private McpServerDetailInfo detail(boolean latest) {
        ServerVersionDetail version = new ServerVersionDetail();
        version.setVersion("1.0.0");
        version.setIs_latest(latest);
        McpServerDetailInfo result = new McpServerDetailInfo();
        result.setId("legacy-id");
        result.setName("mcp-research");
        result.setDescription("Research assistant");
        result.setProtocol(AiConstants.Mcp.MCP_PROTOCOL_STDIO);
        result.setStatus(AiConstants.Mcp.MCP_STATUS_ACTIVE);
        result.setEnabled(true);
        result.setVersionDetail(version);
        return result;
    }
    
    private McpServerBasicInfo basic(String id, String name, String version) {
        McpServerBasicInfo result = new McpServerBasicInfo();
        result.setId(id);
        result.setName(name);
        result.setDescription("description");
        result.setProtocol(AiConstants.Mcp.MCP_PROTOCOL_STDIO);
        result.setStatus(AiConstants.Mcp.MCP_STATUS_ACTIVE);
        result.setEnabled(true);
        result.setVersion(version);
        ServerVersionDetail detail = new ServerVersionDetail();
        detail.setVersion(version);
        detail.setIs_latest(true);
        result.setVersionDetail(detail);
        return result;
    }
    
    private AiResourceSearchDocument document() {
        AiResourceSearchDocument document = new AiResourceSearchDocument();
        document.setNamespaceId("public");
        document.setResourceType(AiResourceConstants.RESOURCE_TYPE_MCP);
        document.setResourceName("mcp-research");
        document.setResourceVersion("1.0.0");
        document.setMetadata(JacksonUtils.toJson(Map.of("mcpServerId", "legacy-id",
            "mcpName", "mcp-research")));
        return document;
    }
    
    private Page<McpServerBasicInfo> page(List<McpServerBasicInfo> servers) {
        Page<McpServerBasicInfo> page = new Page<>();
        page.setPageItems(servers);
        return page;
    }
}
