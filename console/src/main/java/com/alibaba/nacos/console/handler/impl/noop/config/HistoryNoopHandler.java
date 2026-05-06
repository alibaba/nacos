/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.console.handler.impl.noop.config;

import com.alibaba.nacos.api.config.model.ConfigBasicInfo;
import com.alibaba.nacos.api.config.model.ConfigHistoryBasicInfo;
import com.alibaba.nacos.api.config.model.ConfigHistoryDetailInfo;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.console.handler.config.HistoryHandler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Noop Implementation of HistoryHandler for handling internal configuration operations.
 * Used when `config` module is disabled(functionMode is `naming`)
 *
 * @author xiweng.yy
 */
@Service
@ConditionalOnMissingBean(value = HistoryHandler.class, ignored = HistoryNoopHandler.class)
public class HistoryNoopHandler implements HistoryHandler {
    
    private static final String MCP_NOT_ENABLED_MESSAGE = "Current functionMode is `naming`, config module is disabled.";
    
    @Override
    public ConfigHistoryDetailInfo getConfigHistoryInfo(String dataId, String group, String namespaceId, Long nid)
            throws NacosException {
        throw new NacosApiException(NacosException.SERVER_NOT_IMPLEMENTED, ErrorCode.API_FUNCTION_DISABLED,
                MCP_NOT_ENABLED_MESSAGE);
    }
    
    @Override
    public Page<ConfigHistoryBasicInfo> listConfigHistory(String dataId, String group, String namespaceId,
            Integer pageNo, Integer pageSize) throws NacosException {
        throw new NacosApiException(NacosException.SERVER_NOT_IMPLEMENTED, ErrorCode.API_FUNCTION_DISABLED,
                MCP_NOT_ENABLED_MESSAGE);
    }
    
    @Override
    public ConfigHistoryDetailInfo getPreviousConfigHistoryInfo(String dataId, String group, String namespaceId,
            Long id) throws NacosException {
        throw new NacosApiException(NacosException.SERVER_NOT_IMPLEMENTED, ErrorCode.API_FUNCTION_DISABLED,
                MCP_NOT_ENABLED_MESSAGE);
    }
    
    @Override
    public List<ConfigBasicInfo> getConfigsByTenant(String namespaceId) throws NacosException {
        throw new NacosApiException(NacosException.SERVER_NOT_IMPLEMENTED, ErrorCode.API_FUNCTION_DISABLED,
                MCP_NOT_ENABLED_MESSAGE);
    }
    
}