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

import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ConfigInfoGrayMapperByOracleTest {
    
    int startRow = 0;
    
    int pageSize = 5;
    
    long lastMaxId = 100L;
    
    Timestamp startTime = new Timestamp(System.currentTimeMillis());
    
    MapperContext context;
    
    private ConfigInfoGrayMapperByOracle configInfoGrayMapperByOracle;
    
    @BeforeEach
    void setUp() throws Exception {
        configInfoGrayMapperByOracle = new ConfigInfoGrayMapperByOracle();
        context = new MapperContext(startRow, pageSize);
        context.putWhereParameter(FieldConstant.START_TIME, startTime);
        context.putWhereParameter(FieldConstant.LAST_MAX_ID, lastMaxId);
        context.putWhereParameter(FieldConstant.PAGE_SIZE, pageSize);
    }
    
    @Test
    void testFindChangeConfig() {
        MapperResult mapperResult = configInfoGrayMapperByOracle.findChangeConfig(context);
        assertEquals(
            "SELECT id, data_id, group_id, tenant_id, app_name,content,gray_name,gray_rule,md5, gmt_modified, encrypted_data_key "
                + "FROM config_info_gray WHERE gmt_modified >= ? and id > ? order by id fetch first ? rows only",
            mapperResult.getSql());
        assertArrayEquals(new Object[] {startTime, lastMaxId, pageSize},
            mapperResult.getParamList().toArray());
    }
    
    @Test
    void testFindAllConfigInfoGrayForDumpAllFetchRows() {
        MapperResult mapperResult =
            configInfoGrayMapperByOracle.findAllConfigInfoGrayForDumpAllFetchRows(context);
        assertEquals(
            " SELECT id,data_id,group_id,tenant_id,gray_name,gray_rule,app_name,content,md5,gmt_modified "
                + " FROM  config_info_gray  ORDER BY id OFFSET " + startRow + " ROWS FETCH NEXT "
                + pageSize + " ROWS ONLY",
            mapperResult.getSql());
        assertEquals(0, mapperResult.getParamList().size());
    }
    
    @Test
    void testGetTableName() {
        String tableName = configInfoGrayMapperByOracle.getTableName();
        assertEquals(TableConstant.CONFIG_INFO_GRAY, tableName);
    }
    
    @Test
    void testGetDataSource() {
        String dataSource = configInfoGrayMapperByOracle.getDataSource();
        assertEquals(DataSourceConstant.ORACLE, dataSource);
    }
}
