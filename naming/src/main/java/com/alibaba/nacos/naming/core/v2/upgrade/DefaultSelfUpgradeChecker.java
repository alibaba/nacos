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

package com.alibaba.nacos.naming.core.v2.upgrade;

import com.alibaba.nacos.naming.core.ServiceManager;
import com.alibaba.nacos.naming.core.v2.upgrade.doublewrite.delay.DoubleWriteDelayTaskEngine;
import com.alibaba.nacos.naming.monitor.MetricsMonitor;

/**
 * Default upgrade checker for self node.
 *
 * @author xiweng.yy
 */
public class DefaultSelfUpgradeChecker implements SelfUpgradeChecker {
    
    private static final String DEFAULT = "default";
    
    @Override
    public String checkType() {
        return DEFAULT;
    }
    
    @Override
    public boolean isReadyToUpgrade(ServiceManager serviceManager, DoubleWriteDelayTaskEngine taskEngine) {
        return checkServiceAndInstanceNumber(serviceManager) && checkDoubleWriteStatus(taskEngine);
    }
    
    private boolean checkServiceAndInstanceNumber(ServiceManager serviceManager) {
        boolean result = serviceManager.getServiceCount() == MetricsMonitor.getDomCountMonitor().get();
        result &= serviceManager.getInstanceCount() == MetricsMonitor.getIpCountMonitor().get();
        return result;
    }
    
    private boolean checkDoubleWriteStatus(DoubleWriteDelayTaskEngine taskEngine) {
        return taskEngine.isEmpty();
    }
}
