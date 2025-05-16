package com.alibaba.nacos.ai.controller;


import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.form.mcp.admin.McpListForm;
import com.alibaba.nacos.ai.form.mcp.regsitryapi.GetServerForm;
import com.alibaba.nacos.ai.form.mcp.regsitryapi.ListServerForm;
import com.alibaba.nacos.ai.param.McpHttpParamExtractor;
import com.alibaba.nacos.ai.service.McpServerOperationService;
import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.ai.model.mcp.registry.McpRegistryServer;
import com.alibaba.nacos.api.ai.model.mcp.registry.McpRegistryServerDetail;
import com.alibaba.nacos.api.ai.model.mcp.registry.McpRegistryServerList;
import com.alibaba.nacos.api.annotation.NacosApi;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.core.controller.v3.ServerLoaderControllerV3;
import com.alibaba.nacos.core.model.form.PageForm;
import com.alibaba.nacos.core.paramcheck.ExtractorManager;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.ApiType;
import com.alibaba.nacos.plugin.auth.constant.SignType;
import org.apache.logging.log4j.util.Strings;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @author xinluo
 */
@NacosApi
@RestController
@ExtractorManager.Extractor(httpExtractor = McpHttpParamExtractor.class)
public class McpRegistryController {
    
    private McpServerOperationService mcpServerOperationService;
    
    
    public McpRegistryController(McpServerOperationService mcpServerOperationService, ServerLoaderControllerV3 serverLoaderControllerV3) {
        this.mcpServerOperationService = mcpServerOperationService;
    }

    /**
     * List mcp server.
     *
     * @param listServerForm list mcp servers request form
     * @return mcp server list wrapper with {@link Result}
     * @throws NacosApiException if request parameter is invalid or handle error
     */
    @GetMapping(value = "/v0/servers")
    public McpRegistryServerList listMcpServers(ListServerForm listServerForm)
            throws NacosException {
        int limit = listServerForm.getLimit();
        int offset = listServerForm.getOffset();
        Page<McpServerBasicInfo> servers = mcpServerOperationService.listMcpServer(listServerForm.getNamespaceId(), Strings.EMPTY, "blur", offset / limit, limit);
        List<McpRegistryServer> finalServers = servers.getPageItems().stream().map((item) -> {
            McpRegistryServer server = new McpRegistryServer();
            server.setId(item.getId());
            server.setName(item.getName());
            server.setDescription(item.getDescription());
            server.setRepository(item.getRepository());
            server.setVersion_detail(item.getVersionDetail());
            return server;
        }).collect(Collectors.toList());
        
        McpRegistryServerList serverList = new McpRegistryServerList();
        serverList.setTotal_count(servers.getTotalCount());
        serverList.setServers(finalServers);
        return serverList;
    }

    /**
     * List mcp server.
     *
     * @param getServerForm list mcp servers request form
     * @return mcp server list wrapper with {@link Result}
     * @throws NacosApiException if request parameter is invalid or handle error
     */
    @GetMapping(value = "/v0/servers/{id}")
    public McpRegistryServerDetail getServer(@PathVariable String id, GetServerForm getServerForm)
            throws NacosException {
        McpServerDetailInfo mcpServerDetail = mcpServerOperationService.getMcpServerDetail(Strings.EMPTY, id, getServerForm.getVersion());
        McpRegistryServerDetail result = new McpRegistryServerDetail();
        result.setId(mcpServerDetail.getId());
        result.setName(mcpServerDetail.getName());
        result.setDescription(mcpServerDetail.getDescription());
        result.setRepository(mcpServerDetail.getRepository());
        result.setVersion_detail(mcpServerDetail.getVersionDetail());
        return result;
    }
}
