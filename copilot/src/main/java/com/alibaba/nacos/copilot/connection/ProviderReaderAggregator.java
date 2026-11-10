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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Merges all {@link ProviderReader} beans into one {@link ConnectionTable}.
 *
 * <p>Readers are applied from the lowest priority to the highest, so a connection from a
 * higher priority source replaces one with the same normalized name.
 *
 * @author nacos
 */
@Component
public class ProviderReaderAggregator {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ProviderReaderAggregator.class);
    
    private final List<ProviderReader> readers;
    
    private final CopilotConnectionValidator connectionValidator;
    
    /**
     * Create the aggregator from all discovered readers.
     *
     * @param readers             readers discovered by Spring
     * @param connectionValidator load-time validator applied to the merged connections
     */
    @Autowired
    public ProviderReaderAggregator(List<ProviderReader> readers,
        CopilotConnectionValidator connectionValidator) {
        List<ProviderReader> sorted = new ArrayList<>(readers);
        sorted.sort(Comparator.comparingInt(ProviderReader::order).reversed());
        this.readers = Collections.unmodifiableList(sorted);
        this.connectionValidator = connectionValidator;
    }
    
    /**
     * Read every source and merge the results.
     *
     * @param context configuration snapshot shared by all readers in this refresh
     * @return merged connection table
     */
    public ConnectionTable readAll(ProviderReadContext context) {
        Map<String, CopilotModelConnection> merged = new LinkedHashMap<>();
        for (ProviderReader reader : readers) {
            try {
                Map<String, CopilotModelConnection> fromReader = new LinkedHashMap<>();
                for (CopilotModelConnection connection : reader.read(context)) {
                    if (connection == null || StringUtils.isBlank(connection.getName())) {
                        continue;
                    }
                    String key = ConnectionTable.normalizeName(connection.getName());
                    if (fromReader.containsKey(key)) {
                        LOGGER.warn("Duplicate connection name [{}] in {}, keeping the first one",
                            key, reader.getClass().getSimpleName());
                        continue;
                    }
                    fromReader.put(key, connection);
                }
                // Readers are sorted from the lowest to the highest priority, so a later
                // reader replaces connections with the same name.
                for (Map.Entry<String, CopilotModelConnection> entry : fromReader.entrySet()) {
                    if (merged.put(entry.getKey(), entry.getValue()) != null) {
                        LOGGER.warn("Connection [{}] from {} overrides a connection with the "
                            + "same name from a lower priority reader", entry.getKey(),
                            reader.getClass().getSimpleName());
                    }
                }
            } catch (RuntimeException e) {
                LOGGER.warn("Failed to read model connections from {}",
                    reader.getClass().getSimpleName(), e);
            }
        }
        List<CopilotModelConnection> connections = new ArrayList<>(merged.values());
        // Higher priority sources come first, so the first entry is also the default when
        // nothing is explicitly selected. Connections with the same priority are ordered by
        // name because the underlying property source does not preserve declaration order.
        connections.sort(Comparator.comparingInt(CopilotModelConnection::getPriority)
            .thenComparing(connection -> ConnectionTable.normalizeName(connection.getName())));
        for (int i = 0; i < connections.size(); i++) {
            connections.set(i, validate(connections.get(i)));
        }
        return new ConnectionTable(connections);
    }
    
    private CopilotModelConnection validate(CopilotModelConnection connection) {
        try {
            return connectionValidator.validate(connection);
        } catch (RuntimeException e) {
            String reason = StringUtils.isBlank(e.getMessage())
                ? e.getClass().getSimpleName() : e.getMessage();
            LOGGER.warn("Failed to validate Copilot connection [{}]: {}", connection.getName(),
                reason);
            return connection.withAvailability(false, reason);
        }
    }
}
