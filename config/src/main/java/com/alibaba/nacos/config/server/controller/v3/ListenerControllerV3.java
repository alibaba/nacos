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
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.common.utils.NamespaceUtil;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.model.GroupkeyListenserStatus;
import com.alibaba.nacos.config.server.model.SampleResult;
import com.alibaba.nacos.config.server.paramcheck.ConfigDefaultHttpParamExtractor;
import com.alibaba.nacos.config.server.service.ConfigSubService;
import com.alibaba.nacos.config.server.utils.GroupKey2;
import com.alibaba.nacos.core.paramcheck.ExtractorManager;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.ApiType;
import com.alibaba.nacos.plugin.auth.constant.SignType;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Listener management.
 *
 * @author Nacos
 */
@NacosApi
@RestController
@RequestMapping(Constants.LISTENER_CONTROLLER_V3_ADMIN_PATH)
@ExtractorManager.Extractor(httpExtractor = ConfigDefaultHttpParamExtractor.class)
public class ListenerControllerV3 {
    
    private final ConfigSubService configSubService;
    
    public ListenerControllerV3(ConfigSubService configSubService) {
        this.configSubService = configSubService;
    }
    
    /**
     * Get subscribe information from client side.
     */
    @GetMapping
    @Secured(resource = Constants.LISTENER_CONTROLLER_V3_ADMIN_PATH, action = ActionTypes.READ,
            signType = SignType.CONFIG, apiType = ApiType.ADMIN_API)
    public Result<GroupkeyListenserStatus> getAllSubClientConfigByIp(@RequestParam("ip") String ip,
            @RequestParam(value = "all", required = false) boolean all,
            @RequestParam(value = "namespaceId", required = false) String namespaceId,
            @RequestParam(value = "sampleTime", required = false, defaultValue = "1") int sampleTime, ModelMap modelMap) {
        SampleResult collectSampleResult = configSubService.getCollectSampleResultByIp(ip, sampleTime);
        GroupkeyListenserStatus gls = new GroupkeyListenserStatus();
        gls.setCollectStatus(200);
        Map<String, String> configMd5Status = new HashMap<>(100);
        if (collectSampleResult.getLisentersGroupkeyStatus() == null) {
            return Result.success(gls);
        }
        Map<String, String> status = collectSampleResult.getLisentersGroupkeyStatus();
        namespaceId = NamespaceUtil.processNamespaceParameter(namespaceId);
        for (Map.Entry<String, String> config : status.entrySet()) {
            if (!StringUtils.isBlank(namespaceId) && config.getKey().contains(namespaceId)) {
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
        return Result.success(gls);
    }
    
}