/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.ai.storage.model;

/**
 * Storage key for AI resource storage abstraction.
 *
 * <p>Similar to Nacos's dataId/group/tenant encapsulation, this is a unified structure for upper layers.
 * For specific implementations, it is an opaque key carrying a provider identifier.</p>
 *
 * @author mosong.lp
 * @since 3.2.0
 */
public class StorageKey {
    
    /**
     * Storage provider identifier, e.g. "nacos_config", "oss".
     * Corresponds to the provider field in Storage JSON.
     */
    private String provider;
    
    /**
     * Internal key used by the specific storage implementation.
     * Opaque to upper layers, e.g.:
     * <ul>
     *   <li>nacos_config: "namespace:group:dataId"</li>
     *   <li>oss: "bucket/objectPath"</li>
     * </ul>
     */
    private String key;
    
    public StorageKey() {
    }
    
    public StorageKey(String provider, String key) {
        this.provider = provider;
        this.key = key;
    }
    
    public String getProvider() {
        return provider;
    }
    
    public void setProvider(String provider) {
        this.provider = provider;
    }
    
    public String getKey() {
        return key;
    }
    
    public void setKey(String key) {
        this.key = key;
    }
    
    @Override
    public String toString() {
        return "StorageKey{provider='" + provider + "', key='" + key + "'}";
    }
}
