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

import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.model.ConfigHistoryInfo;
import com.alibaba.nacos.config.server.model.Page;
import com.alibaba.nacos.config.server.service.repository.PersistService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * History management controller.
 *
 * @author Nacos
 */
@RestController
@RequestMapping(Constants.HISTORY_CONTROLLER_PATH)
public class HistoryController {
    
    @Autowired
    protected PersistService persistService;
    
    /**
     * Query the list history config.
     *
     * @param dataId   dataId string value.
     * @param group    group string value.
     * @param tenant   tenant string value.
     * @param appName  appName string value.
     * @param pageNo   pageNo integer value.
     * @param pageSize pageSize integer value.
     * @param modelMap modelMap.
     * @return the page of history config.
     */
    @GetMapping(params = "search=accurate")
    public Page<ConfigHistoryInfo> listConfigHistory(@RequestParam("dataId") String dataId,
            @RequestParam("group") String group,
            @RequestParam(value = "tenant", required = false, defaultValue = StringUtils.EMPTY) String tenant,
            @RequestParam(value = "appName", required = false) String appName,
            @RequestParam(value = "pageNo", required = false) Integer pageNo,
            @RequestParam(value = "pageSize", required = false) Integer pageSize,
            ModelMap modelMap) {
        pageNo = null == pageNo ? 1 : pageNo;
        pageSize = null == pageSize ? 100 : pageSize;
        pageSize = Math.min(500, pageSize);
        // configInfoBase has no appName field.
        return persistService.findConfigHistory(dataId, group, tenant, pageNo, pageSize);
    }
    
    /**
     * Query the detailed configuration history information.
     *
     * @param nid history_config_info nid
     * @return history config info
     */
    @GetMapping
    public ConfigHistoryInfo getConfigHistoryInfo(@RequestParam("nid") Long nid) {
        return persistService.detailConfigHistory(nid);
    }
    
    /**
     * Query previous config history information.
     *
     * @param id config_info id
     * @return history config info
     * @since 1.4.0
     */
    @GetMapping(value = "/previous")
    public ConfigHistoryInfo getPreviousConfigHistoryInfo(@RequestParam("id") Long id) {
        return persistService.detailPreviousConfigHistory(id);
    }
    
}
