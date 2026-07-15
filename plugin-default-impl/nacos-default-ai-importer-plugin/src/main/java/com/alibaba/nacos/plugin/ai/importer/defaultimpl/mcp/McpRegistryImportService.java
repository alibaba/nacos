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

package com.alibaba.nacos.plugin.ai.importer.defaultimpl.mcp;

import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.ai.model.mcp.registry.Repository;
import com.alibaba.nacos.api.ai.model.mcp.registry.ServerVersionDetail;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.utils.CollectionUtils;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.ai.importer.AiResourceImportConstants;
import com.alibaba.nacos.plugin.ai.importer.model.AiResourceImportArtifact;
import com.alibaba.nacos.plugin.ai.importer.model.AiResourceImportCandidate;
import com.alibaba.nacos.plugin.ai.importer.model.AiResourceImportCandidatePage;
import com.alibaba.nacos.plugin.ai.importer.model.AiResourceImportContext;
import com.alibaba.nacos.plugin.ai.importer.model.AiResourceImportItem;
import com.alibaba.nacos.plugin.ai.importer.model.AiResourceImportPayloadKind;
import com.alibaba.nacos.plugin.ai.importer.model.AiResourceImportSource;
import com.alibaba.nacos.plugin.ai.importer.spi.AiResourceImportService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Built-in importer for the official MCP registry API.
 *
 * @author xiweng.yy
 * @since 3.2.1
 */
public class McpRegistryImportService implements AiResourceImportService {
    
    public static final String RESOURCE_TYPE_MCP = AiResourceImportConstants.RESOURCE_TYPE_MCP;
    
    private static final int DEFAULT_FETCH_LIMIT = 30;
    
    private static final String METADATA_ID = "id";
    
    private static final String METADATA_PROTOCOL = "protocol";
    
    private static final String METADATA_STATUS = "status";
    
    private static final String METADATA_REPOSITORY = "repository";
    
    private final McpRegistryClient client;
    
    public McpRegistryImportService() {
        this(new McpRegistryClient());
    }
    
    McpRegistryImportService(McpRegistryClient client) {
        this.client = client;
    }
    
    @Override
    public String importerType() {
        return McpRegistryImportServiceBuilder.IMPORTER_TYPE;
    }
    
    @Override
    public Set<String> supportedResourceTypes() {
        return Collections.singleton(RESOURCE_TYPE_MCP);
    }
    
    @Override
    public AiResourceImportCandidatePage search(AiResourceImportContext context)
        throws NacosException {
        try {
            AiResourceImportSource source = requireSource(context.getSource());
            McpRegistryClient.Page registryPage = client.fetchOfficialRegistryPage(
                source, context.getCursor(), context.getLimit(), context.getQuery());
            AiResourceImportCandidatePage result = new AiResourceImportCandidatePage();
            result.setItems(toCandidates(registryPage.getServers()));
            result.setNextCursor(registryPage.getNextCursor());
            result.setHasMore(StringUtils.isNotBlank(registryPage.getNextCursor()));
            return result;
        } catch (NacosException e) {
            throw e;
        } catch (Exception e) {
            throw dataAccess("Search MCP registry source failed: " + e.getMessage(), e);
        }
    }
    
    @Override
    public AiResourceImportArtifact fetch(AiResourceImportContext context,
        AiResourceImportItem item) throws NacosException {
        try {
            AiResourceImportSource source = requireSource(context.getSource());
            String externalId = resolveExternalId(item);
            McpServerDetailInfo server = client.fetchOfficialRegistryServer(
                source, externalId, resolveFetchLimit(context));
            return toArtifact(externalId, server);
        } catch (NacosException e) {
            throw e;
        } catch (Exception e) {
            throw dataAccess("Fetch MCP registry artifact failed: " + e.getMessage(), e);
        }
    }
    
    private AiResourceImportSource requireSource(AiResourceImportSource source)
        throws NacosException {
        if (source == null || StringUtils.isBlank(source.getEndpoint())) {
            throw invalid("MCP registry import source endpoint must not be empty.");
        }
        return source;
    }
    
    private String resolveExternalId(AiResourceImportItem item) throws NacosException {
        if (item == null) {
            throw invalid("MCP registry import item must not be null.");
        }
        String externalId = StringUtils.isNotBlank(item.getExternalId()) ? item.getExternalId()
            : item.getName();
        if (StringUtils.isBlank(externalId)) {
            throw invalid("MCP registry import item external id must not be empty.");
        }
        return externalId;
    }
    
    private int resolveFetchLimit(AiResourceImportContext context) {
        return context.getLimit() > 0 ? context.getLimit() : DEFAULT_FETCH_LIMIT;
    }
    
    private List<AiResourceImportCandidate> toCandidates(List<McpServerDetailInfo> servers) {
        if (CollectionUtils.isEmpty(servers)) {
            return Collections.emptyList();
        }
        List<AiResourceImportCandidate> result = new ArrayList<>(servers.size());
        for (McpServerDetailInfo each : servers) {
            result.add(toCandidate(each));
        }
        return result;
    }
    
    private AiResourceImportCandidate toCandidate(McpServerDetailInfo server) {
        AiResourceImportCandidate result = new AiResourceImportCandidate();
        result.setResourceType(RESOURCE_TYPE_MCP);
        result.setExternalId(server.getName());
        result.setName(server.getName());
        result.setVersion(resolveVersion(server));
        result.setDescription(server.getDescription());
        result.setMetadata(buildMetadata(server));
        return result;
    }
    
    private AiResourceImportArtifact toArtifact(String externalId, McpServerDetailInfo server) {
        AiResourceImportArtifact result = new AiResourceImportArtifact();
        result.setResourceType(RESOURCE_TYPE_MCP);
        result.setExternalId(externalId);
        result.setName(server.getName());
        result.setVersion(resolveVersion(server));
        result.setDescription(server.getDescription());
        result.setPayloadKind(AiResourceImportPayloadKind.MCP_DETAIL);
        result.setPayloadJson(JacksonUtils.toJson(server));
        result.setSourceMetadata(buildMetadata(server));
        return result;
    }
    
    private String resolveVersion(McpServerDetailInfo server) {
        ServerVersionDetail versionDetail = server.getVersionDetail();
        return versionDetail == null ? server.getVersion() : versionDetail.getVersion();
    }
    
    private Map<String, String> buildMetadata(McpServerDetailInfo server) {
        Map<String, String> metadata = new LinkedHashMap<>();
        putIfNotBlank(metadata, METADATA_ID, server.getId());
        putIfNotBlank(metadata, METADATA_PROTOCOL, server.getProtocol());
        putIfNotBlank(metadata, METADATA_STATUS, server.getStatus());
        Repository repository = server.getRepository();
        if (repository != null) {
            putIfNotBlank(metadata, METADATA_REPOSITORY, repository.getUrl());
        }
        return metadata;
    }
    
    private void putIfNotBlank(Map<String, String> metadata, String key, String value) {
        if (StringUtils.isNotBlank(value)) {
            metadata.put(key, value);
        }
    }
    
    private NacosException invalid(String message) {
        return new NacosApiException(NacosException.INVALID_PARAM,
            ErrorCode.PARAMETER_VALIDATE_ERROR, message);
    }
    
    private NacosException dataAccess(String message, Throwable cause) {
        return new NacosApiException(NacosException.SERVER_ERROR, ErrorCode.DATA_ACCESS_ERROR,
            cause, message);
    }
}
