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

package com.alibaba.nacos.naming.push.v2;

import com.alibaba.nacos.api.naming.pojo.ServiceInfo;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Nacos push data wrapper.
 *
 * @author xiweng.yy
 */
public class PushDataWrapper {
    
    private final ServiceInfo originalData;
    
    private final Map<String, Object> processedDatum;
    
    public PushDataWrapper(ServiceInfo originalData) {
        this.originalData = originalData;
        processedDatum = new HashMap<>(1);
    }
    
    public ServiceInfo getOriginalData() {
        return originalData;
    }
    
    public <T> Optional<T> getProcessedPushData(String key) {
        return Optional.ofNullable((T) processedDatum.get(key));
    }
    
    public void addProcessedPushData(String key, Object processedData) {
        processedDatum.put(key, processedData);
    }
}
