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

import com.alibaba.nacos.common.spi.NacosServiceLoader;
import com.alibaba.nacos.common.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Module State Holder.
 *
 * @author xiweng.yy
 */
public class ModuleStateHolder {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ModuleStateHolder.class);
    
    private static final ModuleStateHolder INSTANCE = new ModuleStateHolder();
    
    private final Map<String, ModuleState> moduleStates;
    
    private ModuleStateHolder() {
        this.moduleStates = new HashMap<>();
        for (ModuleStateBuilder each : NacosServiceLoader.load(ModuleStateBuilder.class)) {
            try {
                ModuleState moduleState = each.build();
                moduleStates.put(moduleState.getModuleName(), moduleState);
            } catch (Exception e) {
                LOGGER.warn("Build ModuleState failed in builder:{}", each.getClass().getCanonicalName(), e);
            }
        }
    }
    
    public static ModuleStateHolder getInstance() {
        return INSTANCE;
    }
    
    public Optional<ModuleState> getModuleState(String moduleName) {
        return Optional.ofNullable(moduleStates.get(moduleName));
    }
    
    public Set<ModuleState> getAllModuleStates() {
        return new HashSet<>(moduleStates.values());
    }
    
    public String getStateValueByName(String moduleName, String stateName) {
        return getStateValueByName(moduleName, stateName, StringUtils.EMPTY);
    }
    
    /**
     * Get State Value by module name and state name.
     *
     * @param moduleName   module name of state
     * @param stateName    state name
     * @param defaultValue default value when can't find module or state
     * @return state value
     */
    public <T> T getStateValueByName(String moduleName, String stateName, T defaultValue) {
        Optional<ModuleState> moduleState = getModuleState(moduleName);
        if (!moduleState.isPresent()) {
            return defaultValue;
        }
        return moduleState.get().getState(stateName, defaultValue);
    }
    
    /**
     * Search State Value by state name one by one.
     *
     * @param stateName    state name
     * @param defaultValue default value when can't find module or state
     * @return state value
     */
    @SuppressWarnings("all")
    public <T> T searchStateValue(String stateName, T defaultValue) {
        T result = null;
        for (ModuleState each : getAllModuleStates()) {
            if (each.getStates().containsKey(stateName)) {
                result = (T) each.getStates().get(stateName);
                break;
            }
        }
        return null == result ? defaultValue : result;
    }
}
