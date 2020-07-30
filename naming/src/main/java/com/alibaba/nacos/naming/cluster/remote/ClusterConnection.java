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

import com.alibaba.nacos.api.remote.connection.Connection;
import com.alibaba.nacos.api.remote.connection.ConnectionMetaInfo;
import com.alibaba.nacos.api.remote.response.Response;

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
    public void sendResponse(Response reponse) {
    }
    
    @Override
    public void closeGrapcefully() {
    }
}
