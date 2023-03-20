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
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.sql.Timestamp;

public class HistoryConfigInfoMapperByMySqlTest {
    
    private HistoryConfigInfoMapperByMySql historyConfigInfoMapperByMySql;
    
    int startRow = 0;
    
    int pageSize = 5;
    
    int limitSize = 6;
    
    Timestamp startTime = new Timestamp(System.currentTimeMillis());
    
    Timestamp endTime = new Timestamp(System.currentTimeMillis());
    
    MapperContext context;
    
    @Before
    public void setUp() throws Exception {
        historyConfigInfoMapperByMySql = new HistoryConfigInfoMapperByMySql();
        context = new MapperContext(startRow, pageSize);
        context.putWhereParameter(FieldConstant.START_TIME, startTime);
        context.putWhereParameter(FieldConstant.END_TIME, endTime);
        context.putWhereParameter(FieldConstant.LIMIT_SIZE, limitSize);
        
    }
    
    @Test
    public void testRemoveConfigHistory() {
        MapperResult mapperResult = historyConfigInfoMapperByMySql.removeConfigHistory(context);
        Assert.assertEquals(mapperResult.getSql(), "DELETE FROM his_config_info WHERE gmt_modified < ? LIMIT ?");
        Assert.assertArrayEquals(mapperResult.getParamList().toArray(), new Object[] {startTime, limitSize});
    }
    
    @Test
    public void testFindConfigHistoryCountByTime() {
        MapperResult mapperResult = historyConfigInfoMapperByMySql.findConfigHistoryCountByTime(context);
        Assert.assertEquals(mapperResult.getSql(), "SELECT count(*) FROM his_config_info WHERE gmt_modified < ?");
        Assert.assertArrayEquals(mapperResult.getParamList().toArray(), new Object[] {startTime});
    }
    
    @Test
    public void testFindDeletedConfig() {
        MapperResult mapperResult = historyConfigInfoMapperByMySql.findDeletedConfig(context);
        Assert.assertEquals(mapperResult.getSql(),
                "SELECT DISTINCT data_id, group_id, tenant_id FROM his_config_info WHERE op_type = 'D' AND "
                        + "gmt_modified >= ? AND gmt_modified <= ?");
        
        Assert.assertArrayEquals(mapperResult.getParamList().toArray(), new Object[] {startTime, endTime});
    }
    
    @Test
    public void testFindConfigHistoryFetchRows() {
        Object dataId = "dataId";
        Object groupId = "groupId";
        Object tenantId = "tenantId";
        
        context.putWhereParameter(FieldConstant.DATA_ID, dataId);
        context.putWhereParameter(FieldConstant.GROUP_ID, groupId);
        context.putWhereParameter(FieldConstant.TENANT_ID, tenantId);
        context.putWhereParameter(FieldConstant.DATA_ID, dataId);
        MapperResult mapperResult = historyConfigInfoMapperByMySql.findConfigHistoryFetchRows(context);
        Assert.assertEquals(mapperResult.getSql(),
                "SELECT nid,data_id,group_id,tenant_id,app_name,src_ip,src_user,op_type,gmt_create,gmt_modified FROM his_config_info "
                        + "WHERE data_id = ? AND group_id = ? AND tenant_id = ? ORDER BY nid DESC");
        Assert.assertArrayEquals(mapperResult.getParamList().toArray(), new Object[] {dataId, groupId, tenantId});
    }
    
    @Test
    public void testDetailPreviousConfigHistory() {
        Object id = "1";
        context.putWhereParameter(FieldConstant.ID, id);
        MapperResult mapperResult = historyConfigInfoMapperByMySql.detailPreviousConfigHistory(context);
        Assert.assertEquals(mapperResult.getSql(),
                "SELECT nid,data_id,group_id,tenant_id,app_name,content,md5,src_user,src_ip,op_type,gmt_create,"
                        + "gmt_modified FROM his_config_info WHERE nid = (SELECT max(nid) FROM his_config_info WHERE id = ?)");
        Assert.assertArrayEquals(mapperResult.getParamList().toArray(), new Object[] {id});
    }
    
    @Test
    public void testGetTableName() {
        String tableName = historyConfigInfoMapperByMySql.getTableName();
        Assert.assertEquals(tableName, TableConstant.HIS_CONFIG_INFO);
    }
    
    @Test
    public void testGetDataSource() {
        String dataSource = historyConfigInfoMapperByMySql.getDataSource();
        Assert.assertEquals(dataSource, DataSourceConstant.MYSQL);
    }
}
