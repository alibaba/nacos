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

package com.alibaba.nacos.plugin.control.spi;

import com.alibaba.nacos.plugin.control.connection.ConnectionControlManager;
import com.alibaba.nacos.plugin.control.tps.TpsControlManager;

/**
 * Nacos control plugin manager builder SPI.
 *
 * @author xiweng.yy
 */
public interface ControlManagerBuilder {
    
    /**
     * Get plugin name.
     *
     * @return name of plugin
     */
    String getName();
    
    /**
     * Build {@link ConnectionControlManager} implementation for current plugin.
     *
     * @return ConnectionControlManager implementation
     */
    ConnectionControlManager buildConnectionControlManager();
    
    /**
     * Build {@link TpsControlManager} implementation for current plugin.
     *
     * @return TpsControlManager implementation
     */
    TpsControlManager buildTpsControlManager();
}
