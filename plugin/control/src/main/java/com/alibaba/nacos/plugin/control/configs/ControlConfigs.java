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

package com.alibaba.nacos.plugin.control.configs;

import com.alibaba.nacos.common.spi.NacosServiceLoader;

import java.util.Collection;

/**
 * control configs params.
 *
 * @author shiyiyue
 */
public class ControlConfigs {
    
    private static volatile ControlConfigs instance = null;
    
    public static ControlConfigs getInstance() {
        if (instance == null) {
            synchronized (ControlConfigs.class) {
                if (instance == null) {
                    instance = new ControlConfigs();
                    Collection<ControlConfigsInitializer> load = NacosServiceLoader
                            .load(ControlConfigsInitializer.class);
                    for (ControlConfigsInitializer controlConfigsInitializer : load) {
                        controlConfigsInitializer.initialize(instance);
                    }
                }
            }
        }
        
        return instance;
    }
    
    public static void setInstance(ControlConfigs instance) {
        ControlConfigs.instance = instance;
    }
    
    private String connectionRuntimeEjector = "nacos";
    
    private String ruleExternalStorage = "";
    
    private String localRuleStorageBaseDir = "";
    
    private String controlManagerType = "";
    
    public String getRuleExternalStorage() {
        return ruleExternalStorage;
    }
    
    public void setRuleExternalStorage(String ruleExternalStorage) {
        this.ruleExternalStorage = ruleExternalStorage;
    }
    
    public String getConnectionRuntimeEjector() {
        return connectionRuntimeEjector;
    }
    
    public void setConnectionRuntimeEjector(String connectionRuntimeEjector) {
        this.connectionRuntimeEjector = connectionRuntimeEjector;
    }
    
    public String getLocalRuleStorageBaseDir() {
        return localRuleStorageBaseDir;
    }
    
    public void setLocalRuleStorageBaseDir(String localRuleStorageBaseDir) {
        this.localRuleStorageBaseDir = localRuleStorageBaseDir;
    }
    
    public String getControlManagerType() {
        return controlManagerType;
    }
    
    public void setControlManagerType(String controlManagerType) {
        this.controlManagerType = controlManagerType;
    }
}
