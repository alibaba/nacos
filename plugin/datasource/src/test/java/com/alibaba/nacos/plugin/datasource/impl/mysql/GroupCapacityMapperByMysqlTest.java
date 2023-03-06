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
import com.alibaba.nacos.plugin.datasource.constants.TableConstant;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class GroupCapacityMapperByMysqlTest {
    
    private GroupCapacityMapperByMysql groupCapacityMapperByMysql;
    
    @Before
    public void setUp() throws Exception {
        groupCapacityMapperByMysql = new GroupCapacityMapperByMysql();
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
        String sql = groupCapacityMapperByMysql.insertIntoSelect();
        Assert.assertEquals(sql,
                "INSERT INTO group_capacity (group_id, quota, usage, max_size, max_aggr_count, max_aggr_size,gmt_create,"
                        + " gmt_modified) SELECT ?, ?, count(*), ?, ?, ?, ?, ? FROM config_info");
    }
    
    @Test
    public void testInsertIntoSelectByWhere() {
        String sql = groupCapacityMapperByMysql.insertIntoSelectByWhere();
        Assert.assertEquals(sql,
                "INSERT INTO group_capacity (group_id, quota,usage, max_size, max_aggr_count, max_aggr_size, gmt_create,"
                        + " gmt_modified) SELECT ?, ?, count(*), ?, ?, ?, ?, ? FROM config_info WHERE group_id=? AND tenant_id = ''");
    }
    
    @Test
    public void testIncrementUsageByWhereQuotaEqualZero() {
        String sql = groupCapacityMapperByMysql.incrementUsageByWhereQuotaEqualZero();
        Assert.assertEquals(sql,
                "UPDATE group_capacity SET usage = usage + 1, gmt_modified = ? WHERE group_id = ? AND usage < ? AND quota = 0");
    }
    
    @Test
    public void testIncrementUsageByWhereQuotaNotEqualZero() {
        String sql = groupCapacityMapperByMysql.incrementUsageByWhereQuotaNotEqualZero();
        Assert.assertEquals(sql,
                "UPDATE group_capacity SET usage = usage + 1, gmt_modified = ? WHERE group_id = ? AND usage < quota AND quota != 0");
    }
    
    @Test
    public void testIncrementUsageByWhere() {
        String sql = groupCapacityMapperByMysql.incrementUsageByWhere();
        Assert.assertEquals(sql,
                "UPDATE group_capacity SET usage = usage + 1, gmt_modified = ? WHERE group_id = ?");
    }
    
    @Test
    public void testDecrementUsageByWhere() {
        String sql = groupCapacityMapperByMysql.decrementUsageByWhere();
        Assert.assertEquals(sql,
                "UPDATE group_capacity SET usage = usage - 1, gmt_modified = ? WHERE group_id = ? AND usage > 0");
    }
    
    @Test
    public void testUpdateUsage() {
        String sql = groupCapacityMapperByMysql.updateUsage();
        Assert.assertEquals(sql,
                "UPDATE group_capacity SET usage = (SELECT count(*) FROM config_info), gmt_modified = ? WHERE group_id = ?");
    }
    
    @Test
    public void testUpdateUsageByWhere() {
        String sql = groupCapacityMapperByMysql.updateUsageByWhere();
        Assert.assertEquals(sql,
                "UPDATE group_capacity SET usage = (SELECT count(*) FROM config_info WHERE group_id=? AND tenant_id = ''),"
                        + " gmt_modified = ? WHERE group_id= ?");
    }
    
    @Test
    public void testSelectGroupInfoBySize() {
        String sql = groupCapacityMapperByMysql.selectGroupInfoBySize();
        Assert.assertEquals(sql, "SELECT id, group_id FROM group_capacity WHERE id > ? LIMIT ?");
    }
}