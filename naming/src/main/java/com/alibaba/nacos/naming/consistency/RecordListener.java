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

package com.alibaba.nacos.naming.consistency;

import com.alibaba.nacos.naming.pojo.Record;

/**
 * Data listener public interface.
 *
 * @author nacos
 */
public interface RecordListener<T extends Record> {
    
    /**
     * Determine if the listener was registered with this key.
     *
     * @param key candidate key
     * @return true if the listener was registered with this key
     */
    boolean interests(String key);
    
    /**
     * Determine if the listener is to be removed by matching the 'key'.
     *
     * @param key key to match
     * @return true if match success
     */
    boolean matchUnlistenKey(String key);
    
    /**
     * Action to do if data of target key has changed.
     *
     * @param key   target key
     * @param value data of the key
     * @throws Exception exception
     */
    void onChange(String key, T value) throws Exception;
    
    /**
     * Action to do if data of target key has been removed.
     *
     * @param key target key
     * @throws Exception exception
     */
    void onDelete(String key) throws Exception;
}
