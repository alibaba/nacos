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
    }
    
    public String getServiceName() {
        return serviceName;
    }
    
    public String getGroupName() {
        return groupName;
    }
    
    public boolean isRegistered() {
        return registered;
    }
    
    public void setRegistered(boolean registered) {
        this.registered = registered;
    }
    
    public boolean isUnregistering() {
        return unregistering;
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
    
    /**
     * Get redo type for current redo data.
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
            return RedoType.NONE;
        } else if (isRegistered() && isUnregistering()) {
            return RedoType.UNREGISTER;
        } else if (!isRegistered() && !isUnregistering()) {
            return RedoType.REGISTER;
        } else {
            return RedoType.REMOVE;
        }
    }
    
    public boolean isNeedRedo() {
        return !RedoType.NONE.equals(getRedoType());
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
}
