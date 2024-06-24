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

package com.alibaba.nacos.config.server.controller.v2;

import com.alibaba.nacos.api.annotation.NacosApi;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.common.utils.NamespaceUtil;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.model.ConfigHistoryInfo;
import com.alibaba.nacos.config.server.model.ConfigInfoWrapper;
import com.alibaba.nacos.config.server.paramcheck.ConfigDefaultHttpParamExtractor;
import com.alibaba.nacos.core.paramcheck.ExtractorManager;
import com.alibaba.nacos.persistence.model.Page;
import com.alibaba.nacos.config.server.service.HistoryService;
import com.alibaba.nacos.config.server.utils.ParamUtils;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.SignType;
import com.alibaba.nacos.plugin.auth.exception.AccessException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * config history management controller [v2].
 *
 * @author dongyafei
 * @date 2022/7/25
 * @since 2.2.0
 */
@NacosApi
@RestController
@RequestMapping(Constants.HISTORY_CONTROLLER_V2_PATH)
@ExtractorManager.Extractor(httpExtractor = ConfigDefaultHttpParamExtractor.class)
public class HistoryControllerV2 {
    
    private final HistoryService historyService;
    
    public HistoryControllerV2(HistoryService historyService) {
        this.historyService = historyService;
    }
    
    /**
     * Query the list history config. notes:
     *
     * @param dataId      dataId string value [required].
     * @param group       group string value [required].
     * @param namespaceId namespaceId.
     * @param pageNo      pageNo integer value.
     * @param pageSize    pageSize integer value.
     * @return the page of history config.
     */
    @GetMapping("/list")
    @Secured(action = ActionTypes.READ, signType = SignType.CONFIG)
    public Result<Page<ConfigHistoryInfo>> listConfigHistory(@RequestParam("dataId") String dataId,
            @RequestParam("group") String group,
            @RequestParam(value = "namespaceId", required = false, defaultValue = StringUtils.EMPTY) String namespaceId,
            @RequestParam(value = "pageNo", required = false, defaultValue = "1") Integer pageNo,
            @RequestParam(value = "pageSize", required = false, defaultValue = "100") Integer pageSize) {
        pageSize = Math.min(500, pageSize);
        //fix issue #9783
        namespaceId = NamespaceUtil.processNamespaceParameter(namespaceId);
        return Result.success(historyService.listConfigHistory(dataId, group, namespaceId, pageNo, pageSize));
    }
    
    /**
     * Query the detailed configuration history information. notes:
     *
     * @param nid         history_config_info nid
     * @param dataId      dataId
     * @param group       groupId
     * @param namespaceId namespaceId
     * @return history config info
     */
    @GetMapping
    @Secured(action = ActionTypes.READ, signType = SignType.CONFIG)
    public Result<ConfigHistoryInfo> getConfigHistoryInfo(@RequestParam("dataId") String dataId,
            @RequestParam("group") String group,
            @RequestParam(value = "namespaceId", required = false, defaultValue = StringUtils.EMPTY) String namespaceId,
            @RequestParam("nid") Long nid) throws AccessException, NacosApiException {
        ConfigHistoryInfo configHistoryInfo;
        try {
            //fix issue #9783
            namespaceId = NamespaceUtil.processNamespaceParameter(namespaceId);
            configHistoryInfo = historyService.getConfigHistoryInfo(dataId, group, namespaceId, nid);
        } catch (DataAccessException e) {
            throw new NacosApiException(HttpStatus.NOT_FOUND.value(), ErrorCode.RESOURCE_NOT_FOUND,
                    "certain config history for nid = " + nid + " not exist");
        }
        return Result.success(configHistoryInfo);
    }
    
    /**
     * Query previous config history information. notes:
     *
     * @param id          config_info id
     * @param dataId      dataId
     * @param group       groupId
     * @param namespaceId namespaceId
     * @return history config info
     */
    @GetMapping(value = "/previous")
    @Secured(action = ActionTypes.READ, signType = SignType.CONFIG)
    public Result<ConfigHistoryInfo> getPreviousConfigHistoryInfo(@RequestParam("dataId") String dataId,
            @RequestParam("group") String group,
            @RequestParam(value = "namespaceId", required = false, defaultValue = StringUtils.EMPTY) String namespaceId,
            @RequestParam("id") Long id) throws AccessException, NacosApiException {
        ConfigHistoryInfo configHistoryInfo;
        try {
            //fix issue #9783.
            namespaceId = NamespaceUtil.processNamespaceParameter(namespaceId);
            configHistoryInfo = historyService.getPreviousConfigHistoryInfo(dataId, group, namespaceId, id);
        } catch (DataAccessException e) {
            throw new NacosApiException(HttpStatus.NOT_FOUND.value(), ErrorCode.RESOURCE_NOT_FOUND,
                    "previous config history for id = " + id + " not exist");
        }
        return Result.success(configHistoryInfo);
    }
    
    /**
     * Query configs list by namespace.
     *
     * @param namespaceId config_info namespace
     * @return list
     */
    @GetMapping(value = "/configs")
    @Secured(action = ActionTypes.READ, signType = SignType.CONFIG)
    public Result<List<ConfigInfoWrapper>> getConfigsByTenant(@RequestParam("namespaceId") String namespaceId)
            throws NacosApiException {
        // check namespaceId
        ParamUtils.checkTenantV2(namespaceId);
        namespaceId = NamespaceUtil.processNamespaceParameter(namespaceId);
        return Result.success(historyService.getConfigListByNamespace(namespaceId));
    }
}
