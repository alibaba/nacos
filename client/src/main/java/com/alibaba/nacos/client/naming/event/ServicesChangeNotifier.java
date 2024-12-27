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

import com.alibaba.nacos.api.naming.listener.AbstractFuzzyWatchEventListener;
import com.alibaba.nacos.api.naming.utils.NamingUtils;
import com.alibaba.nacos.common.JustForTest;
import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.common.notify.listener.Subscriber;
import com.alibaba.nacos.common.utils.CollectionUtils;
import com.alibaba.nacos.common.utils.ConcurrentHashSet;
import com.alibaba.nacos.common.utils.StringUtils;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A watcher to notify service change event Listener callback.
 *
 * @author tanyongquan
 */
public class ServicesChangeNotifier extends Subscriber<FuzzyWatchNotifyEvent> {
    
    private final String eventScope;
    
    @JustForTest
    public ServicesChangeNotifier() {
        this.eventScope = UUID.randomUUID().toString();
    }
    
    public ServicesChangeNotifier(String eventScope) {
        this.eventScope = eventScope;
    }
    
    /**
     * The content of map is {pattern -> Set[Listener]}.
     */
    private final Map<String, ConcurrentHashSet<AbstractFuzzyWatchEventListener>> fuzzyWatchListenerMap = new ConcurrentHashMap<>();
    
    /** register fuzzy watch listener.
     *  This listener responds to changes of the services (not the instance's change).
     *
     * @param serviceNamePattern service name pattern
     * @param groupNamePattern group name pattern
     * @param listener custom listener
     */
    public void registerFuzzyWatchListener(String serviceNamePattern, String groupNamePattern, AbstractFuzzyWatchEventListener listener) {
        String key = NamingUtils.getGroupedName(serviceNamePattern, groupNamePattern);
        Set<AbstractFuzzyWatchEventListener> eventListeners = fuzzyWatchListenerMap.computeIfAbsent(key, keyInner -> new ConcurrentHashSet<>());
        eventListeners.add(listener);
    }
    
    /** remove fuzzy watch listener.
     *
     * @param serviceNamePattern service name pattern
     * @param groupNamePattern group name pattern
     */
    public void deregisterFuzzyWatchListener(String serviceNamePattern, String groupNamePattern, AbstractFuzzyWatchEventListener listener) {
        String key = NamingUtils.getGroupedName(serviceNamePattern, groupNamePattern);
        ConcurrentHashSet<AbstractFuzzyWatchEventListener> eventListeners = fuzzyWatchListenerMap.get(key);
        if (eventListeners == null) {
            return;
        }
        eventListeners.remove(listener);
        if (CollectionUtils.isEmpty(eventListeners)) {
            fuzzyWatchListenerMap.remove(key);
        }
    }
    
    /**
     * check pattern is watched.
     *
     * @param serviceNamePattern service name pattern
     * @param groupNamePattern group name pattern
     * @return is pattern watched
     */
    public boolean isWatched(String serviceNamePattern, String groupNamePattern) {
        String key = NamingUtils.getGroupedName(serviceNamePattern, groupNamePattern);
        ConcurrentHashSet<AbstractFuzzyWatchEventListener> eventListeners = fuzzyWatchListenerMap.get(key);
        return CollectionUtils.isNotEmpty(eventListeners);
    }
    
    /**
     * receive fuzzy watch notify (fuzzy watch init or service change) from nacos server, notify all listener watch this pattern.
     * If the event contains a UUID, then the event is used to notify the specified Listener when there are
     * multiple watches for a particular pattern.
     *
     * @param event watch notify event
     */
    @Override
    public void onEvent(FuzzyWatchNotifyEvent event) {
        String uuid = event.getUuid();
        Collection<AbstractFuzzyWatchEventListener> listeners = fuzzyWatchListenerMap.get(event.getPattern());
        final com.alibaba.nacos.api.naming.listener.FuzzyWatchNotifyEvent fuzzyWatchNotifyEvent = transferToWatchNotifyEvent(event);
        for (AbstractFuzzyWatchEventListener each : listeners) {
            // notify all listener watch this pattern
            if (StringUtils.isEmpty(uuid)) {
                if (each.getExecutor() != null) {
                    each.getExecutor().execute(() -> each.onEvent(fuzzyWatchNotifyEvent));
                } else {
                    each.onEvent(fuzzyWatchNotifyEvent);
                }
            } else if (uuid.equals(each.getUuid())) {
                // notify specific listener by uuid, use in duplicate watch a same pattern
                if (each.getExecutor() != null) {
                    each.getExecutor().execute(() -> each.onEvent(fuzzyWatchNotifyEvent));
                } else {
                    each.onEvent(fuzzyWatchNotifyEvent);
                }
                return;
            }
        }
    }
    
    private com.alibaba.nacos.api.naming.listener.FuzzyWatchNotifyEvent transferToWatchNotifyEvent(
            FuzzyWatchNotifyEvent fuzzyWatchNotifyEvent) {
        return new com.alibaba.nacos.api.naming.listener.FuzzyWatchNotifyEvent(fuzzyWatchNotifyEvent.getChangedService(),
                fuzzyWatchNotifyEvent.getServiceChangedType());
    }
    
    @Override
    public Class<? extends Event> subscribeType() {
        return FuzzyWatchNotifyEvent.class;
    }
    
    @Override
    public boolean scopeMatches(FuzzyWatchNotifyEvent event) {
        return this.eventScope.equals(event.scope());
    }
}
