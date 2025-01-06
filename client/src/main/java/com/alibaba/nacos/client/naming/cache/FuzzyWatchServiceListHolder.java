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

package com.alibaba.nacos.client.naming.cache;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.naming.remote.request.AbstractFuzzyWatchNotifyRequest;
import com.alibaba.nacos.api.naming.remote.request.FuzzyWatchNotifyChangeRequest;
import com.alibaba.nacos.api.naming.remote.request.FuzzyWatchNotifyInitRequest;
import com.alibaba.nacos.api.naming.utils.NamingUtils;
import com.alibaba.nacos.client.env.NacosClientProperties;
import com.alibaba.nacos.client.naming.event.FuzzyWatchNotifyEvent;
import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.notify.listener.Subscriber;
import com.alibaba.nacos.common.utils.FuzzyGroupKeyPattern;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Naming client fuzzy watch service list holder.
 *
 * @author tanyongquan
 */
public class FuzzyWatchServiceListHolder extends Subscriber<FuzzyWatchNotifyEvent> {
    
    private String notifierEventScope;
    
    /**
     * The contents of {@code patternMatchMap} are Map{pattern -> Set[matched services]}.
     */
    private Map<String, NamingFuzzyWatchContext> fuzzyMatchContextMap = new ConcurrentHashMap<>();
    
    public FuzzyWatchServiceListHolder(String notifierEventScope, NacosClientProperties properties) {
        this.notifierEventScope = notifierEventScope;
    }
    
    public NamingFuzzyWatchContext patternWatched(String groupKeyPattern){
        return fuzzyMatchContextMap.get(groupKeyPattern);
    }
    
    public NamingFuzzyWatchContext initFuzzyWatchContextIfNeed(String groupKeyPattern){
        if (fuzzyMatchContextMap.containsKey(groupKeyPattern)){
            return fuzzyMatchContextMap.get(groupKeyPattern);
        }else {
            return fuzzyMatchContextMap.putIfAbsent(groupKeyPattern,new NamingFuzzyWatchContext(notifierEventScope,groupKeyPattern));
        }
    }

    public synchronized void removePatternMatchCache(String groupkeyPattern) {
        NamingFuzzyWatchContext namingFuzzyWatchContext = fuzzyMatchContextMap.get(groupkeyPattern);
        if (namingFuzzyWatchContext==null){
            return;
        }
        if (namingFuzzyWatchContext.isDiscard()&&namingFuzzyWatchContext.getNamingFuzzyWatchers().isEmpty()){
            fuzzyMatchContextMap.remove(groupkeyPattern)
        }
    }
    
    public Map<String, NamingFuzzyWatchContext> getFuzzyMatchContextMap() {
        return fuzzyMatchContextMap;
    }
    
    @Override
    public void onEvent(FuzzyWatchNotifyEvent event) {
       if (!event.scope().equals(notifierEventScope)){
           return;
       }
        String changedType = event.getChangedType();
        String serviceKey = event.getServiceKey();
        String pattern = event.getPattern();
        NamingFuzzyWatchContext namingFuzzyWatchContext = fuzzyMatchContextMap.get(pattern);
        if (fuzzyMatchContextMap!=null){
            namingFuzzyWatchContext.notifyFuzzyWatchers(serviceKey,changedType);
        }
    
    }
    
    @Override
    public Class<? extends Event> subscribeType() {
        return FuzzyWatchNotifyEvent.class;
    }
    
    public String getNotifierEventScope() {
        return notifierEventScope;
    }
}
