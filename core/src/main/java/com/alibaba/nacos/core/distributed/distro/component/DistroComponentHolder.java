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

package com.alibaba.nacos.core.distributed.distro.component;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Distro component holder.
 *
 * @author xiweng.yy
 */
@Component
public class DistroComponentHolder {
    
    private final Map<String, DistroTransportAgent> transportAgentMap = new HashMap<>();
    
    private final Map<String, DistroDataStorage> dataStorageMap = new HashMap<>();
    
    private final Map<String, DistroFailedTaskHandler> failedTaskHandlerMap = new HashMap<>();
    
    private final Map<String, DistroDataProcessor> dataProcessorMap = new HashMap<>();
    
    public DistroTransportAgent findTransportAgent(String type) {
        return transportAgentMap.get(type);
    }
    
    public void registerTransportAgent(String type, DistroTransportAgent transportAgent) {
        transportAgentMap.put(type, transportAgent);
    }
    
    public DistroDataStorage findDataStorage(String type) {
        return dataStorageMap.get(type);
    }
    
    public void registerDataStorage(String type, DistroDataStorage dataStorage) {
        dataStorageMap.put(type, dataStorage);
    }
    
    public Set<String> getDataStorageTypes() {
        return dataStorageMap.keySet();
    }
    
    public DistroFailedTaskHandler findFailedTaskHandler(String type) {
        return failedTaskHandlerMap.get(type);
    }
    
    public void registerFailedTaskHandler(String type, DistroFailedTaskHandler failedTaskHandler) {
        failedTaskHandlerMap.put(type, failedTaskHandler);
    }
    
    public void registerDataProcessor(DistroDataProcessor dataProcessor) {
        dataProcessorMap.putIfAbsent(dataProcessor.processType(), dataProcessor);
    }
    
    public DistroDataProcessor findDataProcessor(String processType) {
        return dataProcessorMap.get(processType);
    }
}
