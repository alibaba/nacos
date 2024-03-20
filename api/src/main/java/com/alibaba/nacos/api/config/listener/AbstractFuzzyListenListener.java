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

/**
 * AbstractFuzzyListenListener is an abstract class that provides basic functionality for listening to fuzzy
 * configuration changes in Nacos.
 *
 * @author stone-98
 * @date 2024/3/4
 */
public abstract class AbstractFuzzyListenListener extends AbstractListener {
    
    /**
     * Unique identifier for the listener.
     */
    private String uuid;
    
    /**
     * Get the UUID (Unique Identifier) of the listener.
     *
     * @return The UUID of the listener
     */
    public String getUuid() {
        return uuid;
    }
    
    /**
     * Set the UUID (Unique Identifier) of the listener.
     *
     * @param uuid The UUID to be set
     */
    public void setUuid(String uuid) {
        this.uuid = uuid;
    }
    
    /**
     * Callback method invoked when a fuzzy configuration change event occurs.
     *
     * @param event The fuzzy configuration change event
     */
    public abstract void onEvent(FuzzyListenConfigChangeEvent event);
    
    /**
     * Receive the configuration information. This method is overridden but does nothing in this abstract class.
     *
     * @param configInfo The configuration information
     */
    @Override
    public void receiveConfigInfo(String configInfo) {
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
        AbstractFuzzyListenListener that = (AbstractFuzzyListenListener) o;
        return Objects.equals(uuid, that.uuid);
    }
}
