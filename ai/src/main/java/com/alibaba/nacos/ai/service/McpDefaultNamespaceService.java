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

package com.alibaba.nacos.ai.service;

import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.core.namespace.model.NamespaceTypeEnum;
import com.alibaba.nacos.core.service.NamespaceOperationService;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;

/**
 * Nacos AI MCP module namespace service.
 *
 * @author xiweng.yy
 */
@Service
public class McpDefaultNamespaceService {
    
    private final NamespaceOperationService namespaceOperationService;
    
    public McpDefaultNamespaceService(NamespaceOperationService namespaceOperationService) {
        this.namespaceOperationService = namespaceOperationService;
    }
    
    /**
     * Init mcp default namespace if not exist.
     *
     * @throws NacosException any exception during handling except {@link ErrorCode#NAMESPACE_ALREADY_EXIST}.
     */
    @PostConstruct
    public void init() throws NacosException {
        doCreateNewDefaultNamespace();
    }

    private void doCreateNewDefaultNamespace() throws NacosException {
        try {
            namespaceOperationService.createNamespace(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE,
                    AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, "Nacos default AI MCP module.", NamespaceTypeEnum.AI_MCP);
        } catch (NacosApiException e) {
            if (!ErrorCode.NAMESPACE_ALREADY_EXIST.getCode().equals(e.getDetailErrCode())) {
                throw e;
            }
        }
    }
}
