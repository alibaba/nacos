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
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.auth.common.ActionTypes;
import com.alibaba.nacos.config.server.auth.ConfigResourceParser;
import com.alibaba.nacos.config.server.service.ConfigCacheService;
import com.alibaba.nacos.config.server.utils.GroupKey2;
import com.alibaba.nacos.core.remote.RequestHandler;
import com.alibaba.nacos.core.utils.StringPool;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * config change listen request handler.
 *
 * @author liuzunfei
 * @version $Id: ConfigChangeListenRequestHandler.java, v 0.1 2020年07月14日 10:11 AM liuzunfei Exp $
 */
@Component
public class ConfigChangeBatchListenRequestHandler
        extends RequestHandler<ConfigBatchListenRequest, ConfigChangeBatchListenResponse> {
    
    final ConfigChangeListenContext configChangeListenContext;
    
    public ConfigChangeBatchListenRequestHandler(ConfigChangeListenContext configChangeListenContext) {
        this.configChangeListenContext = configChangeListenContext;
    }
    
    @Override
    @Secured(action = ActionTypes.READ, parser = ConfigResourceParser.class)
    public ConfigChangeBatchListenResponse handle(ConfigBatchListenRequest request, RequestMeta requestMeta)
            throws NacosException {
        ConfigBatchListenRequest configChangeListenRequest = (ConfigBatchListenRequest) request;
        String connectionId = StringPool.get(requestMeta.getConnectionId());
        List<String> changedGroups = null;
        String header = request.getHeader("Vipserver-Tag");
    
        ConfigChangeBatchListenResponse configChangeBatchListenResponse = new ConfigChangeBatchListenResponse();
        for (ConfigBatchListenRequest.ConfigListenContext listenContext : request.getConfigListenContexts()) {
            String groupKey = GroupKey2
                    .getKey(listenContext.getDataId(), listenContext.getGroup(), listenContext.getTenant());
            groupKey = StringPool.get(groupKey);
    
            String md5 = StringPool.get(listenContext.getMd5());
            
            if (configChangeListenRequest.isListen()) {
                configChangeListenContext.addListen(groupKey, md5, connectionId);
                boolean isUptoDate = ConfigCacheService.isUptodate(groupKey, md5, requestMeta.getClientIp(), header);
                if (!isUptoDate) {
                    configChangeBatchListenResponse.addChangeConfig(listenContext.getDataId(), listenContext.getGroup(),
                            listenContext.getTenant());
                }
            } else {
                configChangeListenContext.removeListen(groupKey, connectionId);
            }
        }
    
        return configChangeBatchListenResponse;
        
    }
    
}
