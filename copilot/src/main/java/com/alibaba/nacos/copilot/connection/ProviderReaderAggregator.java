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
    
    /**
     * Create the aggregator from all discovered readers.
     *
     * @param readers readers discovered by Spring
     */
    @Autowired
    public ProviderReaderAggregator(List<ProviderReader> readers) {
        List<ProviderReader> sorted = new ArrayList<>(readers);
        sorted.sort(Comparator.comparingInt(ProviderReader::order).reversed());
        this.readers = Collections.unmodifiableList(sorted);
    }
    
    /**
     * Read every source and merge the results.
     *
     * @return merged connection table
     */
    public ConnectionTable readAll() {
        Map<String, CopilotModelConnection> merged = new LinkedHashMap<>();
        for (ProviderReader reader : readers) {
            try {
                for (CopilotModelConnection connection : reader.read()) {
                    if (connection == null || StringUtils.isBlank(connection.getName())) {
                        continue;
                    }
                    merged.put(ConnectionTable.normalizeName(connection.getName()), connection);
                }
            } catch (RuntimeException e) {
                LOGGER.warn("Failed to read model connections from {}",
                    reader.getClass().getSimpleName(), e);
            }
        }
        return new ConnectionTable(new ArrayList<>(merged.values()));
    }
}
