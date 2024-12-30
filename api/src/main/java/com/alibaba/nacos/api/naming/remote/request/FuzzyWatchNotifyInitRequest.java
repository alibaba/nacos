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
 * Nacos fuzzy watch initial notify request, use it when init a watch request, push service by batch.
 *
 * @author tanyongquan
 */
public class FuzzyWatchNotifyInitRequest extends AbstractFuzzyWatchNotifyRequest {
    
    private String pattern;
    
    private Collection<String> servicesName;
    
    public FuzzyWatchNotifyInitRequest() {
    }
    
    private FuzzyWatchNotifyInitRequest(String namespace, String pattern, String serviceChangedType, Collection<String> servicesName) {
        super(namespace, serviceChangedType);
        this.servicesName = servicesName;
        this.pattern = pattern;
    }
    
    public static FuzzyWatchNotifyInitRequest buildInitRequest(String namespace, String pattern, Collection<String> servicesName) {
        return new FuzzyWatchNotifyInitRequest(namespace, pattern, Constants.FUZZY_WATCH_INIT_NOTIFY, servicesName);
    }
    
    public static FuzzyWatchNotifyInitRequest buildInitFinishRequest(String namespace, String pattern) {
        return new FuzzyWatchNotifyInitRequest(namespace, pattern, Constants.FINISH_FUZZY_WATCH_INIT_NOTIFY, new HashSet<>(1));
    }
    
    public String getPattern() {
        return pattern;
    }
    
    public void setPattern(String pattern) {
        this.pattern = pattern;
    }
    
    public Collection<String> getServicesName() {
        return servicesName;
    }
    
    public void setServicesName(Collection<String> servicesName) {
        this.servicesName = servicesName;
    }
}
