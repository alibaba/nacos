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

package com.alibaba.nacos.client.naming.event;

import com.alibaba.nacos.common.notify.Event;

/**
 * Watch notify event, including service change/watch initial.
 *
 * @author tanyongquan
 */
public class FuzzyWatchNotifyEvent extends Event {
    
    private final String scope;
    
    private String serviceKey;
    
    private String pattern;
    
    private final String changedType;
    
    
    public FuzzyWatchNotifyEvent(String scope,
            String pattern, String serviceKey,String changedType){
        this.scope = scope;
        this.pattern=pattern;
      this.serviceKey=serviceKey;
        this.changedType=changedType;
    }
    public static FuzzyWatchNotifyEvent build(String eventScope,
            String pattern, String serviceKey,String changedType) {
        return new FuzzyWatchNotifyEvent(eventScope,pattern, serviceKey, changedType);
    }
    
    public String getPattern() {
        return pattern;
    }
    
    public void setPattern(String pattern) {
        this.pattern = pattern;
    }
    
    public String getChangedType() {
        return changedType;
    }
    
    @Override
    public String scope() {
        return this.scope;
    }
    
    public String getServiceKey() {
        return serviceKey;
    }
    
    public void setServiceKey(String serviceKey) {
        this.serviceKey = serviceKey;
    }
}
