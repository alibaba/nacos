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

package com.alibaba.nacos.naming.push.v2.hook;

import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.pojo.Subscriber;

/**
 * Nacos naming push result.
 *
 * @author xiweng.yy
 */
public class PushResult {
    
    private final boolean pushSuccess;
    
    private final String subscribeClientId;
    
    private final Service service;
    
    private final ServiceInfo data;
    
    private final Subscriber subscriber;
    
    private final long networkCost;
    
    private final long allCost;
    
    private final long sla;
    
    private final Throwable exception;
    
    private final boolean isPushToAll;
    
    private PushResult(boolean pushSuccess, String subscribeClientId, Service service, ServiceInfo data,
            Subscriber subscriber, long networkCost, long allCost, long sla, Throwable exception, boolean isPushToAll) {
        this.pushSuccess = pushSuccess;
        this.subscribeClientId = subscribeClientId;
        this.service = service;
        this.data = data;
        this.subscriber = subscriber;
        this.networkCost = networkCost;
        this.allCost = allCost;
        this.sla = sla;
        this.exception = exception;
        this.isPushToAll = isPushToAll;
    }
    
    public static PushResult pushSuccess(Service service, String subscribeClientId, ServiceInfo data,
            Subscriber subscriber, long networkCost, long allCost, long sla, boolean isPushToAll) {
        return new PushResult(true, subscribeClientId, service, data, subscriber, networkCost, allCost, sla, null,
                isPushToAll);
    }
    
    public static PushResult pushFailed(Service service, String subscribeClientId, ServiceInfo data,
            Subscriber subscriber, long allCost, Throwable exception, boolean isPushToAll) {
        return new PushResult(false, subscribeClientId, service, data, subscriber, -1, allCost, -1, exception,
                isPushToAll);
    }
    
    public boolean isPushSuccess() {
        return pushSuccess;
    }
    
    public String getSubscribeClientId() {
        return subscribeClientId;
    }
    
    public Service getService() {
        return service;
    }
    
    public ServiceInfo getData() {
        return data;
    }
    
    public Subscriber getSubscriber() {
        return subscriber;
    }
    
    public long getNetworkCost() {
        return networkCost;
    }
    
    public long getAllCost() {
        return allCost;
    }
    
    public long getSla() {
        return sla;
    }
    
    public Throwable getException() {
        return exception;
    }
}
