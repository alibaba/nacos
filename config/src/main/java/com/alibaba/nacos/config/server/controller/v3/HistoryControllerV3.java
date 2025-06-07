/*
 * Copyright 1999-$toady.year Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.config.server.controller.v3;

import com.alibaba.nacos.api.annotation.NacosApi;
import com.alibaba.nacos.api.config.model.ConfigBasicInfo;
import com.alibaba.nacos.api.config.model.ConfigHistoryBasicInfo;
import com.alibaba.nacos.api.config.model.ConfigHistoryDetailInfo;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.common.utils.NamespaceUtil;
import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.model.ConfigHistoryInfo;
import com.alibaba.nacos.config.server.model.ConfigInfoWrapper;
import com.alibaba.nacos.config.server.model.form.ConfigFormV3;
import com.alibaba.nacos.config.server.paramcheck.ConfigDefaultHttpParamExtractor;
import com.alibaba.nacos.config.server.service.HistoryService;
import com.alibaba.nacos.config.server.utils.ParamUtils;
import com.alibaba.nacos.config.server.utils.ResponseUtil;
import com.alibaba.nacos.core.model.form.PageForm;
import com.alibaba.nacos.core.paramcheck.ExtractorManager;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.ApiType;
import com.alibaba.nacos.plugin.auth.constant.SignType;
import com.alibaba.nacos.plugin.auth.exception.AccessException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/**
 * History configuration management.
 *
 * @author Nacos
 */
@NacosApi
@RestController
@RequestMapping(Constants.HISTORY_ADMIN_V3_PATH)
@ExtractorManager.Extractor(httpExtractor = ConfigDefaultHttpParamExtractor.class)
public class HistoryControllerV3 {
    
    private final HistoryService historyService;
    
    public HistoryControllerV3(HistoryService historyService) {
        this.historyService = historyService;
    }
    
    /**
     * Query the list history config.
     */
    @GetMapping("/list")
    @Secured(action = ActionTypes.READ, signType = SignType.CONFIG, apiType = ApiType.ADMIN_API)
    public Result<Page<ConfigHistoryBasicInfo>> listConfigHistory(ConfigFormV3 configForm, PageForm pageForm)
            throws NacosApiException {
        configForm.validate();
        pageForm.validate();
        int pageSize = Math.min(500, pageForm.getPageSize());
        int pageNo = pageForm.getPageNo();
        String dataId = configForm.getDataId();
        String groupName = configForm.getGroupName();
        String namespaceId = NamespaceUtil.processNamespaceParameter(configForm.getNamespaceId());
        Page<ConfigHistoryInfo> configHistoryInfoPage = historyService.listConfigHistory(dataId, groupName, namespaceId,
                pageNo, pageSize);
        Page<ConfigHistoryBasicInfo> result = new Page<>();
        result.setPagesAvailable(configHistoryInfoPage.getPagesAvailable());
        result.setPageNumber(configHistoryInfoPage.getPageNumber());
        result.setTotalCount(configHistoryInfoPage.getTotalCount());
        result.setPageItems(
                configHistoryInfoPage.getPageItems().stream().map(ResponseUtil::transferToConfigHistoryBasicInfo)
                        .collect(Collectors.toList()));
        return Result.success(result);
    }
    
    /**
     * Query the detailed configuration history information.
     */
    @GetMapping
    @Secured(action = ActionTypes.READ, signType = SignType.CONFIG, apiType = ApiType.ADMIN_API)
    public Result<ConfigHistoryDetailInfo> getConfigHistoryInfo(ConfigFormV3 configForm, @RequestParam("nid") Long nid)
            throws AccessException, NacosApiException {
        ConfigHistoryInfo configHistoryInfo;
        configForm.validate();
        String dataId = configForm.getDataId();
        String groupName = configForm.getGroupName();
        String namespaceId = NamespaceUtil.processNamespaceParameter(configForm.getNamespaceId());
        try {
            //fix issue #9783
            namespaceId = NamespaceUtil.processNamespaceParameter(namespaceId);
            configHistoryInfo = historyService.getConfigHistoryInfo(dataId, groupName, namespaceId, nid);
        } catch (DataAccessException e) {
            throw new NacosApiException(HttpStatus.NOT_FOUND.value(), ErrorCode.RESOURCE_NOT_FOUND,
                    "certain config history for nid = " + nid + " not exist");
        }
        return Result.success(ResponseUtil.transferToConfigHistoryDetailInfo(configHistoryInfo));
    }
    
    /**
     * Query previous config history information.
     */
    @GetMapping(value = "/previous")
    @Secured(action = ActionTypes.READ, signType = SignType.CONFIG, apiType = ApiType.ADMIN_API)
    public Result<ConfigHistoryDetailInfo> getPreviousConfigHistoryInfo(ConfigFormV3 configForm,
            @RequestParam("id") Long id) throws AccessException, NacosApiException {
        ConfigHistoryInfo configHistoryInfo;
        configForm.validate();
        String dataId = configForm.getDataId();
        String groupName = configForm.getGroupName();
        String namespaceId = NamespaceUtil.processNamespaceParameter(configForm.getNamespaceId());
        try {
            //fix issue #9783.
            namespaceId = NamespaceUtil.processNamespaceParameter(namespaceId);
            configHistoryInfo = historyService.getPreviousConfigHistoryInfo(dataId, groupName, namespaceId, id);
        } catch (DataAccessException e) {
            throw new NacosApiException(HttpStatus.NOT_FOUND.value(), ErrorCode.RESOURCE_NOT_FOUND,
                    "previous config history for id = " + id + " not exist");
        }
        
        return Result.success(ResponseUtil.transferToConfigHistoryDetailInfo(configHistoryInfo));
    }
    
    /**
     * Query configs list by namespace.
     */
    @GetMapping(value = "/configs")
    @Secured(action = ActionTypes.READ, signType = SignType.CONFIG, apiType = ApiType.ADMIN_API)
    public Result<List<ConfigBasicInfo>> getConfigsByNamespace(@RequestParam("namespaceId") String namespaceId)
            throws NacosApiException {
        // check namespaceId
        ParamUtils.checkTenantV2(namespaceId);
        namespaceId = NamespaceUtil.processNamespaceParameter(namespaceId);
        List<ConfigInfoWrapper> configListByNamespace = historyService.getConfigListByNamespace(namespaceId);
        List<ConfigBasicInfo> result = configListByNamespace.stream().map(ResponseUtil::transferToConfigBasicInfo)
                .toList();
        return Result.success(result);
    }
}