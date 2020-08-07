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

import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.response.PushCallBack;
import com.alibaba.nacos.core.remote.Connection;
import com.alibaba.nacos.core.remote.ConnectionMetaInfo;

import java.util.concurrent.Future;

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
    public boolean sendRequest(Request request, long timeoutMills) throws Exception {
        return false;
    }
    
    @Override
    public boolean sendRequestNoAck(Request request) throws Exception {
        return false;
    }
    
    @Override
    public Future<Boolean> sendRequestWithFuture(Request request) throws Exception {
        return null;
    }
    
    @Override
    public void sendRequestWithCallBack(Request request, PushCallBack callBack) throws Exception {
    
    }
    
    @Override
    public void closeGrapcefully() {
    }
}
