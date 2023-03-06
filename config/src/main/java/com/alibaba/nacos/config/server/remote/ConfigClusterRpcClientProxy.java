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
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.remote.RequestCallBack;
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.remote.ClusterRpcClientProxy;
import org.springframework.stereotype.Service;

/**
 * ConfigClusterRpcClientProxy.
 *
 * @author liuzunfei
 * @version $Id: ConfigClusterRpcClientProxy.java, v 0.1 2020年08月11日 4:28 PM liuzunfei Exp $
 */
@Service
public class ConfigClusterRpcClientProxy {
    
    final ClusterRpcClientProxy clusterRpcClientProxy;
    
    public ConfigClusterRpcClientProxy(ClusterRpcClientProxy clusterRpcClientProxy) {
        this.clusterRpcClientProxy = clusterRpcClientProxy;
    }
    
    /**
     * sync config change request.
     *
     * @param member  member of server.
     * @param request request of config change sync.
     * @param callBack callBack of config change sync.
     * @throws NacosException exception.
     */
    public void syncConfigChange(Member member, ConfigChangeClusterSyncRequest request, RequestCallBack callBack)
            throws NacosException {
    
        clusterRpcClientProxy.asyncRequest(member, request, callBack);
        
    }
}
