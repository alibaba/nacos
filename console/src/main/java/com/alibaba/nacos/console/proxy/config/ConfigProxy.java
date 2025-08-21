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

import com.alibaba.nacos.api.config.model.ConfigBasicInfo;
import com.alibaba.nacos.api.config.model.ConfigDetailInfo;
import com.alibaba.nacos.api.config.model.ConfigGrayInfo;
import com.alibaba.nacos.api.config.model.ConfigListenerInfo;
import com.alibaba.nacos.api.config.model.SameConfigPolicy;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.config.server.controller.parameters.SameNamespaceCloneConfigBean;
import com.alibaba.nacos.config.server.model.ConfigRequestInfo;
import com.alibaba.nacos.config.server.model.form.ConfigForm;
import com.alibaba.nacos.console.handler.config.ConfigHandler;
import jakarta.servlet.ServletException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Proxy class for handling configuration operations.
 *
 * @author zhangyukun
 */
@Service
public class ConfigProxy {
    
    private final ConfigHandler configHandler;
    
    @Autowired
    public ConfigProxy(ConfigHandler configHandler) {
        this.configHandler = configHandler;
    }
    
    /**
     * Get configure information list.
     */
    public Page<ConfigBasicInfo> getConfigList(int pageNo, int pageSize, String dataId, String group, String namespaceId,
            Map<String, Object> configAdvanceInfo) throws IOException, ServletException, NacosException {
        return configHandler.getConfigList(pageNo, pageSize, dataId, group, namespaceId, configAdvanceInfo);
    }
    
    /**
     * Get the specific configuration information.
     */
    public ConfigDetailInfo getConfigDetail(String dataId, String group, String namespaceId) throws NacosException {
        return configHandler.getConfigDetail(dataId, group, namespaceId);
    }
    
    /**
     * Add or update configuration.
     */
    public Boolean publishConfig(ConfigForm configForm, ConfigRequestInfo configRequestInfo) throws NacosException {
        return configHandler.publishConfig(configForm, configRequestInfo);
    }
    
    /**
     * Delete configuration.
     */
    public Boolean deleteConfig(String dataId, String group, String namespaceId, String tag, String clientIp,
            String srcUser) throws NacosException {
        return configHandler.deleteConfig(dataId, group, namespaceId, tag, clientIp, srcUser);
    }
    
    /**
     * Batch delete configurations.
     */
    public Boolean batchDeleteConfigs(List<Long> ids, String clientIp, String srcUser) throws NacosException {
        return configHandler.batchDeleteConfigs(ids, clientIp, srcUser);
    }
    
    /**
     * Search config list by config detail.
     */
    public Page<ConfigBasicInfo> getConfigListByContent(String search, int pageNo, int pageSize, String dataId, String group,
            String namespaceId, Map<String, Object> configAdvanceInfo) throws NacosException {
        return configHandler.getConfigListByContent(search, pageNo, pageSize, dataId, group, namespaceId,
                configAdvanceInfo);
    }
    
    /**
     * Subscribe to configured client information.
     */
    public ConfigListenerInfo getListeners(String dataId, String group, String namespaceId, boolean aggregation)
            throws Exception {
        return configHandler.getListeners(dataId, group, namespaceId, aggregation);
    }
    
    /**
     * Get subscription information based on IP, tenant, and other parameters.
     */
    public ConfigListenerInfo getAllSubClientConfigByIp(String ip, boolean all, String namespaceId, boolean aggregation)
            throws NacosException {
        return configHandler.getAllSubClientConfigByIp(ip, all, namespaceId, aggregation);
    }
    
    /**
     * New version export config adds metadata.yml file to record config metadata.
     */
    public ResponseEntity<byte[]> exportConfigV2(String dataId, String group, String namespaceId, String appName,
            List<Long> ids) throws Exception {
        return configHandler.exportConfig(dataId, group, namespaceId, appName, ids);
    }
    
    /**
     * Imports and publishes a configuration from a file.
     */
    public Result<Map<String, Object>> importAndPublishConfig(String srcUser, String namespaceId,
            SameConfigPolicy policy, MultipartFile file, String srcIp, String requestIpApp) throws NacosException {
        return configHandler.importAndPublishConfig(srcUser, namespaceId, policy, file, srcIp, requestIpApp);
    }
    
    /**
     * Clone configuration.
     */
    public Result<Map<String, Object>> cloneConfig(String srcUser, String namespaceId,
            List<SameNamespaceCloneConfigBean> configBeansList, SameConfigPolicy policy, String srcIp,
            String requestIpApp) throws NacosException {
        return configHandler.cloneConfig(srcUser, namespaceId, configBeansList, policy, srcIp, requestIpApp);
    }
    
    /**
     * Remove beta configuration based on dataId, group, and namespaceId.
     */
    public boolean removeBetaConfig(String dataId, String group, String namespaceId, String remoteIp,
            String requestIpApp, String srcUser) throws NacosException {
        return configHandler.removeBetaConfig(dataId, group, namespaceId, remoteIp, requestIpApp, srcUser);
    }
    
    /**
     * Query beta configuration based on dataId, group, and namespaceId.
     */
    public ConfigGrayInfo queryBetaConfig(String dataId, String group, String namespaceId)
            throws NacosException {
        return configHandler.queryBetaConfig(dataId, group, namespaceId);
    }
}
