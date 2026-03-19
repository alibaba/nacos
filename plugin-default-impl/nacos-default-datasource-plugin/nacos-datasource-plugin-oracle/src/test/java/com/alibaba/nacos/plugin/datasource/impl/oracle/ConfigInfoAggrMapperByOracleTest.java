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

class ConfigInfoAggrMapperByOracleTest {
    
    int startRow = 0;
    
    int pageSize = 5;
    
    String dataId = "dataId";
    
    String groupId = "groupId";
    
    String tenantId = "tenantId";
    
    MapperContext context;
    
    private ConfigInfoAggrMapperByOracle configInfoAggrMapperByOracle;
    
    @BeforeEach
    void setUp() throws Exception {
        configInfoAggrMapperByOracle = new ConfigInfoAggrMapperByOracle();
        context = new MapperContext(startRow, pageSize);
        context.putWhereParameter(FieldConstant.DATA_ID, dataId);
        context.putWhereParameter(FieldConstant.GROUP_ID, groupId);
        context.putWhereParameter(FieldConstant.TENANT_ID, tenantId);
    }
    
    @Test
    void testFindConfigInfoAggrByPageFetchRows() {
        MapperResult mapperResult = configInfoAggrMapperByOracle.findConfigInfoAggrByPageFetchRows(context);
        assertEquals(
                "SELECT data_id,group_id,tenant_id,datum_id,app_name,content FROM config_info_aggr WHERE data_id= ? AND "
                        + "group_id= ? AND tenant_id= ? ORDER BY datum_id OFFSET " + startRow + " ROWS FETCH NEXT " + pageSize + " ROWS ONLY",
                mapperResult.getSql());
        assertArrayEquals(new Object[] {dataId, groupId, tenantId}, mapperResult.getParamList().toArray());
    }
    
    @Test
    void testGetTableName() {
        String tableName = configInfoAggrMapperByOracle.getTableName();
        assertEquals(TableConstant.CONFIG_INFO_AGGR, tableName);
    }
    
    @Test
    void testGetDataSource() {
        String dataSource = configInfoAggrMapperByOracle.getDataSource();
        assertEquals(DataSourceConstant.ORACLE, dataSource);
    }
}
