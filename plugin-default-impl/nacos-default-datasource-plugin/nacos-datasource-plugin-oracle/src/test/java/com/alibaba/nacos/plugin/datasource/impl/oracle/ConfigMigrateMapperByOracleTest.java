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

package com.alibaba.nacos.plugin.datasource.impl.oracle;

import com.alibaba.nacos.plugin.datasource.constants.DataSourceConstant;
import com.alibaba.nacos.plugin.datasource.constants.FieldConstant;
import com.alibaba.nacos.plugin.datasource.constants.TableConstant;
import com.alibaba.nacos.plugin.datasource.model.MapperContext;
import com.alibaba.nacos.plugin.datasource.model.MapperResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigMigrateMapperByOracleTest {
    
    int pageSize = 5;
    
    long id = 100L;
    
    String srcTenant = "srcTenant";
    
    String targetTenant = "targetTenant";
    
    String srcUser = "nacos";
    
    MapperContext context;
    
    private ConfigMigrateMapperByOracle configMigrateMapperByOracle;
    
    @BeforeEach
    void setUp() throws Exception {
        configMigrateMapperByOracle = new ConfigMigrateMapperByOracle();
        context = new MapperContext(0, pageSize);
        context.putWhereParameter(FieldConstant.ID, id);
        context.putWhereParameter(FieldConstant.PAGE_SIZE, pageSize);
        context.putWhereParameter(FieldConstant.SRC_TENANT, srcTenant);
        context.putWhereParameter(FieldConstant.TARGET_TENANT, targetTenant);
        context.putWhereParameter(FieldConstant.SRC_USER, srcUser);
    }
    
    @Test
    void testFindConfigIdNeedInsertMigrate() {
        MapperResult mapperResult = configMigrateMapperByOracle.findConfigIdNeedInsertMigrate(context);
        assertTrue(mapperResult.getSql().contains("SELECT ci.id FROM config_info ci"));
        assertTrue(mapperResult.getSql().contains("FETCH FIRST ? ROWS ONLY"));
        assertArrayEquals(new Object[] {id, pageSize}, mapperResult.getParamList().toArray());
    }
    
    @Test
    void testFindConfigNeedUpdateMigrate() {
        MapperResult mapperResult = configMigrateMapperByOracle.findConfigNeedUpdateMigrate(context);
        assertTrue(mapperResult.getSql().contains("SELECT ci.id, ci.data_id, ci.group_id, ci.tenant_id"));
        assertTrue(mapperResult.getSql().contains("FROM config_info ci"));
        assertTrue(mapperResult.getSql().contains("FETCH FIRST ? ROWS ONLY"));
        assertArrayEquals(new Object[] {srcTenant, srcUser, targetTenant, srcUser, id, pageSize}, 
                mapperResult.getParamList().toArray());
    }
    
    @Test
    void testFindConfigGrayNeedUpdateMigrate() {
        MapperResult mapperResult = configMigrateMapperByOracle.findConfigGrayNeedUpdateMigrate(context);
        assertTrue(mapperResult.getSql().contains("SELECT ci.id, ci.data_id, ci.group_id, ci.tenant_id, ci.gray_name"));
        assertTrue(mapperResult.getSql().contains("FROM config_info_gray ci"));
        assertTrue(mapperResult.getSql().contains("FETCH FIRST ? ROWS ONLY"));
        assertArrayEquals(new Object[] {srcTenant, srcUser, targetTenant, srcUser, id, pageSize}, 
                mapperResult.getParamList().toArray());
    }
    
    @Test
    void testFindConfigGrayIdNeedInsertMigrate() {
        MapperResult mapperResult = configMigrateMapperByOracle.findConfigGrayIdNeedInsertMigrate(context);
        assertTrue(mapperResult.getSql().contains("SELECT ci.id FROM config_info_gray ci"));
        assertTrue(mapperResult.getSql().contains("FETCH FIRST ? ROWS ONLY"));
        assertArrayEquals(new Object[] {id, pageSize}, mapperResult.getParamList().toArray());
    }
    
    @Test
    void testGetTableName() {
        String tableName = configMigrateMapperByOracle.getTableName();
        assertEquals(TableConstant.MIGRATE_CONFIG, tableName);
    }
    
    @Test
    void testGetDataSource() {
        String dataSource = configMigrateMapperByOracle.getDataSource();
        assertEquals(DataSourceConstant.ORACLE, dataSource);
    }
}
