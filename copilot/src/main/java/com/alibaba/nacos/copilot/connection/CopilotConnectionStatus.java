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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Current connection metadata for the console: every connection plus the one serving
 * requests.
 *
 * @author nacos
 */
public final class CopilotConnectionStatus {
    
    private final List<CopilotConnectionInfo> connections;
    
    private final String activeConnection;
    
    /**
     * Create a status snapshot.
     *
     * @param connections      sanitized connections in selection order
     * @param activeConnection name of the connection serving requests, or {@code null}
     */
    public CopilotConnectionStatus(List<CopilotConnectionInfo> connections,
        String activeConnection) {
        this.connections = connections == null ? Collections.emptyList()
            : Collections.unmodifiableList(new ArrayList<>(connections));
        this.activeConnection = activeConnection;
    }
    
    /**
     * Every connection in selection order.
     *
     * @return sanitized connections
     */
    public List<CopilotConnectionInfo> getConnections() {
        return connections;
    }
    
    /**
     * Name of the connection that serves requests after routing, including the fallback when
     * the selected connection is unusable.
     *
     * @return active connection name, or {@code null} when no connection can serve
     */
    public String getActiveConnection() {
        return activeConnection;
    }
}
