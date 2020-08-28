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

package com.alibaba.nacos.naming.consistency.ephemeral.distro.newimpl.component;

import com.alibaba.nacos.common.task.NacosTaskProcessor;
import com.alibaba.nacos.naming.consistency.ephemeral.distro.newimpl.task.delay.DistroDelayTaskExecuteEngine;
import com.alibaba.nacos.naming.consistency.ephemeral.distro.newimpl.task.execute.DistroExecuteWorkersManager;

/**
 * Distro component holder.
 *
 * @author xiweng.yy
 */
public class DistroComponentHolder {
    
    private DistroTransportAgent transportAgent;
    
    private DistroDataStorage dataStorage;
    
    private DistroFailedTaskHandler failedTaskHandler;
    
    private DistroDelayTaskExecuteEngine delayTaskExecuteEngine = new DistroDelayTaskExecuteEngine();
    
    private DistroExecuteWorkersManager executeWorkersManager = new DistroExecuteWorkersManager();
    
    public DistroTransportAgent getTransportAgent() {
        return transportAgent;
    }
    
    public void setTransportAgent(DistroTransportAgent transportAgent) {
        this.transportAgent = transportAgent;
    }
    
    public DistroDataStorage getDataStorage() {
        return dataStorage;
    }
    
    public void setDataStorage(DistroDataStorage dataStorage) {
        this.dataStorage = dataStorage;
    }
    
    public DistroFailedTaskHandler getFailedTaskHandler() {
        return failedTaskHandler;
    }
    
    public void setFailedTaskHandler(DistroFailedTaskHandler failedTaskHandler) {
        this.failedTaskHandler = failedTaskHandler;
    }
    
    public DistroDelayTaskExecuteEngine getDelayTaskExecuteEngine() {
        return delayTaskExecuteEngine;
    }
    
    public void setDelayTaskExecuteEngine(DistroDelayTaskExecuteEngine delayTaskExecuteEngine) {
        this.delayTaskExecuteEngine = delayTaskExecuteEngine;
    }
    
    public DistroExecuteWorkersManager getExecuteWorkersManager() {
        return executeWorkersManager;
    }
    
    public void setExecuteWorkersManager(DistroExecuteWorkersManager executeWorkersManager) {
        this.executeWorkersManager = executeWorkersManager;
    }
    
    public void registerNacosTaskProcessor(Object key, NacosTaskProcessor nacosTaskProcessor) {
        this.delayTaskExecuteEngine.addProcessor(key, nacosTaskProcessor);
    }
}
