/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.persistence.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Manager RowMapper {@link RowMapper} for database object mapping.
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public final class RowMapperManager {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(RowMapperManager.class);
    
    public static final MapRowMapper MAP_ROW_MAPPER = new MapRowMapper();
    
    public static Map<String, RowMapper> mapperMap = new HashMap<>(16);
    
    static {
        // MAP_ROW_MAPPER
        mapperMap.put(MAP_ROW_MAPPER.getClass().getCanonicalName(), MAP_ROW_MAPPER);
    }
    
    public static <D> RowMapper<D> getRowMapper(String classFullName) {
        return (RowMapper<D>) mapperMap.get(classFullName);
    }
    
    /**
     * Register custom row mapper to manager.
     *
     * @param classFullName full class name of row mapper handled.
     * @param rowMapper     row mapper
     * @param <D>           class of row mapper handled
     */
    public static synchronized <D> void registerRowMapper(String classFullName, RowMapper<D> rowMapper) {
        if (mapperMap.containsKey(classFullName)) {
            LOGGER.warn("row mapper {} conflicts, {} will be replaced by {}", classFullName,
                    mapperMap.get(classFullName).getClass().getCanonicalName(),
                    rowMapper.getClass().getCanonicalName());
        }
        mapperMap.put(classFullName, rowMapper);
    }
    
    public static final class MapRowMapper implements RowMapper<Map<String, Object>> {
        
        @Override
        public Map<String, Object> mapRow(ResultSet resultSet, int rowNum) throws SQLException {
            ResultSetMetaData metaData = resultSet.getMetaData();
            int columnCount = metaData.getColumnCount();
            Map<String, Object> map = new LinkedHashMap<>(columnCount);
            for (int i = 1; i <= columnCount; i++) {
                map.put(metaData.getColumnLabel(i), resultSet.getObject(i));
            }
            return map;
        }
    }
    
}
