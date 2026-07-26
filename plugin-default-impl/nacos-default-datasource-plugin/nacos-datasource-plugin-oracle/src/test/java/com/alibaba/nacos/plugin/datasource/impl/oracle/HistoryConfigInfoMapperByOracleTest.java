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

class HistoryConfigInfoMapperByOracleTest {
    
    int startRow = 0;
    
    int pageSize = 5;
    
    int limitSize = 6;
    
    long lastMaxId = 644;
    
    Timestamp startTime = new Timestamp(System.currentTimeMillis());
    
    Timestamp endTime = new Timestamp(System.currentTimeMillis());
    
    String publishType = "formal";
    
    MapperContext context;
    
    private HistoryConfigInfoMapperByOracle historyConfigInfoMapperByOracle;
    
    @BeforeEach
    void setUp() throws Exception {
        historyConfigInfoMapperByOracle = new HistoryConfigInfoMapperByOracle();
        context = new MapperContext(startRow, pageSize);
        context.putWhereParameter(FieldConstant.START_TIME, startTime);
        context.putWhereParameter(FieldConstant.END_TIME, endTime);
        context.putWhereParameter(FieldConstant.LIMIT_SIZE, limitSize);
        context.putWhereParameter(FieldConstant.LAST_MAX_ID, lastMaxId);
        context.putWhereParameter(FieldConstant.PAGE_SIZE, pageSize);
        context.putWhereParameter(FieldConstant.PUBLISH_TYPE, publishType);
    }
    
    @Test
    void testRemoveConfigHistory() {
        MapperResult mapperResult = historyConfigInfoMapperByOracle.removeConfigHistory(context);
        assertEquals(
            "DELETE FROM his_config_info WHERE ROWID IN (SELECT ROWID FROM his_config_info WHERE gmt_modified < ? FETCH FIRST ? ROWS ONLY)",
            mapperResult.getSql());
        assertArrayEquals(new Object[] {startTime, limitSize},
            mapperResult.getParamList().toArray());
    }
    
    @Test
    void testFindDeletedConfig() {
        MapperResult mapperResult = historyConfigInfoMapperByOracle.findDeletedConfig(context);
        assertEquals(
            "SELECT id, nid, data_id, group_id, app_name, content, md5, gmt_create, gmt_modified, src_user, src_ip, "
                + "op_type, tenant_id, publish_type, gray_name, ext_info, encrypted_data_key FROM his_config_info WHERE op_type = 'D' AND "
                + "publish_type = ? and gmt_modified >= ? and nid > ? order by nid fetch first ? rows only",
            mapperResult.getSql());
        
        assertArrayEquals(new Object[] {publishType, startTime, lastMaxId, pageSize},
            mapperResult.getParamList().toArray());
    }
    
    @Test
    void testPageFindConfigHistoryFetchRows() {
        Object dataId = "dataId";
        Object groupId = "groupId";
        Object tenantId = "tenantId";
        
        context.putWhereParameter(FieldConstant.DATA_ID, dataId);
        context.putWhereParameter(FieldConstant.GROUP_ID, groupId);
        context.putWhereParameter(FieldConstant.TENANT_ID, tenantId);
        
        MapperResult mapperResult =
            historyConfigInfoMapperByOracle.pageFindConfigHistoryFetchRows(context);
        assertEquals(mapperResult.getSql(),
            "SELECT nid,data_id,group_id,tenant_id,app_name,src_ip,src_user,op_type,ext_info,publish_type,gray_name,gmt_create,gmt_modified "
                + "FROM his_config_info "
                + "WHERE data_id = ? AND group_id = ? AND tenant_id = ? ORDER BY nid DESC OFFSET "
                + startRow + " ROWS FETCH NEXT " + pageSize + " ROWS ONLY");
        assertArrayEquals(new Object[] {dataId, groupId, tenantId},
            mapperResult.getParamList().toArray());
    }
    
    @Test
    void testGetNextHistoryInfo() {
        Object dataId = "dataId";
        Object groupId = "groupId";
        Object tenantId = "tenantId";
        Object nid = 100L;
        
        context.putWhereParameter(FieldConstant.DATA_ID, dataId);
        context.putWhereParameter(FieldConstant.GROUP_ID, groupId);
        context.putWhereParameter(FieldConstant.TENANT_ID, tenantId);
        context.putWhereParameter(FieldConstant.NID, nid);
        
        MapperResult mapperResult = historyConfigInfoMapperByOracle.getNextHistoryInfo(context);
        assertEquals(mapperResult.getSql(),
            "SELECT nid,data_id,group_id,tenant_id,app_name,content,md5,src_user,src_ip,op_type,publish_type,"
                + "gray_name,ext_info,gmt_create,gmt_modified,encrypted_data_key FROM his_config_info "
                + "WHERE data_id = ? AND group_id = ? AND tenant_id = ? AND publish_type = ? "
                + "AND nid > ? ORDER BY nid FETCH FIRST 1 ROWS ONLY");
        assertArrayEquals(new Object[] {dataId, groupId, tenantId, publishType, nid},
            mapperResult.getParamList().toArray());
    }
    
    @Test
    void testGetTableName() {
        String tableName = historyConfigInfoMapperByOracle.getTableName();
        assertEquals(TableConstant.HIS_CONFIG_INFO, tableName);
    }
    
    @Test
    void testGetDataSource() {
        String dataSource = historyConfigInfoMapperByOracle.getDataSource();
        assertEquals(DataSourceConstant.ORACLE, dataSource);
    }
}
