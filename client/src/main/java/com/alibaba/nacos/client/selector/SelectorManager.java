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

package com.alibaba.nacos.client.selector;

import com.alibaba.nacos.common.utils.CollectionUtils;
import com.alibaba.nacos.common.utils.ConcurrentHashSet;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Selector Manager.
 *
 * @param <S> the type of selector wrapper
 * @author lideyou
 */
public class SelectorManager<S extends AbstractSelectorWrapper<?, ?, ?>> {
    
    Map<String, Set<S>> selectorMap = new ConcurrentHashMap<>();
    
    /**
     * Add a selectorWrapper to subId.
     *
     * @param subId   subscription id
     * @param wrapper selector wrapper
     */
    public void addSelectorWrapper(String subId, S wrapper) {
        selectorMap.compute(subId, (k, v) -> {
            if (v == null) {
                v = new ConcurrentHashSet<>();
            }
            v.add(wrapper);
            return v;
        });
    }
    
    /**
     * Get all SelectorWrappers by id.
     *
     * @param subId subscription id
     * @return the set of SelectorWrappers
     */
    public Set<S> getSelectorWrappers(String subId) {
        return selectorMap.getOrDefault(subId, Collections.emptySet());
    }
    
    /**
     * Remove a SelectorWrapper by id.
     *
     * @param subId   subscription id
     * @param wrapper selector wrapper
     */
    public void removeSelectorWrapper(String subId, S wrapper) {
        selectorMap.computeIfPresent(subId, (k, v) -> {
            v.remove(wrapper);
            return v.isEmpty() ? null : v;
        });
    }
    
    /**
     * Remove a subscription by id.
     *
     * @param subId subscription id
     */
    public void removeSubscription(String subId) {
        selectorMap.remove(subId);
    }
    
    /**
     * Get all subscriptions.
     *
     * @return all subscriptions
     */
    public Set<String> getSubscriptions() {
        return selectorMap.keySet();
    }
    
    /**
     * Determine whether subId is subscribed.
     *
     * @param subId subscription id
     * @return true if is subscribed
     */
    public boolean isSubscribed(String subId) {
        return CollectionUtils.isNotEmpty(this.getSelectorWrappers(subId));
    }
}
