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

class ConfigInfoTagMapperByOracleTest {
    
    private final Object[] emptyObjs = new Object[] {};
    
    int startRow = 0;
    
    int pageSize = 5;
    
    String appName = "appName";
    
    String tenantId = "tenantId";
    
    MapperContext context;
    
    private ConfigInfoTagMapperByOracle configInfoTagMapperByOracle;
    
    @BeforeEach
    void setUp() throws Exception {
        configInfoTagMapperByOracle = new ConfigInfoTagMapperByOracle();
        
        context = new MapperContext(startRow, pageSize);
        context.putWhereParameter(FieldConstant.APP_NAME, appName);
        context.putWhereParameter(FieldConstant.TENANT_ID, tenantId);
    }
    
    @Test
    void testFindAllConfigInfoTagForDumpAllFetchRows() {
        MapperResult mapperResult =
            configInfoTagMapperByOracle.findAllConfigInfoTagForDumpAllFetchRows(context);
        assertEquals(mapperResult.getSql(),
            " SELECT t.id,data_id,group_id,tenant_id,tag_id,app_name,content,md5,gmt_modified "
                + " FROM (  SELECT id FROM config_info_tag  ORDER BY id OFFSET " + startRow
                + " ROWS FETCH NEXT "
                + pageSize + " ROWS ONLY) g, config_info_tag t  WHERE g.id = t.id  ");
        assertArrayEquals(mapperResult.getParamList().toArray(), emptyObjs);
    }
    
    @Test
    void testGetTableName() {
        String tableName = configInfoTagMapperByOracle.getTableName();
        assertEquals(TableConstant.CONFIG_INFO_TAG, tableName);
    }
    
    @Test
    void testGetDataSource() {
        String dataSource = configInfoTagMapperByOracle.getDataSource();
        assertEquals(DataSourceConstant.ORACLE, dataSource);
    }
}
