/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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
import com.alibaba.nacos.plugin.datasource.mapper.Mapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MapperManagerTest {
    
    private Map<String, Map<String, Mapper>> originalMappers;
    
    @BeforeEach
    void setUp() {
        originalMappers = new HashMap<>();
        MapperManager.MAPPER_SPI_MAP.forEach((dataSource, tableMappers) -> originalMappers
            .put(dataSource, new HashMap<>(tableMappers)));
        MapperManager.MAPPER_SPI_MAP.clear();
    }
    
    @AfterEach
    void tearDown() {
        MapperManager.MAPPER_SPI_MAP.clear();
        originalMappers.forEach((dataSource, tableMappers) -> MapperManager.MAPPER_SPI_MAP
            .put(dataSource, new HashMap<>(tableMappers)));
    }
    
    @Test
    void testJoinAndFindMapper() {
        Mapper mapper = new TestMapper();
        
        MapperManager.join(null);
        MapperManager.join(mapper);
        MapperManager.join(new DuplicateTestMapper());
        
        Mapper found = MapperManager.instance(false).findMapper("mysql", "config_info");
        assertSame(mapper, found);
        assertEquals(1, MapperManager.instance(false).getAllMappers().get("mysql").size());
    }
    
    @Test
    void testFindMapperWithProxyAndFailures() {
        Mapper mapper = new TestMapper();
        MapperManager.join(mapper);
        
        Mapper proxy = MapperManager.instance(true).findMapper("mysql", "config_info");
        
        assertNotSame(mapper, proxy);
        assertEquals("SELECT data_id FROM config_info WHERE data_id=?",
            proxy.select(Arrays.asList("data_id"), Arrays.asList("data_id")));
        assertThrows(UnsupportedOperationException.class,
            () -> MapperManager.instance(false).getAllMappers().clear());
        assertThrows(NacosRuntimeException.class,
            () -> MapperManager.instance(false).findMapper("", "config_info"));
        assertThrows(NacosRuntimeException.class,
            () -> MapperManager.instance(false).findMapper("derby", "config_info"));
        assertThrows(NacosRuntimeException.class,
            () -> MapperManager.instance(false).findMapper("mysql", "missing_table"));
    }
    
    @Test
    void testLoadInitialFromSpi() throws Exception {
        Method method = MapperManager.class.getDeclaredMethod("loadInitial");
        method.setAccessible(true);
        
        method.invoke(MapperManager.instance(false));
        
        Mapper mapper = MapperManager.instance(false).findMapper("spi", "spi_table");
        assertEquals("spi", mapper.getDataSource());
        assertEquals("spi_table", mapper.getTableName());
    }
    
    private static class TestMapper implements Mapper {
        
        @Override
        public String select(List<String> columns, List<String> where) {
            return "SELECT " + String.join(",", columns) + " FROM config_info WHERE "
                + where.get(0) + "=?";
        }
        
        @Override
        public String insert(List<String> columns) {
            return null;
        }
        
        @Override
        public String update(List<String> columns, List<String> where) {
            return null;
        }
        
        @Override
        public String delete(List<String> params) {
            return null;
        }
        
        @Override
        public String count(List<String> where) {
            return null;
        }
        
        @Override
        public String getTableName() {
            return "config_info";
        }
        
        @Override
        public String getDataSource() {
            return "mysql";
        }
        
        @Override
        public String[] getPrimaryKeyGeneratedKeys() {
            return new String[] {"id"};
        }
        
        @Override
        public String getFunction(String functionName) {
            return functionName;
        }
    }
    
    private static class DuplicateTestMapper extends TestMapper {
    }
}
