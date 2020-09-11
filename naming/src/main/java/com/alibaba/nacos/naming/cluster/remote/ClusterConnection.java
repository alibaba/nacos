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

package com.alibaba.nacos.naming.cluster.remote;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.remote.RequestCallBack;
import com.alibaba.nacos.api.remote.RequestFuture;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.core.remote.Connection;
import com.alibaba.nacos.core.remote.ConnectionMetaInfo;

import java.util.Map;

/**
 * Cluster connection.
 *
 * @author xiweng.yy
 */
public class ClusterConnection extends Connection {

    public ClusterConnection(ConnectionMetaInfo metaInfo) {
        super(metaInfo);
    }
    
    @Override
    public Response request(Request request, RequestMeta requestMeta) throws NacosException {
        return null;
    }
    
    @Override
    public Response request(Request request, RequestMeta requestMeta, long timeoutMills) throws NacosException {
        return null;
    }
    
    @Override
    public RequestFuture requestFuture(Request request, RequestMeta requestMeta) throws NacosException {
        return null;
    }
    
    @Override
    public void asyncRequest(Request request, RequestMeta requestMeta, RequestCallBack requestCallBack)
            throws NacosException {
        
    }
    
    @Override
    public Map<String, String> getLabels() {
        return null;
    }
    
    @Override
    public void close() {
    
    }
}
