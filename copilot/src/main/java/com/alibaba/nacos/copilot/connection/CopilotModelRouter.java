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

package com.alibaba.nacos.copilot.connection;

/**
 * Selects the connection used for a new Copilot agent.
 *
 * @author nacos
 */
public interface CopilotModelRouter {
    
    /**
     * Select a connection from the current table.
     *
     * @param table             current connections
     * @param selectedConnection explicitly selected connection name, may be {@code null}
     * @return connection to use, or {@code null} when none can serve a request
     */
    CopilotModelConnection route(ConnectionTable table, String selectedConnection);
}
