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

package com.alibaba.nacos.console.handler.impl.remote.config;

import com.alibaba.nacos.api.config.model.ConfigBasicInfo;
import com.alibaba.nacos.api.config.model.ConfigHistoryBasicInfo;
import com.alibaba.nacos.api.config.model.ConfigHistoryDetailInfo;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.config.server.model.ConfigHistoryInfo;
import com.alibaba.nacos.config.server.model.ConfigInfoWrapper;
import com.alibaba.nacos.console.handler.config.HistoryHandler;
import com.alibaba.nacos.console.handler.impl.remote.EnabledRemoteHandler;
import com.alibaba.nacos.console.handler.impl.remote.NacosMaintainerClientHolder;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.LinkedList;
import java.util.List;

/**
 * Remote Implementation of HistoryHandler for handling internal configuration operations.
 *
 * @author xiweng.yy
 */
@Service
@EnabledRemoteHandler
public class HistoryRemoteHandler implements HistoryHandler {
    
    private final NacosMaintainerClientHolder clientHolder;
    
    public HistoryRemoteHandler(NacosMaintainerClientHolder clientHolder) {
        this.clientHolder = clientHolder;
    }
    
    @Override
    public ConfigHistoryDetailInfo getConfigHistoryInfo(String dataId, String group, String namespaceId, Long nid)
            throws NacosException {
        return clientHolder.getConfigMaintainerService().getConfigHistoryInfo(dataId, group, namespaceId, nid);
    }
    
    @Override
    public Page<ConfigHistoryInfo> listConfigHistory(String dataId, String group, String namespaceId, Integer pageNo,
            Integer pageSize) throws NacosException {
        Page<ConfigHistoryBasicInfo> historyDetailInfos = clientHolder.getConfigMaintainerService()
                .listConfigHistory(dataId, group, namespaceId, pageNo, pageSize);
        return transferToConfigHistoryInfoPage(historyDetailInfos);
    }
    
    @Override
    public ConfigHistoryDetailInfo getPreviousConfigHistoryInfo(String dataId, String group, String namespaceId,
            Long id) throws NacosException {
        return clientHolder.getConfigMaintainerService().getPreviousConfigHistoryInfo(dataId, group, namespaceId, id);
    }
    
    @Override
    public List<ConfigBasicInfo> getConfigsByTenant(String namespaceId) throws NacosException {
        return clientHolder.getConfigMaintainerService().getConfigListByNamespace(namespaceId);
    }
    
    /**
     * TODO removed after console-ui changed.
     */
    private ConfigHistoryInfo transferToConfigHistoryInfo(ConfigHistoryDetailInfo historyDetailInfo) {
        ConfigHistoryInfo result = new ConfigHistoryInfo();
        injectConfigHistoryBasicInfoToConfigHistoryInfo(historyDetailInfo, result);
        result.setContent(historyDetailInfo.getContent());
        result.setGrayName(historyDetailInfo.getGrayName());
        result.setExtInfo(historyDetailInfo.getExtInfo());
        result.setEncryptedDataKey(historyDetailInfo.getEncryptedDataKey());
        return result;
    }
    
    /**
     * TODO removed after console-ui changed.
     */
    private Page<ConfigHistoryInfo> transferToConfigHistoryInfoPage(Page<ConfigHistoryBasicInfo> historyDetailInfos) {
        Page<ConfigHistoryInfo> result = new Page<>();
        result.setPageNumber(historyDetailInfos.getPageNumber());
        result.setPagesAvailable(historyDetailInfos.getPagesAvailable());
        result.setTotalCount(historyDetailInfos.getTotalCount());
        List<ConfigHistoryInfo> infos = new LinkedList<>();
        historyDetailInfos.getPageItems().forEach(configHistoryBasicInfo -> {
            ConfigHistoryInfo info = new ConfigHistoryInfo();
            injectConfigHistoryBasicInfoToConfigHistoryInfo(configHistoryBasicInfo, info);
            infos.add(info);
        });
        result.setPageItems(infos);
        return result;
    }
    
    /**
     * TODO removed after console-ui changed.
     */
    private void injectConfigHistoryBasicInfoToConfigHistoryInfo(ConfigHistoryBasicInfo basicInfo,
            ConfigHistoryInfo result) {
        result.setId(basicInfo.getId());
        result.setDataId(basicInfo.getDataId());
        result.setGroup(basicInfo.getGroupName());
        result.setTenant(basicInfo.getNamespaceId());
        result.setAppName(basicInfo.getAppName());
        result.setMd5(basicInfo.getMd5());
        result.setSrcIp(basicInfo.getSrcIp());
        result.setSrcUser(basicInfo.getSrcUser());
        result.setOpType(basicInfo.getOpType());
        result.setPublishType(basicInfo.getPublishType());
        result.setCreatedTime(new Timestamp(basicInfo.getCreateTime()));
        result.setLastModifiedTime(new Timestamp(basicInfo.getModifyTime()));
    }
    
    /**
     * TODO removed after console-ui changed.
     */
    private List<ConfigInfoWrapper> transferToConfigInfoWrapperList(List<ConfigBasicInfo> configInfos) {
        List<ConfigInfoWrapper> result = new LinkedList<>();
        configInfos.forEach(configInfo -> {
            ConfigInfoWrapper configInfoWrapper = new ConfigInfoWrapper();
            configInfoWrapper.setId(configInfo.getId());
            configInfoWrapper.setDataId(configInfo.getDataId());
            configInfoWrapper.setGroup(configInfo.getGroupName());
            configInfoWrapper.setTenant(configInfo.getNamespaceId());
            configInfoWrapper.setMd5(configInfo.getMd5());
            configInfo.setType(configInfo.getType());
            configInfoWrapper.setAppName(configInfo.getAppName());
            configInfoWrapper.setLastModified(configInfo.getModifyTime());
            result.add(configInfoWrapper);
        });
        return result;
    }
}