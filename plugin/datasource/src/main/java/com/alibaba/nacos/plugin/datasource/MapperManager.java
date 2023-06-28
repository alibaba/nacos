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
import java.util.stream.Collectors;

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

    private static MapperManager instatnce;

    private boolean dataSourceLogEnable;

    private static final Map<String, String> DATASOURCE_MAPPER_NAMES = new HashMap<String, String>() {
        {
            put("mysql", "com.alibaba.nacos.plugin.datasource.impl.mysql.AbstractMysqlMapper");
            put("derby", "com.alibaba.nacos.plugin.datasource.impl.derby.AbstractDerbyMapper");
            put("dm", "com.alibaba.nacos.plugin.datasource.impl.dm.AbstractDmMapper");
        }
    };

    private String databaseType;

    private MapperManager(String databaseType) {
        this.databaseType = databaseType;
        loadInitial(databaseType);
    }

    /**
     * Get the instance of MapperManager.
     *
     * @return The instance of MapperManager.
     */
    public static synchronized MapperManager instance(boolean isDataSourceLogEnable, String databaseType) {
        if (instatnce == null) {
            instatnce = new MapperManager(databaseType);
        }
        instatnce.dataSourceLogEnable = isDataSourceLogEnable;
        return instatnce;
    }

    /**
     * The init method.
     */
    public void loadInitial(String databaseType) {
        String strDatabaseType = this.DATASOURCE_MAPPER_NAMES.get(databaseType);
        if (StringUtils.isBlank(strDatabaseType)) {
            LOGGER.warn("Not support type({}) of database", databaseType);
            throw new NacosRuntimeException(FIND_DATASOURCE_ERROR_CODE,
                    "Not support type of database");
        }
        Class<?> clazz = null;
        try {
            clazz = Class.forName(strDatabaseType);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
        if (clazz == null) {
            LOGGER.warn("Not support type({}) of database", databaseType);
            throw new NacosRuntimeException(FIND_DATASOURCE_ERROR_CODE,
                    "Not support type of database");
        }
        Collection<Mapper> mappers = NacosServiceLoader.load(clazz).stream().map(m -> (Mapper) m).collect(Collectors.toList());
        for (Mapper mapper : mappers) {
            Map<String, Mapper> mapperMap = MAPPER_SPI_MAP.computeIfAbsent(mapper.getDataSource(), (r) -> new HashMap<>(16));
            mapperMap.put(mapper.getTableName(), mapper);
            LOGGER.info("[MapperManager] Load Mapper({}) datasource({}) tableName({}) successfully.",
                    mapper.getClass(), mapper.getDataSource(), mapper.getTableName());
        }
    }

    /**
     * To join mapper in MAPPER_SPI_MAP.
     *
     * @param mapper The mapper you want join.
     */
    public static synchronized void join(Mapper mapper) {
        if (Objects.isNull(mapper)) {
            return;
        }
        Map<String, Mapper> mapperMap = MAPPER_SPI_MAP.getOrDefault(mapper.getDataSource(), new HashMap<>(16));
        mapperMap.put(mapper.getTableName(), mapper);
        MAPPER_SPI_MAP.put(mapper.getDataSource(), mapperMap);
        LOGGER.warn("[MapperManager] join successfully.");
    }

    /**
     * Get the mapper by table name.
     *
     * @param tableName table name.
     * @return mapper.
     */
    public <R extends Mapper> R findMapper(String tableName) {
        LOGGER.info("[MapperManager] findMapper dataSource: {}, tableName: {}", this.databaseType, tableName);
        if (StringUtils.isBlank(this.databaseType) || StringUtils.isBlank(tableName)) {
            throw new NacosRuntimeException(FIND_DATASOURCE_ERROR_CODE, "dataSource or tableName is null");
        }
        Map<String, Mapper> tableMapper = MAPPER_SPI_MAP.get(this.databaseType);
        if (Objects.isNull(tableMapper)) {
            throw new NacosRuntimeException(FIND_DATASOURCE_ERROR_CODE,
                    "[MapperManager] Failed to find the datasource,dataSource:" + this.databaseType);
        }
        Mapper mapper = tableMapper.get(tableName);
        if (Objects.isNull(mapper)) {
            throw new NacosRuntimeException(FIND_TABLE_ERROR_CODE,
                    "[MapperManager] Failed to find the table ,tableName:" + tableName);
        }
        if (dataSourceLogEnable) {
            return (R) MapperProxy.createSingleProxy(mapper);
        }
        return (R) mapper;
    }
}
