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

package com.alibaba.nacos.core.plugin.sync;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.core.plugin.condition.ConditionOnStandaloneMode;
import com.alibaba.nacos.core.plugin.storage.PluginPersistenceException;
import com.alibaba.nacos.core.plugin.storage.PluginStatePersistenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Standalone plugin state synchronizer.
 * Only persists state locally without cluster synchronization.
 * Only activated in standalone mode (nacos.standalone=true).
 *
 * @author WangzJi
 * @since 3.2.0
 */
@Component
@Conditional(ConditionOnStandaloneMode.class)
public class StandalonePluginStateSynchronizer implements PluginStateSynchronizer {

    private static final Logger LOGGER = LoggerFactory.getLogger(StandalonePluginStateSynchronizer.class);

    private final PluginStatePersistenceService persistence;

    private final PluginStateApplier applier;

    public StandalonePluginStateSynchronizer(PluginStatePersistenceService persistence,
            PluginStateApplier applier) {
        this.persistence = persistence;
        this.applier = applier;
        LOGGER.info("[StandalonePluginStateSynchronizer] Initialized in standalone mode");
    }

    @Override
    public void syncStateChange(String pluginId, boolean enabled) throws NacosApiException {
        try {
            applier.applyStateChange(pluginId, enabled);
            persistence.saveState(pluginId, enabled);
        } catch (PluginPersistenceException e) {
            throw new NacosApiException(NacosException.SERVER_ERROR, ErrorCode.SERVER_ERROR, e,
                    "Failed to persist plugin state: " + pluginId);
        }
    }

    @Override
    public void syncConfigChange(String pluginId, Map<String, String> config) throws NacosApiException {
        try {
            applier.applyConfigChange(pluginId, config);
            persistence.saveConfig(pluginId, config);
        } catch (PluginPersistenceException e) {
            throw new NacosApiException(NacosException.SERVER_ERROR, ErrorCode.SERVER_ERROR, e,
                    "Failed to persist plugin config: " + pluginId);
        }
    }
}
