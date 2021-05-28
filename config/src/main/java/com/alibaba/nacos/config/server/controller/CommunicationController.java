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
import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.model.SampleResult;
import com.alibaba.nacos.config.server.remote.ConfigChangeListenContext;
import com.alibaba.nacos.config.server.service.LongPollingService;
import com.alibaba.nacos.config.server.service.dump.DumpService;
import com.alibaba.nacos.config.server.service.notify.NotifyService;
import com.alibaba.nacos.config.server.utils.GroupKey2;
import com.alibaba.nacos.core.remote.Connection;
import com.alibaba.nacos.core.remote.ConnectionManager;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
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
public class CommunicationController {
    
    private final DumpService dumpService;
    
    private final LongPollingService longPollingService;
    
    private String trueStr = "true";
    
    @Autowired
    private ConfigChangeListenContext configChangeListenContext;
    
    @Autowired
    private ConnectionManager connectionManager;
    
    @Autowired
    public CommunicationController(DumpService dumpService, LongPollingService longPollingService) {
        this.dumpService = dumpService;
        this.longPollingService = longPollingService;
    }
    
    /**
     * Notify the change of config information.
     */
    @GetMapping("/dataChange")
    public Boolean notifyConfigInfo(HttpServletRequest request, @RequestParam("dataId") String dataId,
            @RequestParam("group") String group,
            @RequestParam(value = "tenant", required = false, defaultValue = StringUtils.EMPTY) String tenant,
            @RequestParam(value = "tag", required = false) String tag) {
        dataId = dataId.trim();
        group = group.trim();
        String lastModified = request.getHeader(NotifyService.NOTIFY_HEADER_LAST_MODIFIED);
        long lastModifiedTs = StringUtils.isEmpty(lastModified) ? -1 : Long.parseLong(lastModified);
        String handleIp = request.getHeader(NotifyService.NOTIFY_HEADER_OP_HANDLE_IP);
        String isBetaStr = request.getHeader("isBeta");
        if (StringUtils.isNotBlank(isBetaStr) && trueStr.equals(isBetaStr)) {
            dumpService.dump(dataId, group, tenant, lastModifiedTs, handleIp, true);
        } else {
            dumpService.dump(dataId, group, tenant, tag, lastModifiedTs, handleIp);
        }
        return true;
    }
    
    /**
     * Get client config information of subscriber in local machine.
     */
    @GetMapping("/configWatchers")
    public SampleResult getSubClientConfig(@RequestParam("dataId") String dataId, @RequestParam("group") String group,
            @RequestParam(value = "tenant", required = false) String tenant, ModelMap modelMap) {
        group = StringUtils.isBlank(group) ? Constants.DEFAULT_GROUP : group;
        // long polling listners.
        SampleResult result = longPollingService.getCollectSubscribleInfo(dataId, group, tenant);
        // rpc listeners.
        String groupKey = GroupKey2.getKey(dataId, group, tenant);
        Set<String> listenersClients = configChangeListenContext.getListeners(groupKey);
        if (CollectionUtils.isEmpty(listenersClients)) {
            return result;
        }
        Map<String, String> lisentersGroupkeyStatus = new HashMap<String, String>(listenersClients.size(), 1);
        for (String connectionId : listenersClients) {
            Connection client = connectionManager.getConnection(connectionId);
            if (client != null) {
                String md5 = configChangeListenContext.getListenKeyMd5(connectionId, groupKey);
                if (md5 != null) {
                    lisentersGroupkeyStatus.put(client.getMetaInfo().getClientIp(), md5);
                }
            }
        }
        result.getLisentersGroupkeyStatus().putAll(lisentersGroupkeyStatus);
        return result;
    }
    
    /**
     * Get client config listener lists of subscriber in local machine.
     */
    @GetMapping("/watcherConfigs")
    public SampleResult getSubClientConfigByIp(HttpServletRequest request, HttpServletResponse response,
            @RequestParam("ip") String ip, ModelMap modelMap) {
        
        SampleResult result = longPollingService.getCollectSubscribleInfoByIp(ip);
        List<Connection> connectionsByIp = connectionManager.getConnectionByIp(ip);
        for (Connection connectionByIp : connectionsByIp) {
            Map<String, String> listenKeys = configChangeListenContext
                    .getListenKeys(connectionByIp.getMetaInfo().getConnectionId());
            if (listenKeys != null) {
                result.getLisentersGroupkeyStatus().putAll(listenKeys);
            }
        }
        return result;
        
    }
    
}
