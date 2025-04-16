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
import com.alibaba.nacos.ai.form.mcp.McpDetailForm;
import com.alibaba.nacos.ai.form.mcp.McpForm;
import com.alibaba.nacos.ai.form.mcp.McpListForm;
import com.alibaba.nacos.ai.param.McpHttpParamExtractor;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.mcp.McpEndpointSpec;
import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpTool;
import com.alibaba.nacos.api.annotation.NacosApi;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.exception.runtime.NacosDeserializationException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.console.proxy.ai.McpProxy;
import com.alibaba.nacos.core.model.form.PageForm;
import com.alibaba.nacos.core.paramcheck.ExtractorManager;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.ApiType;
import com.alibaba.nacos.plugin.auth.constant.SignType;
import com.fasterxml.jackson.core.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;

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
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ConsoleMcpController.class);
    
    private final McpProxy mcpProxy;
    
    public ConsoleMcpController(McpProxy mcpProxy) {
        this.mcpProxy = mcpProxy;
    }
    
    /**
     * List mcp server.
     *
     * @param mcpListForm list mcp servers request form
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
        return Result.success(mcpProxy.getMcpServer(mcpForm.getNamespaceId(), mcpForm.getMcpName()));
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
        McpServerBasicInfo basicInfo = parseMcpServerBasicInfo(mcpForm);
        List<McpTool> mcpTools = parseMcpTools(mcpForm);
        McpEndpointSpec endpointSpec = parseMcpEndpointSpec(basicInfo, mcpForm);
        mcpProxy.createMcpServer(mcpForm.getNamespaceId(), mcpForm.getMcpName(), basicInfo, mcpTools, endpointSpec);
        return Result.success("ok");
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
    public Result<String> updateMcpServer(McpDetailForm mcpForm) throws NacosException {
        mcpForm.validate();
        McpServerBasicInfo basicInfo = parseMcpServerBasicInfo(mcpForm);
        List<McpTool> mcpTools = parseMcpTools(mcpForm);
        McpEndpointSpec endpointSpec = parseMcpEndpointSpec(basicInfo, mcpForm);
        mcpProxy.updateMcpServer(mcpForm.getNamespaceId(), mcpForm.getMcpName(), basicInfo, mcpTools, endpointSpec);
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
        mcpProxy.deleteMcpServer(mcpForm.getNamespaceId(), mcpForm.getMcpName());
        return Result.success("ok");
    }
    
    private McpServerBasicInfo parseMcpServerBasicInfo(McpDetailForm mcpForm) throws NacosApiException {
        McpServerBasicInfo result = deserializeSpec(mcpForm.getServerSpecification(), new TypeReference<>() {
        });
        if (StringUtils.isEmpty(result.getName())) {
            result.setName(mcpForm.getMcpName());
        }
        if (!StringUtils.equals(mcpForm.getMcpName(), result.getName())) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_VALIDATE_ERROR, String.format(
                    "Mcp Name is conflicted, `%s` is in spec, but requested is `%s`, please not set name in spec or set `%s` in spec",
                    result.getName(), mcpForm.getMcpName(), mcpForm.getMcpName()));
        }
        return result;
    }
    
    private List<McpTool> parseMcpTools(McpDetailForm mcpForm) throws NacosApiException {
        if (StringUtils.isBlank(mcpForm.getToolSpecification())) {
            return Collections.emptyList();
        }
        return deserializeSpec(mcpForm.getToolSpecification(), new TypeReference<>() {
        });
    }
    
    private McpEndpointSpec parseMcpEndpointSpec(McpServerBasicInfo basicInfo, McpDetailForm mcpForm)
            throws NacosApiException {
        if (AiConstants.Mcp.MCP_TYPE_LOCAL.equalsIgnoreCase(basicInfo.getType())) {
            return null;
        }
        if (StringUtils.isBlank(mcpForm.getEndpointSpecification())) {
            throw new NacosApiException(NacosApiException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                    "request parameter `endpointSpecification` is required if mcp server type not `local`.");
        }
        return deserializeSpec(mcpForm.getEndpointSpecification(), new TypeReference<>() {
        });
    }
    
    private <T> T deserializeSpec(String spec, TypeReference<T> typeReference) throws NacosApiException {
        try {
            return JacksonUtils.toObj(spec, typeReference);
        } catch (NacosDeserializationException e) {
            LOGGER.error(String.format("Deserialize %s from %s failed, ", typeReference.getType().getTypeName(), spec),
                    e);
            throw new NacosApiException(NacosApiException.INVALID_PARAM, ErrorCode.PARAMETER_VALIDATE_ERROR,
                    "serverSpecification or toolSpecification is invalid. Can't be parsed.");
        }
    }
}