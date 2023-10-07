/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

import com.alibaba.nacos.api.common.Constants;

import java.util.Collection;
import java.util.HashSet;

/**
 * Nacos watch initial notify request, use it when init a watch request, push service by batch.
 *
 * @author tanyongquan
 */
public class WatchNotifyInitRequest extends AbstractWatchNotifyRequest {
    
    private Collection<String> servicesName;
    
    public WatchNotifyInitRequest() {
    }
    
    private WatchNotifyInitRequest(String namespace, String pattern, String serviceChangedType, Collection<String> servicesName) {
        super(namespace, pattern, serviceChangedType);
        this.servicesName = servicesName;
    }
    
    public static WatchNotifyInitRequest buildInitRequest(String namespace, String pattern, Collection<String> servicesName) {
        return new WatchNotifyInitRequest(namespace, pattern, Constants.ServiceChangedType.WATCH_INITIAL_MATCH, servicesName);
    }
    
    public static WatchNotifyInitRequest buildInitFinishRequest(String namespace, String pattern) {
        return new WatchNotifyInitRequest(namespace, pattern, Constants.ServiceChangedType.FINISH_WATCH_INIT, new HashSet<>(1));
    }
    
    public Collection<String> getServicesName() {
        return servicesName;
    }
    
    public void setServicesName(Collection<String> servicesName) {
        this.servicesName = servicesName;
    }
}
