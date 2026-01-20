/*
 * Copyright 1999-2024 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.core.plugin.model;

import java.io.Serializable;
import java.util.Map;

/**
 * Plugin state snapshot for Raft recovery.
 * Contains all plugin states and configurations for snapshot save/load.
 *
 * @author WangzJi
 * @since 3.2.0
 */
public class PluginStateSnapshot implements Serializable {

    private static final long serialVersionUID = 1L;

    private Map<String, Boolean> states;

    private Map<String, Map<String, String>> configs;

    public PluginStateSnapshot() {
    }

    public Map<String, Boolean> getStates() {
        return states;
    }

    public void setStates(Map<String, Boolean> states) {
        this.states = states;
    }

    public Map<String, Map<String, String>> getConfigs() {
        return configs;
    }

    public void setConfigs(Map<String, Map<String, String>> configs) {
        this.configs = configs;
    }
}
