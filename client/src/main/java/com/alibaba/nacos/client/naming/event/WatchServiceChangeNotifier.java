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

import com.alibaba.nacos.api.naming.listener.AbstractWatchEventListener;
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
 * A watcher to notify watch event Listener callback.
 *
 * @author tanyongquan
 */
public class WatchServiceChangeNotifier extends Subscriber<WatchNotifyEvent> {
    
    private final String eventScope;
    
    @JustForTest
    public WatchServiceChangeNotifier() {
        this.eventScope = UUID.randomUUID().toString();
    }
    
    public WatchServiceChangeNotifier(String eventScope) {
        this.eventScope = eventScope;
    }
    
    /**
     * pattern -> Set[Listener].
     */
    private final Map<String, ConcurrentHashSet<AbstractWatchEventListener>> watchListenerMap = new ConcurrentHashMap<>();
    
    /** register watch listener.
     *  This listener responds to changes of the services (not the instance).
     *
     * @param serviceNamePattern service name pattern
     * @param groupNamePattern group name pattern
     * @param listener custom listener
     */
    public void registerWatchListener(String serviceNamePattern, String groupNamePattern, AbstractWatchEventListener listener) {
        String key = NamingUtils.getGroupedName(serviceNamePattern, groupNamePattern);
        Set<AbstractWatchEventListener> eventListeners = watchListenerMap.computeIfAbsent(key, keyInner -> new ConcurrentHashSet<>());
        eventListeners.add(listener);
    }
    
    /** remove watch listener.
     *
     * @param serviceNamePattern service name pattern
     * @param groupNamePattern group name pattern
     */
    public void deregisterWatchListener(String serviceNamePattern, String groupNamePattern, AbstractWatchEventListener listener) {
        String key = NamingUtils.getGroupedName(serviceNamePattern, groupNamePattern);
        ConcurrentHashSet<AbstractWatchEventListener> eventListeners = watchListenerMap.get(key);
        if (eventListeners == null) {
            return;
        }
        eventListeners.remove(listener);
        if (CollectionUtils.isEmpty(eventListeners)) {
            watchListenerMap.remove(key);
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
        ConcurrentHashSet<AbstractWatchEventListener> eventListeners = watchListenerMap.get(key);
        return CollectionUtils.isNotEmpty(eventListeners);
    }
    
    /**
     * receive watch notify (watch init or service change) from nacos server, notify all listener watch this pattern.
     * If the event contains a UUID, then the event is used to notify the specified Listener when there are
     * multiple watches for a particular Pattern
     *
     * @param event watch notify event
     */
    @Override
    public void onEvent(WatchNotifyEvent event) {
        String uuid = event.getUuid();
        Collection<AbstractWatchEventListener> listeners = watchListenerMap.get(event.getPattern());
        final com.alibaba.nacos.api.naming.listener.WatchNotifyEvent watchNotifyEvent = transferToWatchNotifyEvent(event);
        for (AbstractWatchEventListener each : listeners) {
            // notify all listener watch this pattern
            if (StringUtils.isEmpty(uuid)) {
                if (each.getExecutor() != null) {
                    each.getExecutor().execute(() -> each.onEvent(watchNotifyEvent));
                } else {
                    each.onEvent(watchNotifyEvent);
                }
            } else if (uuid.equals(each.getUuid())) {
                // notify specific listener by uuid, use in duplicate watch a same pattern
                if (each.getExecutor() != null) {
                    each.getExecutor().execute(() -> each.onEvent(watchNotifyEvent));
                } else {
                    each.onEvent(watchNotifyEvent);
                }
                return;
            }
        }
    }
    
    private com.alibaba.nacos.api.naming.listener.WatchNotifyEvent transferToWatchNotifyEvent(
            WatchNotifyEvent watchNotifyEvent) {
        return new com.alibaba.nacos.api.naming.listener.WatchNotifyEvent(watchNotifyEvent.getChangedService(),
                watchNotifyEvent.getServiceChangedType());
    }
    
    @Override
    public Class<? extends Event> subscribeType() {
        return WatchNotifyEvent.class;
    }
    
    @Override
    public boolean scopeMatches(WatchNotifyEvent event) {
        return this.eventScope.equals(event.scope());
    }
}
