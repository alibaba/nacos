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

/**
 * Upgrade checker for self-node to judge whether current node is ready to upgrade.
 *
 * @author xiweng.yy
 */
public interface SelfUpgradeChecker {
    
    /**
     * Get the check type of this self upgrade checker.
     *
     * @return type
     */
    String checkType();
    
    /**
     * Judge whether current node is ready to upgrade.
     *
     * @param serviceManager service manager for v1 mode.
     * @param taskEngine double write task engine
     * @return {@code true} if current node is ready to upgrade, otherwise {@code false}
     */
    boolean isReadyToUpgrade(ServiceManager serviceManager, DoubleWriteDelayTaskEngine taskEngine);
}
