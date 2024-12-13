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
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.config.server.model.ConfigHistoryInfo;
import com.alibaba.nacos.config.server.model.ConfigHistoryInfoPair;
import com.alibaba.nacos.config.server.model.ConfigInfo;
import com.alibaba.nacos.config.server.model.ConfigInfoWrapper;
import com.alibaba.nacos.persistence.model.Page;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoPersistService;
import com.alibaba.nacos.config.server.service.repository.HistoryConfigInfoPersistService;
import com.alibaba.nacos.plugin.auth.exception.AccessException;
import com.alibaba.nacos.plugin.encryption.handler.EncryptionHandler;
import org.springframework.beans.BeanUtils;
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

    /**
     * Query the detailed configuration history information pair, including the original version and the updated version.
     */
    public ConfigHistoryInfoPair getConfigHistoryInfoPair(String dataId, String group, String namespaceId, Long nid)
            throws AccessException {
        ConfigHistoryInfo configHistoryInfo = historyConfigInfoPersistService.detailConfigHistory(nid);
        if (Objects.isNull(configHistoryInfo)) {
            return null;
        }

        // check if history config match the input
        checkHistoryInfoPermission(configHistoryInfo, dataId, group, namespaceId);

        // transform
        ConfigHistoryInfoPair configHistoryInfoPair = new ConfigHistoryInfoPair();
        BeanUtils.copyProperties(configHistoryInfo, configHistoryInfoPair);
        configHistoryInfoPair.setOpType(configHistoryInfoPair.getOpType().trim());

        if ("I".equals(configHistoryInfoPair.getOpType())) {
            configHistoryInfoPair.setUpdatedContent(configHistoryInfoPair.getContent());
            configHistoryInfoPair.setUpdatedMd5(configHistoryInfoPair.getMd5());
            configHistoryInfoPair.setContent(StringUtils.EMPTY);
            configHistoryInfoPair.setMd5(StringUtils.EMPTY);
        }

        if ("U".equals(configHistoryInfoPair.getOpType())) {
            ConfigHistoryInfo configHistoryInfoUpdated = historyConfigInfoPersistService.detailUpdatedConfigHistory(nid);
            if (Objects.isNull(configHistoryInfoUpdated)) {
                // get the latest config info
                ConfigInfo configInfo = configInfoPersistService.findConfigInfo(dataId, group, namespaceId);
                configHistoryInfoPair.setUpdatedMd5(configInfo.getMd5());
                configHistoryInfoPair.setUpdatedContent(configInfo.getContent());
            } else {
                configHistoryInfoPair.setUpdatedMd5(configHistoryInfoUpdated.getMd5());
                configHistoryInfoPair.setUpdatedContent(configHistoryInfoUpdated.getContent());
            }
        }

        if ("D".equals(configHistoryInfoPair.getOpType())) {
            configHistoryInfoPair.setUpdatedMd5(StringUtils.EMPTY);
            configHistoryInfoPair.setUpdatedContent(StringUtils.EMPTY);
        }

        // decrypt content
        String encryptedDataKey = configHistoryInfoPair.getEncryptedDataKey();
        String originalContent = EncryptionHandler.decryptHandler(dataId, encryptedDataKey,
                configHistoryInfoPair.getContent()).getSecond();
        configHistoryInfoPair.setContent(originalContent);

        String updatedContent = EncryptionHandler.decryptHandler(dataId, encryptedDataKey,
                configHistoryInfoPair.getUpdatedContent()).getSecond();
        configHistoryInfoPair.setUpdatedContent(updatedContent);

        return configHistoryInfoPair;
    }
}
