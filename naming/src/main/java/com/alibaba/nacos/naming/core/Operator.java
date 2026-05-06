/*
 * Copyright 1999-$toady.year Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.naming.core;

import com.alibaba.nacos.naming.misc.SwitchDomain;
import com.alibaba.nacos.naming.model.vo.MetricsInfoVo;

/**
 * Operator service.
 * @author Matthew
 */
public interface Operator {
    
    /**
     * Retrieves the current state of system switches.
     *
     * @return A {@link SwitchDomain} object containing the current switch configurations.
     */
    SwitchDomain switches();
    
    /**
     * Updates a specific system switch with a new value.
     *
     * @param entry The name of the switch entry to update.
     * @param value The new value to set for the switch.
     * @param debug If true, enables debug mode for the operation.
     * @throws Exception If an error occurs during the update process.
     */
    void updateSwitch(String entry, String value, boolean debug) throws Exception;
    
    /**
     * Retrieves system metrics information.
     * TODO use {@link com.alibaba.nacos.api.naming.pojo.maintainer.MetricsInfo} replaced return object, after v1/v2 api removed.
     *
     * @param onlyStatus If true, returns only the status information; otherwise, returns full metrics.
     * @return A {@link MetricsInfoVo} object containing the requested metrics data.
     */
    MetricsInfoVo metrics(boolean onlyStatus);
    
    /**
     * Sets the log level for a specified logger.
     *
     * @param logName  The name of the logger to configure.
     * @param logLevel The log level to set (e.g., "DEBUG", "INFO", "WARN", "ERROR").
     */
    void setLogLevel(String logName, String logLevel);
}