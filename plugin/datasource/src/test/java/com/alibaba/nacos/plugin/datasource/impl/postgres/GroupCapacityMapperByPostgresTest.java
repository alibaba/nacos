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

package com.alibaba.nacos.plugin.datasource.impl.postgres;

import com.alibaba.nacos.plugin.datasource.constants.DataSourceConstant;
import com.alibaba.nacos.plugin.datasource.constants.TableConstant;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class GroupCapacityMapperByPostgresTest {
    
    private GroupCapacityMapperByPostgres groupCapacityMapperByPostgres;
    
    @Before
    public void setUp() throws Exception {
        groupCapacityMapperByPostgres = new GroupCapacityMapperByPostgres();
    }
    
    @Test
    public void testGetTableName() {
        String tableName = groupCapacityMapperByPostgres.getTableName();
        Assert.assertEquals(tableName, TableConstant.GROUP_CAPACITY);
    }
    
    @Test
    public void testGetDataSource() {
        String dataSource = groupCapacityMapperByPostgres.getDataSource();
        Assert.assertEquals(dataSource, DataSourceConstant.POSTGRES);
    }
    
    @Test
    public void testInsertIntoSelect() {
        String sql = groupCapacityMapperByPostgres.insertIntoSelect();
        Assert.assertEquals(sql,
                "INSERT INTO group_capacity (group_id, quota, `usage`, `max_size`, max_aggr_count, max_aggr_size,gmt_create,"
                        + " gmt_modified) SELECT ?, ?, count(*), ?, ?, ?, ?, ? FROM config_info");
    }
    
    @Test
    public void testInsertIntoSelectByWhere() {
        String sql = groupCapacityMapperByPostgres.insertIntoSelectByWhere();
        Assert.assertEquals(sql,
                "INSERT INTO group_capacity (group_id, quota,`usage`, `max_size`, max_aggr_count, max_aggr_size, gmt_create,"
                        + " gmt_modified) SELECT ?, ?, count(*), ?, ?, ?, ?, ? FROM config_info WHERE group_id=? AND tenant_id = ''");
    }
    
    @Test
    public void testIncrementUsageByWhereQuotaEqualZero() {
        String sql = groupCapacityMapperByPostgres.incrementUsageByWhereQuotaEqualZero();
        Assert.assertEquals(sql,
                "UPDATE group_capacity SET `usage` = `usage` + 1, gmt_modified = ? WHERE group_id = ? AND `usage` < ? AND quota = 0");
    }
    
    @Test
    public void testIncrementUsageByWhereQuotaNotEqualZero() {
        String sql = groupCapacityMapperByPostgres.incrementUsageByWhereQuotaNotEqualZero();
        Assert.assertEquals(sql,
                "UPDATE group_capacity SET `usage` = `usage` + 1, gmt_modified = ? WHERE group_id = ? AND `usage` < quota AND quota != 0");
    }
    
    @Test
    public void testIncrementUsageByWhere() {
        String sql = groupCapacityMapperByPostgres.incrementUsageByWhere();
        Assert.assertEquals(sql,
                "UPDATE group_capacity SET `usage` = `usage` + 1, gmt_modified = ? WHERE group_id = ?");
    }
    
    @Test
    public void testDecrementUsageByWhere() {
        String sql = groupCapacityMapperByPostgres.decrementUsageByWhere();
        Assert.assertEquals(sql,
                "UPDATE group_capacity SET `usage` = `usage` - 1, gmt_modified = ? WHERE group_id = ? AND `usage` > 0");
    }
    
    @Test
    public void testUpdateUsage() {
        String sql = groupCapacityMapperByPostgres.updateUsage();
        Assert.assertEquals(sql,
                "UPDATE group_capacity SET `usage` = (SELECT count(*) FROM config_info), gmt_modified = ? WHERE group_id = ?");
    }
    
    @Test
    public void testUpdateUsageByWhere() {
        String sql = groupCapacityMapperByPostgres.updateUsageByWhere();
        Assert.assertEquals(sql,
                "UPDATE group_capacity SET `usage` = (SELECT count(*) FROM config_info WHERE group_id=? AND tenant_id = ''),"
                        + " gmt_modified = ? WHERE group_id= ?");
    }
    
    @Test
    public void testSelectGroupInfoBySize() {
        String sql = groupCapacityMapperByPostgres.selectGroupInfoBySize();
        Assert.assertEquals(sql, "SELECT id, group_id FROM group_capacity WHERE id > ? LIMIT ?");
    }
}