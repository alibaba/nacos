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

package com.alibaba.nacos.plugin.datasource.impl.sqlserver;

import com.alibaba.nacos.plugin.datasource.constants.DatabaseTypeConstant;
import com.alibaba.nacos.plugin.datasource.constants.FieldConstant;
import com.alibaba.nacos.plugin.datasource.constants.TableConstant;
import com.alibaba.nacos.plugin.datasource.model.MapperContext;
import com.alibaba.nacos.plugin.datasource.model.MapperResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ConfigInfoBetaMapperBySqlServerTest {
    
    private final Object[] emptyObjs = new Object[] {};
    
    int startRow = 0;
    
    int pageSize = 5;
    
    String dataId = "dataId";
    
    String groupId = "groupId";
    
    String tenantId = "tenantId";
    
    Timestamp startTime = new Timestamp(System.currentTimeMillis());
    
    MapperContext context;
    
    private ConfigInfoBetaMapperBySqlServer configInfoBetaMapperBySqlServer;
    
    @BeforeEach
    void setUp() throws Exception {
        configInfoBetaMapperBySqlServer = new ConfigInfoBetaMapperBySqlServer();
        
        context = new MapperContext(startRow, pageSize);
        context.putWhereParameter(FieldConstant.DATA_ID, dataId);
        context.putWhereParameter(FieldConstant.GROUP_ID, groupId);
        context.putWhereParameter(FieldConstant.TENANT_ID, tenantId);
        context.putWhereParameter(FieldConstant.START_TIME, startTime);
    }
    
    @Test
    void testGetTableName() {
        String tableName = configInfoBetaMapperBySqlServer.getTableName();
        assertEquals(TableConstant.CONFIG_INFO_BETA, tableName);
    }
    
    @Test
    void testGetDataSource() {
        String dataSource = configInfoBetaMapperBySqlServer.getDataSource();
        assertEquals(DatabaseTypeConstant.SQLSERVER, dataSource);
    }
    
    @Test
    void testFindAllConfigInfoBetaForDumpAllFetchRows() {
        MapperResult mapperResult = configInfoBetaMapperBySqlServer.findAllConfigInfoBetaForDumpAllFetchRows(context);
        assertEquals(mapperResult.getSql(),
            " SELECT t.id,data_id,group_id,tenant_id,app_name,content,md5,gmt_modified,beta_ips,encrypted_data_key "
                + " FROM ( SELECT id FROM config_info_beta "
                + " ORDER BY id OFFSET " + startRow + " ROWS FETCH NEXT " + pageSize + " ROWS ONLY) "
                + " g, config_info_beta t WHERE g.id = t.id ");
        assertArrayEquals(new Object[] {startRow, pageSize}, mapperResult.getParamList().toArray());
    }
}
