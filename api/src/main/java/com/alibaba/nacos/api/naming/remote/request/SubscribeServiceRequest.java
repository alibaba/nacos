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

package com.alibaba.nacos.api.naming.remote.request;

/**
 * Nacos naming subscribe service request.
 *
 * @author xiweng.yy
 */
public class SubscribeServiceRequest extends AbstractNamingRequest {
    
    private boolean subscribe;
    
    private String clusters;
    
    public SubscribeServiceRequest() {
    }
    
    public SubscribeServiceRequest(String namespace, String groupName, String serviceName, String clusters,
            boolean subscribe) {
        super(namespace, serviceName, groupName);
        this.clusters = clusters;
        this.subscribe = subscribe;
    }
    
    public String getClusters() {
        return clusters;
    }
    
    public void setClusters(String clusters) {
        this.clusters = clusters;
    }
    
    public boolean isSubscribe() {
        return subscribe;
    }
    
    public void setSubscribe(boolean subscribe) {
        this.subscribe = subscribe;
    }
}
