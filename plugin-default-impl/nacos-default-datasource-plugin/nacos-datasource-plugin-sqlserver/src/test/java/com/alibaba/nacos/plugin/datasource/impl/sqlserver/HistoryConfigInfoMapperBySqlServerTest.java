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

class HistoryConfigInfoMapperBySqlServerTest {
    
    private final Object[] emptyObjs = new Object[] {};
    
    int startRow = 0;
    
    int pageSize = 5;
    
    String dataId = "dataId";
    
    String groupId = "groupId";
    
    String tenantId = "tenantId";
    
    String appName = "appName";
    
    Timestamp startTime = new Timestamp(System.currentTimeMillis());
    
    Timestamp endTime = new Timestamp(System.currentTimeMillis());
    
    long lastMaxId = 123;
    
    MapperContext context;
    
    private HistoryConfigInfoMapperBySqlServer historyConfigInfoMapperBySqlServer;
    
    @BeforeEach
    void setUp() throws Exception {
        historyConfigInfoMapperBySqlServer = new HistoryConfigInfoMapperBySqlServer();
        
        context = new MapperContext(startRow, pageSize);
        context.putWhereParameter(FieldConstant.DATA_ID, dataId);
        context.putWhereParameter(FieldConstant.GROUP_ID, groupId);
        context.putWhereParameter(FieldConstant.TENANT_ID, tenantId);
        context.putWhereParameter(FieldConstant.APP_NAME, appName);
        context.putWhereParameter(FieldConstant.START_TIME, startTime);
        context.putWhereParameter(FieldConstant.END_TIME, endTime);
        context.putWhereParameter(FieldConstant.LAST_MAX_ID, lastMaxId);
        context.putWhereParameter(FieldConstant.PAGE_SIZE, pageSize);
    }
    
    @Test
    void testGetTableName() {
        String tableName = historyConfigInfoMapperBySqlServer.getTableName();
        assertEquals(TableConstant.HIS_CONFIG_INFO, tableName);
    }
    
    @Test
    void testGetDataSource() {
        String dataSource = historyConfigInfoMapperBySqlServer.getDataSource();
        assertEquals(DatabaseTypeConstant.SQLSERVER, dataSource);
    }
    
    @Test
    void testRemoveConfigHistory() {
        int limit = 20;
        context.putWhereParameter(FieldConstant.LIMIT_SIZE, limit);
        MapperResult mapperResult = historyConfigInfoMapperBySqlServer.removeConfigHistory(context);
        assertEquals("DELETE FROM his_config_info WHERE gmt_modified < ? OFFSET 0 ROWS FETCH NEXT ? ROWS ONLY",
            mapperResult.getSql());
        assertArrayEquals(new Object[] {startTime, limit}, mapperResult.getParamList().toArray());
    }
    
    @Test
    void testPageFindConfigHistoryFetchRows() {
        MapperResult mapperResult = historyConfigInfoMapperBySqlServer.pageFindConfigHistoryFetchRows(context);
        String expectedSql = "SELECT nid,data_id,group_id,tenant_id,app_name,content,md5,type,gmt_modified,"
            + "src_user,src_ip,op_type,publish_type,ext_info,gray_name FROM his_config_info "
            + "WHERE data_id = ? AND group_id = ? AND tenant_id = ? "
            + "ORDER BY nid DESC OFFSET " + startRow + " ROWS FETCH NEXT " + pageSize + " ROWS ONLY";
        assertEquals(expectedSql, mapperResult.getSql());
        assertArrayEquals(new Object[] {dataId, groupId, tenantId}, mapperResult.getParamList().toArray());
    }
    
    @Test
    void testFindDeletedConfig() {
        String publishType = "formal";
        context.putWhereParameter(FieldConstant.PUBLISH_TYPE, publishType);
        MapperResult mapperResult = historyConfigInfoMapperBySqlServer.findDeletedConfig(context);
        assertEquals(mapperResult.getSql(),
            "SELECT nid,data_id,group_id,tenant_id,app_name,content,md5,type,gmt_modified,src_user,src_ip,"
                + "op_type,publish_type FROM his_config_info WHERE op_type = ? AND gmt_modified >= ? AND nid > ? "
                + "ORDER BY nid OFFSET 0 ROWS FETCH NEXT ? ROWS ONLY");
        assertArrayEquals(new Object[] {publishType, startTime, lastMaxId, pageSize},
            mapperResult.getParamList().toArray());
    }
}
