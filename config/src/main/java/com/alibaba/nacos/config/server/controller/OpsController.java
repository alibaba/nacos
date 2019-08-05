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

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.service.PersistService;
import com.alibaba.nacos.config.server.service.dump.DumpService;

import com.alibaba.nacos.config.server.utils.LogUtil;
import com.alibaba.nacos.core.utils.WebUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * 管理控制器。
 *
 * @author Nacos
 */
@Controller
@RequestMapping(Constants.OPS_CONTROLLER_PATH)
public class OpsController {

    private static final Logger log = LoggerFactory.getLogger(OpsController.class);

    protected final PersistService persistService;

    private final DumpService dumpService;

    @Autowired
    public OpsController(PersistService persistService, DumpService dumpService) {
        this.persistService = persistService;
        this.dumpService = dumpService;
    }

    /**
     * ops call
     */
    @RequestMapping(value = "/localCache", method = RequestMethod.POST)
    @ResponseBody
    public String updateLocalCacheFromStore(HttpServletRequest request, HttpServletResponse respons) {
        log.info("start to dump all data from store.");
        dumpService.dumpAll();
        log.info("finish to dump all data from store.");
        return HttpServletResponse.SC_OK + "";
    }

    @RequestMapping(value = "/log", method = RequestMethod.PUT)
    @ResponseBody
    public String setLogLevel(HttpServletRequest request) {
        String logName = WebUtils.required(request, "logName");
        String logLevel = WebUtils.required(request, "logLevel");
        LogUtil.setLogLevel(logName, logLevel);
        return HttpServletResponse.SC_OK + "";
    }

}
