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

import com.alibaba.nacos.common.utils.StringUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Immutable snapshot of the merged model connections.
 *
 * <p>Connections are keyed by their normalized name, keeping the order in which the
 * aggregator produced them.
 *
 * @author nacos
 */
public final class ConnectionTable {
    
    private static final ConnectionTable EMPTY = new ConnectionTable(Collections.emptyList());
    
    private final Map<String, CopilotModelConnection> connections;
    
    /**
     * Create a table from the given connections.
     *
     * @param connections merged connections, in priority order
     */
    public ConnectionTable(List<CopilotModelConnection> connections) {
        Map<String, CopilotModelConnection> byName = new LinkedHashMap<>();
        for (CopilotModelConnection connection : connections) {
            if (connection == null || StringUtils.isBlank(connection.getName())) {
                continue;
            }
            byName.put(normalizeName(connection.getName()), connection);
        }
        this.connections = Collections.unmodifiableMap(byName);
    }
    
    /**
     * An empty table, used before the first read.
     *
     * @return empty table
     */
    public static ConnectionTable empty() {
        return EMPTY;
    }
    
    /**
     * Look up a connection by name, ignoring leading/trailing spaces and case.
     *
     * @param name connection name
     * @return connection, or {@code null} when absent
     */
    public CopilotModelConnection get(String name) {
        if (name == null) {
            return null;
        }
        return connections.get(normalizeName(name));
    }
    
    /**
     * The connection used when no explicit selection is made: the first enabled one.
     *
     * @return connection, or {@code null} when the table is empty or all are disabled
     */
    public CopilotModelConnection getDefault() {
        for (CopilotModelConnection connection : connections.values()) {
            if (connection.isEnabled()) {
                return connection;
            }
        }
        return null;
    }
    
    /**
     * All connections in table order.
     *
     * @return connections
     */
    public Collection<CopilotModelConnection> getAll() {
        return connections.values();
    }
    
    /**
     * Number of connections in the table.
     *
     * @return size
     */
    public int size() {
        return connections.size();
    }
    
    static String normalizeName(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }
}
