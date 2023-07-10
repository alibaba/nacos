/*
 * Copyright 1999-2021 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.sys.module;

import java.util.HashMap;
import java.util.Map;

/**
 * Module state.
 *
 * @author xiweng.yy
 */
public class ModuleState {
    
    private final String moduleName;
    
    private final Map<String, Object> states;
    
    public ModuleState(String moduleName) {
        this.moduleName = moduleName;
        this.states = new HashMap<>();
    }
    
    public String getModuleName() {
        return moduleName;
    }
    
    public ModuleState newState(String stateName, Object stateValue) {
        this.states.put(stateName, stateValue);
        return this;
    }
    
    public Map<String, Object> getStates() {
        return states;
    }
    
    @SuppressWarnings("all")
    public <T> T getState(String stateName, T defaultValue) {
        return (T) states.getOrDefault(stateName, defaultValue);
    }
}
