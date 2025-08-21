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

package com.alibaba.nacos.maintainer.client.config;

import com.alibaba.nacos.api.exception.NacosException;

/**
 * Nacos Config module config ops maintainer service.
 *
 * @author xiweng.yy
 */
public interface ConfigOpsMaintainerService {
    
    /**
     * Manually trigger dump of local configuration files from the store.
     *
     * @return A success message or error details.
     * @throws NacosException if the operation fails.
     */
    String updateLocalCacheFromStore() throws NacosException;
    
    /**
     * Set the log level for a specific module.
     *
     * @param logName  Name of the log module (required).
     * @param logLevel Desired log level (required).
     * @return A success message or error details.
     * @throws NacosException if the operation fails.
     */
    String setLogLevel(String logName, String logLevel) throws NacosException;
}
