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
import com.alibaba.nacos.config.server.model.GroupkeyListenserStatus;
import com.alibaba.nacos.config.server.model.SampleResult;
import com.alibaba.nacos.config.server.service.ConfigSubService;
import com.alibaba.nacos.config.server.utils.GroupKey2;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Config longpolling.
 *
 * @author Nacos
 */
@RestController
@RequestMapping(Constants.LISTENER_CONTROLLER_PATH)
public class ListenerController {
    
    private final ConfigSubService configSubService;
    
    @Autowired
    public ListenerController(ConfigSubService configSubService) {
        this.configSubService = configSubService;
    }
    
    /**
     * Get subscribe information from client side.
     */
    @GetMapping
    public GroupkeyListenserStatus getAllSubClientConfigByIp(@RequestParam("ip") String ip,
            @RequestParam(value = "all", required = false) boolean all,
            @RequestParam(value = "tenant", required = false) String tenant,
            @RequestParam(value = "sampleTime", required = false, defaultValue = "1") int sampleTime, ModelMap modelMap)
            throws Exception {
        SampleResult collectSampleResult = configSubService.getCollectSampleResultByIp(ip, sampleTime);
        GroupkeyListenserStatus gls = new GroupkeyListenserStatus();
        gls.setCollectStatus(200);
        Map<String, String> configMd5Status = new HashMap<String, String>(100);
        if (collectSampleResult.getLisentersGroupkeyStatus() == null) {
            return gls;
        }
        Map<String, String> status = collectSampleResult.getLisentersGroupkeyStatus();
        for (Map.Entry<String, String> config : status.entrySet()) {
            if (!StringUtils.isBlank(tenant) && config.getKey().contains(tenant)) {
                configMd5Status.put(config.getKey(), config.getValue());
                continue;
            }
            // Get common config default value, if want to get all config, you need to add "all".
            if (all) {
                configMd5Status.put(config.getKey(), config.getValue());
            } else {
                String[] configKeys = GroupKey2.parseKey(config.getKey());
                if (StringUtils.isBlank(configKeys[2])) {
                    configMd5Status.put(config.getKey(), config.getValue());
                }
            }
        }
        gls.setLisentersGroupkeyStatus(configMd5Status);
        return gls;
    }
    
}

