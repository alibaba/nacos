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

import com.alibaba.nacos.common.utils.CollectionUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.enums.ConfigSearchRequestTypeEnum;
import com.alibaba.nacos.config.server.model.SampleResult;
import com.alibaba.nacos.config.server.paramcheck.ConfigDefaultHttpParamExtractor;
import com.alibaba.nacos.config.server.remote.ConfigChangeListenContext;
import com.alibaba.nacos.config.server.service.LongPollingService;
import com.alibaba.nacos.config.server.utils.GroupKey2;
import com.alibaba.nacos.core.paramcheck.ExtractorManager;
import com.alibaba.nacos.core.remote.Connection;
import com.alibaba.nacos.core.remote.ConnectionManager;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Controller for other node notification.
 *
 * @author boyan
 * @date 2010-5-7
 */
@RestController
@RequestMapping(Constants.COMMUNICATION_CONTROLLER_PATH)
@ExtractorManager.Extractor(httpExtractor = ConfigDefaultHttpParamExtractor.class)
public class CommunicationController {
    
    private final LongPollingService longPollingService;
    
    private final ConfigChangeListenContext configChangeListenContext;
    
    private final ConnectionManager connectionManager;
    
    public CommunicationController(LongPollingService longPollingService,
            ConfigChangeListenContext configChangeListenContext, ConnectionManager connectionManager) {
        this.longPollingService = longPollingService;
        this.configChangeListenContext = configChangeListenContext;
        this.connectionManager = connectionManager;
    }
    
    /**
     * Get client config information of subscriber in local machine.
     */
    @Deprecated
    @GetMapping("/configWatchers")
    public SampleResult getSubClientConfig(@RequestParam("dataId") String dataId, @RequestParam("group") String group,
            @RequestParam(value = "tenant", required = false) String tenant, ModelMap modelMap) {
        return searchByConfig(dataId, group, tenant);
    }
    
    private SampleResult searchByConfig(String dataId, String group, String tenant) {
        if (StringUtils.isBlank(dataId)) {
            return new SampleResult();
        }
        group = StringUtils.isBlank(group) ? Constants.DEFAULT_GROUP : group;
        // long polling listeners.
        SampleResult result = longPollingService.getCollectSubscribleInfo(dataId, group, tenant);
        // rpc listeners.
        String groupKey = GroupKey2.getKey(dataId, group, tenant);
        Set<String> listenersClients = configChangeListenContext.getListeners(groupKey);
        if (CollectionUtils.isEmpty(listenersClients)) {
            return result;
        }
        Map<String, String> listenersGroupkeyStatus = new HashMap<>(listenersClients.size(), 1);
        for (String connectionId : listenersClients) {
            Connection client = connectionManager.getConnection(connectionId);
            if (client != null) {
                String md5 = configChangeListenContext.getListenKeyMd5(connectionId, groupKey);
                if (md5 != null) {
                    listenersGroupkeyStatus.put(client.getMetaInfo().getClientIp(), md5);
                }
            }
        }
        result.getLisentersGroupkeyStatus().putAll(listenersGroupkeyStatus);
        return result;
    }
    
    /**
     * Get client config listener lists of subscriber in local machine.
     */
    @Deprecated
    @GetMapping("/watcherConfigs")
    public SampleResult getSubClientConfigByIp(HttpServletRequest request, HttpServletResponse response,
            @RequestParam("ip") String ip, ModelMap modelMap) {
        return searchByIp(ip);
    }
    
    private SampleResult searchByIp(String ip) {
        if (StringUtils.isBlank(ip)) {
            return new SampleResult();
        }
        SampleResult result = longPollingService.getCollectSubscribleInfoByIp(ip);
        List<Connection> connectionsByIp = connectionManager.getConnectionByIp(ip);
        for (Connection connectionByIp : connectionsByIp) {
            Map<String, String> listenKeys = configChangeListenContext.getListenKeys(
                    connectionByIp.getMetaInfo().getConnectionId());
            if (listenKeys != null) {
                result.getLisentersGroupkeyStatus().putAll(listenKeys);
            }
        }
        return result;
    }
    
    /**
     * Get client config information of subscriber in local machine.
     *
     * @param type   {@link ConfigSearchRequestTypeEnum}
     * @param ip     machine ip
     * @param dataId config dataId
     * @param group  config group
     * @param tenant config tenant
     * @return SampleResult
     */
    @GetMapping("/config")
    public SampleResult getSubClientConfigV2(
            @RequestParam("type") String type,
            @RequestParam(value = "ip", required = false) String ip,
            @RequestParam(value = "dataId", required = false) String dataId,
            @RequestParam(value = "group", required = false) String group,
            @RequestParam(value = "tenant", required = false) String tenant) {
        if (!ConfigSearchRequestTypeEnum.checkTypeLegal(type)) {
            return new SampleResult();
        }
        if (ConfigSearchRequestTypeEnum.IP.getType().equals(type)) {
            return searchByIp(ip);
        } else if (ConfigSearchRequestTypeEnum.CONFIG.getType().equals(type)) {
            return searchByConfig(dataId, group, tenant);
        }
        return new SampleResult();
    }
    
}
