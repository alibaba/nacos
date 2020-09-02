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

package com.alibaba.nacos.config.server.remote;

import com.alibaba.nacos.api.config.remote.request.ConfigBatchListenRequest;
import com.alibaba.nacos.api.config.remote.response.ConfigChangeBatchListenResponse;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.config.server.service.ConfigCacheService;
import com.alibaba.nacos.config.server.utils.MD5Util;
import com.alibaba.nacos.core.remote.RequestHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * config change listen request handler.
 *
 * @author liuzunfei
 * @version $Id: ConfigChangeListenRequestHandler.java, v 0.1 2020年07月14日 10:11 AM liuzunfei Exp $
 */
@Component
public class ConfigChangeBatchListenRequestHandler
        extends RequestHandler<ConfigBatchListenRequest, ConfigChangeBatchListenResponse> {
    
    @Autowired
    ConfigChangeListenContext configChangeListenContext;
    
    @Override
    public ConfigChangeBatchListenResponse handle(ConfigBatchListenRequest request, RequestMeta requestMeta)
            throws NacosException {
        ConfigBatchListenRequest configChangeListenRequest = (ConfigBatchListenRequest) request;
        String listeningConfigs = configChangeListenRequest.getListeningConfigs();
        Map<String, String> clientMd5Map = MD5Util.getClientMd5Map(listeningConfigs);
        String connectionId = requestMeta.getConnectionId();
        List<String> changedGroups = null;
        String header = request.getHeader("Vipserver-Tag");
    
        for (Map.Entry<String, String> entry : clientMd5Map.entrySet()) {
            String groupKey = entry.getKey();
            String md5 = entry.getValue();
            if (configChangeListenRequest.isListenConfig()) {
                configChangeListenContext.addListen(groupKey, md5, connectionId);
                boolean isUptoDate = ConfigCacheService.isUptodate(groupKey, md5, requestMeta.getClientIp(), header);
                if (!isUptoDate) {
                    if (changedGroups == null) {
                        changedGroups = new LinkedList<>();
                    }
                    changedGroups.add(groupKey);
                }
            } else {
                configChangeListenContext.removeListen(groupKey, connectionId);
            }
        }
        return ConfigChangeBatchListenResponse.buildSucessResponse(changedGroups);
        
    }
    
}
