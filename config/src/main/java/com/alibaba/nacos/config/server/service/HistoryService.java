/*
 * Copyright 1999-2022 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.config.server.service;

import com.alibaba.nacos.common.utils.Pair;
import com.alibaba.nacos.config.server.model.ConfigHistoryInfo;
import com.alibaba.nacos.config.server.model.ConfigInfoWrapper;
import com.alibaba.nacos.persistence.model.Page;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoPersistService;
import com.alibaba.nacos.config.server.service.repository.HistoryConfigInfoPersistService;
import com.alibaba.nacos.plugin.auth.exception.AccessException;
import com.alibaba.nacos.plugin.encryption.handler.EncryptionHandler;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * HistoryService.
 *
 * @author dongyafei
 * @date 2022/8/11
 */
@Service
public class HistoryService {
    
    private final HistoryConfigInfoPersistService historyConfigInfoPersistService;
    
    private final ConfigInfoPersistService configInfoPersistService;
    
    public HistoryService(HistoryConfigInfoPersistService historyConfigInfoPersistService,
            ConfigInfoPersistService configInfoPersistService) {
        this.historyConfigInfoPersistService = historyConfigInfoPersistService;
        this.configInfoPersistService = configInfoPersistService;
    }
    
    /**
     * Query the list history config.
     */
    public Page<ConfigHistoryInfo> listConfigHistory(String dataId, String group, String namespaceId, Integer pageNo,
            Integer pageSize) {
        return historyConfigInfoPersistService.findConfigHistory(dataId, group, namespaceId, pageNo, pageSize);
    }
    
    /**
     * Query the detailed configuration history information.
     */
    public ConfigHistoryInfo getConfigHistoryInfo(String dataId, String group, String namespaceId, Long nid)
            throws AccessException {
        ConfigHistoryInfo configHistoryInfo = historyConfigInfoPersistService.detailConfigHistory(nid);
        if (Objects.isNull(configHistoryInfo)) {
            return null;
        }
        // check if history config match the input
        checkHistoryInfoPermission(configHistoryInfo, dataId, group, namespaceId);
        
        String encryptedDataKey = configHistoryInfo.getEncryptedDataKey();
        Pair<String, String> pair = EncryptionHandler
                .decryptHandler(dataId, encryptedDataKey, configHistoryInfo.getContent());
        configHistoryInfo.setContent(pair.getSecond());
        
        return configHistoryInfo;
    }
    
    /**
     * Query previous config history information.
     */
    public ConfigHistoryInfo getPreviousConfigHistoryInfo(String dataId, String group, String namespaceId, Long id)
            throws AccessException {
        ConfigHistoryInfo configHistoryInfo = historyConfigInfoPersistService.detailPreviousConfigHistory(id);
        if (Objects.isNull(configHistoryInfo)) {
            return null;
        }
        // check if history config match the input
        checkHistoryInfoPermission(configHistoryInfo, dataId, group, namespaceId);
        
        String encryptedDataKey = configHistoryInfo.getEncryptedDataKey();
        Pair<String, String> pair = EncryptionHandler
                .decryptHandler(dataId, encryptedDataKey, configHistoryInfo.getContent());
        configHistoryInfo.setContent(pair.getSecond());
        
        return configHistoryInfo;
    }
    
    /**
     * Query configs list by namespace.
     */
    public List<ConfigInfoWrapper> getConfigListByNamespace(String namespaceId) {
        return configInfoPersistService.queryConfigInfoByNamespace(namespaceId);
    }
    
    /**
     * Check if the input dataId,group and namespaceId match the history config.
     */
    private void checkHistoryInfoPermission(ConfigHistoryInfo configHistoryInfo, String dataId, String group,
            String namespaceId) throws AccessException {
        if (!Objects.equals(configHistoryInfo.getDataId(), dataId) || !Objects
                .equals(configHistoryInfo.getGroup(), group) || !Objects
                .equals(configHistoryInfo.getTenant(), namespaceId)) {
            throw new AccessException("Please check dataId, group or namespaceId.");
        }
    }
}
