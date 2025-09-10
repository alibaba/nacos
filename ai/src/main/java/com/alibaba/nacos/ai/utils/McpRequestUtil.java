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

package com.alibaba.nacos.ai.utils;

import com.alibaba.nacos.ai.form.mcp.admin.McpDetailForm;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.mcp.McpEndpointSpec;
import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServiceRef;
import com.alibaba.nacos.api.ai.model.mcp.McpTool;
import com.alibaba.nacos.api.ai.model.mcp.McpToolSpecification;
import com.alibaba.nacos.api.ai.remote.request.AbstractMcpRequest;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.exception.runtime.NacosDeserializationException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * MCP request util.
 *
 * @author xiweng.yy
 */
public class McpRequestUtil {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(McpRequestUtil.class);
    
    /**
     * Parse Mcp detail request form to {@link McpServerBasicInfo}.
     *
     * @param mcpForm mcp detail request.
     * @return mcp server basic info.
     * @throws NacosApiException if parse failed or request parameter is conflicted.
     */
    public static McpServerBasicInfo parseMcpServerBasicInfo(McpDetailForm mcpForm) throws NacosApiException {
        McpServerBasicInfo result = McpRequestUtil.deserializeSpec(mcpForm.getServerSpecification(),
                new TypeReference<>() {
                });
        if (StringUtils.isEmpty(result.getName())) {
            result.setName(mcpForm.getMcpName());
        }
        return result;
    }
    
    /**
     * Parse Mcp tools request form to {@link McpTool}.
     *
     * @param mcpForm mcp detail request.
     * @return mcp server tool info
     * @throws NacosApiException if parse failed.
     */
    public static McpToolSpecification parseMcpTools(McpDetailForm mcpForm) throws NacosApiException {
        if (StringUtils.isBlank(mcpForm.getToolSpecification())) {
            return null;
        }
        return McpRequestUtil.deserializeSpec(mcpForm.getToolSpecification(), new TypeReference<>() {
        });
    }
    
    /**
     * Parse Mcp endpoint request form to {@link McpEndpointSpec}.
     *
     * @param basicInfo mcp server basic info
     * @param mcpForm   mcp detail request.
     * @return mcp server endpoint info
     * @throws NacosApiException  if parse failed or request parameter is conflicted.
     */
    public static McpEndpointSpec parseMcpEndpointSpec(McpServerBasicInfo basicInfo, McpDetailForm mcpForm)
            throws NacosApiException {
        if (AiConstants.Mcp.MCP_PROTOCOL_STDIO.equalsIgnoreCase(basicInfo.getProtocol())) {
            return null;
        }
        if (StringUtils.isBlank(mcpForm.getEndpointSpecification())) {
            throw new NacosApiException(NacosApiException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                    "request parameter `endpointSpecification` is required if mcp server type not `local`.");
        }
        return McpRequestUtil.deserializeSpec(mcpForm.getEndpointSpecification(), new TypeReference<>() {
        });
    }
    
    /**
     * Deserialize spec from json request.
     *
     * @param spec          spec json string.
     * @param typeReference the type of spec.
     * @param <T>           the type of spec.
     * @return spec object.
     * @throws NacosApiException if deserialize failed.
     */
    public static <T> T deserializeSpec(String spec, TypeReference<T> typeReference) throws NacosApiException {
        return deserializeSpec(spec, typeReference, LOGGER);
    }
    
    /**
     * Deserialize spec from json request.
     *
     * @param spec          spec json string.
     * @param typeReference the type of spec.
     * @param logger        the logger to log error.
     * @param <T>           the type of spec.
     * @return spec object.
     * @throws NacosApiException if deserialize failed.
     */
    public static <T> T deserializeSpec(String spec, TypeReference<T> typeReference, Logger logger)
            throws NacosApiException {
        try {
            return JacksonUtils.toObj(spec, typeReference);
        } catch (NacosDeserializationException e) {
            logger.error(String.format("Deserialize %s from %s failed, ", typeReference.getType().getTypeName(), spec),
                    e);
            throw new NacosApiException(NacosApiException.INVALID_PARAM, ErrorCode.PARAMETER_VALIDATE_ERROR,
                    "serverSpecification or toolSpecification is invalid. Can't be parsed.");
        }
    }
    
    /**
     * Transfer input to McpServiceRef.
     *
     * @param input input object, should be McpServiceRef type or Map type.
     * @return McpServiceRef
     */
    public static McpServiceRef transferToMcpServiceRef(Object input) {
        if (input instanceof McpServiceRef) {
            return (McpServiceRef) input;
        }
        if (input instanceof Map) {
            return JacksonUtils.toObj(JacksonUtils.toJson(input), McpServiceRef.class);
        }
        throw new IllegalArgumentException("input must be instance of McpServiceRef or Map");
    }
    
    /**
     * If request contains valid namespaceId, do nothing. If not, fill default namespaceId.
     *
     * @param request mcp request
     */
    public static void fillNamespaceId(AbstractMcpRequest request) {
        if (StringUtils.isEmpty(request.getNamespaceId())) {
            request.setNamespaceId(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE);
        }
    }
}
