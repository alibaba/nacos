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
import com.alibaba.nacos.config.server.enums.OperationType;
import com.alibaba.nacos.config.server.model.ConfigHistoryInfo;
import com.alibaba.nacos.config.server.model.ConfigHistoryInfoDetail;
import com.alibaba.nacos.config.server.model.ConfigInfo;
import com.alibaba.nacos.config.server.model.ConfigInfoWrapper;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoGrayPersistService;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoPersistService;
import com.alibaba.nacos.config.server.service.repository.HistoryConfigInfoPersistService;
import com.alibaba.nacos.persistence.model.Page;
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
    
    private final ConfigInfoGrayPersistService configInfoGrayPersistService;
    
    public HistoryService(HistoryConfigInfoPersistService historyConfigInfoPersistService,
            ConfigInfoPersistService configInfoPersistService,
            ConfigInfoGrayPersistService configInfoGrayPersistService) {
        this.historyConfigInfoPersistService = historyConfigInfoPersistService;
        this.configInfoPersistService = configInfoPersistService;
        this.configInfoGrayPersistService = configInfoGrayPersistService;
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
        Pair<String, String> pair = EncryptionHandler.decryptHandler(dataId, encryptedDataKey,
                configHistoryInfo.getContent());
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
        Pair<String, String> pair = EncryptionHandler.decryptHandler(dataId, encryptedDataKey,
                configHistoryInfo.getContent());
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
        if (!Objects.equals(configHistoryInfo.getDataId(), dataId) || !Objects.equals(configHistoryInfo.getGroup(),
                group) || !Objects.equals(configHistoryInfo.getTenant(), namespaceId)) {
            throw new AccessException("Please check dataId, group or namespaceId.");
        }
    }
    
    /**
     * Query the detailed config history info pair, including the original version and the updated version.
     */
    public ConfigHistoryInfoDetail getConfigHistoryInfoDetail(String dataId, String group, String namespaceId, Long nid)
            throws AccessException {
        ConfigHistoryInfo configHistoryInfo = historyConfigInfoPersistService.detailConfigHistory(nid);
        if (Objects.isNull(configHistoryInfo)) {
            return null;
        }
        
        // check if history config match the input
        checkHistoryInfoPermission(configHistoryInfo, dataId, group, namespaceId);
        
        // transform
        ConfigHistoryInfoDetail configHistoryInfoDetail = new ConfigHistoryInfoDetail();
        BeanUtils.copyProperties(configHistoryInfo, configHistoryInfoDetail);
        configHistoryInfoDetail.setOpType(configHistoryInfoDetail.getOpType().trim());
        
        //insert
        if (OperationType.INSERT.getValue().equals(configHistoryInfoDetail.getOpType())) {
            configHistoryInfoDetail.setUpdatedContent(configHistoryInfo.getContent());
            configHistoryInfoDetail.setUpdatedMd5(configHistoryInfo.getMd5());
            configHistoryInfoDetail.setUpdatedEncryptedDataKey(configHistoryInfo.getEncryptedDataKey());
            configHistoryInfoDetail.setUpdateExtInfo(configHistoryInfo.getExtInfo());
            configHistoryInfoDetail.setOriginalExtInfo(StringUtils.EMPTY);
            configHistoryInfoDetail.setOriginalContent(StringUtils.EMPTY);
            configHistoryInfoDetail.setOriginalMd5(StringUtils.EMPTY);
            configHistoryInfoDetail.setOriginalEncryptedDataKey(StringUtils.EMPTY);
        }
        
        //update
        if (OperationType.UPDATE.getValue().equals(configHistoryInfoDetail.getOpType())) {
            
            configHistoryInfoDetail.setOriginalExtInfo(configHistoryInfo.getExtInfo());
            configHistoryInfoDetail.setOriginalContent(configHistoryInfo.getContent());
            configHistoryInfoDetail.setOriginalMd5(configHistoryInfo.getMd5());
            configHistoryInfoDetail.setOriginalEncryptedDataKey(configHistoryInfo.getEncryptedDataKey());
            
            ConfigHistoryInfo nextHistoryInfo = historyConfigInfoPersistService.getNextHistoryInfo(dataId, group,
                    namespaceId, configHistoryInfoDetail.getPublishType(), configHistoryInfoDetail.getGrayName(), nid);
            
            ConfigInfo currentConfigInfo = null;
            if (Objects.isNull(nextHistoryInfo)) {
                //double check for concurrent
                currentConfigInfo = StringUtils.isEmpty(configHistoryInfoDetail.getGrayName())
                        ? configInfoPersistService.findConfigInfo(dataId, group, namespaceId)
                        : configInfoGrayPersistService.findConfigInfo4Gray(dataId, group, namespaceId,
                                configHistoryInfoDetail.getGrayName());
                nextHistoryInfo = historyConfigInfoPersistService.getNextHistoryInfo(dataId, group, namespaceId,
                        configHistoryInfoDetail.getPublishType(), configHistoryInfoDetail.getGrayName(), nid);
                
            }
            
            if (nextHistoryInfo != null) {
                configHistoryInfoDetail.setUpdateExtInfo(nextHistoryInfo.getExtInfo());
                configHistoryInfoDetail.setUpdatedContent(nextHistoryInfo.getContent());
                configHistoryInfoDetail.setUpdatedMd5(nextHistoryInfo.getMd5());
                configHistoryInfoDetail.setUpdatedEncryptedDataKey(nextHistoryInfo.getEncryptedDataKey());
            } else {
                configHistoryInfoDetail.setUpdatedContent(currentConfigInfo.getContent());
                configHistoryInfoDetail.setUpdatedMd5(currentConfigInfo.getMd5());
                configHistoryInfoDetail.setUpdatedEncryptedDataKey(currentConfigInfo.getEncryptedDataKey());
    
            }
        }
        
        //delete
        if (OperationType.DELETE.getValue().equals(configHistoryInfoDetail.getOpType())) {
            configHistoryInfoDetail.setOriginalMd5(configHistoryInfo.getMd5());
            configHistoryInfoDetail.setOriginalContent(configHistoryInfo.getContent());
            configHistoryInfoDetail.setOriginalEncryptedDataKey(configHistoryInfo.getEncryptedDataKey());
            configHistoryInfoDetail.setOriginalExtInfo(configHistoryInfo.getExtInfo());
        }
        
        // decrypt content
        if (StringUtils.isNotBlank(configHistoryInfoDetail.getOriginalContent())) {
            String originalContent = EncryptionHandler.decryptHandler(dataId,
                            configHistoryInfoDetail.getOriginalEncryptedDataKey(), configHistoryInfoDetail.getOriginalContent())
                    .getSecond();
            configHistoryInfoDetail.setOriginalContent(originalContent);
        }
        if (StringUtils.isNotBlank(configHistoryInfoDetail.getUpdatedContent())) {
            String updatedContent = EncryptionHandler.decryptHandler(dataId,
                            configHistoryInfoDetail.getUpdatedEncryptedDataKey(), configHistoryInfoDetail.getUpdatedContent())
                    .getSecond();
            configHistoryInfoDetail.setUpdatedContent(updatedContent);
        }
        
        return configHistoryInfoDetail;
    }
    
}