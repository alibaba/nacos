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

package com.alibaba.nacos.api.config.listener;

import java.util.Objects;
import java.util.UUID;

/**
 * AbstractFuzzyListenListener is an abstract class that provides basic functionality for listening to fuzzy
 * configuration changes in Nacos.
 *
 * @author stone-98
 * @date 2024/3/4
 */
public abstract class AbstractFuzzyWatchListener extends AbstractListener {
    
    /**
     * Unique identifier for the listener.
     */
    String uuid= UUID.randomUUID().toString();
    
    /**
     * Get the UUID (Unique Identifier) of the listener.
     *
     * @return The UUID of the listener
     */
    public String getUuid() {
        return uuid;
    }
    
    /**
     * Callback method invoked when a fuzzy configuration change event occurs.
     *
     * @param event The fuzzy configuration change event
     */
    public abstract void onEvent(ConfigFuzzyWatchChangeEvent event);
    
    /**
     * Receive the configuration information. This method is overridden but does nothing in this abstract class.
     *
     * @param configInfo The configuration information
     */
    @Override
    public final void receiveConfigInfo(String configInfo) {
        // Do nothing by default
    }
    
    /**
     * Compute the hash code for this listener based on its UUID.
     *
     * @return The hash code value for this listener
     */
    @Override
    public int hashCode() {
        return Objects.hashCode(uuid);
    }
    
    /**
     * Compare this listener to the specified object for equality. Two listeners are considered equal if they have the
     * same UUID.
     *
     * @param o The object to compare to
     * @return true if the specified object is equal to this listener, false otherwise
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AbstractFuzzyWatchListener that = (AbstractFuzzyWatchListener) o;
        return Objects.equals(uuid, that.uuid);
    }
}
