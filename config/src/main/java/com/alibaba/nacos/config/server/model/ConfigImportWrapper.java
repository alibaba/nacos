/*
 * Copyright 1999-$toady.year Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.config.server.model;

import com.alibaba.nacos.config.server.enums.ConfigImportResEnum;

/**
 * Import configuration wrapper.
 *
 * @author Nacos
 */
public class ConfigImportWrapper {
    
    private SameConfigPolicy sameConfigPolicy;
    
    private ConfigImportResEnum configImportResEnum;
    
    public ConfigImportWrapper() {
    }
    
    public ConfigImportWrapper(ConfigImportResEnum configImportResEnum, SameConfigPolicy sameConfigPolicy) {
        this.configImportResEnum = configImportResEnum;
        this.sameConfigPolicy = sameConfigPolicy;
    }
    
    public ConfigImportResEnum getConfigImportResEnum() {
        return configImportResEnum;
    }
    
    public void setConfigImportResEnum(ConfigImportResEnum configImportResEnum) {
        this.configImportResEnum = configImportResEnum;
    }
    
    public SameConfigPolicy getSameConfigPolicy() {
        return sameConfigPolicy;
    }
    
    public void setSameConfigPolicy(SameConfigPolicy sameConfigPolicy) {
        this.sameConfigPolicy = sameConfigPolicy;
    }
}