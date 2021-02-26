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

package com.alibaba.nacos.naming.core.v2.client;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Client sync data attributes.
 *
 * @author xiweng.yy
 */
public class ClientSyncAttributes implements Serializable {
    
    private static final long serialVersionUID = -5794675800507288793L;
    
    private Map<String, Object> clientAttributes;
    
    public ClientSyncAttributes() {
        this.clientAttributes = new HashMap<>(1);
    }
    
    public Map<String, Object> getClientAttributes() {
        return clientAttributes;
    }
    
    public void setClientAttributes(Map<String, Object> clientAttributes) {
        this.clientAttributes = clientAttributes;
    }
    
    public void addClientAttribute(String key, Object value) {
        clientAttributes.put(key, value);
    }
    
    /**
     * Get client attribute.
     *
     * @param key attribute key.
     * @param <T> Expected type of attribute.
     * @return client attribute, if not exist or type can't case, return {@code null}
     */
    public <T> T getClientAttribute(String key) {
        try {
            return (T) clientAttributes.get(key);
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Get client attribute.
     *
     * @param key attribute key.
     * @param <T> Expected type of attribute.
     * @param defaultValue default value when not exist or type can't case
     * @return client attribute, if not exist or type can't case, return defaultValue
     */
    public <T> T getClientAttribute(String key, T defaultValue) {
        Object result = clientAttributes.get(key);
        if (null == result) {
            return defaultValue;
        }
        try {
            return (T) result;
        } catch (Exception e) {
            return defaultValue;
        }
    }
}
