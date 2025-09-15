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

package com.alibaba.nacos.mcpregistry.controller;

import com.alibaba.nacos.mcpregistry.form.GetServerForm;
import com.alibaba.nacos.mcpregistry.form.ListServerForm; // internal legacy form (for service call)
import com.alibaba.nacos.mcpregistry.form.ListServersOfficialForm; // new official form
import com.alibaba.nacos.api.ai.model.mcp.registry.Meta;
import com.alibaba.nacos.api.ai.model.mcp.registry.OfficialMeta;
import com.alibaba.nacos.ai.param.McpHttpParamExtractor;
import com.alibaba.nacos.api.ai.model.mcp.McpToolSpecification;
import com.alibaba.nacos.api.ai.model.mcp.registry.McpErrorResponse;
import com.alibaba.nacos.api.ai.model.mcp.registry.McpRegistryServerDetail;
import com.alibaba.nacos.api.ai.model.mcp.registry.McpRegistryServerList;
import com.alibaba.nacos.api.annotation.NacosApi;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.common.constant.HttpHeaderConsts;
import com.alibaba.nacos.core.paramcheck.ExtractorManager;
import com.alibaba.nacos.mcpregistry.service.NacosMcpRegistryService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Collections;
import java.util.Objects;
import java.util.stream.Collectors;


/**
 * McpRegistryController.
 * @author xinluo
 */
@NacosApi
@RestController
@ExtractorManager.Extractor(httpExtractor = McpHttpParamExtractor.class)
public class McpRegistryController {
    
    private final NacosMcpRegistryService nacosMcpRegistryService;
    
    public McpRegistryController(NacosMcpRegistryService nacosMcpRegistryService) {
        this.nacosMcpRegistryService = nacosMcpRegistryService;
    }

    /**
     * List mcp servers.
     * All server info is related to the latest version of the server.
     *
     * @param form list mcp servers request form
     *                       Support blur and accurate search
     *                       mode.
     *                       default offset is 0
     *                       default limit is 30
     * @return mcp server list {@link McpRegistryServerList}
     * @throws NacosApiException if request parameter is invalid or handle error
     */
    @GetMapping(value = "/v0/servers")
    public McpRegistryServerList listMcpServers(ListServersOfficialForm form) throws NacosApiException, NacosException {
        form.validate();
        int offset = form.resolveOffset();
        int limit = form.getLimit();
        // reuse internal service with converted form
        ListServerForm internal = new ListServerForm();
        internal.setOffset(offset);
        internal.setLimit(limit);
        McpRegistryServerList internalList = nacosMcpRegistryService.listMcpServers(internal);
        // Null-safe server list handling
        List<McpRegistryServerDetail> raw = internalList.getServers();
        if (raw == null) {
            raw = Collections.emptyList();
        }
        List<McpRegistryServerDetail> details = raw.stream()
                .map(this::enrich)
                .collect(Collectors.toList());
        McpRegistryServerList response = new McpRegistryServerList();
        response.setServers(details);
        int returned = details.size();
        String nextCursor = (offset + returned) < internalList.getTotal_count() ? String.valueOf(offset + returned) : null;
        response.setMetadata(new McpRegistryServerList.Metadata(nextCursor, returned));
        return response;
    }

    /**
     * Get mcp server details.
     * If version is not provided, this api will return the latest version of the server.
     * @param getServerForm list mcp servers request form
     * @return mcp server detail or McpErrorResponse when server not found.
     * @throws NacosApiException if request parameter is invalid or handle error
     */
    @GetMapping(value = "/v0/servers/{id}")
    public Object getServer(@PathVariable String id, GetServerForm getServerForm, HttpServletResponse response)
            throws NacosException {
        getServerForm.validate();
        McpRegistryServerDetail server = nacosMcpRegistryService.getServer(id, getServerForm);
        if (Objects.isNull(server)) {
            response.setStatus(404);
            response.setHeader(HttpHeaderConsts.CONTENT_TYPE, "application/json");
            McpErrorResponse errorResponse = new McpErrorResponse();
            errorResponse.setError("Server not found");
            return errorResponse;
        }
        return enrich(server);
    }

    /**
     * Get tools of the specified server and version.
     * @param id mcp server id.
     * @param getServerForm additional params
     *                      version mcp server version
     * @return tools specification of the server.
     * @throws NacosException if request parameter is invalid or handle error
     */
    @GetMapping(value = "/v0/servers/{id}/tools")
    public McpToolSpecification getMcpServerToolsInfo(@PathVariable String id, GetServerForm getServerForm) throws NacosException {
        getServerForm.validate();
        return nacosMcpRegistryService.getTools(id, getServerForm.getVersion());
    }

    private McpRegistryServerDetail enrich(McpRegistryServerDetail detail) {
        if (detail == null) {
            return null;
        }
        detail.setStatus("active");
        detail.setSchema("https://static.modelcontextprotocol.io/schemas/2025-07-09/server.schema.json");
        Meta meta = detail.getMeta();
        if (meta == null) {
            meta = new Meta();
        }
        OfficialMeta official = meta.getOfficial();
        if (official == null) {
            official = new OfficialMeta();
        }
        official.setId(detail.getId());
        official.setPublishedAt(detail.getPublishedAt());
        official.setUpdatedAt(detail.getUpdatedAt());
        official.setIsLatest(detail.getUpdatedAt() != null && detail.getUpdatedAt().equals(detail.getPublishedAt()));
        meta.setOfficial(official);
        detail.setMeta(meta);
        return detail;
    }
}
