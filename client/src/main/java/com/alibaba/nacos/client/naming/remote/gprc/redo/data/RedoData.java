/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.client.naming.remote.gprc.redo.data;

import java.util.Objects;

/**
 * Nacos naming redo data.
 *
 * @author xiweng.yy
 */
@SuppressWarnings("PMD.AbstractClassShouldStartWithAbstractNamingRule")
public abstract class RedoData<T> {
    
    private final String serviceName;
    
    private final String groupName;
    
    /**
     * Expected states for finally.
     *
     * <ul>
     *     <li>{@code true} meas the cached data expect registered to server finally.</li>
     *     <li>{@code false} means unregistered from server.</li>
     * </ul>
     */
    private volatile boolean expectedRegistered;
    
    /**
     * If {@code true} means cached data has been registered to server successfully.
     */
    private volatile boolean registered;
    
    /**
     * If {@code true} means cached data is unregistering from server.
     */
    private volatile boolean unregistering;
    
    private T data;
    
    protected RedoData(String serviceName, String groupName) {
        this.serviceName = serviceName;
        this.groupName = groupName;
        this.expectedRegistered = true;
    }
    
    public String getServiceName() {
        return serviceName;
    }
    
    public String getGroupName() {
        return groupName;
    }
    
    public void setExpectedRegistered(boolean registered) {
        this.expectedRegistered = registered;
    }
    
    public boolean isExpectedRegistered() {
        return expectedRegistered;
    }
    
    public boolean isRegistered() {
        return registered;
    }
    
    public boolean isUnregistering() {
        return unregistering;
    }
    
    public void setRegistered(boolean registered) {
        this.registered = registered;
    }
    
    public void setUnregistering(boolean unregistering) {
        this.unregistering = unregistering;
    }
    
    public T get() {
        return data;
    }
    
    public void set(T data) {
        this.data = data;
    }
    
    public void registered() {
        this.registered = true;
        this.unregistering = false;
    }
    
    public void unregistered() {
        this.registered = false;
        this.unregistering = true;
    }
    
    public boolean isNeedRedo() {
        return !RedoType.NONE.equals(getRedoType());
    }
    
    /**
     * Get redo type for current redo data without expected state.
     *
     * <ul>
     *     <li>{@code registered=true} & {@code unregistering=false} means data has registered, so redo should not do anything.</li>
     *     <li>{@code registered=true} & {@code unregistering=true} means data has registered and now need unregister.</li>
     *     <li>{@code registered=false} & {@code unregistering=false} means not registered yet, need register again.</li>
     *     <li>{@code registered=false} & {@code unregistering=true} means not registered yet and not continue to register.</li>
     * </ul>
     *
     * @return redo type
     */
    public RedoType getRedoType() {
        if (isRegistered() && !isUnregistering()) {
            return expectedRegistered ? RedoType.NONE : RedoType.UNREGISTER;
        } else if (isRegistered() && isUnregistering()) {
            return RedoType.UNREGISTER;
        } else if (!isRegistered() && !isUnregistering()) {
            return RedoType.REGISTER;
        } else {
            return expectedRegistered ? RedoType.REGISTER : RedoType.REMOVE;
        }
    }
    
    public enum RedoType {
        
        /**
         * Redo register.
         */
        REGISTER,
        
        /**
         * Redo unregister.
         */
        UNREGISTER,
        
        /**
         * Redo nothing.
         */
        NONE,
        
        /**
         * Remove redo data.
         */
        REMOVE;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        RedoData<?> redoData = (RedoData<?>) o;
        return registered == redoData.registered && unregistering == redoData.unregistering && serviceName
                .equals(redoData.serviceName) && groupName.equals(redoData.groupName) && Objects
                .equals(data, redoData.data);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(serviceName, groupName, registered, unregistering, data);
    }
}
