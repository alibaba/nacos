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

package com.alibaba.nacos.ai.service;

import com.alibaba.nacos.config.server.model.form.ConfigForm;

import java.util.concurrent.TimeUnit;

/**
 * Nacos Mcp server async effect service.
 *
 * <p>
 *     Nacos Mcp server or Agent will be written to Nacos configuration, and the configuration dump to local is async and need time.
 *     This service is used to adapt and transfer async dump to sync dump.
 * </p>
 *
 * @author xiweng.yy
 */
public interface SyncEffectService {
    
    /**
     * Transfer Async mcp server operation to sync with 200 milliseconds timeout.
     *
     * @param configForm        mcp server configuration changed form
     * @param startTimeStamp    start time of operation mcp server.
     */
    default void toSync(ConfigForm configForm, long startTimeStamp) {
        toSync(configForm, startTimeStamp, 200L, TimeUnit.MILLISECONDS);
    }
    
    /**
     * Transfer Async mcp server operation to sync.
     *
     * @param configForm        mcp server configuration changed form
     * @param startTimeStamp    start time of operation mcp server.
     * @param timeout           max time wait for operation mcp server.
     * @param timeUnit          the time unit for timeout
     */
    void toSync(ConfigForm configForm, long startTimeStamp, long timeout, TimeUnit timeUnit);
    
}
