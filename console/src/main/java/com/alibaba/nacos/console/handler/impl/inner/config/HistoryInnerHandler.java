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

package com.alibaba.nacos.console.handler.impl.inner.config;

import com.alibaba.nacos.api.config.model.ConfigBasicInfo;
import com.alibaba.nacos.api.config.model.ConfigHistoryBasicInfo;
import com.alibaba.nacos.api.config.model.ConfigHistoryDetailInfo;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.config.server.model.ConfigHistoryInfo;
import com.alibaba.nacos.config.server.model.ConfigInfoWrapper;
import com.alibaba.nacos.config.server.service.HistoryService;
import com.alibaba.nacos.config.server.utils.ResponseUtil;
import com.alibaba.nacos.console.handler.config.HistoryHandler;
import com.alibaba.nacos.console.handler.impl.ConditionFunctionEnabled;
import com.alibaba.nacos.console.handler.impl.inner.EnabledInnerHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * .
 *
 * @author zhangyukun on:2024/8/16
 */
@Service
@EnabledInnerHandler
@Conditional(ConditionFunctionEnabled.ConditionConfigEnabled.class)
public class HistoryInnerHandler implements HistoryHandler {
    
    private final HistoryService historyService;
    
    @Autowired
    public HistoryInnerHandler(HistoryService historyService) {
        this.historyService = historyService;
    }
    
    @Override
    public ConfigHistoryDetailInfo getConfigHistoryInfo(String dataId, String group, String namespaceId, Long nid)
            throws NacosException {
        ConfigHistoryDetailInfo result;
        try {
            ConfigHistoryInfo configHistoryInfo = historyService.getConfigHistoryInfo(dataId, group, namespaceId, nid);
            result = ResponseUtil.transferToConfigHistoryDetailInfo(configHistoryInfo);
        } catch (DataAccessException e) {
            throw new NacosApiException(HttpStatus.NOT_FOUND.value(), ErrorCode.RESOURCE_NOT_FOUND,
                    "certain config history for nid = " + nid + " not exist");
        }
        return result;
    }
    
    @Override
    public Page<ConfigHistoryBasicInfo> listConfigHistory(String dataId, String group, String namespaceId,
            Integer pageNo, Integer pageSize) throws NacosException {
        Page<ConfigHistoryInfo> configHistoryInfoPage = historyService.listConfigHistory(dataId, group, namespaceId,
                pageNo, pageSize);
        Page<ConfigHistoryBasicInfo> result = new Page<>();
        result.setPagesAvailable(configHistoryInfoPage.getPagesAvailable());
        result.setPageNumber(configHistoryInfoPage.getPageNumber());
        result.setTotalCount(configHistoryInfoPage.getTotalCount());
        result.setPageItems(
                configHistoryInfoPage.getPageItems().stream().map(ResponseUtil::transferToConfigHistoryBasicInfo)
                        .collect(Collectors.toList()));
        return result;
    }
    
    @Override
    public ConfigHistoryDetailInfo getPreviousConfigHistoryInfo(String dataId, String group, String namespaceId,
            Long id) throws NacosException {
        ConfigHistoryDetailInfo result;
        try {
            ConfigHistoryInfo configHistoryInfo = historyService.getPreviousConfigHistoryInfo(dataId, group,
                    namespaceId, id);
            result = ResponseUtil.transferToConfigHistoryDetailInfo(configHistoryInfo);
        } catch (DataAccessException e) {
            throw new NacosApiException(HttpStatus.NOT_FOUND.value(), ErrorCode.RESOURCE_NOT_FOUND,
                    "previous config history for id = " + id + " not exist");
        }
        return result;
    }
    
    @Override
    public List<ConfigBasicInfo> getConfigsByTenant(String namespaceId) {
        List<ConfigInfoWrapper> configListByNamespace = historyService.getConfigListByNamespace(namespaceId);
        return configListByNamespace.stream().map(ResponseUtil::transferToConfigBasicInfo).toList();
    }
}