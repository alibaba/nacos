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

package com.alibaba.nacos.naming.cluster.remote.request;

import com.alibaba.nacos.api.naming.remote.NamingRemoteConstants;
import com.alibaba.nacos.api.naming.remote.request.InstanceRequest;
import com.alibaba.nacos.api.remote.request.RequestMeta;

/**
 * Forward instance request.
 *
 * @author xiweng.yy
 */
public class ForwardInstanceRequest extends AbstractClusterRequest {
    
    private InstanceRequest instanceRequest;
    
    private RequestMeta sourceRequestMeta;
    
    public ForwardInstanceRequest() {
    }
    
    public ForwardInstanceRequest(InstanceRequest instanceRequest, RequestMeta sourceRequestMeta) {
        this.instanceRequest = instanceRequest;
        this.sourceRequestMeta = sourceRequestMeta;
    }
    
    public InstanceRequest getInstanceRequest() {
        return instanceRequest;
    }
    
    public void setInstanceRequest(InstanceRequest instanceRequest) {
        this.instanceRequest = instanceRequest;
    }
    
    public RequestMeta getSourceRequestMeta() {
        return sourceRequestMeta;
    }
    
    public void setSourceRequestMeta(RequestMeta sourceRequestMeta) {
        this.sourceRequestMeta = sourceRequestMeta;
    }
}
