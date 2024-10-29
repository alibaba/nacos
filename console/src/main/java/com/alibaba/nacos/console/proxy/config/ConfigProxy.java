/*
 * Copyright 1999-2024 Alibaba Group Holding Ltd.
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
 *
 */

package com.alibaba.nacos.console.proxy.config;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.config.server.controller.parameters.SameNamespaceCloneConfigBean;
import com.alibaba.nacos.config.server.model.ConfigAllInfo;
import com.alibaba.nacos.config.server.model.ConfigInfo;
import com.alibaba.nacos.config.server.model.ConfigInfo4Beta;
import com.alibaba.nacos.config.server.model.ConfigRequestInfo;
import com.alibaba.nacos.config.server.model.GroupkeyListenserStatus;
import com.alibaba.nacos.config.server.model.SameConfigPolicy;
import com.alibaba.nacos.config.server.model.form.ConfigForm;
import com.alibaba.nacos.console.config.ConsoleConfig;
import com.alibaba.nacos.console.handler.config.ConfigHandler;
import com.alibaba.nacos.console.handler.inner.config.ConfigInnerHandler;
import com.alibaba.nacos.persistence.model.Page;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Proxy class for handling configuration operations.
 *
 * @author zhangyukun
 */
@Service
public class ConfigProxy {
    
    private final Map<String, ConfigHandler> configHandlerMap = new HashMap<>();
    
    private final ConsoleConfig consoleConfig;
    
    @Autowired
    public ConfigProxy(ConfigInnerHandler configInnerHandler, ConsoleConfig consoleConfig) {
        this.configHandlerMap.put("merged", configInnerHandler);
        this.consoleConfig = consoleConfig;
    }
    
    /**
     * Get configure information list.
     */
    public Page<ConfigInfo> getConfigList(int pageNo, int pageSize, String dataId, String group, String namespaceId,
            Map<String, Object> configAdvanceInfo) throws IOException, ServletException, NacosException {
        ConfigHandler configHandler = configHandlerMap.get(consoleConfig.getType());
        if (configHandler == null) {
            throw new NacosException(NacosException.INVALID_PARAM, "Invalid deployment type");
        }
        return configHandler.getConfigList(pageNo, pageSize, dataId, group, namespaceId, configAdvanceInfo);
    }
    
    /**
     * Get the specific configuration information.
     */
    public ConfigAllInfo getConfigDetail(String dataId, String group, String namespaceId) throws NacosException {
        ConfigHandler configHandler = configHandlerMap.get(consoleConfig.getType());
        if (configHandler == null) {
            throw new NacosException(NacosException.INVALID_PARAM, "Invalid deployment type");
        }
        return configHandler.getConfigDetail(dataId, group, namespaceId);
    }
    
    /**
     * Add or update configuration.
     */
    public Boolean publishConfig(ConfigForm configForm, ConfigRequestInfo configRequestInfo) throws NacosException {
        ConfigHandler configHandler = configHandlerMap.get(consoleConfig.getType());
        if (configHandler == null) {
            throw new NacosException(NacosException.INVALID_PARAM, "Invalid deployment type");
        }
        return configHandler.publishConfig(configForm, configRequestInfo);
    }
    
    /**
     * Delete configuration.
     */
    public Boolean deleteConfig(String dataId, String group, String namespaceId, String tag, String clientIp,
            String srcUser) throws NacosException {
        ConfigHandler configHandler = configHandlerMap.get(consoleConfig.getType());
        if (configHandler == null) {
            throw new NacosException(NacosException.INVALID_PARAM, "Invalid deployment type");
        }
        return configHandler.deleteConfig(dataId, group, namespaceId, tag, clientIp, srcUser);
    }
    
    /**
     * Batch delete configurations.
     */
    public Boolean batchDeleteConfigs(List<Long> ids, String clientIp, String srcUser) throws NacosException {
        ConfigHandler configHandler = configHandlerMap.get(consoleConfig.getType());
        if (configHandler == null) {
            throw new NacosException(NacosException.INVALID_PARAM, "Invalid deployment type");
        }
        return configHandler.batchDeleteConfigs(ids, clientIp, srcUser);
    }
    
    /**
     * Search config list by config detail.
     */
    public Page<ConfigInfo> getConfigListByContent(String search, int pageNo, int pageSize, String dataId, String group,
            String namespaceId, Map<String, Object> configAdvanceInfo) throws NacosException {
        ConfigHandler configHandler = configHandlerMap.get(consoleConfig.getType());
        if (configHandler == null) {
            throw new NacosException(NacosException.INVALID_PARAM, "Invalid deployment type");
        }
        return configHandler.getConfigListByContent(search, pageNo, pageSize, dataId, group, namespaceId,
                configAdvanceInfo);
    }
    
    /**
     * Subscribe to configured client information.
     */
    public GroupkeyListenserStatus getListeners(String dataId, String group, String namespaceId, int sampleTime)
            throws Exception {
        ConfigHandler configHandler = configHandlerMap.get(consoleConfig.getType());
        if (configHandler == null) {
            throw new NacosException(NacosException.INVALID_PARAM, "Invalid deployment type");
        }
        return configHandler.getListeners(dataId, group, namespaceId, sampleTime);
    }
    
    /**
     * Get subscription information based on IP, tenant, and other parameters.
     */
    public GroupkeyListenserStatus getAllSubClientConfigByIp(String ip, boolean all, String namespaceId, int sampleTime) throws NacosException {
        ConfigHandler configHandler = configHandlerMap.get(consoleConfig.getType());
        if (configHandler == null) {
            throw new NacosException(NacosException.INVALID_PARAM, "Invalid deployment type");
        }
        return configHandler.getAllSubClientConfigByIp(ip, all, namespaceId, sampleTime);
    }
    
    /**
     * Export configuration.
     */
    public ResponseEntity<byte[]> exportConfig(String dataId, String group, String namespaceId, String appName,
            List<Long> ids) throws Exception {
        ConfigHandler configHandler = configHandlerMap.get(consoleConfig.getType());
        if (configHandler == null) {
            throw new NacosException(NacosException.INVALID_PARAM, "Invalid deployment type");
        }
        return configHandler.exportConfig(dataId, group, namespaceId, appName, ids);
    }
    
    /**
     * New version export config adds metadata.yml file to record config metadata.
     */
    public ResponseEntity<byte[]> exportConfigV2(String dataId, String group, String namespaceId, String appName,
            List<Long> ids) throws Exception {
        ConfigHandler configHandler = configHandlerMap.get(consoleConfig.getType());
        if (configHandler == null) {
            throw new NacosException(NacosException.INVALID_PARAM, "Invalid deployment type");
        }
        return configHandler.exportConfigV2(dataId, group, namespaceId, appName, ids);
    }
    
    /**
     * Imports and publishes a configuration from a file.
     */
    public Result<Map<String, Object>> importAndPublishConfig(String srcUser, String namespaceId,
            SameConfigPolicy policy, MultipartFile file, String srcIp, String requestIpApp) throws NacosException {
        ConfigHandler configHandler = configHandlerMap.get(consoleConfig.getType());
        if (configHandler == null) {
            throw new NacosException(NacosException.INVALID_PARAM, "Invalid deployment type");
        }
        return configHandler.importAndPublishConfig(srcUser, namespaceId, policy, file, srcIp, requestIpApp);
    }
    
    /**
     * Clone configuration.
     */
    public Result<Map<String, Object>> cloneConfig(String srcUser, String namespaceId,
            List<SameNamespaceCloneConfigBean> configBeansList, SameConfigPolicy policy, String srcIp,
            String requestIpApp) throws NacosException {
        ConfigHandler configHandler = configHandlerMap.get(consoleConfig.getType());
        if (configHandler == null) {
            throw new NacosException(NacosException.INVALID_PARAM, "Invalid deployment type");
        }
        return configHandler.cloneConfig(srcUser, namespaceId, configBeansList, policy, srcIp, requestIpApp);
    }
    
    /**
     * Remove beta configuration based on dataId, group, and namespaceId.
     */
    public boolean removeBetaConfig(String dataId, String group, String namespaceId, String remoteIp,
            String requestIpApp) throws NacosException {
        ConfigHandler configHandler = configHandlerMap.get(consoleConfig.getType());
        if (configHandler == null) {
            throw new NacosException(NacosException.INVALID_PARAM, "Invalid deployment type");
        }
        return configHandler.removeBetaConfig(dataId, group, namespaceId, remoteIp, requestIpApp);
    }
    
    /**
     * Query beta configuration based on dataId, group, and namespaceId.
     */
    public Result<ConfigInfo4Beta> queryBetaConfig(String dataId, String group, String namespaceId) throws NacosException {
        ConfigHandler configHandler = configHandlerMap.get(consoleConfig.getType());
        if (configHandler == null) {
            throw new NacosException(NacosException.INVALID_PARAM, "Invalid deployment type");
        }
        return configHandler.queryBetaConfig(dataId, group, namespaceId);
    }
}
