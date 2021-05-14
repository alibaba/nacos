/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
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

import com.alibaba.nacos.api.config.remote.request.cluster.ConfigChangeClusterSyncRequest;
import com.alibaba.nacos.api.config.remote.response.cluster.ConfigChangeClusterSyncResponse;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.config.server.service.dump.DumpService;
import com.alibaba.nacos.core.remote.RequestHandler;
import com.alibaba.nacos.core.remote.control.TpsControl;
import org.springframework.stereotype.Component;

/**
 * handller to handler config change from other servers.
 *
 * @author liuzunfei
 * @version $Id: ConfigChangeClusterSyncRequestHandler.java, v 0.1 2020年08月11日 4:35 PM liuzunfei Exp $
 */
@Component
public class ConfigChangeClusterSyncRequestHandler
        extends RequestHandler<ConfigChangeClusterSyncRequest, ConfigChangeClusterSyncResponse> {
    
    private final DumpService dumpService;
    
    public ConfigChangeClusterSyncRequestHandler(DumpService dumpService) {
        this.dumpService = dumpService;
    }
    
    @TpsControl(pointName = "ClusterConfigChangeNotify")
    @Override
    public ConfigChangeClusterSyncResponse handle(ConfigChangeClusterSyncRequest configChangeSyncRequest,
            RequestMeta meta) throws NacosException {
        
        if (configChangeSyncRequest.isBeta()) {
            dumpService.dump(configChangeSyncRequest.getDataId(), configChangeSyncRequest.getGroup(),
                    configChangeSyncRequest.getTenant(), configChangeSyncRequest.getLastModified(), meta.getClientIp(),
                    true);
        } else {
            dumpService.dump(configChangeSyncRequest.getDataId(), configChangeSyncRequest.getGroup(),
                    configChangeSyncRequest.getTenant(), configChangeSyncRequest.getLastModified(), meta.getClientIp());
        }
        return new ConfigChangeClusterSyncResponse();
    }
    
}
