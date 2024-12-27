/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.console;

import com.alibaba.nacos.core.listener.startup.AbstractNacosStartUp;
import com.alibaba.nacos.core.listener.startup.NacosStartUp;
import org.slf4j.Logger;

/**
 * Nacos Server Web API start up phase.
 *
 * @author xiweng.yy
 */
public class NacosConsoleStartUp extends AbstractNacosStartUp {
    
    public NacosConsoleStartUp() {
        super(NacosStartUp.CONSOLE_START_UP_PHASE);
    }
    
    @Override
    protected String getPhaseNameInStartingInfo() {
        return "Nacos Console";
    }
    
    @Override
    public void logStarted(Logger logger) {
        long endTimestamp = System.currentTimeMillis();
        long startupCost = endTimestamp - getStartTimestamp();
        logger.info("Nacos Console started successfully in {} ms", startupCost);
    }
}
