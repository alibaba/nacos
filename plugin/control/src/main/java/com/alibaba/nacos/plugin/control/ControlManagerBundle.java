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

package com.alibaba.nacos.plugin.control;

import com.alibaba.nacos.plugin.control.connection.ConnectionControlManager;
import com.alibaba.nacos.plugin.control.tps.TpsControlManager;

import java.util.Objects;

/**
 * Immutable connection and TPS control manager bundle.
 *
 * @author Nacos
 */
public final class ControlManagerBundle {
    
    private final ConnectionControlManager connectionControlManager;
    
    private final TpsControlManager tpsControlManager;
    
    /**
     * Create control manager bundle.
     *
     * @param connectionControlManager connection control manager
     * @param tpsControlManager TPS control manager
     */
    public ControlManagerBundle(ConnectionControlManager connectionControlManager,
        TpsControlManager tpsControlManager) {
        this.connectionControlManager = Objects.requireNonNull(connectionControlManager,
            "Connection control manager cannot be null");
        this.tpsControlManager = Objects.requireNonNull(tpsControlManager,
            "TPS control manager cannot be null");
    }
    
    /**
     * Get connection control manager.
     *
     * @return connection control manager
     */
    public ConnectionControlManager getConnectionControlManager() {
        return connectionControlManager;
    }
    
    /**
     * Get TPS control manager.
     *
     * @return TPS control manager
     */
    public TpsControlManager getTpsControlManager() {
        return tpsControlManager;
    }
}
