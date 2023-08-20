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

package com.alibaba.nacos.client.naming.event;

import com.alibaba.nacos.api.naming.listener.EventListener;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.api.naming.selector.NamingSelector;
import com.alibaba.nacos.api.naming.utils.NamingUtils;
import com.alibaba.nacos.client.naming.selector.NamingSelectorWrapper;
import com.alibaba.nacos.client.selector.SelectorManager;
import com.alibaba.nacos.common.JustForTest;
import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.common.notify.listener.Subscriber;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * A subscriber to notify eventListener callback.
 *
 * @author horizonzy
 * @since 1.4.1
 */
public class InstancesChangeNotifier extends Subscriber<InstancesChangeEvent> {

    private final String eventScope;

    private final SelectorManager<NamingSelectorWrapper> selectorManager = new SelectorManager<>();

    @JustForTest
    public InstancesChangeNotifier() {
        this.eventScope = UUID.randomUUID().toString();
    }

    public InstancesChangeNotifier(String eventScope) {
        this.eventScope = eventScope;
    }

    /**
     * register listener.
     *
     * @param groupName   group name
     * @param serviceName serviceName
     * @param selector    selector
     * @param listener    custom listener
     */
    public void registerListener(String groupName, String serviceName, NamingSelector selector, EventListener listener) {
        if (selector == null || listener == null) {
            return;
        }
        String subId = NamingUtils.getGroupedName(serviceName, groupName);
        selectorManager.addSelectorWrapper(subId, wrap(selector, listener));
    }

    /**
     * deregister listener.
     *
     * @param groupName   group name
     * @param serviceName serviceName
     * @param selector    selector
     * @param listener    custom listener
     */
    public void deregisterListener(String groupName, String serviceName, NamingSelector selector, EventListener listener) {
        if (selector == null || listener == null) {
            return;
        }
        String subId = NamingUtils.getGroupedName(serviceName, groupName);
        selectorManager.removeSelectorWrapper(subId, wrap(selector, listener));
    }

    /**
     * check serviceName,groupName is subscribed.
     *
     * @param groupName   group name
     * @param serviceName serviceName
     * @return is serviceName,clusters subscribed
     */
    public boolean isSubscribed(String groupName, String serviceName) {
        String subId = NamingUtils.getGroupedName(serviceName, groupName);
        return selectorManager.isSubscribed(subId);
    }

    public List<ServiceInfo> getSubscribeServices() {
        List<ServiceInfo> serviceInfos = new ArrayList<>();
        for (String key : selectorManager.getSubscriptions()) {
            serviceInfos.add(ServiceInfo.fromKey(key));
        }
        return serviceInfos;
    }

    @Override
    public void onEvent(InstancesChangeEvent event) {
        String subId = NamingUtils.getGroupedName(event.getServiceName(), event.getGroupName());
        Collection<NamingSelectorWrapper> selectorWrappers = selectorManager.getSelectorWrappers(subId);
        for (NamingSelectorWrapper selectorWrapper : selectorWrappers) {
            selectorWrapper.notifyListener(event);
        }
    }

    @Override
    public Class<? extends Event> subscribeType() {
        return InstancesChangeEvent.class;
    }

    @Override
    public boolean scopeMatches(InstancesChangeEvent event) {
        return this.eventScope.equals(event.scope());
    }

    private static NamingSelectorWrapper wrap(NamingSelector selector, EventListener listener) {
        if (selector == null) {
            throw new NullPointerException("Selector cannot be null.");
        }
        if (listener == null) {
            throw new NullPointerException("EventListener cannot be null.");
        }

        return new NamingSelectorWrapper(selector, listener);
    }
}
