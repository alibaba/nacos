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
import com.alibaba.nacos.plugin.datasource.mapper.Mapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * DataSource Plugin Mapper Management.
 *
 * @author hyx
 **/

public class MapperManager implements InitAndClosable {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(MapperManager.class);
    
    private static final MapperManager INSTANCE = new MapperManager();
    
    private MapperManager() {}
    
    /**
     * Get the instance of MapperManager.
     * @return The instance of MapperManager.
     */
    public static MapperManager instance() {
        return INSTANCE;
    }
    
    private static final Map<String, Mapper> MAPPER_SPI_MAP = new HashMap<>();
    
    @Override
    public void loadInitial() {
        Collection<Mapper> mappers = NacosServiceLoader.load(Mapper.class);
        for (Mapper mapper : mappers) {
            MAPPER_SPI_MAP.put(mapper.getTableName(), mapper);
            LOGGER.info("[MapperManager] Load Mapper({}) tableName({}) successfully.",
                    mapper.getClass(), mapper.getTableName());
        }
    }
    
    /**
     * To join mapper in MAPPER_SPI_MAP.
     * @param mapper The mapper you want join.
     */
    public static synchronized void join(Mapper mapper) {
        if (Objects.isNull(mapper)) {
            return;
        }
        MAPPER_SPI_MAP.put(mapper.getTableName(), mapper);
        LOGGER.warn("[MapperManager] join successfully.");
    }
    
    @Override
    public boolean close() {
        return false;
    }
    
    /**
     * Get the mapper by table name.
     * @param tableName table name.
     * @return mapper.
     */
    public Optional<Mapper> findMapper(String tableName) {
        return Optional.ofNullable(MAPPER_SPI_MAP.get(tableName));
    }
}