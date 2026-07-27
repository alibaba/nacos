/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.core.plugin;

import com.alibaba.nacos.api.plugin.PluginInitializationPhase;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

/**
 * Standard post-context plugin initializer.
 *
 * @author Nacos
 */
@Component
public class StandardPluginInitializer
    implements PluginInitializer, ApplicationListener<ApplicationReadyEvent> {
    
    private final PluginManager pluginManager;
    
    public StandardPluginInitializer(PluginManager pluginManager) {
        this.pluginManager = pluginManager;
    }
    
    @Override
    public PluginInitializationPhase getInitializationPhase() {
        return PluginInitializationPhase.STANDARD;
    }
    
    @Override
    public void initialize() {
        pluginManager.initialize();
    }
    
    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        initialize();
    }
}
