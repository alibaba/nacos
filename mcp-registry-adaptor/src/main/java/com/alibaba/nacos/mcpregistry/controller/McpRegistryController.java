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
import com.alibaba.nacos.mcpregistry.form.ListServerForm;
import com.alibaba.nacos.mcpregistry.form.ListServersNacosForm;
import com.alibaba.nacos.ai.param.McpHttpParamExtractor;
import com.alibaba.nacos.api.ai.model.mcp.registry.McpErrorResponse;
import com.alibaba.nacos.api.ai.model.mcp.registry.McpRegistryServerList;
import com.alibaba.nacos.api.ai.model.mcp.registry.ServerResponse;
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

/**
 * McpRegistryController.
 * 
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
     *             Support blur and accurate search
     *             mode.
     *             default offset is 0
     *             default limit is 30
     * @return mcp server list {@link McpRegistryServerList}
     * @throws NacosApiException if request parameter is invalid or handle error
     */
    @GetMapping(value = "/v0/servers")
    public McpRegistryServerList listMcpServers(ListServersNacosForm form) throws NacosApiException, NacosException {
        form.validate();
        int offset = form.resolveOffset();
        int limit = form.getLimit();
        String namespaceId = form.getNamespaceId();
        // reuse internal service with converted form
        ListServerForm internal = new ListServerForm();
        internal.setOffset(offset);
        internal.setLimit(limit);
        internal.setNamespaceId(namespaceId);
        internal.setServerName(form.getSearch());
        McpRegistryServerList internalList = nacosMcpRegistryService.listMcpServers(internal);
        // Null-safe server list handling; service has enriched items already
        List<ServerResponse> details = internalList.getServers();
        if (details == null) {
            details = Collections.emptyList();
        }
        McpRegistryServerList response = new McpRegistryServerList();
        response.setServers(details);
        int returned = details.size();
        String nextCursor = String.valueOf(offset + returned);
        response.setMetadata(new McpRegistryServerList.Metadata(nextCursor, returned));
        return response;
    }

    /**
     * Get mcp server details.
     * If version is not provided, this api will return the latest version of the
     * server.
     * 
     * @param name     server name
     * @param form     get server request form
     * @param response HTTP response
     * @return mcp server detail or McpErrorResponse when server not found.
     * @throws NacosApiException if request parameter is invalid or handle error
     */
    @GetMapping(value = "/v0/servers/{name}/versions")
    public Object getServerVersions(@PathVariable String name, GetServerForm form, HttpServletResponse response)
            throws NacosException {
        McpRegistryServerList server = nacosMcpRegistryService.getServerVersions(form.getNamespaceId(), name);
        if (Objects.isNull(server)) {
            response.setStatus(404);
            response.setHeader(HttpHeaderConsts.CONTENT_TYPE, "application/json");
            McpErrorResponse errorResponse = new McpErrorResponse();
            errorResponse.setError("Server not found");
            return errorResponse;
        }
        return server;
    }

    /**
     * Get specific MCP server version.
     * Returns detailed information about a specific version of an MCP server.
     * Use the special version `latest` to get the latest version.
     * 
     * @param serverName URL-encoded server name (e.g., "com.example%2Fmy-server")
     * @param version    URL-encoded version to retrieve (e.g., "1.0.0" or
     *                   "1.0.0%2B20130313144700")
     * @param form       get server request form
     * @param response   HTTP response
     * @return mcp server detail or McpErrorResponse when server or version not
     *         found.
     * @throws NacosException if handle error
     */
    @GetMapping(value = "/v0/servers/{serverName}/versions/{version}")
    public Object getVersionedServer(@PathVariable String serverName, @PathVariable String version,
            GetServerForm form, HttpServletResponse response)
            throws NacosException {
        ServerResponse server = nacosMcpRegistryService.getServer(serverName, form.getNamespaceId(), version);
        if (Objects.isNull(server)) {
            response.setStatus(404);
            response.setHeader(HttpHeaderConsts.CONTENT_TYPE, "application/json");
            McpErrorResponse errorResponse = new McpErrorResponse();
            errorResponse.setError("Server not found");
            return errorResponse;
        }
        return server;
    }
}
