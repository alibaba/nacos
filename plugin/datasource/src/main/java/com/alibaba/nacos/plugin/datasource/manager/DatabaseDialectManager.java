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

import com.alibaba.nacos.common.spi.NacosServiceLoader;
import com.alibaba.nacos.plugin.datasource.dialect.DatabaseDialect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
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
        DatabaseDialect databaseDialect = SUPPORT_DIALECT_MAP.get(databaseType);
        if (databaseDialect == null) {
            LOGGER.warn("[DatabaseDialectManager] No dialect found for type: {}, using first available dialect as fallback",
                    databaseType);
            // Fallback to any available dialect, prefer one that is loaded via SPI
            if (!SUPPORT_DIALECT_MAP.isEmpty()) {
                return SUPPORT_DIALECT_MAP.values().iterator().next();
            }
            throw new IllegalStateException("No DatabaseDialect implementation found. "
                    + "Please ensure datasource plugin is properly loaded.");
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
    
}
