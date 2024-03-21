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

package com.alibaba.nacos.plugin.datasource;

import com.alibaba.nacos.api.exception.runtime.NacosRuntimeException;
import com.alibaba.nacos.common.spi.NacosServiceLoader;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.datasource.mapper.Mapper;
import com.alibaba.nacos.plugin.datasource.proxy.MapperProxy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static com.alibaba.nacos.api.common.Constants.Exception.FIND_DATASOURCE_ERROR_CODE;
import static com.alibaba.nacos.api.common.Constants.Exception.FIND_TABLE_ERROR_CODE;

/**
 * DataSource Plugin Mapper Management.
 *
 * @author hyx
 **/

public class MapperManager {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(MapperManager.class);
    
    public static final Map<String, Map<String, Mapper>> MAPPER_SPI_MAP = new HashMap<>();
    
    private static final MapperManager INSTANCE = new MapperManager();
    
    private boolean dataSourceLogEnable;
    
    private MapperManager() {
        loadInitial();
    }
    
    /**
     * Get the instance of MapperManager.
     * @return The instance of MapperManager.
     */
    public static MapperManager instance(boolean isDataSourceLogEnable) {
        INSTANCE.dataSourceLogEnable = isDataSourceLogEnable;
        return INSTANCE;
    }
    
    /**
     * The init method.
     */
    public synchronized void loadInitial() {
        Collection<Mapper> mappers = NacosServiceLoader.load(Mapper.class);
        for (Mapper mapper : mappers) {
            putMapper(mapper);
            LOGGER.info("[MapperManager] Load Mapper({}) datasource({}) tableName({}) successfully.",
                    mapper.getClass(), mapper.getDataSource(), mapper.getTableName());
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
        putMapper(mapper);
        LOGGER.info("[MapperManager] join successfully.");
    }
    
    private static void putMapper(Mapper mapper) {
        Map<String, Mapper> mapperMap = MAPPER_SPI_MAP.computeIfAbsent(mapper.getDataSource(), key ->
                new HashMap<>(16));
        mapperMap.putIfAbsent(mapper.getTableName(), mapper);
    }
    
    /**
     * Get the mapper by table name.
     *
     * @param tableName  table name.
     * @param dataSource the datasource.
     * @return mapper.
     */
    public <R extends Mapper> R findMapper(String dataSource, String tableName) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[MapperManager] findMapper dataSource: {}, tableName: {}", dataSource, tableName);
        }
        if (StringUtils.isBlank(dataSource) || StringUtils.isBlank(tableName)) {
            throw new NacosRuntimeException(FIND_DATASOURCE_ERROR_CODE, "dataSource or tableName is null");
        }
        Map<String, Mapper> tableMapper = MAPPER_SPI_MAP.get(dataSource);
        if (Objects.isNull(tableMapper)) {
            throw new NacosRuntimeException(FIND_DATASOURCE_ERROR_CODE,
                    "[MapperManager] Failed to find the datasource,dataSource:" + dataSource);
        }
        Mapper mapper = tableMapper.get(tableName);
        if (Objects.isNull(mapper)) {
            throw new NacosRuntimeException(FIND_TABLE_ERROR_CODE,
                    "[MapperManager] Failed to find the table ,tableName:" + tableName);
        }
        if (dataSourceLogEnable) {
            return MapperProxy.createSingleProxy(mapper);
        }
        return (R) mapper;
    }
}
