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
import com.alibaba.nacos.api.annotation.NacosApiResponseWrap;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.common.utils.Pair;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.model.ConfigHistoryInfo;
import com.alibaba.nacos.config.server.model.Page;
import com.alibaba.nacos.config.server.service.repository.PersistService;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.SignType;
import com.alibaba.nacos.plugin.auth.exception.AccessException;
import com.alibaba.nacos.plugin.encryption.handler.EncryptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

/**
 * config history management controller [v2].
 * @author dongyafei
 * @date 2022/7/25
 */

@NacosApi
@RestController
@RequestMapping(Constants.HISTORY_CONTROLLER_V2_PATH)
public class HistoryControllerV2 {
    
    private final PersistService persistService;
    
    public HistoryControllerV2(PersistService persistService) {
        this.persistService = persistService;
    }
    
    /**
     * Query the list history config. notes:
     *
     * @param dataId   dataId string value [required].
     * @param group    group string value [required].
     * @param tenant   tenant string value.
     * @param pageNo   pageNo integer value.
     * @param pageSize pageSize integer value.
     * @return the page of history config.
     * @since 2.0.3 add {@link Secured} for history config permission check.
     */
    @NacosApiResponseWrap
    @GetMapping("/list")
    @Secured(action = ActionTypes.READ, signType = SignType.CONFIG)
    public Page<ConfigHistoryInfo> listConfigHistory(
            @RequestParam("dataId") String dataId,
            @RequestParam("group") String group,
            @RequestParam(value = "tenant", required = false, defaultValue = StringUtils.EMPTY) String tenant,
            @RequestParam(value = "pageNo", required = false) Integer pageNo,
            @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        pageNo = null == pageNo ? 1 : pageNo;
        pageSize = null == pageSize ? 100 : pageSize;
        pageSize = Math.min(500, pageSize);
        return persistService.findConfigHistory(dataId, group, tenant, pageNo, pageSize);
    }
    
    /**
     * Query the detailed configuration history information. notes:
     *
     * @param nid    history_config_info nid
     * @param dataId dataId  @since 2.0.3
     * @param group  groupId  @since 2.0.3
     * @param tenant tenantId  @since 2.0.3
     * @return history config info
     * @since 2.0.3 add {@link Secured}, dataId, groupId and tenant for history config permission check.
     */
    @NacosApiResponseWrap
    @GetMapping
    @Secured(action = ActionTypes.READ, signType = SignType.CONFIG)
    public ConfigHistoryInfo getConfigHistoryInfo(
            @RequestParam("dataId") String dataId,
            @RequestParam("group") String group,
            @RequestParam(value = "tenant", required = false, defaultValue = StringUtils.EMPTY) String tenant,
            @RequestParam("nid") Long nid) throws AccessException {
        
        ConfigHistoryInfo configHistoryInfo = persistService.detailConfigHistory(nid);
        if (Objects.isNull(configHistoryInfo)) {
            return null;
        }
        // check if history config match the input
        checkHistoryInfoPermission(configHistoryInfo, dataId, group, tenant);
        
        String encryptedDataKey = configHistoryInfo.getEncryptedDataKey();
        Pair<String, String> pair = EncryptionHandler.decryptHandler(dataId, encryptedDataKey,
                configHistoryInfo.getContent());
        configHistoryInfo.setContent(pair.getSecond());
        
        return configHistoryInfo;
    }
    
    /**
     * Query previous config history information. notes:
     *
     * @param id     config_info id
     * @param dataId dataId  @since 2.0.3
     * @param group  groupId  @since 2.0.3
     * @param tenant tenantId  @since 2.0.3
     * @return history config info
     * @since 2.0.3 add {@link Secured}, dataId, groupId and tenant for history config permission check.
     */
    @NacosApiResponseWrap
    @GetMapping(value = "/previous")
    @Secured(action = ActionTypes.READ, signType = SignType.CONFIG)
    public ConfigHistoryInfo getPreviousConfigHistoryInfo(
            @RequestParam("dataId") String dataId,
            @RequestParam("group") String group,
            @RequestParam(value = "tenant", required = false, defaultValue = StringUtils.EMPTY) String tenant,
            @RequestParam("id") Long id) throws AccessException {
        ConfigHistoryInfo configHistoryInfo = persistService.detailPreviousConfigHistory(id);
        if (Objects.isNull(configHistoryInfo)) {
            return null;
        }
        // check if history config match the input
        checkHistoryInfoPermission(configHistoryInfo, dataId, group, tenant);
        return configHistoryInfo;
    }
    
    /**
     * Check if the input dataId,group and tenant match the history config.
     */
    private void checkHistoryInfoPermission(ConfigHistoryInfo configHistoryInfo, String dataId, String group,
            String tenant) throws AccessException {
        if (!Objects.equals(configHistoryInfo.getDataId(), dataId)
                || !Objects.equals(configHistoryInfo.getGroup(), group)
                || !Objects.equals(configHistoryInfo.getTenant(), tenant)) {
            throw new AccessException("Please check dataId, group or tenant.");
        }
    }
}
