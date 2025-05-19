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


import com.alibaba.nacos.ai.form.mcp.regsitryapi.GetServerForm;
import com.alibaba.nacos.ai.form.mcp.regsitryapi.ListServerForm;
import com.alibaba.nacos.ai.param.McpHttpParamExtractor;
import com.alibaba.nacos.api.ai.model.mcp.McpToolSpecification;
import com.alibaba.nacos.api.ai.model.mcp.registry.*;
import com.alibaba.nacos.api.annotation.NacosApi;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.common.constant.HttpHeaderConsts;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.core.paramcheck.ExtractorManager;
import com.alibaba.nacos.mcpregistry.service.NacosMcpRegistryService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.*;

import java.util.Objects;


/**
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
     * @param listServerForm list mcp servers request form
     *                       Support blur and accurate search
     *                       mode.
     *                       default offset is 0
     *                       default limit is 30
     * @return mcp server list {@link McpRegistryServerList}
     * @throws NacosApiException if request parameter is invalid or handle error
     */
    @GetMapping(value = "/v0/servers")
    public McpRegistryServerList listMcpServers(ListServerForm listServerForm) throws NacosApiException {
        listServerForm.validate();
        return nacosMcpRegistryService.listMcpServers(listServerForm);
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
        McpRegistryServerDetail server = nacosMcpRegistryService.getServer(id, getServerForm);
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
     * Publish a new mcp server or new version mcp server.
     * This api support additional tools specification info.
     * If id exist in request, this api will update the exist mcp server or this api will create a new mcp server.
     * @param serverDetail server details
     * @throws NacosException if request parameter is invalid or handle error
     */
    @PostMapping(value = "/v0/publish")
    public void publishMcpServer(@RequestBody NacosMcpRegistryServerDetail serverDetail) throws NacosException {
        if (StringUtils.isNotEmpty(serverDetail.getId())) {
            nacosMcpRegistryService.updateMcpServer(serverDetail);
        } else {
            nacosMcpRegistryService.createMcpServer(serverDetail);
        }
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
        return nacosMcpRegistryService.getTools(id, getServerForm.getVersion());
    }
}
