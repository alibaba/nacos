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

public class GroupCapacityMapperByMysqlTest {
    
    private GroupCapacityMapperByMysql groupCapacityMapperByMysql;
    
    private final Object[] emptyObjs = new Object[] {};
    
    int startRow = 0;
    
    int pageSize = 5;
    
    Object groupId = "group";
    
    Object createTime = new Timestamp(System.currentTimeMillis());
    
    Object modified = new Timestamp(System.currentTimeMillis());
    
    MapperContext context;
    
    @Before
    public void setUp() throws Exception {
        groupCapacityMapperByMysql = new GroupCapacityMapperByMysql();
        context = new MapperContext(startRow, pageSize);
        context.putUpdateParameter(FieldConstant.GMT_MODIFIED, modified);
        
        context.putWhereParameter(FieldConstant.GMT_MODIFIED, modified);
        context.putWhereParameter(FieldConstant.GROUP_ID, groupId);
    }
    
    @Test
    public void testGetTableName() {
        String tableName = groupCapacityMapperByMysql.getTableName();
        Assert.assertEquals(tableName, TableConstant.GROUP_CAPACITY);
    }
    
    @Test
    public void testGetDataSource() {
        String dataSource = groupCapacityMapperByMysql.getDataSource();
        Assert.assertEquals(dataSource, DataSourceConstant.MYSQL);
    }
    
    @Test
    public void testInsertIntoSelect() {
        Object group = "group";
        Object quota = "quota";
        Object maxAggrSize = 10;
        Object maxAggrCount = 3;
        Object maxSize = 1;
        
        context.putUpdateParameter(FieldConstant.GROUP_ID, group);
        context.putUpdateParameter(FieldConstant.QUOTA, quota);
        context.putUpdateParameter(FieldConstant.MAX_SIZE, maxSize);
        context.putUpdateParameter(FieldConstant.MAX_AGGR_SIZE, maxAggrSize);
        context.putUpdateParameter(FieldConstant.MAX_AGGR_COUNT, maxAggrCount);
    
        context.putUpdateParameter(FieldConstant.GMT_CREATE, createTime);
        context.putUpdateParameter(FieldConstant.GMT_MODIFIED, modified);
        
        MapperResult mapperResult = groupCapacityMapperByMysql.insertIntoSelect(context);
        Assert.assertEquals(mapperResult.getSql(),
                "INSERT INTO group_capacity (group_id, quota, usage, max_size, max_aggr_count, max_aggr_size,gmt_create,"
                        + " gmt_modified) SELECT ?, ?, count(*), ?, ?, ?, ?, ? FROM config_info");
        
        Assert.assertArrayEquals(mapperResult.getParamList().toArray(),
                new Object[] {group, quota, maxSize, maxAggrCount, maxAggrSize, createTime, modified});
    }
    
    @Test
    public void testInsertIntoSelectByWhere() {
        Object group = "group";
        Object quota = "quota";
        Object maxAggrSize = 10;
        Object maxAggrCount = 3;
        Object maxSize = 1;
        Object createTime = new Timestamp(System.currentTimeMillis());
        Object modified = new Timestamp(System.currentTimeMillis());
        
        context.putUpdateParameter(FieldConstant.GROUP_ID, group);
        context.putUpdateParameter(FieldConstant.QUOTA, quota);
        context.putUpdateParameter(FieldConstant.MAX_SIZE, maxSize);
        context.putUpdateParameter(FieldConstant.MAX_AGGR_SIZE, maxAggrSize);
        context.putUpdateParameter(FieldConstant.MAX_AGGR_COUNT, maxAggrCount);
        context.putUpdateParameter(FieldConstant.GMT_CREATE, createTime);
        context.putUpdateParameter(FieldConstant.GMT_MODIFIED, modified);
        
        MapperResult mapperResult = groupCapacityMapperByMysql.insertIntoSelectByWhere(context);
        Assert.assertEquals(mapperResult.getSql(),
                "INSERT INTO group_capacity (group_id, quota, usage, max_size, max_aggr_count, max_aggr_size, gmt_create,"
                        + " gmt_modified) SELECT ?, ?, count(*), ?, ?, ?, ?, ? FROM config_info WHERE group_id=? AND tenant_id = ''");
        Assert.assertArrayEquals(mapperResult.getParamList().toArray(),
                new Object[] {group, quota, maxSize, maxAggrCount, maxAggrSize, createTime, modified, group});
    }
    
    @Test
    public void testIncrementUsageByWhereQuotaEqualZero() {
        Object usage = 1;
        context.putWhereParameter(FieldConstant.USAGE, usage);
        MapperResult mapperResult = groupCapacityMapperByMysql.incrementUsageByWhereQuotaEqualZero(context);
        Assert.assertEquals(mapperResult.getSql(),
                "UPDATE group_capacity SET usage = usage + 1, gmt_modified = ? WHERE group_id = ? AND usage < ? AND quota = 0");
        Assert.assertArrayEquals(mapperResult.getParamList().toArray(), new Object[] {modified, groupId, usage});
    }
    
    @Test
    public void testIncrementUsageByWhereQuotaNotEqualZero() {
        
        MapperResult mapperResult = groupCapacityMapperByMysql.incrementUsageByWhereQuotaNotEqualZero(context);
        Assert.assertEquals(mapperResult.getSql(),
                "UPDATE group_capacity SET usage = usage + 1, gmt_modified = ? WHERE group_id = ? AND usage < quota AND quota != 0");
        Assert.assertArrayEquals(mapperResult.getParamList().toArray(), new Object[] {modified, groupId});
    }
    
    @Test
    public void testIncrementUsageByWhere() {
        MapperResult mapperResult = groupCapacityMapperByMysql.incrementUsageByWhere(context);
        Assert.assertEquals(mapperResult.getSql(),
                "UPDATE group_capacity SET usage = usage + 1, gmt_modified = ? WHERE group_id = ?");
        Assert.assertArrayEquals(mapperResult.getParamList().toArray(), new Object[] {modified, groupId});
    }
    
    @Test
    public void testDecrementUsageByWhere() {
        MapperResult mapperResult = groupCapacityMapperByMysql.decrementUsageByWhere(context);
        Assert.assertEquals(mapperResult.getSql(),
                "UPDATE group_capacity SET usage = usage - 1, gmt_modified = ? WHERE group_id = ? AND usage > 0");
        Assert.assertArrayEquals(mapperResult.getParamList().toArray(), new Object[] {modified, groupId});
    }
    
    @Test
    public void testUpdateUsage() {
        MapperResult mapperResult = groupCapacityMapperByMysql.updateUsage(context);
        Assert.assertEquals(mapperResult.getSql(),
                "UPDATE group_capacity SET usage = (SELECT count(*) FROM config_info), gmt_modified = ? WHERE group_id = ?");
        Assert.assertArrayEquals(mapperResult.getParamList().toArray(), new Object[] {modified, groupId});
    }
    
    @Test
    public void testUpdateUsageByWhere() {
        MapperResult mapperResult = groupCapacityMapperByMysql.updateUsageByWhere(context);
        Assert.assertEquals(mapperResult.getSql(),
                "UPDATE group_capacity SET usage = (SELECT count(*) FROM config_info WHERE group_id=? AND tenant_id = ''),"
                        + " gmt_modified = ? WHERE group_id= ?");
        Assert.assertArrayEquals(mapperResult.getParamList().toArray(), new Object[] {groupId, modified, groupId});
    }
    
    @Test
    public void testSelectGroupInfoBySize() {
        Object id = 1;
        context.putWhereParameter(FieldConstant.ID, id);
        MapperResult mapperResult = groupCapacityMapperByMysql.selectGroupInfoBySize(context);
        Assert.assertEquals(mapperResult.getSql(), "SELECT id, group_id FROM group_capacity WHERE id > ? LIMIT ?");
        context.putWhereParameter(FieldConstant.GMT_CREATE, createTime);
        Assert.assertArrayEquals(mapperResult.getParamList().toArray(), new Object[] {id, pageSize});
    }
}
