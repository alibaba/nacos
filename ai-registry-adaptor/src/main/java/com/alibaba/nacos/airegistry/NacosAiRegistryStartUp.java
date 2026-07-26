/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.airegistry;

import com.alibaba.nacos.core.listener.startup.AbstractNacosStartUp;
import com.alibaba.nacos.core.listener.startup.NacosStartUp;
import org.slf4j.Logger;

/**
 * NacosAiRegistryStartUp.
 * @author xinluo
 */
public class NacosAiRegistryStartUp extends AbstractNacosStartUp {
    
    public NacosAiRegistryStartUp() {
        super(NacosStartUp.AI_REGISTRY_START_UP_PHASE);
    }
    
    /**
     * Get phase name in starting info.
     *
     * @return phase name
     */
    @Override
    protected String getPhaseNameInStartingInfo() {
        return "Nacos AI Registry";
    }
    
    /**
     * Log started info for current Nacos Server.
     *
     * @param logger logger for print info
     */
    @Override
    public void logStarted(Logger logger) {
        logger.info("Nacos AI Registry Started.");
    }
}
