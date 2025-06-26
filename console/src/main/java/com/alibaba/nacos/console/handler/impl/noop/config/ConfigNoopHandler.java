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
import com.alibaba.nacos.api.config.model.ConfigDetailInfo;
import com.alibaba.nacos.api.config.model.ConfigGrayInfo;
import com.alibaba.nacos.api.config.model.ConfigListenerInfo;
import com.alibaba.nacos.api.config.model.SameConfigPolicy;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.config.server.controller.parameters.SameNamespaceCloneConfigBean;
import com.alibaba.nacos.config.server.model.ConfigRequestInfo;
import com.alibaba.nacos.config.server.model.form.ConfigForm;
import com.alibaba.nacos.console.handler.config.ConfigHandler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * Noop Implementation of ConfigHandler for handling internal configuration operations.
 * Used when `config` module is disabled(functionMode is `naming`)
 *
 * @author xiweng.yy
 */
@Service
@ConditionalOnMissingBean(value = ConfigHandler.class, ignored = ConfigNoopHandler.class)
public class ConfigNoopHandler implements ConfigHandler {
    
    private static final String MCP_NOT_ENABLED_MESSAGE = "Current functionMode is `naming`, config module is disabled.";
    
    @Override
    public Page<ConfigBasicInfo> getConfigList(int pageNo, int pageSize, String dataId, String group,
            String namespaceId, Map<String, Object> configAdvanceInfo) throws NacosException {
        throw new NacosApiException(NacosException.SERVER_NOT_IMPLEMENTED, ErrorCode.API_FUNCTION_DISABLED,
                MCP_NOT_ENABLED_MESSAGE);
    }
    
    @Override
    public ConfigDetailInfo getConfigDetail(String dataId, String group, String namespaceId) throws NacosException {
        throw new NacosApiException(NacosException.SERVER_NOT_IMPLEMENTED, ErrorCode.API_FUNCTION_DISABLED,
                MCP_NOT_ENABLED_MESSAGE);
    }
    
    @Override
    public Boolean publishConfig(ConfigForm configForm, ConfigRequestInfo configRequestInfo) throws NacosException {
        throw new NacosApiException(NacosException.SERVER_NOT_IMPLEMENTED, ErrorCode.API_FUNCTION_DISABLED,
                MCP_NOT_ENABLED_MESSAGE);
    }
    
    @Override
    public Boolean deleteConfig(String dataId, String group, String namespaceId, String tag, String clientIp,
            String srcUser) throws NacosException {
        throw new NacosApiException(NacosException.SERVER_NOT_IMPLEMENTED, ErrorCode.API_FUNCTION_DISABLED,
                MCP_NOT_ENABLED_MESSAGE);
    }
    
    @Override
    public Boolean batchDeleteConfigs(List<Long> ids, String clientIp, String srcUser) throws NacosException {
        throw new NacosApiException(NacosException.SERVER_NOT_IMPLEMENTED, ErrorCode.API_FUNCTION_DISABLED,
                MCP_NOT_ENABLED_MESSAGE);
    }
    
    @Override
    public Page<ConfigBasicInfo> getConfigListByContent(String search, int pageNo, int pageSize, String dataId,
            String group, String namespaceId, Map<String, Object> configAdvanceInfo) throws NacosException {
        throw new NacosApiException(NacosException.SERVER_NOT_IMPLEMENTED, ErrorCode.API_FUNCTION_DISABLED,
                MCP_NOT_ENABLED_MESSAGE);
    }
    
    @Override
    public ConfigListenerInfo getListeners(String dataId, String group, String namespaceId, boolean aggregation)
            throws Exception {
        throw new NacosApiException(NacosException.SERVER_NOT_IMPLEMENTED, ErrorCode.API_FUNCTION_DISABLED,
                MCP_NOT_ENABLED_MESSAGE);
    }
    
    @Override
    public ConfigListenerInfo getAllSubClientConfigByIp(String ip, boolean all, String namespaceId, boolean aggregation)
            throws NacosException {
        throw new NacosApiException(NacosException.SERVER_NOT_IMPLEMENTED, ErrorCode.API_FUNCTION_DISABLED,
                MCP_NOT_ENABLED_MESSAGE);
    }
    
    @Override
    public ResponseEntity<byte[]> exportConfig(String dataId, String group, String namespaceId, String appName,
            List<Long> ids) throws Exception {
        throw new NacosApiException(NacosException.SERVER_NOT_IMPLEMENTED, ErrorCode.API_FUNCTION_DISABLED,
                MCP_NOT_ENABLED_MESSAGE);
    }
    
    @Override
    public Result<Map<String, Object>> importAndPublishConfig(String srcUser, String namespaceId,
            SameConfigPolicy policy, MultipartFile file, String srcIp, String requestIpApp) throws NacosException {
        throw new NacosApiException(NacosException.SERVER_NOT_IMPLEMENTED, ErrorCode.API_FUNCTION_DISABLED,
                MCP_NOT_ENABLED_MESSAGE);
    }
    
    @Override
    public Result<Map<String, Object>> cloneConfig(String srcUser, String namespaceId,
            List<SameNamespaceCloneConfigBean> configBeansList, SameConfigPolicy policy, String srcIp,
            String requestIpApp) throws NacosException {
        throw new NacosApiException(NacosException.SERVER_NOT_IMPLEMENTED, ErrorCode.API_FUNCTION_DISABLED,
                MCP_NOT_ENABLED_MESSAGE);
    }
    
    @Override
    public boolean removeBetaConfig(String dataId, String group, String namespaceId, String remoteIp,
            String requestIpApp, String srcUser) throws NacosException {
        throw new NacosApiException(NacosException.SERVER_NOT_IMPLEMENTED, ErrorCode.API_FUNCTION_DISABLED,
                MCP_NOT_ENABLED_MESSAGE);
    }
    
    @Override
    public ConfigGrayInfo queryBetaConfig(String dataId, String group, String namespaceId) throws NacosException {
        throw new NacosApiException(NacosException.SERVER_NOT_IMPLEMENTED, ErrorCode.API_FUNCTION_DISABLED,
                MCP_NOT_ENABLED_MESSAGE);
    }
    
}
