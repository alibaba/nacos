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

package com.alibaba.nacos.plugin.datasource.impl.derby;

import com.alibaba.nacos.plugin.datasource.constants.DataSourceConstant;
import com.alibaba.nacos.plugin.datasource.constants.FieldConstant;
import com.alibaba.nacos.plugin.datasource.constants.TableConstant;
import com.alibaba.nacos.plugin.datasource.model.MapperContext;
import com.alibaba.nacos.plugin.datasource.model.MapperResult;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.sql.Timestamp;

public class HistoryConfigInfoMapperByDerbyTest {
    
    private HistoryConfigInfoMapperByDerby historyConfigInfoMapperByDerby;
    
    int startRow = 0;
    
    int pageSize = 5;
    
    int limitSize = 6;
    
    int lastMaxId = 123;
    
    Timestamp startTime = new Timestamp(System.currentTimeMillis());
    
    Timestamp endTime = new Timestamp(System.currentTimeMillis());
    
    MapperContext context;
    
    @Before
    public void setUp() throws Exception {
        historyConfigInfoMapperByDerby = new HistoryConfigInfoMapperByDerby();
        context = new MapperContext(startRow, pageSize);
        context.putWhereParameter(FieldConstant.START_TIME, startTime);
        context.putWhereParameter(FieldConstant.END_TIME, endTime);
        context.putWhereParameter(FieldConstant.LIMIT_SIZE, limitSize);
        context.putWhereParameter(FieldConstant.LAST_MAX_ID, lastMaxId);
        context.putWhereParameter(FieldConstant.PAGE_SIZE, pageSize);
        
    }
    
    @Test
    public void testRemoveConfigHistory() {
        MapperResult mapperResult = historyConfigInfoMapperByDerby.removeConfigHistory(context);
        Assert.assertEquals(mapperResult.getSql(),
                "DELETE FROM his_config_info WHERE id IN( SELECT id FROM his_config_info WHERE gmt_modified < ? "
                        + "OFFSET 0 ROWS FETCH NEXT ? ROWS ONLY)");
        Assert.assertArrayEquals(mapperResult.getParamList().toArray(), new Object[] {startTime, limitSize});
    }
    
    @Test
    public void testFindConfigHistoryCountByTime() {
        MapperResult mapperResult = historyConfigInfoMapperByDerby.findConfigHistoryCountByTime(context);
        Assert.assertEquals(mapperResult.getSql(), "SELECT count(*) FROM his_config_info WHERE gmt_modified < ?");
        Assert.assertArrayEquals(mapperResult.getParamList().toArray(), new Object[] {startTime});
    }
    
    @Test
    public void testFindDeletedConfig() {
        MapperResult mapperResult = historyConfigInfoMapperByDerby.findDeletedConfig(context);
        Assert.assertEquals(mapperResult.getSql(),
                "SELECT data_id, group_id, tenant_id,gmt_modified,nid FROM his_config_info WHERE op_type = 'D' "
                        + "AND gmt_modified >= ? and nid > ? order by nid OFFSET 0 ROWS FETCH NEXT ? ROWS ONLY");
        
        Assert.assertArrayEquals(mapperResult.getParamList().toArray(), new Object[] {startTime, lastMaxId, pageSize});
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
        MapperResult mapperResult = historyConfigInfoMapperByDerby.findConfigHistoryFetchRows(context);
        Assert.assertEquals(mapperResult.getSql(),
                "SELECT nid,data_id,group_id,tenant_id,app_name,src_ip,src_user,op_type,gmt_create,gmt_modified FROM his_config_info "
                        + "WHERE data_id = ? AND group_id = ? AND tenant_id = ? ORDER BY nid DESC");
        Assert.assertArrayEquals(mapperResult.getParamList().toArray(), new Object[] {dataId, groupId, tenantId});
    }
    
    @Test
    public void testDetailPreviousConfigHistory() {
        Object id = "1";
        context.putWhereParameter(FieldConstant.ID, id);
        MapperResult mapperResult = historyConfigInfoMapperByDerby.detailPreviousConfigHistory(context);
        Assert.assertEquals(mapperResult.getSql(),
                "SELECT nid,data_id,group_id,tenant_id,app_name,content,md5,src_user,src_ip,op_type,gmt_create,"
                        + "gmt_modified,encrypted_data_key FROM his_config_info WHERE nid = (SELECT max(nid) FROM his_config_info WHERE id = ?)");
        Assert.assertArrayEquals(mapperResult.getParamList().toArray(), new Object[] {id});
    }
    
    @Test
    public void testGetTableName() {
        String tableName = historyConfigInfoMapperByDerby.getTableName();
        Assert.assertEquals(tableName, TableConstant.HIS_CONFIG_INFO);
    }
    
    @Test
    public void testGetDataSource() {
        String dataSource = historyConfigInfoMapperByDerby.getDataSource();
        Assert.assertEquals(dataSource, DataSourceConstant.DERBY);
    }
}
