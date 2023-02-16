/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.config.server.controller;

import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.common.utils.NamespaceUtil;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.model.ConfigHistoryInfo;
import com.alibaba.nacos.config.server.model.ConfigInfoWrapper;
import com.alibaba.nacos.config.server.model.Page;
import com.alibaba.nacos.config.server.service.HistoryService;
import com.alibaba.nacos.config.server.utils.ParamUtils;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.SignType;
import com.alibaba.nacos.plugin.auth.exception.AccessException;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * History management controller.
 *
 * @author Nacos
 */
@RestController
@RequestMapping(Constants.HISTORY_CONTROLLER_PATH)
public class HistoryController {
    
    private final HistoryService historyService;
    
    public HistoryController(HistoryService historyService) {
        this.historyService = historyService;
    }
    
    /**
     * Query the list history config. notes:
     *
     * @param dataId   dataId string value.
     * @param group    group string value.
     * @param tenant   tenant string value.
     * @param appName  appName string value.
     * @param pageNo   pageNo integer value.
     * @param pageSize pageSize integer value.
     * @param modelMap modelMap.
     * @return the page of history config.
     * @since 2.0.3 add {@link Secured} for history config permission check.
     */
    @GetMapping(params = "search=accurate")
    @Secured(action = ActionTypes.READ, signType = SignType.CONFIG)
    public Page<ConfigHistoryInfo> listConfigHistory(@RequestParam("dataId") String dataId,
            @RequestParam("group") String group,
            @RequestParam(value = "tenant", required = false, defaultValue = StringUtils.EMPTY) String tenant,
            @RequestParam(value = "appName", required = false) String appName,
            @RequestParam(value = "pageNo", required = false) Integer pageNo,
            @RequestParam(value = "pageSize", required = false) Integer pageSize, ModelMap modelMap) {
        pageNo = null == pageNo ? 1 : pageNo;
        pageSize = null == pageSize ? 100 : pageSize;
        pageSize = Math.min(500, pageSize);
        // configInfoBase has no appName field.
        return historyService.listConfigHistory(dataId, group, tenant, pageNo, pageSize);
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
    @GetMapping
    @Secured(action = ActionTypes.READ, signType = SignType.CONFIG)
    public ConfigHistoryInfo getConfigHistoryInfo(@RequestParam("dataId") String dataId,
            @RequestParam("group") String group,
            @RequestParam(value = "tenant", required = false, defaultValue = StringUtils.EMPTY) String tenant,
            @RequestParam("nid") Long nid) throws AccessException {
        return historyService.getConfigHistoryInfo(dataId, group, tenant, nid);
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
     * @since 1.4.0
     */
    @GetMapping(value = "/previous")
    @Secured(action = ActionTypes.READ, signType = SignType.CONFIG)
    public ConfigHistoryInfo getPreviousConfigHistoryInfo(@RequestParam("dataId") String dataId,
            @RequestParam("group") String group,
            @RequestParam(value = "tenant", required = false, defaultValue = StringUtils.EMPTY) String tenant,
            @RequestParam("id") Long id) throws AccessException {
        return historyService.getPreviousConfigHistoryInfo(dataId, group, tenant, id);
    }
    
    /**
     * Query configs list by namespace.
     *
     * @param tenant config_info namespace
     * @return list
     * @since 2.1.1
     */
    @GetMapping(value = "/configs")
    @Secured(action = ActionTypes.READ, signType = SignType.CONFIG)
    public List<ConfigInfoWrapper> getDataIds(@RequestParam("tenant") String tenant) {
        // check tenant
        ParamUtils.checkTenant(tenant);
        tenant = NamespaceUtil.processNamespaceParameter(tenant);
        return historyService.getConfigListByNamespace(tenant);
    }
    
}
