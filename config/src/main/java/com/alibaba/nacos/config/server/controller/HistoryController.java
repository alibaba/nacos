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
import com.alibaba.nacos.config.server.service.PersistService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 管理控制器。
 *
 * @author Nacos
 */
@Controller
@RequestMapping(Constants.HISTORY_CONTROLLER_PATH)
public class HistoryController {

    @Autowired
    protected PersistService persistService;

    @RequestMapping(params = "search=accurate", method = RequestMethod.GET)
    @ResponseBody
    public Page<ConfigHistoryInfo> listConfigHistory(HttpServletRequest request, HttpServletResponse response,
                                                     @RequestParam("dataId") String dataId, //
                                                     @RequestParam("group") String group, //
                                                     @RequestParam(value = "tenant", required = false,
                                                         defaultValue = StringUtils.EMPTY) String tenant,
                                                     @RequestParam(value = "appName", required = false) String appName,
                                                     @RequestParam(value = "pageNo", required = false) Integer pageNo,
                                                     //
                                                     @RequestParam(value = "pageSize", required = false)
                                                         Integer pageSize, //
                                                     ModelMap modelMap) {
        pageNo = null == pageNo ? Integer.valueOf(1) : pageNo;
        pageSize = null == pageSize ? Integer.valueOf(100) : pageSize;
        pageSize = pageSize > 500 ? Integer.valueOf(500) : pageSize;
        // configInfoBase没有appName字段
        return persistService.findConfigHistory(dataId, group, tenant, pageNo, pageSize);
    }

    /**
     * 查看配置历史信息详情
     */
    @RequestMapping(method = RequestMethod.GET)
    @ResponseBody
    public ConfigHistoryInfo getConfigHistoryInfo(HttpServletRequest request, HttpServletResponse response,
                                                  @RequestParam("nid") Long nid, ModelMap modelMap) {
        return persistService.detailConfigHistory(nid);
    }

}
