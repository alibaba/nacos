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

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.model.SampleResult;
import com.alibaba.nacos.config.server.service.LongPollingService;
import com.alibaba.nacos.config.server.service.dump.DumpService;
import com.alibaba.nacos.config.server.service.notify.NotifyService;
import com.alibaba.nacos.config.server.service.watch.ConfigWatchCenter;
import com.alibaba.nacos.config.server.utils.ParamUtils;
import com.alibaba.nacos.core.utils.WebUtils;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Controller for other node notification.
 *
 * @author boyan
 * @date 2010-5-7
 */
@RestController
@RequestMapping(Constants.COMMUNICATION_CONTROLLER_PATH)
public class CommunicationController {
    
    private final DumpService dumpService;
    
    private final ConfigWatchCenter configWatchCenter;
    
    private final LongPollingService longPollingService;
    
    public CommunicationController(DumpService dumpService, ConfigWatchCenter configWatchCenter, LongPollingService longPollingService) {
        this.dumpService = dumpService;
        this.configWatchCenter = configWatchCenter;
        this.longPollingService = longPollingService;
    }
    
    /**
     * Notify the change of config information.
     *
     */
    @GetMapping("/dataChange")
    public Boolean notifyConfigInfo(HttpServletRequest request, @RequestParam("dataId") String dataId,
            @RequestParam("group") String group,
            @RequestParam(value = "tenant", required = false, defaultValue = StringUtils.EMPTY) String tenant,
            @RequestParam(value = "tag", required = false) String tag) {
        final String namespace = ParamUtils.processNamespace(tenant);
        dataId = ParamUtils.processDataID(dataId);
        group = ParamUtils.processGroupID(group);
        final String lastModified = WebUtils.getHeader(request, NotifyService.NOTIFY_HEADER_LAST_MODIFIED);
        final long lastModifiedTs = StringUtils.isEmpty(lastModified) ? -1 : Long.parseLong(lastModified);
        final String handleIp = WebUtils.getHeader(request, NotifyService.NOTIFY_HEADER_OP_HANDLE_IP);
        final String isBetaStr = WebUtils.getHeader(request, "isBeta");
        if (StringUtils.isTrueStr(isBetaStr)) {
            dumpService.dump(dataId, group, namespace, lastModifiedTs, handleIp, true);
        } else {
            dumpService.dump(dataId, group, namespace, tag, lastModifiedTs, handleIp);
        }
        return true;
    }
    
    /**
     * Get client config information of subscriber in local machine.
     *
     */
    @GetMapping("/configWatchers")
    public SampleResult getSubClientConfig(@RequestParam("dataId") String dataId, @RequestParam("group") String group,
            @RequestParam(value = "tenant", required = false) String tenant, ModelMap modelMap) throws NacosException {
        final String namespace = ParamUtils.processNamespace(tenant);
        ParamUtils.checkParam(dataId, group, "datumId", "content");
        dataId = ParamUtils.processDataID(dataId);
        group = ParamUtils.processGroupID(group);
        return configWatchCenter.getCollectSubscribeInfo(dataId, group, namespace);
    }
    
    /**
     * Get client config listener lists of subscriber in local machine.
     *
     */
    @GetMapping("/watcherConfigs")
    public SampleResult getSubClientConfigByIp(HttpServletRequest request, HttpServletResponse response,
            @RequestParam("ip") String ip, ModelMap modelMap) {
        return configWatchCenter.getCollectSubscribeInfoByIp(ip);
    }
}
