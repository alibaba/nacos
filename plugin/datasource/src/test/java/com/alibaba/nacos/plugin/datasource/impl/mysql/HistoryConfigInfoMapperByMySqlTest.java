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

package com.alibaba.nacos.plugin.datasource.impl.mysql;

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

class HistoryConfigInfoMapperByMySqlTest {
    
    int startRow = 0;
    
    int pageSize = 5;
    
    int limitSize = 6;
    
    long lastMaxId = 644;
    
    Timestamp startTime = new Timestamp(System.currentTimeMillis());
    
    Timestamp endTime = new Timestamp(System.currentTimeMillis());
    
    String publishType = "formal";
    
    MapperContext context;
    
    private HistoryConfigInfoMapperByMySql historyConfigInfoMapperByMySql;
    
    @BeforeEach
    void setUp() throws Exception {
        historyConfigInfoMapperByMySql = new HistoryConfigInfoMapperByMySql();
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
        MapperResult mapperResult = historyConfigInfoMapperByMySql.removeConfigHistory(context);
        assertEquals("DELETE FROM his_config_info WHERE gmt_modified < ? LIMIT ?", mapperResult.getSql());
        assertArrayEquals(new Object[] {startTime, limitSize}, mapperResult.getParamList().toArray());
    }
    
    @Test
    void testFindConfigHistoryCountByTime() {
        MapperResult mapperResult = historyConfigInfoMapperByMySql.findConfigHistoryCountByTime(context);
        assertEquals("SELECT count(*) FROM his_config_info WHERE gmt_modified < ?", mapperResult.getSql());
        assertArrayEquals(new Object[] {startTime}, mapperResult.getParamList().toArray());
    }
    
    @Test
    void testFindDeletedConfig() {
        MapperResult mapperResult = historyConfigInfoMapperByMySql.findDeletedConfig(context);
        assertEquals(mapperResult.getSql(), "SELECT id, nid, data_id, group_id, app_name, content, md5, gmt_create, gmt_modified, src_user, src_ip,"
                + " op_type, tenant_id, publish_type, ext_info, encrypted_data_key FROM his_config_info WHERE op_type = 'D' AND "
                + "publish_type = ? and gmt_modified >= ? and nid > ? order by nid limit ? ");
        
        assertArrayEquals(new Object[] {publishType, startTime, lastMaxId, pageSize}, mapperResult.getParamList().toArray());
    }
    
    @Test
    void testFindConfigHistoryFetchRows() {
        Object dataId = "dataId";
        Object groupId = "groupId";
        Object tenantId = "tenantId";
        
        context.putWhereParameter(FieldConstant.DATA_ID, dataId);
        context.putWhereParameter(FieldConstant.GROUP_ID, groupId);
        context.putWhereParameter(FieldConstant.TENANT_ID, tenantId);
        context.putWhereParameter(FieldConstant.DATA_ID, dataId);
        MapperResult mapperResult = historyConfigInfoMapperByMySql.findConfigHistoryFetchRows(context);
        assertEquals(mapperResult.getSql(),
                "SELECT nid,data_id,group_id,tenant_id,app_name,src_ip,src_user,op_type,gmt_create,gmt_modified FROM his_config_info "
                        + "WHERE data_id = ? AND group_id = ? AND tenant_id = ? ORDER BY nid DESC");
        assertArrayEquals(new Object[] {dataId, groupId, tenantId}, mapperResult.getParamList().toArray());
    }
    
    @Test
    void testDetailPreviousConfigHistory() {
        Object id = "1";
        context.putWhereParameter(FieldConstant.ID, id);
        MapperResult mapperResult = historyConfigInfoMapperByMySql.detailPreviousConfigHistory(context);
        assertEquals(mapperResult.getSql(), "SELECT nid,data_id,group_id,tenant_id,app_name,content,md5,src_user,src_ip,op_type,publish_type"
                + ",ext_info,gmt_create,gmt_modified,encrypted_data_key "
                + "FROM his_config_info WHERE nid = (SELECT max(nid) FROM his_config_info WHERE id = ?)");
        assertArrayEquals(new Object[] {id}, mapperResult.getParamList().toArray());
    }
    
    @Test
    void testGetTableName() {
        String tableName = historyConfigInfoMapperByMySql.getTableName();
        assertEquals(TableConstant.HIS_CONFIG_INFO, tableName);
    }
    
    @Test
    void testGetDataSource() {
        String dataSource = historyConfigInfoMapperByMySql.getDataSource();
        assertEquals(DataSourceConstant.MYSQL, dataSource);
    }
}
