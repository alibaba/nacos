/*
 * Copyright 1999-2022 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.datasource.manager;

import com.alibaba.nacos.api.plugin.PluginStateCheckerHolder;
import com.alibaba.nacos.api.plugin.PluginType;
import com.alibaba.nacos.common.spi.NacosServiceLoader;
import com.alibaba.nacos.plugin.datasource.dialect.DatabaseDialect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DatabaseDialect SPI Manager.
 * @author Long Yu
 */
public class DatabaseDialectManager {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseDialectManager.class);
    
    private static final DatabaseDialectManager INSTANCE = new DatabaseDialectManager();
    
    private static final Map<String, DatabaseDialect> SUPPORT_DIALECT_MAP = new ConcurrentHashMap<String, DatabaseDialect>();
    
    private DatabaseDialectManager() {
    }
    
    static {
        //加载多种数据库方言为映射信息
        Collection<DatabaseDialect> dialectList = NacosServiceLoader.load(DatabaseDialect.class);
        
        for (DatabaseDialect dialect : dialectList) {
            SUPPORT_DIALECT_MAP.put(dialect.getType(), dialect);
        }
        if (SUPPORT_DIALECT_MAP.isEmpty()) {
            LOGGER.warn("[DatasourceDialectManager] Load DatabaseDialect fail, No DatabaseDialect implements");
        }
    }
    
    public DatabaseDialect getDialect(String databaseType) {
        // Check if plugin is enabled
        if (!PluginStateCheckerHolder.isPluginEnabled(PluginType.DATASOURCE_DIALECT.getType(), databaseType)) {
            LOGGER.debug("[DatabaseDialectManager] Plugin DATASOURCE_DIALECT:{} is disabled", databaseType);
            throw new IllegalStateException(
                    "DatabaseDialect plugin is disabled: " + databaseType
                            + ". Please enable it via plugin management API.");
        }

        DatabaseDialect databaseDialect = SUPPORT_DIALECT_MAP.get(databaseType);
        if (databaseDialect == null) {
            LOGGER.warn("[DatabaseDialectManager] No dialect found for type: {}, checking for enabled fallback dialects",
                    databaseType);
            // Find first enabled dialect as fallback
            for (Map.Entry<String, DatabaseDialect> entry : SUPPORT_DIALECT_MAP.entrySet()) {
                String dialectType = entry.getKey();
                if (PluginStateCheckerHolder.isPluginEnabled(PluginType.DATASOURCE_DIALECT.getType(), dialectType)) {
                    LOGGER.warn("[DatabaseDialectManager] Using enabled dialect {} as fallback for {}",
                            dialectType, databaseType);
                    return entry.getValue();
                }
            }
            throw new IllegalStateException(
                    "No enabled DatabaseDialect implementation found. "
                            + "Please ensure datasource plugin is properly loaded and enabled.");
        }
        return databaseDialect;
    }

    /**
     * Get DatasourceDialectManager instance.
     *
     * @return DataSourceDialectProvider
     */
    public static DatabaseDialectManager getInstance() {
        return INSTANCE;
    }

    /**
     * Get all registered database dialects.
     *
     * @return unmodifiable map of database type to DatabaseDialect
     */
    public Map<String, DatabaseDialect> getAllDialects() {
        return Collections.unmodifiableMap(SUPPORT_DIALECT_MAP);
    }

}
