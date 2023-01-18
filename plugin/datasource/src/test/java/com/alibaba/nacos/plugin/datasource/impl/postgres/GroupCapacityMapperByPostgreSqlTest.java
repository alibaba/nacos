package com.alibaba.nacos.plugin.datasource.impl.postgres;

import com.alibaba.nacos.plugin.datasource.constants.DataSourceConstant;
import com.alibaba.nacos.plugin.datasource.constants.TableConstant;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class GroupCapacityMapperByPostgreSqlTest {

    private GroupCapacityMapperByPostgreSql groupCapacityMapperByPostgreSql;

    @Before
    public void setUp() throws Exception {
        groupCapacityMapperByPostgreSql = new GroupCapacityMapperByPostgreSql();
    }

    @Test
    public void testGetTableName() {
        String tableName = groupCapacityMapperByPostgreSql.getTableName();
        Assert.assertEquals(tableName, TableConstant.GROUP_CAPACITY);
    }

    @Test
    public void testGetDataSource() {
        String dataSource = groupCapacityMapperByPostgreSql.getDataSource();
        Assert.assertEquals(dataSource, DataSourceConstant.POSTGRES);
    }

    @Test
    public void testInsertIntoSelect() {
        String sql = groupCapacityMapperByPostgreSql.insertIntoSelect();
        Assert.assertEquals(sql,
                "INSERT INTO group_capacity (group_id, quota, usage, max_size, max_aggr_count, max_aggr_size,gmt_create,"
                        + " gmt_modified) SELECT ?, ?, count(*), ?, ?, ?, ?, ? FROM config_info");
    }

    @Test
    public void testInsertIntoSelectByWhere() {
        String sql = groupCapacityMapperByPostgreSql.insertIntoSelectByWhere();
        Assert.assertEquals(sql,
                "INSERT INTO group_capacity (group_id, quota,usage, max_size, max_aggr_count, max_aggr_size, gmt_create,"
                        + " gmt_modified) SELECT ?, ?, count(*), ?, ?, ?, ?, ? FROM config_info WHERE group_id=? AND tenant_id = ''");
    }

    @Test
    public void testIncrementUsageByWhereQuotaEqualZero() {
        String sql = groupCapacityMapperByPostgreSql.incrementUsageByWhereQuotaEqualZero();
        Assert.assertEquals(sql,
                "UPDATE group_capacity SET usage = usage + 1, gmt_modified = ? WHERE group_id = ? AND usage < ? AND quota = 0");
    }

    @Test
    public void testIncrementUsageByWhereQuotaNotEqualZero() {
        String sql = groupCapacityMapperByPostgreSql.incrementUsageByWhereQuotaNotEqualZero();
        Assert.assertEquals(sql,
                "UPDATE group_capacity SET usage = usage + 1, gmt_modified = ? WHERE group_id = ? AND usage < quota AND quota != 0");
    }

    @Test
    public void testIncrementUsageByWhere() {
        String sql = groupCapacityMapperByPostgreSql.incrementUsageByWhere();
        Assert.assertEquals(sql,
                "UPDATE group_capacity SET usage = usage + 1, gmt_modified = ? WHERE group_id = ?");
    }

    @Test
    public void testDecrementUsageByWhere() {
        String sql = groupCapacityMapperByPostgreSql.decrementUsageByWhere();
        Assert.assertEquals(sql,
                "UPDATE group_capacity SET usage = usage - 1, gmt_modified = ? WHERE group_id = ? AND usage > 0");
    }

    @Test
    public void testUpdateUsage() {
        String sql = groupCapacityMapperByPostgreSql.updateUsage();
        Assert.assertEquals(sql,
                "UPDATE group_capacity SET usage = (SELECT count(*) FROM config_info), gmt_modified = ? WHERE group_id = ?");
    }

    @Test
    public void testUpdateUsageByWhere() {
        String sql = groupCapacityMapperByPostgreSql.updateUsageByWhere();
        Assert.assertEquals(sql,
                "UPDATE group_capacity SET usage = (SELECT count(*) FROM config_info WHERE group_id=? AND tenant_id = ''),"
                        + " gmt_modified = ? WHERE group_id= ?");
    }

    @Test
    public void testSelectGroupInfoBySize() {
        String sql = groupCapacityMapperByPostgreSql.selectGroupInfoBySize();
        Assert.assertEquals(sql, "SELECT id, group_id FROM group_capacity WHERE id > ? LIMIT ?");
    }
}
